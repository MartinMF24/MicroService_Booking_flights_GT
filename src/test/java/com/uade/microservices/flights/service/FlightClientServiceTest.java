package com.uade.microservices.flights.service;

import com.uade.microservices.flights.config.FlightRapidApiProperties;
import com.uade.microservices.flights.dto.rapidapi.FlightRawDto;
import com.uade.microservices.flights.model.FlightDirection;
import com.uade.microservices.flights.model.GranPremioTarget;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlightClientServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private FlightRapidApiProperties properties;
    private ObjectMapper objectMapper;
    private FlightClientService clientService;

    @BeforeEach
    void setUp() {
        properties = new FlightRapidApiProperties();
        objectMapper = new ObjectMapper();
        clientService = new FlightClientService(restTemplate, properties, objectMapper);
    }

    @Test
    @DisplayName("Debe activar el fallback de simulación si no se definieron credenciales de RapidAPI")
    void shouldReturnSimulatedFlightsWhenNoCredentials() {
        properties.setKey(""); // Sin credenciales

        List<FlightRawDto> result = clientService.extractFlights(
                "EZE", "MAD", LocalDate.parse("2026-09-10"),
                GranPremioTarget.MADRID, FlightDirection.IDA
        );

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(3, result.size());
        assertEquals("EZE", result.get(0).getDepartureAirport());
        assertEquals("MAD", result.get(0).getArrivalAirport());
    }

    @Test
    @DisplayName("Debe activar fallback ante error HTTP 429 (Cuota excedida) sin lanzar excepción")
    void shouldHandleHttp429GracefullyWithFallback() {
        properties.setKey("valid_rapidapi_key_test");
        when(restTemplate.exchange(any(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "Quota Exceeded"));

        List<FlightRawDto> result = clientService.extractFlights(
                "EZE", "GRU", LocalDate.parse("2026-11-05"),
                GranPremioTarget.SAO_PAULO, FlightDirection.IDA
        );

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("Debe activar fallback ante timeout de red (ResourceAccessException)")
    void shouldHandleTimeoutGracefullyWithFallback() {
        properties.setKey("valid_rapidapi_key_test");
        when(restTemplate.exchange(any(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("Read timed out"));

        List<FlightRawDto> result = clientService.extractFlights(
                "SIN", "EZE", LocalDate.parse("2026-10-11"),
                GranPremioTarget.SINGAPUR, FlightDirection.VUELTA
        );

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("Debe parsear correctamente una respuesta JSON válida de RapidAPI")
    void shouldParseValidApiResponse() {
        properties.setKey("valid_rapidapi_key_test");
        String json = """
        {
          "data": {
            "flightOffers": [
              {
                "segments": [
                  {
                    "departureTime": "2026-09-10T14:00:00Z",
                    "arrivalTime": "2026-09-11T06:00:00Z",
                    "legs": [
                      {
                        "flightInfo": {
                          "carrierInfo": {
                            "operatingCarrierName": "Iberia"
                          }
                        }
                      }
                    ]
                  }
                ],
                "priceBreakdown": {
                  "total": {
                    "units": 820.0
                  }
                },
                "availableSeats": 18
              }
            ]
          }
        }
        """;

        when(restTemplate.exchange(any(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(json, HttpStatus.OK));

        List<FlightRawDto> result = clientService.extractFlights(
                "EZE", "MAD", LocalDate.parse("2026-09-10"),
                GranPremioTarget.MADRID, FlightDirection.IDA
        );

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Iberia", result.get(0).getAirline());
        assertEquals(820.0, result.get(0).getPrice());
        assertEquals(18, result.get(0).getAvailableSeats());
    }
}
