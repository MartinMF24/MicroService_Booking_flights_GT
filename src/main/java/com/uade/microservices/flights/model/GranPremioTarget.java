package com.uade.microservices.flights.model;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Catálogo de destinos de Gran Premio de Fórmula 1 y fechas de carrera/viaje.
 * Incluye metadatos necesarios para el proceso ETL de vuelos (código IATA, fechas y ciudad asociada).
 */
public enum GranPremioTarget {

    // ==========================================
    // DESTINOS ORIGINALES (TEMPORADA 2026)
    // ==========================================
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
    ),

    // ==========================================
    // NUEVOS DESTINOS (TEMPORADA 2027)
    // ==========================================
    SAKHIR(
            "2027-03-14",
            "2027-03-11",
            "2027-03-15",
            "-783756",
            "city",
            "Sakhir",
            UUID.fromString("e4b0c201-1111-4444-8888-000000000001"),
            "BAH",
            true
    ),
    YEDA(
            "2027-03-21",
            "2027-03-18",
            "2027-03-22",
            "-3096644",
            "city",
            "Yeda",
            UUID.fromString("e4b0c201-1111-4444-8888-000000000002"),
            "JED",
            true
    ),
    MELBOURNE(
            "2027-04-04",
            "2027-04-01",
            "2027-04-05",
            "-1586835",
            "city",
            "Melbourne",
            UUID.fromString("e4b0c201-1111-4444-8888-000000000003"),
            "MEL",
            true
    ),
    SUZUKA(
            "2027-04-11",
            "2027-04-08",
            "2027-04-12",
            "-244837",
            "city",
            "Suzuka",
            UUID.fromString("e4b0c201-1111-4444-8888-000000000004"),
            "NGO",
            true
    ),
    SHANGHAI(
            "2027-04-18",
            "2027-04-15",
            "2027-04-19",
            "-1924536",
            "city",
            "Shanghái",
            UUID.fromString("e4b0c201-1111-4444-8888-000000000005"),
            "PVG",
            true
    ),
    MIAMI(
            "2027-05-02",
            "2027-04-29",
            "2027-05-03",
            "20023181",
            "city",
            "Miami",
            UUID.fromString("e4b0c201-1111-4444-8888-000000000006"),
            "MIA",
            true
    ),
    MONTREAL(
            "2027-05-23",
            "2027-05-20",
            "2027-05-24",
            "-564344",
            "city",
            "Montreal",
            UUID.fromString("e4b0c201-1111-4444-8888-000000000007"),
            "YUL",
            true
    ),
    MONTECARLO(
            "2027-06-06",
            "2027-06-03",
            "2027-06-07",
            "-90886",
            "city",
            "Montecarlo",
            UUID.fromString("e4b0c201-1111-4444-8888-000000000008"),
            "NCE",
            true
    ),
    PORTIMAO(
            "2027-06-20",
            "2027-06-17",
            "2027-06-21",
            "-2173167",
            "city",
            "Portimão",
            UUID.fromString("e4b0c201-1111-4444-8888-000000000009"),
            "FAO",
            true
    ),
    SILVERSTONE(
            "2027-07-04",
            "2027-07-01",
            "2027-07-05",
            "-2607519",
            "city",
            "Silverstone",
            UUID.fromString("e4b0c201-1111-4444-8888-000000000010"),
            "LHR",
            true
    );

    public static final String ORIGEN_DEFAULT_IATA = "EZE";
    public static final String ORIGEN_CIUDAD_NOMBRE = "Buenos Aires";
    public static final UUID BUENOS_AIRES_DEFAULT_ID = UUID.fromString("b14f828a-6617-48f8-8422-5441a11ff497");

    private final LocalDate fechaCarrera;
    private final LocalDate fechaIda;
    private final LocalDate fechaVuelta;
    private final String destId;
    private final String destType;
    private final String nombreCiudad;
    private final UUID ciudadId;
    private final String codigoAeropuerto;
    private final boolean nuevoDestino;

    // Constructor para destinos originales 2026 (-1 día de ida, +2 días de vuelta)
    GranPremioTarget(String fechaCarrera, String destId, String destType, String nombreCiudad, UUID ciudadId, String codigoAeropuerto) {
        this(
                fechaCarrera,
                LocalDate.parse(fechaCarrera).minusDays(1).toString(),
                LocalDate.parse(fechaCarrera).plusDays(2).toString(),
                destId,
                destType,
                nombreCiudad,
                ciudadId,
                codigoAeropuerto,
                false
        );
    }

    // Constructor completo con fechas de viaje explícitas
    GranPremioTarget(String fechaCarrera, String fechaIda, String fechaVuelta, String destId, String destType,
                     String nombreCiudad, UUID ciudadId, String codigoAeropuerto, boolean nuevoDestino) {
        this.fechaCarrera = LocalDate.parse(fechaCarrera);
        this.fechaIda = LocalDate.parse(fechaIda);
        this.fechaVuelta = LocalDate.parse(fechaVuelta);
        this.destId = destId;
        this.destType = destType;
        this.nombreCiudad = nombreCiudad;
        this.ciudadId = ciudadId;
        this.codigoAeropuerto = codigoAeropuerto;
        this.nuevoDestino = nuevoDestino;
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

    public boolean isNuevoDestino() {
        return nuevoDestino;
    }

    /**
     * Fecha del vuelo de ida (Buenos Aires -> Destino).
     */
    public LocalDate getFechaIda() {
        return this.fechaIda;
    }

    public LocalDate getCheckinDate() {
        return getFechaIda();
    }

    /**
     * Fecha del vuelo de vuelta (Destino -> Buenos Aires).
     */
    public LocalDate getFechaVuelta() {
        return this.fechaVuelta;
    }

    public LocalDate getCheckoutDate() {
        return getFechaVuelta();
    }

    /**
     * Obtiene exclusivamente los 10 nuevos destinos agregados (temporada 2027).
     */
    public static List<GranPremioTarget> getNuevosDestinos() {
        return Arrays.stream(values())
                .filter(GranPremioTarget::isNuevoDestino)
                .toList();
    }

    /**
     * Obtiene los destinos originales del calendario 2026.
     */
    public static List<GranPremioTarget> getDestinosOriginales() {
        return Arrays.stream(values())
                .filter(t -> !t.isNuevoDestino())
                .toList();
    }

    /**
     * Busca un destino por nombre insensible a mayúsculas, minúsculas, acentos o guiones.
     */
    public static Optional<GranPremioTarget> fromString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String clean = stripAccents(value).trim().toUpperCase().replace("-", "_").replace(" ", "_");

        for (GranPremioTarget target : values()) {
            if (target.name().equals(clean)) {
                return Optional.of(target);
            }
            if (stripAccents(target.getNombreCiudad()).equalsIgnoreCase(stripAccents(value).trim())) {
                return Optional.of(target);
            }
        }

        // Variaciones comunes de nombres
        return switch (clean) {
            case "BAHRAIN", "BAHREIN" -> Optional.of(SAKHIR);
            case "JEDDAH", "JIDDAH", "YEDDAH" -> Optional.of(YEDA);
            case "MONACO" -> Optional.of(MONTECARLO);
            case "FARO", "ALGARVE" -> Optional.of(PORTIMAO);
            case "LONDON", "LONDRES" -> Optional.of(SILVERSTONE);
            case "JAPON", "JAPAN", "NAGOYA" -> Optional.of(SUZUKA);
            default -> Optional.empty();
        };
    }

    private static String stripAccents(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
