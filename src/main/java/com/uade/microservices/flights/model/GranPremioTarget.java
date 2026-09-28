package com.uade.microservices.flights.model;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * Catálogo de destinos de Gran Premio de Fórmula 1 y fechas de carrera para 2026.
 * Incluye metadatos necesarios para el proceso ETL de vuelos (código IATA, fechas y ciudad asociada).
 */
public enum GranPremioTarget {

    MADRID(
            "2026-09-11",
            "-391194",
            "city",
            "Madrid",
            UUID.fromString("34053323-6e73-47d9-b306-b4dc8df920cb"),
            "MAD"
    ),
    BAKU(
            "2026-09-25",
            "-2422998",
            "city",
            "Bakú",
            UUID.fromString("a010620b-e30a-48c6-8b2a-21ec0b18bb90"),
            "GYD"
    ),
    SINGAPUR(
            "2026-10-09",
            "-114060",
            "city",
            "Singapur",
            UUID.fromString("1c3915ad-97c0-46cf-9a93-d73826bee539"),
            "SIN"
    ),
    AUSTIN(
            "2026-10-23",
            "20014288",
            "city",
            "Austin",
            UUID.fromString("6f9d5fbb-c1a3-492e-a625-1cb1942282a6"),
            "AUS"
    ),
    CIUDAD_DE_MEXICO(
            "2026-10-30",
            "-1658079",
            "city",
            "Ciudad de México",
            UUID.fromString("c3be3c61-bb7c-433b-8249-b59b1eba96c9"),
            "MEX"
    ),
    SAO_PAULO(
            "2026-11-06",
            "-671824",
            "city",
            "São Paulo",
            UUID.fromString("4501eeb9-010b-468a-9024-4343f698cf58"),
            "GRU"
    ),
    LAS_VEGAS(
            "2026-11-19",
            "20079110",
            "city",
            "Las Vegas",
            UUID.fromString("0ddcaf82-55ca-4c1c-af87-b777acca60d4"),
            "LAS"
    ),
    LUSAIL(
            "2026-11-27",
            "-2092875",
            "city",
            "Lusail",
            UUID.fromString("a81419b4-d2d2-4bc7-9255-7db766e71a32"),
            "DOH"
    ),
    ABU_DABI(
            "2026-12-04",
            "-782066",
            "city",
            "Abu Dabi",
            UUID.fromString("d028fc05-78cd-4dd8-96d0-9134ff624468"),
            "AUH"
    );

    public static final String ORIGEN_DEFAULT_IATA = "EZE";
    public static final String ORIGEN_CIUDAD_NOMBRE = "Buenos Aires";
    public static final UUID BUENOS_AIRES_DEFAULT_ID = UUID.fromString("b14f828a-6617-48f8-8422-5441a11ff497");

    private final LocalDate fechaCarrera;
    private final String destId;
    private final String destType;
    private final String nombreCiudad;
    private final UUID ciudadId;
    private final String codigoAeropuerto;

    GranPremioTarget(String fechaCarrera, String destId, String destType, String nombreCiudad, UUID ciudadId, String codigoAeropuerto) {
        this.fechaCarrera = LocalDate.parse(fechaCarrera);
        this.destId = destId;
        this.destType = destType;
        this.nombreCiudad = nombreCiudad;
        this.ciudadId = ciudadId;
        this.codigoAeropuerto = codigoAeropuerto;
    }

    public LocalDate getFechaCarrera() {
        return fechaCarrera;
    }

    public String getDestId() {
        return destId;
    }

    public String getDestType() {
        return destType;
    }

    public String getNombreCiudad() {
        return nombreCiudad;
    }

    public UUID getCiudadId() {
        return ciudadId;
    }

    public String getCodigoAeropuerto() {
        return codigoAeropuerto;
    }

    /**
     * Fecha del vuelo de ida: 1 día antes de la carrera (check-in de viaje).
     */
    public LocalDate getFechaIda() {
        return this.fechaCarrera.minusDays(1);
    }

    public LocalDate getCheckinDate() {
        return getFechaIda();
    }

    /**
     * Fecha del vuelo de vuelta: 2 días después de la carrera (checkout de viaje).
     */
    public LocalDate getFechaVuelta() {
        return this.fechaCarrera.plusDays(2);
    }

    public LocalDate getCheckoutDate() {
        return getFechaVuelta();
    }

    /**
     * Busca un destino por nombre insensible a mayúsculas, minúsculas o guiones.
     */
    public static Optional<GranPremioTarget> fromString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase().replace("-", "_");
        return Arrays.stream(values())
                .filter(target -> target.name().equals(normalized))
                .findFirst();
    }
}
