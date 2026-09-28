package com.uade.microservices.flights.dto.rapidapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Random;

/**
 * DTO tolerante para capturar la información cruda de un vuelo desde la API de Booking / RapidAPI.
 * Soporta múltiples convenciones de nombres JSON y provee métodos resolutores seguros.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlightRawDto {

    private static final Random RANDOM = new Random();

    @JsonProperty("airline")
    private String airline;

    @JsonProperty("carrier_name")
    private String carrierName;

    @JsonProperty("operating_carrier_name")
    private String operatingCarrierName;

    @JsonProperty("flight_number")
    private String flightNumber;

    @JsonProperty("departure_time")
    private String departureTime;

    @JsonProperty("arrival_time")
    private String arrivalTime;

    @JsonProperty("departure_airport")
    private String departureAirport;

    @JsonProperty("arrival_airport")
    private String arrivalAirport;

    @JsonProperty("price")
    private Double price;

    @JsonProperty("total_price")
    private Double totalPrice;

    @JsonProperty("price_usd")
    private Double priceUsd;

    @JsonProperty("currency_code")
    private String currencyCode;

    @JsonProperty("available_seats")
    private Integer availableSeats;

    @JsonProperty("seats")
    private Integer seats;

    public FlightRawDto() {
    }

    public FlightRawDto(String airline, String flightNumber, String departureAirport, String arrivalAirport,
                         String departureTime, String arrivalTime, Double price, Integer availableSeats) {
        this.airline = airline;
        this.flightNumber = flightNumber;
        this.departureAirport = departureAirport;
        this.arrivalAirport = arrivalAirport;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.price = price;
        this.availableSeats = availableSeats;
    }

    /**
     * Resuelve el nombre de la aerolínea truncándolo a 100 caracteres.
     */
    public String resolveAirline(String fallbackAirline) {
        String name = null;
        if (airline != null && !airline.isBlank()) {
            name = airline.trim();
        } else if (carrierName != null && !carrierName.isBlank()) {
            name = carrierName.trim();
        } else if (operatingCarrierName != null && !operatingCarrierName.isBlank()) {
            name = operatingCarrierName.trim();
        } else {
            name = fallbackAirline != null ? fallbackAirline : "Aerolínea Internacional";
        }

        return name.length() > 100 ? name.substring(0, 100).trim() : name;
    }

    /**
     * Resuelve la fecha y hora de salida (OffsetDateTime UTC).
     * Si no viene informada en el DTO, genera una hora coherente con la fecha base indicada.
     */
    public OffsetDateTime resolveFechaSalida(LocalDate flightDate, int defaultHour, int defaultMinute) {
        if (departureTime != null && !departureTime.isBlank()) {
            try {
                return parseToOffsetDateTime(departureTime.trim());
            } catch (Exception ignored) {
                // Fallback a generación basada en la fecha provista
            }
        }
        LocalDate date = flightDate != null ? flightDate : LocalDate.now().plusDays(30);
        return date.atTime(defaultHour, defaultMinute).atOffset(ZoneOffset.UTC);
    }

    /**
     * Resuelve la fecha y hora de llegada (OffsetDateTime UTC).
     * Si no viene informada en el DTO, suma una duración estimada (horas) a la fecha de salida.
     */
    public OffsetDateTime resolveFechaLlegada(OffsetDateTime salida, int flightDurationHours) {
        if (arrivalTime != null && !arrivalTime.isBlank()) {
            try {
                OffsetDateTime parsedArrival = parseToOffsetDateTime(arrivalTime.trim());
                if (parsedArrival.isAfter(salida)) {
                    return parsedArrival;
                }
            } catch (Exception ignored) {
                // Fallback a cálculo por duración
            }
        }
        return salida.plusHours(Math.max(1, flightDurationHours));
    }

    /**
     * Resuelve el precio en USD como BigDecimal con precisión (10, 2).
     */
    public BigDecimal resolvePrecio(double fallbackPrice) {
        Double resolved = null;
        if (priceUsd != null && priceUsd > 0) {
            resolved = priceUsd;
        } else if (totalPrice != null && totalPrice > 0) {
            resolved = totalPrice;
        } else if (price != null && price > 0) {
            resolved = price;
        } else {
            resolved = fallbackPrice;
        }
        return BigDecimal.valueOf(resolved).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Resuelve el stock de asientos disponibles. Si viene nulo o <= 0, retorna null para que el adapter lo simule.
     */
    public Integer resolveStockAsientos() {
        if (availableSeats != null && availableSeats > 0) {
            return availableSeats;
        }
        if (seats != null && seats > 0) {
            return seats;
        }
        return null;
    }

    private OffsetDateTime parseToOffsetDateTime(String text) {
        // Intentar parseo directo estándar ISO_OFFSET_DATE_TIME (ej: 2026-09-10T14:30:00Z o +02:00)
        try {
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
        }

        // Intentar parseo LocalDateTime (ej: 2026-09-10T14:30:00) asumiendo UTC
        try {
            return java.time.LocalDateTime.parse(text).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }

        // Intentar parseo con formato común "yyyy-MM-dd HH:mm:ss"
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return java.time.LocalDateTime.parse(text, formatter).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }

        throw new IllegalArgumentException("No se pudo parsear la fecha/hora de vuelo: " + text);
    }

    // Getters y Setters
    public String getAirline() {
        return airline;
    }

    public void setAirline(String airline) {
        this.airline = airline;
    }

    public String getCarrierName() {
        return carrierName;
    }

    public void setCarrierName(String carrierName) {
        this.carrierName = carrierName;
    }

    public String getOperatingCarrierName() {
        return operatingCarrierName;
    }

    public void setOperatingCarrierName(String operatingCarrierName) {
        this.operatingCarrierName = operatingCarrierName;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public void setFlightNumber(String flightNumber) {
        this.flightNumber = flightNumber;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }

    public String getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(String arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public String getDepartureAirport() {
        return departureAirport;
    }

    public void setDepartureAirport(String departureAirport) {
        this.departureAirport = departureAirport;
    }

    public String getArrivalAirport() {
        return arrivalAirport;
    }

    public void setArrivalAirport(String arrivalAirport) {
        this.arrivalAirport = arrivalAirport;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(Double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public Double getPriceUsd() {
        return priceUsd;
    }

    public void setPriceUsd(Double priceUsd) {
        this.priceUsd = priceUsd;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public Integer getAvailableSeats() {
        return availableSeats;
    }

    public void setAvailableSeats(Integer availableSeats) {
        this.availableSeats = availableSeats;
    }

    public Integer getSeats() {
        return seats;
    }

    public void setSeats(Integer seats) {
        this.seats = seats;
    }
}
