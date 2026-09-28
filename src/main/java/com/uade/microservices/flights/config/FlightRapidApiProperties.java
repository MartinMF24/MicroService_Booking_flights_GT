package com.uade.microservices.flights.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Propiedades de configuración inyectadas para RapidAPI - Booking Flights.
 * Soporta variables de entorno directas o desde application.properties.
 */
@Component
public class FlightRapidApiProperties {

    @Value("${rapidapi.booking.key:}")
    private String key;

    @Value("${rapidapi.booking.host:booking-com15.p.rapidapi.com}")
    private String host;

    @Value("${rapidapi.booking.base-url:https://booking-com15.p.rapidapi.com}")
    private String baseUrl;

    @Value("${rapidapi.booking.endpoint:/api/v1/flights/searchFlights}")
    private String endpoint;

    @Value("${flights.origin.iata:EZE}")
    private String originIata;

    @Value("${flights.origin.city-name:Buenos Aires}")
    private String originCityName;

    public FlightRapidApiProperties() {
    }

    public String getKey() {
        return key != null ? key.trim() : "";
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getHost() {
        return host != null ? host.trim() : "";
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getBaseUrl() {
        return baseUrl != null ? baseUrl.trim() : "";
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getEndpoint() {
        return endpoint != null ? endpoint.trim() : "";
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getOriginIata() {
        return originIata != null ? originIata.trim() : "EZE";
    }

    public void setOriginIata(String originIata) {
        this.originIata = originIata;
    }

    public String getOriginCityName() {
        return originCityName != null ? originCityName.trim() : "Buenos Aires";
    }

    public void setOriginCityName(String originCityName) {
        this.originCityName = originCityName;
    }

    public boolean hasValidCredentials() {
        return key != null && !key.isBlank() && !key.equalsIgnoreCase("dummy") && !key.equalsIgnoreCase("your_rapidapi_key");
    }
}
