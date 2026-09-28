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
    @DisplayName("POST /api/microservicios/sync-flights/all debe responder 200 OK y ejecutar masivo")
    void shouldExecuteSyncAll() throws Exception {
        FlightSyncAllSummaryDto summary = new FlightSyncAllSummaryDto(
                9, 9, 0, 18, 18, 0, OffsetDateTime.now(), List.of()
        );

        when(flightSyncService.syncAllFlightData()).thenReturn(summary);

        mockMvc.perform(post("/api/microservicios/sync-flights/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalDestinosProcesados").value(9))
                .andExpect(jsonPath("$.data.destinosExitosos").value(9));

        verify(flightSyncService).syncAllFlightData();
    }

    @Test
    @DisplayName("POST /api/microservicios/sync-flights/{target} debe procesar destino individual")
    void shouldExecuteSingleTarget() throws Exception {
        FlightSyncResultDto result = new FlightSyncResultDto(
                GranPremioTarget.MADRID,
                "Madrid",
                "MAD",
                LocalDate.parse("2026-09-11"),
                LocalDate.parse("2026-09-10"),
                LocalDate.parse("2026-09-13"),
                6, 6, 0, 3, 3,
                "SUCCESS",
                "Sincronización completada",
                OffsetDateTime.now(),
                List.of()
        );

        when(flightSyncService.syncFlightData(GranPremioTarget.MADRID)).thenReturn(result);

        mockMvc.perform(post("/api/microservicios/sync-flights/MADRID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.codigoAeropuertoDestino").value("MAD"))
                .andExpect(jsonPath("$.data.nombreCiudad").value("Madrid"));

        verify(flightSyncService).syncFlightData(GranPremioTarget.MADRID);
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
    @DisplayName("GET /api/microservicios/sync-flights/targets debe devolver catálogo informativo")
    void shouldReturnTargetsList() throws Exception {
        mockMvc.perform(get("/api/microservicios/sync-flights/targets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(9));
    }
}
