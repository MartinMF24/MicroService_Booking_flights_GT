package com.uade.microservices.flights.dto.response;

import com.uade.microservices.flights.model.FlightDirection;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resumen de un vuelo sincronizado en la base de datos Supabase.
 */
public record FlightSummaryDto(
        UUID idVuelo,
        String aerolinea,
        UUID origenIdCiudad,
        UUID destinoIdCiudad,
        OffsetDateTime fechaSalida,
        OffsetDateTime fechaLlegada,
        BigDecimal precioUsd,
        Integer stockAsientos,
        FlightDirection direccion
) {
}
