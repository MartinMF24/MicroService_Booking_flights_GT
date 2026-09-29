package com.uade.microservices.flights.service;

import com.uade.microservices.flights.config.FlightRapidApiProperties;
import com.uade.microservices.flights.dto.rapidapi.FlightRawDto;
import com.uade.microservices.flights.dto.rapidapi.RapidApiFlightResponseDto;
import com.uade.microservices.flights.model.FlightDirection;
import com.uade.microservices.flights.model.GranPremioTarget;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Fase EXTRACT del módulo ETL.
 * Se encarga de consumir la API de Booking Flights en RapidAPI y mapear la respuesta cruda a DTOs.
 */
@Service
public class FlightClientService {

    private static final Logger log = LoggerFactory.getLogger(FlightClientService.class);

    private final RestTemplate restTemplate;
    private final FlightRapidApiProperties properties;
    private final ObjectMapper objectMapper;

    public FlightClientService(
            @Qualifier("flightRestTemplate") RestTemplate restTemplate,
            FlightRapidApiProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Consume la API externa de RapidAPI Booking Flights para una ruta (origen -> destino) y fecha dada.
     * En caso de credenciales ausentes o fallas de red/cuota, activa un fallback con vuelos representativos
     * para garantizar continuidad operativa y resiliencia.
     */
    public List<FlightRawDto> extractFlights(String originAirport, String destAirport, LocalDate flightDate,
                                             GranPremioTarget target, FlightDirection direction) {
        if (!properties.hasValidCredentials()) {
            log.warn("RapidAPI Key no configurada o es inválida (RAPIDAPI_KEY). Usando datos de simulación para {} ({}) {} -> {}.",
                    target.name(), direction, originAirport, destAirport);
            return generateSimulatedFlights(target, originAirport, destAirport, flightDate, direction);
        }

        try {
            URI uri = buildRequestUri(originAirport, destAirport, flightDate);
            HttpHeaders headers = buildRequestHeaders();
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            log.info("Llamando a RapidAPI Booking Flights: {} (target={}, dir={}, origen={}, destino={}, fecha={})",
                    uri, target.name(), direction, originAirport, destAirport, flightDate);

            ResponseEntity<String> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseApiResponse(response.getBody(), originAirport, destAirport, flightDate, target, direction);
            }

            log.warn("Respuesta inesperada de RapidAPI Booking Flights (HTTP {}). Activando fallback representativo.", response.getStatusCode());
            return generateSimulatedFlights(target, originAirport, destAirport, flightDate, direction);

        } catch (HttpStatusCodeException ex) {
            log.warn("Error HTTP al invocar RapidAPI Booking Flights [{} - {}]: {}. Activando fallback representativo.",
                    ex.getStatusCode(), ex.getStatusText(), ex.getResponseBodyAsString());
            return generateSimulatedFlights(target, originAirport, destAirport, flightDate, direction);

        } catch (ResourceAccessException ex) {
            log.warn("Timeout o falla de red al invocar RapidAPI Booking Flights: {}. Activando fallback representativo.", ex.getMessage());
            return generateSimulatedFlights(target, originAirport, destAirport, flightDate, direction);

        } catch (Exception ex) {
            log.error("Excepción inesperada al invocar RapidAPI Booking Flights: {}. Activando fallback representativo.", ex.getMessage(), ex);
            return generateSimulatedFlights(target, originAirport, destAirport, flightDate, direction);
        }
    }

    private URI buildRequestUri(String fromId, String toId, LocalDate flightDate) {
        String baseUrl = properties.getBaseUrl();
        String endpoint = properties.getEndpoint();

        String fromParam = fromId.contains(".") ? fromId : fromId + ".AIRPORT";
        String toParam = toId.contains(".") ? toId : toId + ".AIRPORT";

        return UriComponentsBuilder.fromUriString(baseUrl)
                .path(endpoint)
                .queryParam("fromId", fromParam)
                .queryParam("toId", toParam)
                .queryParam("departDate", flightDate.toString())
                .queryParam("pageNo", 1)
                .queryParam("adults", 1)
                .queryParam("currency_code", "USD")
                .queryParam("cabinClass", "ECONOMY")
                .queryParam("sort", "BEST")
                .build()
                .toUri();
    }

    private HttpHeaders buildRequestHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-rapidapi-key", properties.getKey());
        headers.set("x-rapidapi-host", properties.getHost());
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }

    /**
     * Parsea el JSON crudo retornado por RapidAPI. Soporta varias variantes estructurales.
     */
    private List<FlightRawDto> parseApiResponse(String rawJson, String originAirport, String destAirport,
                                                LocalDate flightDate, GranPremioTarget target, FlightDirection direction) {
        List<FlightRawDto> extracted = new ArrayList<>();
        try {
            JsonNode rootNode = objectMapper.readTree(rawJson);

            // Variante 1: data -> flightOffers
            if (rootNode.has("data") && rootNode.get("data").has("flightOffers") && rootNode.get("data").get("flightOffers").isArray()) {
                for (JsonNode offer : rootNode.get("data").get("flightOffers")) {
                    FlightRawDto dto = parseOfferNode(offer, originAirport, destAirport, flightDate);
                    if (dto != null) {
                        extracted.add(dto);
                    }
                }
            }

            // Variante 2: flights array en root o data
            if (extracted.isEmpty() && rootNode.has("flights") && rootNode.get("flights").isArray()) {
                for (JsonNode node : rootNode.get("flights")) {
                    extracted.add(objectMapper.treeToValue(node, FlightRawDto.class));
                }
            }

            // Variante 3: result array
            if (extracted.isEmpty() && rootNode.has("result") && rootNode.get("result").isArray()) {
                for (JsonNode node : rootNode.get("result")) {
                    extracted.add(objectMapper.treeToValue(node, FlightRawDto.class));
                }
            }

            if (!extracted.isEmpty()) {
                log.info("Extracción exitosa desde RapidAPI Flights: {} vuelos obtenidos para {} ({} -> {}).",
                        extracted.size(), target.name(), originAirport, destAirport);
                return extracted;
            }

        } catch (Exception e) {
            log.error("Error al parsear el JSON de RapidAPI Booking Flights: {}", e.getMessage(), e);
        }

        log.warn("No se encontraron vuelos en la respuesta JSON de RapidAPI. Empleando fallback simulado para {} ({}).",
                target.name(), direction);
        return generateSimulatedFlights(target, originAirport, destAirport, flightDate, direction);
    }

    private FlightRawDto parseOfferNode(JsonNode offer, String originAirport, String destAirport, LocalDate flightDate) {
        FlightRawDto dto = new FlightRawDto();
        dto.setDepartureAirport(originAirport);
        dto.setArrivalAirport(destAirport);

        // Segmentos / Itinerarios
        if (offer.has("segments") && offer.get("segments").isArray() && !offer.get("segments").isEmpty()) {
            JsonNode seg = offer.get("segments").get(0);
            if (seg.has("departureTime")) {
                dto.setDepartureTime(seg.get("departureTime").asText());
            }
            if (seg.has("arrivalTime")) {
                dto.setArrivalTime(seg.get("arrivalTime").asText());
            }
            if (seg.has("legs") && seg.get("legs").isArray() && !seg.get("legs").isEmpty()) {
                JsonNode leg = seg.get("legs").get(0);
                if (leg.has("flightInfo") && leg.get("flightInfo").has("carrierInfo")) {
                    JsonNode carrier = leg.get("flightInfo").get("carrierInfo");
                    if (carrier.has("operatingCarrierName")) {
                        dto.setAirline(carrier.get("operatingCarrierName").asText());
                    }
                }
            }
        }

        // Precio
        if (offer.has("priceBreakdown") && offer.get("priceBreakdown").has("total")) {
            JsonNode total = offer.get("priceBreakdown").get("total");
            if (total.has("units")) {
                double units = total.get("units").asDouble();
                double nanos = total.has("nanos") ? total.get("nanos").asDouble() / 1_000_000_000.0 : 0.0;
                dto.setPrice(units + nanos);
            }
        } else if (offer.has("price")) {
            dto.setPrice(offer.get("price").asDouble());
        }

        // Asientos disponibles
        if (offer.has("availableSeats")) {
            dto.setAvailableSeats(offer.get("availableSeats").asInt());
        }

        return dto;
    }

    /**
     * Generador de vuelos representativos coherentes con la ruta y sede del Gran Premio.
     */
    public List<FlightRawDto> generateSimulatedFlights(GranPremioTarget target, String originAirport,
                                                        String destAirport, LocalDate flightDate,
                                                        FlightDirection direction) {
        List<FlightRawDto> list = new ArrayList<>();
        LocalDate baseDate = flightDate != null ? flightDate : LocalDate.now().plusDays(15);

        // Aerolíneas y precios según el circuito
        List<AirlinesConfig> configs = getAirlinesForTarget(target);

        int durationHours = getEstimatedDurationHours(target);

        for (int i = 0; i < configs.size(); i++) {
            AirlinesConfig config = configs.get(i);
            LocalTime depTime;
            if (direction == FlightDirection.IDA) {
                depTime = LocalTime.of(7 + (i * 5), 15 + (i * 10)); // e.g. 07:15, 12:25, 17:35
            } else {
                depTime = LocalTime.of(8 + (i * 4), 30 + (i * 10)); // e.g. 08:30, 12:40, 16:50
            }

            String depIso = baseDate.atTime(depTime).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String arrIso = baseDate.atTime(depTime).plusHours(durationHours).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            double price = config.basePriceUsd() + (direction == FlightDirection.VUELTA ? 35.0 : 0.0) + (i * 45.0);

            list.add(new FlightRawDto(
                    config.airlineName(),
                    config.code() + "-" + (1000 + (i * 105)),
                    originAirport,
                    destAirport,
                    depIso,
                    arrIso,
                    price,
                    15 + (i * 8) // Asientos iniciales
            ));
        }

        return list;
    }

    private record AirlinesConfig(String airlineName, String code, double basePriceUsd) {}

    private List<AirlinesConfig> getAirlinesForTarget(GranPremioTarget target) {
        return switch (target) {
            case MADRID -> List.of(
                    new AirlinesConfig("Iberia", "IB", 820.0),
                    new AirlinesConfig("Aerolíneas Argentinas", "AR", 890.0),
                    new AirlinesConfig("Air Europa", "UX", 760.0)
            );
            case BAKU -> List.of(
                    new AirlinesConfig("Turkish Airlines", "TK", 1150.0),
                    new AirlinesConfig("Qatar Airways", "QR", 1280.0),
                    new AirlinesConfig("Lufthansa", "LH", 1210.0)
            );
            case SINGAPUR -> List.of(
                    new AirlinesConfig("Singapore Airlines", "SQ", 1450.0),
                    new AirlinesConfig("Emirates", "EK", 1380.0),
                    new AirlinesConfig("Qatar Airways", "QR", 1320.0)
            );
            case AUSTIN -> List.of(
                    new AirlinesConfig("American Airlines", "AA", 980.0),
                    new AirlinesConfig("United Airlines", "UA", 940.0),
                    new AirlinesConfig("Delta Air Lines", "DL", 995.0)
            );
            case CIUDAD_DE_MEXICO -> List.of(
                    new AirlinesConfig("Aeroméxico", "AM", 790.0),
                    new AirlinesConfig("Copa Airlines", "CM", 690.0),
                    new AirlinesConfig("Avianca", "AV", 720.0)
            );
            case SAO_PAULO -> List.of(
                    new AirlinesConfig("LATAM Airlines", "LA", 320.0),
                    new AirlinesConfig("Aerolíneas Argentinas", "AR", 350.0),
                    new AirlinesConfig("Gol Linhas Aéreas", "G3", 290.0)
            );
            case LAS_VEGAS -> List.of(
                    new AirlinesConfig("American Airlines", "AA", 1020.0),
                    new AirlinesConfig("Delta Air Lines", "DL", 1080.0),
                    new AirlinesConfig("United Airlines", "UA", 990.0)
            );
            case LUSAIL -> List.of(
                    new AirlinesConfig("Qatar Airways", "QR", 1390.0),
                    new AirlinesConfig("Emirates", "EK", 1420.0),
                    new AirlinesConfig("Turkish Airlines", "TK", 1250.0)
            );
            case ABU_DABI -> List.of(
                    new AirlinesConfig("Etihad Airways", "EY", 1360.0),
                    new AirlinesConfig("Emirates", "EK", 1390.0),
                    new AirlinesConfig("Turkish Airlines", "TK", 1220.0)
            );
            case SAKHIR -> List.of(
                    new AirlinesConfig("Gulf Air", "GF", 950.0),
                    new AirlinesConfig("Qatar Airways", "QR", 1050.0),
                    new AirlinesConfig("Emirates", "EK", 1020.0)
            );
            case YEDA -> List.of(
                    new AirlinesConfig("Saudia", "SV", 980.0),
                    new AirlinesConfig("Qatar Airways", "QR", 1080.0),
                    new AirlinesConfig("Emirates", "EK", 1040.0)
            );
            case MELBOURNE -> List.of(
                    new AirlinesConfig("Qantas", "QF", 1650.0),
                    new AirlinesConfig("LATAM Airlines", "LA", 1520.0),
                    new AirlinesConfig("Qatar Airways", "QR", 1580.0)
            );
            case SUZUKA -> List.of(
                    new AirlinesConfig("Japan Airlines", "JL", 1420.0),
                    new AirlinesConfig("All Nippon Airways", "NH", 1390.0),
                    new AirlinesConfig("Emirates", "EK", 1450.0)
            );
            case SHANGHAI -> List.of(
                    new AirlinesConfig("China Eastern", "MU", 1320.0),
                    new AirlinesConfig("Air China", "CA", 1290.0),
                    new AirlinesConfig("Qatar Airways", "QR", 1350.0)
            );
            case MIAMI -> List.of(
                    new AirlinesConfig("American Airlines", "AA", 750.0),
                    new AirlinesConfig("Aerolíneas Argentinas", "AR", 780.0),
                    new AirlinesConfig("Delta Air Lines", "DL", 740.0)
            );
            case MONTREAL -> List.of(
                    new AirlinesConfig("Air Canada", "AC", 890.0),
                    new AirlinesConfig("American Airlines", "AA", 860.0),
                    new AirlinesConfig("Copa Airlines", "CM", 820.0)
            );
            case MONTECARLO -> List.of(
                    new AirlinesConfig("Air France", "AF", 890.0),
                    new AirlinesConfig("Iberia", "IB", 840.0),
                    new AirlinesConfig("Lufthansa", "LH", 870.0)
            );
            case PORTIMAO -> List.of(
                    new AirlinesConfig("TAP Air Portugal", "TP", 780.0),
                    new AirlinesConfig("Iberia", "IB", 760.0),
                    new AirlinesConfig("Air Europa", "UX", 730.0)
            );
            case SILVERSTONE -> List.of(
                    new AirlinesConfig("British Airways", "BA", 890.0),
                    new AirlinesConfig("LATAM Airlines", "LA", 860.0),
                    new AirlinesConfig("Air Europa", "UX", 820.0)
            );
        };
    }

    private int getEstimatedDurationHours(GranPremioTarget target) {
        return switch (target) {
            case SAO_PAULO -> 3;
            case CIUDAD_DE_MEXICO, MIAMI -> 9;
            case MADRID, MONTREAL -> 12;
            case AUSTIN, LAS_VEGAS, PORTIMAO, SILVERSTONE -> 13;
            case MONTECARLO -> 14;
            case BAKU, LUSAIL, ABU_DABI, SAKHIR, YEDA, MELBOURNE -> 19;
            case SINGAPUR, SUZUKA, SHANGHAI -> 24;
        };
    }
}
