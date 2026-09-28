package com.uade.microservices.flights.controller;

import com.uade.microservices.flights.dto.response.FlightSyncAllSummaryDto;
import com.uade.microservices.flights.dto.response.FlightSyncResultDto;
import com.uade.microservices.flights.model.GranPremioTarget;
import com.uade.microservices.flights.service.FlightSyncService;
import com.uade.microservices.flights.shared.response.ApiResponse;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para la ejecución manual del módulo ETL de Vuelos (Booking / RapidAPI).
 * Provee endpoints individuales por destino (POST /{target}) y masivos (POST /all).
 * No utiliza @Scheduled.
 */
@RestController
@RequestMapping("/api/microservicios/sync-flights")
public class FlightSyncController {

    private static final Logger log = LoggerFactory.getLogger(FlightSyncController.class);

    private final FlightSyncService flightSyncService;

    public FlightSyncController(FlightSyncService flightSyncService) {
        this.flightSyncService = flightSyncService;
    }

    /**
     * Endpoint para ejecutar la sincronización ETL masiva de vuelos
     * para TODOS los 9 destinos de Gran Premio de Fórmula 1 para 2026.
     * Carga dos búsquedas (Ida Buenos Aires -> Destino y Vuelta Destino -> Buenos Aires)
     * para cada sede y resuelve las claves foráneas en Supabase.
     *
     * @return ApiResponse con el consolidado general y la lista detallada por destino.
     */
    @PostMapping({"/all", "/sync-all", ""})
    public ResponseEntity<ApiResponse<FlightSyncAllSummaryDto>> syncAllFlightData() {
        log.info("Petición recibida para sincronización masiva de vuelos de TODOS los destinos");
        FlightSyncAllSummaryDto summary = flightSyncService.syncAllFlightData();
        return ResponseEntity.ok(ApiResponse.success(
                String.format("Sincronización masiva de vuelos completada: %d destinos exitosos de %d procesados (%d vuelos creados, %d actualizados)",
                        summary.destinosExitosos(), summary.totalDestinosProcesados(), summary.totalVuelosCreados(), summary.totalVuelosActualizados()),
                summary
        ));
    }

    /**
     * Endpoint para ejecutar manualmente la sincronización ETL de vuelos
     * para un Gran Premio específico.
     *
     * @param target Nombre del enum GranPremioTarget (ej: SAO_PAULO, MADRID, BAKU, etc.)
     * @return ApiResponse con los detalles de vuelos de ida y vuelta sincronizados.
     */
    @PostMapping("/{target}")
    public ResponseEntity<?> syncFlightData(@PathVariable("target") String target) {
        log.info("Petición recibida para sincronización manual de vuelos: target={}", target);

        if ("all".equalsIgnoreCase(target) || "sync-all".equalsIgnoreCase(target)) {
            return syncAllFlightData();
        }

        Optional<GranPremioTarget> targetOpt = GranPremioTarget.fromString(target);
        if (targetOpt.isEmpty()) {
            List<String> validTargets = Arrays.stream(GranPremioTarget.values())
                    .map(Enum::name)
                    .toList();
            String errorMessage = String.format(
                    "Destino inválido: '%s'. Los destinos válidos son: %s",
                    target, validTargets
            );
            log.warn(errorMessage);
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(errorMessage));
        }

        GranPremioTarget granPremio = targetOpt.get();
        FlightSyncResultDto result = flightSyncService.syncFlightData(granPremio);

        return ResponseEntity.ok(ApiResponse.success(
                "Sincronización ETL de vuelos ejecutada correctamente para " + granPremio.getNombreCiudad(),
                result
        ));
    }

    /**
     * Endpoint informativo auxiliar para listar todos los destinos de Gran Premio disponibles
     * con sus códigos IATA, fechas de carrera, vuelos de ida y vuelos de vuelta.
     */
    @GetMapping("/targets")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listAvailableTargets() {
        List<Map<String, Object>> targets = Arrays.stream(GranPremioTarget.values())
                .map(t -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("enum", t.name());
                    map.put("ciudad", t.getNombreCiudad());
                    map.put("codigoAeropuerto", t.getCodigoAeropuerto());
                    map.put("fechaCarrera", t.getFechaCarrera().toString());
                    map.put("fechaVueloIda", t.getFechaIda().toString());
                    map.put("fechaVueloVuelta", t.getFechaVuelta().toString());
                    map.put("origen", "Buenos Aires (EZE/BUE)");
                    return map;
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Listado de destinos disponibles para sincronización de vuelos", targets));
    }
}
