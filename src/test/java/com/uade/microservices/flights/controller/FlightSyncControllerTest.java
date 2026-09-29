package com.uade.microservices.flights.controller;

import com.uade.microservices.flights.dto.response.FlightSyncAllSummaryDto;
import com.uade.microservices.flights.dto.response.FlightSyncResultDto;
import com.uade.microservices.flights.model.GranPremioTarget;
import com.uade.microservices.flights.service.FlightSyncService;
import com.uade.microservices.flights.shared.exception.GlobalExceptionHandler;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FlightSyncControllerTest {

    private MockMvc mockMvc;

    @Mock
    private FlightSyncService flightSyncService;

    @InjectMocks
    private FlightSyncController flightSyncController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(flightSyncController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/microservicios/sync-flights/all debe responder 200 OK y ejecutar masivo completo")
    void shouldExecuteSyncAll() throws Exception {
        FlightSyncAllSummaryDto summary = new FlightSyncAllSummaryDto(
                19, 19, 0, 38, 38, 0, OffsetDateTime.now(), List.of()
        );

        when(flightSyncService.syncAllFlightData()).thenReturn(summary);

        mockMvc.perform(post("/api/microservicios/sync-flights/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalDestinosProcesados").value(19))
                .andExpect(jsonPath("$.data.destinosExitosos").value(19));

        verify(flightSyncService).syncAllFlightData();
    }

    @Test
    @DisplayName("POST /api/microservicios/sync-flights/new-destinations debe responder 200 OK y ejecutar masivo de nuevos destinos")
    void shouldExecuteSyncNewDestinations() throws Exception {
        FlightSyncAllSummaryDto summary = new FlightSyncAllSummaryDto(
                10, 10, 0, 20, 20, 0, OffsetDateTime.now(), List.of()
        );

        when(flightSyncService.syncNewDestinationsFlightData()).thenReturn(summary);

        mockMvc.perform(post("/api/microservicios/sync-flights/new-destinations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalDestinosProcesados").value(10))
                .andExpect(jsonPath("$.data.destinosExitosos").value(10));

        verify(flightSyncService).syncNewDestinationsFlightData();
    }

    @Test
    @DisplayName("POST /api/microservicios/sync-flights/{target} debe procesar destino individual")
    void shouldExecuteSingleTarget() throws Exception {
        FlightSyncResultDto result = new FlightSyncResultDto(
                GranPremioTarget.SAKHIR,
                "Sakhir",
                "BAH",
                LocalDate.parse("2027-03-14"),
                LocalDate.parse("2027-03-11"),
                LocalDate.parse("2027-03-15"),
                6, 6, 0, 3, 3,
                "SUCCESS",
                "Sincronización completada",
                OffsetDateTime.now(),
                List.of()
        );

        when(flightSyncService.syncFlightData(GranPremioTarget.SAKHIR)).thenReturn(result);

        mockMvc.perform(post("/api/microservicios/sync-flights/SAKHIR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.codigoAeropuertoDestino").value("BAH"))
                .andExpect(jsonPath("$.data.nombreCiudad").value("Sakhir"));

        verify(flightSyncService).syncFlightData(GranPremioTarget.SAKHIR);
    }

    @Test
    @DisplayName("POST /api/microservicios/sync-flights/{target} con target inválido debe responder 400 Bad Request")
    void shouldReturn400OnInvalidTarget() throws Exception {
        mockMvc.perform(post("/api/microservicios/sync-flights/DESTINO_INEXISTENTE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("GET /api/microservicios/sync-flights/targets debe devolver catálogo informativo con 19 destinos")
    void shouldReturnTargetsList() throws Exception {
        mockMvc.perform(get("/api/microservicios/sync-flights/targets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(19));
    }
}
