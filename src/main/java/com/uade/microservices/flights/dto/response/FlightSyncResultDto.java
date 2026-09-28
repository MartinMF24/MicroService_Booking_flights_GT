package com.uade.microservices.flights.dto.response;

import com.uade.microservices.flights.model.GranPremioTarget;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Resultado de la sincronización ETL de vuelos para un Gran Premio específico.
 */
public record FlightSyncResultDto(
        GranPremioTarget target,
        String nombreCiudad,
        String codigoAeropuertoDestino,
        LocalDate fechaCarrera,
        LocalDate fechaIda,
        LocalDate fechaVuelta,
        int totalVuelosExtraidos,
        int vuelosCreados,
        int vuelosActualizados,
        int vuelosIda,
        int vuelosVuelta,
        String estado,
        String mensaje,
        OffsetDateTime fechaSincronizacion,
        List<FlightSummaryDto> vuelos
) {
}
