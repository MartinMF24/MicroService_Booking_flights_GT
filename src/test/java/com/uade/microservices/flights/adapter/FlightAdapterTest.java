package com.uade.microservices.flights.adapter;

import com.uade.microservices.flights.dto.rapidapi.FlightRawDto;
import com.uade.microservices.flights.model.Vuelo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightAdapterTest {

    private FlightAdapter adapter;
    private UUID ciudadOrigenId;
    private UUID ciudadDestinoId;

    @BeforeEach
    void setUp() {
        adapter = new FlightAdapter();
        ciudadOrigenId = UUID.randomUUID();
        ciudadDestinoId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Debe transformar un FlightRawDto en Vuelo con stock simulado entre 5 y 50 cuando no viene provisto")
    void shouldTransformRawDtoToVueloWithSimulatedStock() {
        FlightRawDto raw = new FlightRawDto();
        raw.setAirline("Iberia");
        raw.setFlightNumber("IB-6844");
        raw.setPrice(850.0);
        raw.setDepartureTime("2026-09-10T13:30:00Z");
        raw.setArrivalTime("2026-09-11T05:45:00Z");
        // No se asigna availableSeats

        LocalDate flightDate = LocalDate.parse("2026-09-10");
        Vuelo vuelo = adapter.toEntity(raw, ciudadOrigenId, ciudadDestinoId, flightDate);

        assertNotNull(vuelo);
        assertEquals("Iberia", vuelo.getAerolinea());
        assertEquals(ciudadOrigenId, vuelo.getOrigenIdCiudad());
        assertEquals(ciudadDestinoId, vuelo.getDestinoIdCiudad());
        assertEquals(BigDecimal.valueOf(850.00).setScale(2), vuelo.getPrecioUsd());
        assertEquals(OffsetDateTime.parse("2026-09-10T13:30:00Z"), vuelo.getFechaSalida());
        assertEquals(OffsetDateTime.parse("2026-09-11T05:45:00Z"), vuelo.getFechaLlegada());

        // Verificar simulación de stock entre 5 y 50
        assertNotNull(vuelo.getStockAsientos());
        assertTrue(vuelo.getStockAsientos() >= 5 && vuelo.getStockAsientos() <= 50,
                "El stock simulado debe estar entre 5 y 50 pero fue: " + vuelo.getStockAsientos());
    }

    @Test
    @DisplayName("Debe conservar el stock de asientos cuando la API sí lo provee")
    void shouldPreserveStockWhenProvidedByApi() {
        FlightRawDto raw = new FlightRawDto();
        raw.setAirline("LATAM Airlines");
        raw.setPrice(340.0);
        raw.setAvailableSeats(28);

        LocalDate flightDate = LocalDate.parse("2026-11-05");
        Vuelo vuelo = adapter.toEntity(raw, ciudadOrigenId, ciudadDestinoId, flightDate);

        assertNotNull(vuelo);
        assertEquals(28, vuelo.getStockAsientos());
    }

    @Test
    @DisplayName("Debe truncar el nombre de la aerolínea si excede los 100 caracteres")
    void shouldTruncateAirlineExceeding100Chars() {
        String longAirline = "A".repeat(120);
        FlightRawDto raw = new FlightRawDto();
        raw.setAirline(longAirline);
        raw.setPrice(500.0);

        LocalDate flightDate = LocalDate.parse("2026-09-10");
        Vuelo vuelo = adapter.toEntity(raw, ciudadOrigenId, ciudadDestinoId, flightDate);

        assertEquals(100, vuelo.getAerolinea().length());
    }

    @Test
    @DisplayName("Debe lanzar IllegalArgumentException ante argumentos nulos obligatorios")
    void shouldThrowExceptionOnNullArgs() {
        FlightRawDto raw = new FlightRawDto();
        LocalDate now = LocalDate.now();

        assertThrows(IllegalArgumentException.class, () -> adapter.toEntity(null, ciudadOrigenId, ciudadDestinoId, now));
        assertThrows(IllegalArgumentException.class, () -> adapter.toEntity(raw, null, ciudadDestinoId, now));
        assertThrows(IllegalArgumentException.class, () -> adapter.toEntity(raw, ciudadOrigenId, null, now));
    }

    @Test
    @DisplayName("Debe mapear una lista completa de DTOs crudos")
    void shouldMapEntityList() {
        FlightRawDto dto1 = new FlightRawDto("Aerolíneas Argentinas", "AR-1132", "EZE", "MAD", null, null, 900.0, 15);
        FlightRawDto dto2 = new FlightRawDto("Air Europa", "UX-042", "EZE", "MAD", null, null, 780.0, null);

        List<Vuelo> vuelos = adapter.toEntityList(List.of(dto1, dto2), ciudadOrigenId, ciudadDestinoId, LocalDate.parse("2026-09-10"));

        assertEquals(2, vuelos.size());
        assertEquals("Aerolíneas Argentinas", vuelos.get(0).getAerolinea());
        assertEquals("Air Europa", vuelos.get(1).getAerolinea());
        assertTrue(vuelos.get(1).getStockAsientos() >= 5 && vuelos.get(1).getStockAsientos() <= 50);
    }
}
