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
    @DisplayName("Debe contener los 19 Grandes Premios en total (9 originales 2026 y 10 nuevos 2027)")
    void shouldHave19Targets() {
        assertEquals(19, GranPremioTarget.values().length);
        assertEquals(9, GranPremioTarget.getDestinosOriginales().size());
        assertEquals(10, GranPremioTarget.getNuevosDestinos().size());
    }

    @Test
    @DisplayName("Debe contener códigos IATA coherentes para cada sede de Gran Premio")
    void shouldContainValidIataCodes() {
        // Originales
        assertEquals("MAD", GranPremioTarget.MADRID.getCodigoAeropuerto());
        assertEquals("GYD", GranPremioTarget.BAKU.getCodigoAeropuerto());
        assertEquals("SIN", GranPremioTarget.SINGAPUR.getCodigoAeropuerto());
        assertEquals("AUS", GranPremioTarget.AUSTIN.getCodigoAeropuerto());
        assertEquals("MEX", GranPremioTarget.CIUDAD_DE_MEXICO.getCodigoAeropuerto());
        assertEquals("GRU", GranPremioTarget.SAO_PAULO.getCodigoAeropuerto());
        assertEquals("LAS", GranPremioTarget.LAS_VEGAS.getCodigoAeropuerto());
        assertEquals("DOH", GranPremioTarget.LUSAIL.getCodigoAeropuerto());
        assertEquals("AUH", GranPremioTarget.ABU_DABI.getCodigoAeropuerto());

        // Nuevos destinos
        assertEquals("BAH", GranPremioTarget.SAKHIR.getCodigoAeropuerto());
        assertEquals("JED", GranPremioTarget.YEDA.getCodigoAeropuerto());
        assertEquals("MEL", GranPremioTarget.MELBOURNE.getCodigoAeropuerto());
        assertEquals("NGO", GranPremioTarget.SUZUKA.getCodigoAeropuerto());
        assertEquals("PVG", GranPremioTarget.SHANGHAI.getCodigoAeropuerto());
        assertEquals("MIA", GranPremioTarget.MIAMI.getCodigoAeropuerto());
        assertEquals("YUL", GranPremioTarget.MONTREAL.getCodigoAeropuerto());
        assertEquals("NCE", GranPremioTarget.MONTECARLO.getCodigoAeropuerto());
        assertEquals("FAO", GranPremioTarget.PORTIMAO.getCodigoAeropuerto());
        assertEquals("LHR", GranPremioTarget.SILVERSTONE.getCodigoAeropuerto());
    }

    @Test
    @DisplayName("Debe calcular y proveer correctamente las fechas de vuelo de ida y de vuelta para destinos originales")
    void shouldCalculateCorrectFlightDatesForOriginals() {
        GranPremioTarget target = GranPremioTarget.MADRID;
        LocalDate fechaCarrera = target.getFechaCarrera();

        assertEquals(fechaCarrera.minusDays(1), target.getFechaIda());
        assertEquals(fechaCarrera.plusDays(2), target.getFechaVuelta());
        assertEquals(target.getFechaIda(), target.getCheckinDate());
        assertEquals(target.getFechaVuelta(), target.getCheckoutDate());
    }

    @Test
    @DisplayName("Debe contener las fechas exactas requeridas para los nuevos destinos de 2027")
    void shouldHaveExactFlightDatesForNewTargets() {
        // Sakhir: Llegada 11/03/2027 — Salida 15/03/2027
        assertEquals(LocalDate.parse("2027-03-11"), GranPremioTarget.SAKHIR.getFechaIda());
        assertEquals(LocalDate.parse("2027-03-15"), GranPremioTarget.SAKHIR.getFechaVuelta());

        // Yeda: Llegada 18/03/2027 — Salida 22/03/2027
        assertEquals(LocalDate.parse("2027-03-18"), GranPremioTarget.YEDA.getFechaIda());
        assertEquals(LocalDate.parse("2027-03-22"), GranPremioTarget.YEDA.getFechaVuelta());

        // Melbourne: Llegada 01/04/2027 — Salida 05/04/2027
        assertEquals(LocalDate.parse("2027-04-01"), GranPremioTarget.MELBOURNE.getFechaIda());
        assertEquals(LocalDate.parse("2027-04-05"), GranPremioTarget.MELBOURNE.getFechaVuelta());

        // Suzuka: Llegada 08/04/2027 — Salida 12/04/2027
        assertEquals(LocalDate.parse("2027-04-08"), GranPremioTarget.SUZUKA.getFechaIda());
        assertEquals(LocalDate.parse("2027-04-12"), GranPremioTarget.SUZUKA.getFechaVuelta());

        // Shanghái: Llegada 15/04/2027 — Salida 19/04/2027
        assertEquals(LocalDate.parse("2027-04-15"), GranPremioTarget.SHANGHAI.getFechaIda());
        assertEquals(LocalDate.parse("2027-04-19"), GranPremioTarget.SHANGHAI.getFechaVuelta());

        // Miami: Llegada 29/04/2027 — Salida 03/05/2027
        assertEquals(LocalDate.parse("2027-04-29"), GranPremioTarget.MIAMI.getFechaIda());
        assertEquals(LocalDate.parse("2027-05-03"), GranPremioTarget.MIAMI.getFechaVuelta());

        // Montreal: Llegada 20/05/2027 — Salida 24/05/2027
        assertEquals(LocalDate.parse("2027-05-20"), GranPremioTarget.MONTREAL.getFechaIda());
        assertEquals(LocalDate.parse("2027-05-24"), GranPremioTarget.MONTREAL.getFechaVuelta());

        // Montecarlo: Llegada 03/06/2027 — Salida 07/06/2027
        assertEquals(LocalDate.parse("2027-06-03"), GranPremioTarget.MONTECARLO.getFechaIda());
        assertEquals(LocalDate.parse("2027-06-07"), GranPremioTarget.MONTECARLO.getFechaVuelta());

        // Portimão: Llegada 17/06/2027 — Salida 21/06/2027
        assertEquals(LocalDate.parse("2027-06-17"), GranPremioTarget.PORTIMAO.getFechaIda());
        assertEquals(LocalDate.parse("2027-06-21"), GranPremioTarget.PORTIMAO.getFechaVuelta());

        // Silverstone: Llegada 01/07/2027 — Salida 05/07/2027
        assertEquals(LocalDate.parse("2027-07-01"), GranPremioTarget.SILVERSTONE.getFechaIda());
        assertEquals(LocalDate.parse("2027-07-05"), GranPremioTarget.SILVERSTONE.getFechaVuelta());
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

        Optional<GranPremioTarget> opt3 = GranPremioTarget.fromString("sakhir");
        assertTrue(opt3.isPresent());
        assertEquals(GranPremioTarget.SAKHIR, opt3.get());

        Optional<GranPremioTarget> opt4 = GranPremioTarget.fromString("montecarlo");
        assertTrue(opt4.isPresent());
        assertEquals(GranPremioTarget.MONTECARLO, opt4.get());

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
