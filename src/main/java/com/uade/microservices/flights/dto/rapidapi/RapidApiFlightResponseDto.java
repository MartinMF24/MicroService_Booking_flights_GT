package com.uade.microservices.flights.dto.rapidapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Contenedor general para respuestas JSON provenientes de Booking RapidAPI Flights.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RapidApiFlightResponseDto {

    @JsonProperty("status")
    private Boolean status;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private Object data;

    @JsonProperty("flights")
    private List<FlightRawDto> flights = new ArrayList<>();

    public RapidApiFlightResponseDto() {
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public List<FlightRawDto> getFlights() {
        return flights;
    }

    public void setFlights(List<FlightRawDto> flights) {
        this.flights = flights;
    }
}
