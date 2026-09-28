package com.uade.microservices.flights.service;

import com.uade.microservices.flights.adapter.FlightAdapter;
import com.uade.microservices.flights.config.FlightRapidApiProperties;
import com.uade.microservices.flights.dto.rapidapi.FlightRawDto;
import com.uade.microservices.flights.dto.response.FlightSummaryDto;
import com.uade.microservices.flights.dto.response.FlightSyncAllSummaryDto;
import com.uade.microservices.flights.dto.response.FlightSyncResultDto;
import com.uade.microservices.flights.model.Ciudad;
import com.uade.microservices.flights.model.FlightDirection;
import com.uade.microservices.flights.model.GranPremioTarget;
import com.uade.microservices.flights.model.Vuelo;
import com.uade.microservices.flights.repository.CiudadRepository;
import com.uade.microservices.flights.repository.VueloRepository;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio orquestador del módulo ETL de Vuelos (Extract, Transform, Load).
 * Contiene la lógica de negocio de rutas ida y vuelta (Buenos Aires <-> Ciudad GP),
 * resolución de FKs a la tabla ciudades y operaciones Upsert en PostgreSQL/Supabase.
 */
@Service
public class FlightSyncService {

    private static final Logger log = LoggerFactory.getLogger(FlightSyncService.class);

    private final FlightClientService flightClientService;
    private final FlightAdapter flightAdapter;
    private final VueloRepository vueloRepository;
    private final CiudadRepository ciudadRepository;
    private final FlightRapidApiProperties properties;

    public FlightSyncService(
            FlightClientService flightClientService,
            FlightAdapter flightAdapter,
            VueloRepository vueloRepository,
            CiudadRepository ciudadRepository,
            FlightRapidApiProperties properties
    ) {
        this.flightClientService = flightClientService;
        this.flightAdapter = flightAdapter;
        this.vueloRepository = vueloRepository;
        this.ciudadRepository = ciudadRepository;
        this.properties = properties;
    }

    /**
     * Ejecuta el proceso ETL para todos los destinos de Gran Premio del calendario 2026.
     * Genera vuelos de Ida (Buenos Aires -> GP) y de Vuelta (GP -> Buenos Aires) para cada sede.
     * Incorpora aislamiento de excepciones para que fallas en una ciudad no interrumpan el proceso.
     *
     * @return Resumen consolidado de todos los destinos procesados.
     */
    public FlightSyncAllSummaryDto syncAllFlightData() {
        log.info("Iniciando sincronización masiva de vuelos para todos los Grandes Premios...");

        List<FlightSyncResultDto> resultados = new ArrayList<>();
        int totalVuelosExtraidos = 0;
        int totalVuelosCreados = 0;
        int totalVuelosActualizados = 0;
        int destinosExitosos = 0;
        int destinosConError = 0;

        for (GranPremioTarget target : GranPremioTarget.values()) {
            try {
                log.info("Sincronizando vuelos para destino: {} ({})", target.name(), target.getNombreCiudad());
                FlightSyncResultDto res = syncFlightData(target);
                resultados.add(res);

                totalVuelosExtraidos += res.totalVuelosExtraidos();
                totalVuelosCreados += res.vuelosCreados();
                totalVuelosActualizados += res.vuelosActualizados();

                if ("SUCCESS".equalsIgnoreCase(res.estado())) {
                    destinosExitosos++;
                } else {
                    destinosConError++;
                }
            } catch (Exception ex) {
                destinosConError++;
                log.error("Error al sincronizar los vuelos para el destino {}: {}", target.name(), ex.getMessage(), ex);
                resultados.add(new FlightSyncResultDto(
                        target,
                        target.getNombreCiudad(),
                        target.getCodigoAeropuerto(),
                        target.getFechaCarrera(),
                        target.getFechaIda(),
                        target.getFechaVuelta(),
                        0, 0, 0, 0, 0,
                        "ERROR",
                        "Error al sincronizar vuelos: " + ex.getMessage(),
                        OffsetDateTime.now(),
                        List.of()
                ));
            }
        }

        log.info("Sincronización masiva de vuelos finalizada. Destinos: {}, Exitosos: {}, Con error: {}, Vuelos creados: {}, Vuelos actualizados: {}",
                GranPremioTarget.values().length, destinosExitosos, destinosConError, totalVuelosCreados, totalVuelosActualizados);

        return new FlightSyncAllSummaryDto(
                GranPremioTarget.values().length,
                destinosExitosos,
                destinosConError,
                totalVuelosExtraidos,
                totalVuelosCreados,
                totalVuelosActualizados,
                OffsetDateTime.now(),
                resultados
        );
    }

    /**
     * Ejecuta el pipeline ETL de vuelos para un Gran Premio específico:
     * 1. Resuelve los id_ciudad en Supabase para Buenos Aires y para la ciudad sede del GP.
     * 2. BÚSQUEDA 1 (IDA): Buenos Aires -> Ciudad GP (Fecha = check-in, 1-2 días antes de la carrera).
     * 3. BÚSQUEDA 2 (VUELTA): Ciudad GP -> Buenos Aires (Fecha = check-out, 1-2 días después de la carrera).
     * 4. TRANSFORM: Normalización de DTOs a entidades Vuelo (simulación de stock de 5 a 50 si es necesario).
     * 5. LOAD (UPSERT): Si ya existe un vuelo de esa aerolínea para esa ruta y esa fecha/hora exacta,
     *    actualiza precio y stock. Si no, lo crea.
     */
    @Transactional
    public FlightSyncResultDto syncFlightData(GranPremioTarget target) {
        log.info("Iniciando proceso ETL de Vuelos para el Gran Premio: {}", target.name());

        // 1. Resolver UUIDs reales de las ciudades en Supabase
        UUID ciudadBuenosAiresId = resolveBuenosAiresId();
        UUID ciudadDestinoId = resolveCiudadDestinoId(target);

        String origenIata = properties.getOriginIata();
        String destinoIata = target.getCodigoAeropuerto();

        LocalDate fechaIda = target.getFechaIda();
        LocalDate fechaVuelta = target.getFechaVuelta();

        log.info("Configuración de vuelos para {}: Origen=[{} - {}], Destino=[{} - {}], FechaIda={}, FechaVuelta={}",
                target.name(), properties.getOriginCityName(), origenIata, target.getNombreCiudad(), destinoIata, fechaIda, fechaVuelta);

        List<FlightSummaryDto> summaries = new ArrayList<>();
        int vuelosCreados = 0;
        int vuelosActualizados = 0;
        int vuelosIdaCreados = 0;
        int vuelosVueltaCreados = 0;

        // =========================================================================
        // PASO 2: VUELO DE IDA (Buenos Aires -> Ciudad del Gran Premio)
        // =========================================================================
        log.info("Procesando vuelos de IDA: {} -> {} ({})", origenIata, destinoIata, fechaIda);
        List<FlightRawDto> rawIda = flightClientService.extractFlights(origenIata, destinoIata, fechaIda, target, FlightDirection.IDA);
        List<Vuelo> vuelosIda = flightAdapter.toEntityList(rawIda, ciudadBuenosAiresId, ciudadDestinoId, fechaIda);

        for (Vuelo transformed : vuelosIda) {
            Optional<Vuelo> existingOpt = vueloRepository.findFlightForUpsert(
                    transformed.getAerolinea(),
                    ciudadBuenosAiresId,
                    ciudadDestinoId,
                    transformed.getFechaSalida()
            );

            Vuelo saved;
            if (existingOpt.isPresent()) {
                Vuelo existing = existingOpt.get();
                existing.setPrecioUsd(transformed.getPrecioUsd());
                existing.setStockAsientos(transformed.getStockAsientos());
                existing.setFechaLlegada(transformed.getFechaLlegada());
                saved = vueloRepository.save(existing);
                vuelosActualizados++;
                log.debug("Vuelo de IDA actualizado [UPSERT]: {} (id={})", saved.getAerolinea(), saved.getIdVuelo());
            } else {
                saved = vueloRepository.save(transformed);
                vuelosCreados++;
                vuelosIdaCreados++;
                log.debug("Nuevo vuelo de IDA guardado [UPSERT]: {} (id={})", saved.getAerolinea(), saved.getIdVuelo());
            }
            summaries.add(toSummaryDto(saved, FlightDirection.IDA));
        }

        // =========================================================================
        // PASO 3: VUELO DE VUELTA (Ciudad del Gran Premio -> Buenos Aires)
        // =========================================================================
        log.info("Procesando vuelos de VUELTA: {} -> {} ({})", destinoIata, origenIata, fechaVuelta);
        List<FlightRawDto> rawVuelta = flightClientService.extractFlights(destinoIata, origenIata, fechaVuelta, target, FlightDirection.VUELTA);
        List<Vuelo> vuelosVuelta = flightAdapter.toEntityList(rawVuelta, ciudadDestinoId, ciudadBuenosAiresId, fechaVuelta);

        for (Vuelo transformed : vuelosVuelta) {
            Optional<Vuelo> existingOpt = vueloRepository.findFlightForUpsert(
                    transformed.getAerolinea(),
                    ciudadDestinoId,
                    ciudadBuenosAiresId,
                    transformed.getFechaSalida()
            );

            Vuelo saved;
            if (existingOpt.isPresent()) {
                Vuelo existing = existingOpt.get();
                existing.setPrecioUsd(transformed.getPrecioUsd());
                existing.setStockAsientos(transformed.getStockAsientos());
                existing.setFechaLlegada(transformed.getFechaLlegada());
                saved = vueloRepository.save(existing);
                vuelosActualizados++;
                log.debug("Vuelo de VUELTA actualizado [UPSERT]: {} (id={})", saved.getAerolinea(), saved.getIdVuelo());
            } else {
                saved = vueloRepository.save(transformed);
                vuelosCreados++;
                vuelosVueltaCreados++;
                log.debug("Nuevo vuelo de VUELTA guardado [UPSERT]: {} (id={})", saved.getAerolinea(), saved.getIdVuelo());
            }
            summaries.add(toSummaryDto(saved, FlightDirection.VUELTA));
        }

        int totalExtraidos = rawIda.size() + rawVuelta.size();
        String mensaje = String.format(
                "Sincronización ETL de vuelos para %s completada con éxito. Vuelos creados: %d (Ida: %d, Vuelta: %d), Vuelos actualizados: %d.",
                target.getNombreCiudad(), vuelosCreados, vuelosIdaCreados, vuelosVueltaCreados, vuelosActualizados
        );
        log.info(mensaje);

        return new FlightSyncResultDto(
                target,
                target.getNombreCiudad(),
                target.getCodigoAeropuerto(),
                target.getFechaCarrera(),
                fechaIda,
                fechaVuelta,
                totalExtraidos,
                vuelosCreados,
                vuelosActualizados,
                vuelosIdaCreados,
                vuelosVueltaCreados,
                "SUCCESS",
                mensaje,
                OffsetDateTime.now(),
                summaries
        );
    }

    private FlightSummaryDto toSummaryDto(Vuelo vuelo, FlightDirection direction) {
        return new FlightSummaryDto(
                vuelo.getIdVuelo(),
                vuelo.getAerolinea(),
                vuelo.getOrigenIdCiudad(),
                vuelo.getDestinoIdCiudad(),
                vuelo.getFechaSalida(),
                vuelo.getFechaLlegada(),
                vuelo.getPrecioUsd(),
                vuelo.getStockAsientos(),
                direction
        );
    }

    /**
     * Resuelve el id_ciudad para Buenos Aires en la tabla 'ciudades' de Supabase.
     */
    private UUID resolveBuenosAiresId() {
        String nombre = properties.getOriginCityName();

        Optional<Ciudad> ciudadOpt = ciudadRepository.findFirstByNombreIgnoreCase(nombre);
        if (ciudadOpt.isPresent()) {
            return ciudadOpt.get().getIdCiudad();
        }

        // Búsqueda parcial o sin acento
        List<Ciudad> partials = ciudadRepository.searchByTermIgnoreCase("Buenos Aires");
        if (!partials.isEmpty()) {
            return partials.get(0).getIdCiudad();
        }

        // Fallback predeterminado seguro
        log.warn("Ciudad de origen '{}' no encontrada en Supabase. Utilizando UUID por defecto.", nombre);
        return GranPremioTarget.BUENOS_AIRES_DEFAULT_ID;
    }

    /**
     * Resuelve el id_ciudad de la sede del Gran Premio en la base de datos Supabase.
     */
    private UUID resolveCiudadDestinoId(GranPremioTarget target) {
        String nombreCiudad = target.getNombreCiudad();

        // 1. Coincidencia exacta insensible a mayúsculas
        Optional<Ciudad> ciudadOpt = ciudadRepository.findFirstByNombreIgnoreCase(nombreCiudad);
        if (ciudadOpt.isPresent()) {
            return ciudadOpt.get().getIdCiudad();
        }

        // 2. Variante sin tildes
        String normalized = stripAccents(nombreCiudad);
        if (!normalized.equalsIgnoreCase(nombreCiudad)) {
            ciudadOpt = ciudadRepository.findFirstByNombreIgnoreCase(normalized);
            if (ciudadOpt.isPresent()) {
                return ciudadOpt.get().getIdCiudad();
            }
        }

        // 3. Alias conocidos
        List<String> aliases = getCityAliases(target);
        for (String alias : aliases) {
            ciudadOpt = ciudadRepository.findFirstByNombreIgnoreCase(alias);
            if (ciudadOpt.isPresent()) {
                return ciudadOpt.get().getIdCiudad();
            }
        }

        // 4. Búsqueda por término (LIKE)
        List<Ciudad> partialMatches = ciudadRepository.searchByTermIgnoreCase(normalized);
        if (!partialMatches.isEmpty()) {
            return partialMatches.get(0).getIdCiudad();
        }

        // 5. Fallback a id preconfigurado en el enum si existe
        if (target.getCiudadId() != null) {
            log.warn("Ciudad destino '{}' no encontrada directamente. Usando fallback configurado: {}",
                    nombreCiudad, target.getCiudadId());
            return target.getCiudadId();
        }

        throw new IllegalStateException("No se encontró la ciudad destino '" + nombreCiudad
                + "' en la tabla 'ciudades' de la base de datos.");
    }

    private String stripAccents(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private List<String> getCityAliases(GranPremioTarget target) {
        return switch (target) {
            case MADRID -> List.of("Madrid");
            case BAKU -> List.of("Baku", "Bakú");
            case SINGAPUR -> List.of("Singapore", "Singapur");
            case AUSTIN -> List.of("Austin");
            case CIUDAD_DE_MEXICO -> List.of("Ciudad de Mexico", "Mexico", "CDMX", "Ciudad de México");
            case SAO_PAULO -> List.of("Sao Paulo", "São Paulo", "San Pablo");
            case LAS_VEGAS -> List.of("Las Vegas");
            case LUSAIL -> List.of("Lusail", "Doha");
            case ABU_DABI -> List.of("Abu Dhabi", "Abu Dabi");
        };
    }
}
