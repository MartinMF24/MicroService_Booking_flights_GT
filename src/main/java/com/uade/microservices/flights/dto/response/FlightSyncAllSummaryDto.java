package com.uade.microservices.flights.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Resumen global consolidado de la sincronización masiva de vuelos para todos los Grandes Premios.
 */
public record FlightSyncAllSummaryDto(
        int totalDestinosProcesados,
        int destinosExitosos,
        int destinosConError,
        int totalVuelosExtraidos,
        int totalVuelosCreados,
        int totalVuelosActualizados,
        OffsetDateTime fechaEjecucion,
        List<FlightSyncResultDto> resultados
) {
}
