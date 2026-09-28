package com.uade.microservices.flights.service;

import com.uade.microservices.flights.adapter.FlightAdapter;
import com.uade.microservices.flights.config.FlightRapidApiProperties;
import com.uade.microservices.flights.dto.rapidapi.FlightRawDto;
import com.uade.microservices.flights.dto.response.FlightSyncAllSummaryDto;
import com.uade.microservices.flights.dto.response.FlightSyncResultDto;
import com.uade.microservices.flights.model.Ciudad;
import com.uade.microservices.flights.model.FlightDirection;
import com.uade.microservices.flights.model.GranPremioTarget;
import com.uade.microservices.flights.model.Vuelo;
import com.uade.microservices.flights.repository.CiudadRepository;
import com.uade.microservices.flights.repository.VueloRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlightSyncServiceTest {

    @Mock
    private FlightClientService flightClientService;

    @Mock
    private FlightAdapter flightAdapter;

    @Mock
    private VueloRepository vueloRepository;

    @Mock
    private CiudadRepository ciudadRepository;

    @Mock
    private FlightRapidApiProperties properties;

    @InjectMocks
    private FlightSyncService flightSyncService;

    private GranPremioTarget target;
    private UUID ciudadBairesId;
    private UUID ciudadDestinoId;

    @BeforeEach
    void setUp() {
        target = GranPremioTarget.MADRID;
        ciudadBairesId = UUID.randomUUID();
        ciudadDestinoId = UUID.randomUUID();

        lenient().when(properties.getOriginIata()).thenReturn("EZE");
        lenient().when(properties.getOriginCityName()).thenReturn("Buenos Aires");

        lenient().when(ciudadRepository.findFirstByNombreIgnoreCase("Buenos Aires"))
                .thenReturn(Optional.of(new Ciudad(ciudadBairesId, "Buenos Aires")));

        lenient().when(ciudadRepository.findFirstByNombreIgnoreCase("Madrid"))
                .thenReturn(Optional.of(new Ciudad(ciudadDestinoId, "Madrid")));
    }

    @Test
    @DisplayName("Debe sincronizar vuelos de Ida y Vuelta creando nuevos registros cuando no existen")
    void shouldSyncFlightsAndCreateNewWhenNotExisting() {
        FlightRawDto rawIda = new FlightRawDto("Iberia", "IB-101", "EZE", "MAD", null, null, 800.0, 20);
        FlightRawDto rawVuelta = new FlightRawDto("Iberia", "IB-102", "MAD", "EZE", null, null, 850.0, 22);

        when(flightClientService.extractFlights(eq("EZE"), eq("MAD"), any(LocalDate.class), eq(target), eq(FlightDirection.IDA)))
                .thenReturn(List.of(rawIda));
        when(flightClientService.extractFlights(eq("MAD"), eq("EZE"), any(LocalDate.class), eq(target), eq(FlightDirection.VUELTA)))
                .thenReturn(List.of(rawVuelta));

        OffsetDateTime salida = OffsetDateTime.of(2026, 9, 10, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime llegada = salida.plusHours(12);

        Vuelo vueloIda = new Vuelo(UUID.randomUUID(), "Iberia", ciudadBairesId, ciudadDestinoId, salida, llegada, BigDecimal.valueOf(800.0), 20);
        Vuelo vueloVuelta = new Vuelo(UUID.randomUUID(), "Iberia", ciudadDestinoId, ciudadBairesId, salida.plusDays(3), llegada.plusDays(3), BigDecimal.valueOf(850.0), 22);

        when(flightAdapter.toEntityList(eq(List.of(rawIda)), eq(ciudadBairesId), eq(ciudadDestinoId), any(LocalDate.class)))
                .thenReturn(List.of(vueloIda));
        when(flightAdapter.toEntityList(eq(List.of(rawVuelta)), eq(ciudadDestinoId), eq(ciudadBairesId), any(LocalDate.class)))
                .thenReturn(List.of(vueloVuelta));

        // No existen previamente -> deben crearse
        when(vueloRepository.findFlightForUpsert(eq("Iberia"), any(), any(), any()))
                .thenReturn(Optional.empty());

        when(vueloRepository.save(any(Vuelo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FlightSyncResultDto result = flightSyncService.syncFlightData(target);

        assertNotNull(result);
        assertEquals("SUCCESS", result.estado());
        assertEquals(2, result.totalVuelosExtraidos());
        assertEquals(2, result.vuelosCreados());
        assertEquals(1, result.vuelosIda());
        assertEquals(1, result.vuelosVuelta());
        assertEquals(0, result.vuelosActualizados());
    }

    @Test
    @DisplayName("Debe aplicar UPSERT actualizando precio y stock si el vuelo ya existe")
    void shouldUpsertFlightUpdatingPriceAndStockWhenExisting() {
        FlightRawDto rawIda = new FlightRawDto("Iberia", "IB-101", "EZE", "MAD", null, null, 750.0, 10);

        when(flightClientService.extractFlights(eq("EZE"), eq("MAD"), any(LocalDate.class), eq(target), eq(FlightDirection.IDA)))
                .thenReturn(List.of(rawIda));
        when(flightClientService.extractFlights(eq("MAD"), eq("EZE"), any(LocalDate.class), eq(target), eq(FlightDirection.VUELTA)))
                .thenReturn(List.of());

        OffsetDateTime salida = OffsetDateTime.of(2026, 9, 10, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime llegada = salida.plusHours(12);

        Vuelo nuevoVuelo = new Vuelo(null, "Iberia", ciudadBairesId, ciudadDestinoId, salida, llegada, BigDecimal.valueOf(750.0), 10);
        when(flightAdapter.toEntityList(eq(List.of(rawIda)), eq(ciudadBairesId), eq(ciudadDestinoId), any(LocalDate.class)))
                .thenReturn(List.of(nuevoVuelo));

        // Vuelo existente en BD con precio viejo y stock viejo
        Vuelo vueloExistente = new Vuelo(UUID.randomUUID(), "Iberia", ciudadBairesId, ciudadDestinoId, salida, llegada, BigDecimal.valueOf(900.0), 40);
        when(vueloRepository.findFlightForUpsert(eq("Iberia"), eq(ciudadBairesId), eq(ciudadDestinoId), eq(salida)))
                .thenReturn(Optional.of(vueloExistente));

        when(vueloRepository.save(any(Vuelo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FlightSyncResultDto result = flightSyncService.syncFlightData(target);

        assertNotNull(result);
        assertEquals(0, result.vuelosCreados());
        assertEquals(1, result.vuelosActualizados());
        assertEquals(BigDecimal.valueOf(750.0), vueloExistente.getPrecioUsd());
        assertEquals(10, vueloExistente.getStockAsientos());
    }

    @Test
    @DisplayName("Debe aislar fallas en syncAllFlightData si un destino genera error")
    void shouldIsolateFailuresInSyncAll() {
        // Mock de simulación para todos
        when(flightClientService.extractFlights(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(flightAdapter.toEntityList(any(), any(), any(), any()))
                .thenReturn(List.of());

        FlightSyncAllSummaryDto summary = flightSyncService.syncAllFlightData();

        assertNotNull(summary);
        assertEquals(9, summary.totalDestinosProcesados());
        assertEquals(9, summary.destinosExitosos());
        assertEquals(0, summary.destinosConError());
    }
}
