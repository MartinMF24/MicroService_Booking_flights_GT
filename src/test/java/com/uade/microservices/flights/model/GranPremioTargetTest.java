package com.uade.microservices.flights.model;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GranPremioTargetTest {

    @Test
    @DisplayName("Debe contener los 9 Grandes Premios de 2026")
    void shouldHave9Targets() {
        assertEquals(9, GranPremioTarget.values().length);
    }

    @Test
    @DisplayName("Debe contener códigos IATA coherentes para cada sede de Gran Premio")
    void shouldContainValidIataCodes() {
        assertEquals("MAD", GranPremioTarget.MADRID.getCodigoAeropuerto());
        assertEquals("GYD", GranPremioTarget.BAKU.getCodigoAeropuerto());
        assertEquals("SIN", GranPremioTarget.SINGAPUR.getCodigoAeropuerto());
        assertEquals("AUS", GranPremioTarget.AUSTIN.getCodigoAeropuerto());
        assertEquals("MEX", GranPremioTarget.CIUDAD_DE_MEXICO.getCodigoAeropuerto());
        assertEquals("GRU", GranPremioTarget.SAO_PAULO.getCodigoAeropuerto());
        assertEquals("LAS", GranPremioTarget.LAS_VEGAS.getCodigoAeropuerto());
        assertEquals("DOH", GranPremioTarget.LUSAIL.getCodigoAeropuerto());
        assertEquals("AUH", GranPremioTarget.ABU_DABI.getCodigoAeropuerto());
    }

    @Test
    @DisplayName("Debe calcular correctamente la fecha de vuelo de ida (-1 día) y de vuelta (+2 días)")
    void shouldCalculateCorrectFlightDates() {
        GranPremioTarget target = GranPremioTarget.MADRID;
        LocalDate fechaCarrera = target.getFechaCarrera();

        assertEquals(fechaCarrera.minusDays(1), target.getFechaIda());
        assertEquals(fechaCarrera.plusDays(2), target.getFechaVuelta());
        assertEquals(target.getFechaIda(), target.getCheckinDate());
        assertEquals(target.getFechaVuelta(), target.getCheckoutDate());
    }

    @Test
    @DisplayName("Debe resolver destinos a partir de cadenas con mayúsculas, minúsculas y guiones")
    void shouldResolveFromString() {
        Optional<GranPremioTarget> opt1 = GranPremioTarget.fromString("sao-paulo");
        assertTrue(opt1.isPresent());
        assertEquals(GranPremioTarget.SAO_PAULO, opt1.get());

        Optional<GranPremioTarget> opt2 = GranPremioTarget.fromString("CIUDAD_DE_MEXICO");
        assertTrue(opt2.isPresent());
        assertEquals(GranPremioTarget.CIUDAD_DE_MEXICO, opt2.get());

        Optional<GranPremioTarget> invalid = GranPremioTarget.fromString("DESCONOCIDO");
        assertFalse(invalid.isPresent());
    }

    @Test
    @DisplayName("Debe proveer constantes de origen por defecto (Buenos Aires)")
    void shouldProvideOriginConstants() {
        assertEquals("EZE", GranPremioTarget.ORIGEN_DEFAULT_IATA);
        assertEquals("Buenos Aires", GranPremioTarget.ORIGEN_CIUDAD_NOMBRE);
        assertNotNull(GranPremioTarget.BUENOS_AIRES_DEFAULT_ID);
    }
}
