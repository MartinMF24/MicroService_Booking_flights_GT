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
 * Provee endpoints individuales por destino (POST /{target}), masivos completos (POST /all)
 * y masivos exclusivos para nuevos destinos (POST /new-destinations).
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
     * para TODOS los destinos del catálogo completo.
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
     * Endpoint para ejecutar la sincronización ETL masiva de vuelos
     * EXCLUSIVAMENTE para los NUEVOS destinos de Gran Premio (Temporada 2027):
     * - Sakhir (Bahréin): 11/03/2027 -> 15/03/2027
     * - Yeda (Arabia Saudita): 18/03/2027 -> 22/03/2027
     * - Melbourne (Australia): 01/04/2027 -> 05/04/2027
     * - Suzuka (Japón): 08/04/2027 -> 12/04/2027
     * - Shanghái (China): 15/04/2027 -> 19/04/2027
     * - Miami (Estados Unidos): 29/04/2027 -> 03/05/2027
     * - Montreal (Canadá): 20/05/2027 -> 24/05/2027
     * - Montecarlo (Mónaco): 03/06/2027 -> 07/06/2027
     * - Portimão (Portugal): 17/06/2027 -> 21/06/2027
     * - Silverstone (Reino Unido): 01/07/2027 -> 05/07/2027
     *
     * @return ApiResponse con el consolidado general de los 10 nuevos destinos procesados.
     */
    @PostMapping({"/new-destinations", "/nuevos-destinos", "/sync-new", "/new"})
    public ResponseEntity<ApiResponse<FlightSyncAllSummaryDto>> syncNewDestinationsFlightData() {
        log.info("Petición recibida para sincronización masiva de vuelos EXCLUSIVA para los NUEVOS destinos");
        FlightSyncAllSummaryDto summary = flightSyncService.syncNewDestinationsFlightData();
        return ResponseEntity.ok(ApiResponse.success(
                String.format("Sincronización de nuevos destinos completada: %d destinos exitosos de %d procesados (%d vuelos creados, %d actualizados)",
                        summary.destinosExitosos(), summary.totalDestinosProcesados(), summary.totalVuelosCreados(), summary.totalVuelosActualizados()),
                summary
        ));
    }

    /**
     * Endpoint para ejecutar manualmente la sincronización ETL de vuelos
     * para un Gran Premio específico (sea original o nuevo).
     *
     * @param target Nombre del enum GranPremioTarget (ej: SAKHIR, MIAMI, SAO_PAULO, MADRID, etc.)
     * @return ApiResponse con los detalles de vuelos de ida y vuelta sincronizados.
     */
    @PostMapping("/{target}")
    public ResponseEntity<?> syncFlightData(@PathVariable("target") String target) {
        log.info("Petición recibida para sincronización manual de vuelos: target={}", target);

        if ("all".equalsIgnoreCase(target) || "sync-all".equalsIgnoreCase(target)) {
            return syncAllFlightData();
        }

        if ("new-destinations".equalsIgnoreCase(target) || "nuevos-destinos".equalsIgnoreCase(target)
                || "sync-new".equalsIgnoreCase(target) || "new".equalsIgnoreCase(target)) {
            return syncNewDestinationsFlightData();
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
                    map.put("nuevoDestino", t.isNuevoDestino());
                    map.put("temporada", t.isNuevoDestino() ? "2027" : "2026");
                    return map;
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Listado de destinos disponibles para sincronización de vuelos", targets));
    }
}
