# MicroService Flights GP — Módulo ETL (RapidAPI Booking Flights)

Microservicio ETL (**Extract, Transform, Load**) para la ingesta, normalización y persistencia de vuelos hacia y desde las sedes del campeonato de Fórmula 1 en la base de datos PostgreSQL (Supabase).

---

## 1. Arquitectura y Ubicación

Este proyecto es un microservicio autónomo desarrollado con **Java 24** y **Spring Boot 4.1.1**, organizado bajo una arquitectura **Package by Feature**:

```
src/main/java/com/uade/microservices/flights/
├── BookingFlightsMicroserviceApplication.java   # Punto de entrada de la aplicación Spring Boot
│
├── adapter/
│   └── FlightAdapter.java                       # Fase TRANSFORM: Transforma y normaliza DTOs a entidades Vuelo (stock simulado 5-50)
├── config/
│   ├── FlightRapidApiProperties.java            # Mapeo de credenciales y propiedades de RapidAPI y vuelos
│   ├── FlightRestTemplateConfig.java            # Configuración del bean RestTemplate con timeouts
│   └── CorsConfig.java                          # Configuración de CORS para clientes web
├── controller/
│   └── FlightSyncController.java                # Endpoints REST para sincronización manual (POST /all, POST /{target})
├── dto/
│   ├── rapidapi/
│   │   ├── FlightRawDto.java                    # DTO tolerante para capturar cada vuelo crudo de RapidAPI
│   │   └── RapidApiFlightResponseDto.java       # DTO contenedor de respuestas de RapidAPI
│   └── response/
│       ├── FlightSummaryDto.java                # Resumen individual de vuelo sincronizado (con dirección IDA/VUELTA)
│       ├── FlightSyncResultDto.java             # Resultado de la sincronización de un Gran Premio específico
│       └── FlightSyncAllSummaryDto.java         # Consolidado general de sincronización masiva
├── model/
│   ├── Ciudad.java                              # Entidad JPA para la tabla ciudades (resolución de FKs)
│   ├── FlightDirection.java                     # Enum indicador de dirección de vuelo (IDA / VUELTA)
│   ├── GranPremioTarget.java                    # Catálogo Enum de carreras, fechas, códigos IATA y ciudades
│   └── Vuelo.java                               # Entidad JPA para la tabla vuelos
├── repository/
│   ├── CiudadRepository.java                    # Repositorio JPA para búsqueda de ciudades por nombre/alias
│   └── VueloRepository.java                     # Repositorio Spring Data JPA de vuelos (queries Upsert)
├── service/
│   ├── FlightClientService.java                 # Fase EXTRACT: Cliente HTTP hacia RapidAPI Booking Flights
│   └── FlightSyncService.java                   # Fase LOAD y Orquestador del flujo ETL (Upsert atómico)
└── shared/
    ├── exception/
    │   └── GlobalExceptionHandler.java          # Manejador centralizado de errores HTTP
    └── response/
        └── ApiResponse.java                     # Estructura uniforme de respuesta API
```

---

## 2. El Catálogo de Destinos, Fechas y Aeropuertos (`GranPremioTarget`)

El Enum `GranPremioTarget` centraliza el calendario oficial de eventos de 2026, los códigos de aeropuerto destino (IATA) y los metadatos geográficos.

La **ciudad y aeropuerto de origen siempre es Buenos Aires** (`EZE` / `BUE`). Por cada sede de carrera, el ETL realiza **dos búsquedas solo ida**:
- **Vuelo de Ida**: Buenos Aires (`EZE`) &rarr; Aeropuerto Sede GP (Fecha = check-in, 1 día antes de la carrera).
- **Vuelo de Vuelta**: Aeropuerto Sede GP &rarr; Buenos Aires (`EZE`) (Fecha = check-out, 2 días después de la carrera).

| Enum Constant | Sede / Ciudad | Fecha Carrera | Fecha Ida (-1 día) | Fecha Vuelta (+2 días) | Código IATA Destino | Origen |
|---|---|:---:|:---:|:---:|:---:|:---:|
| `MADRID` | Madrid | 2026-09-11 | 2026-09-10 | 2026-09-13 | `MAD` | EZE (Buenos Aires) |
| `BAKU` | Bakú | 2026-09-25 | 2026-09-24 | 2026-09-27 | `GYD` | EZE (Buenos Aires) |
| `SINGAPUR` | Singapur | 2026-10-09 | 2026-10-08 | 2026-10-11 | `SIN` | EZE (Buenos Aires) |
| `AUSTIN` | Austin | 2026-10-23 | 2026-10-22 | 2026-10-25 | `AUS` | EZE (Buenos Aires) |
| `CIUDAD_DE_MEXICO` | Ciudad de México | 2026-10-30 | 2026-10-29 | 2026-11-01 | `MEX` | EZE (Buenos Aires) |
| `SAO_PAULO` | São Paulo | 2026-11-06 | 2026-11-05 | 2026-11-08 | `GRU` | EZE (Buenos Aires) |
| `LAS_VEGAS` | Las Vegas | 2026-11-19 | 2026-11-18 | 2026-11-21 | `LAS` | EZE (Buenos Aires) |
| `LUSAIL` | Lusail (Doha) | 2026-11-27 | 2026-11-26 | 2026-11-29 | `DOH` | EZE (Buenos Aires) |
| `ABU_DABI` | Abu Dabi | 2026-12-04 | 2026-12-03 | 2026-12-06 | `AUH` | EZE (Buenos Aires) |

---

## 3. Configuración y Credenciales (.env / application.properties)

```properties
# Puerto dinámico (default 8082 en local para evitar conflicto con backend 8080 y hoteles 8081)
server.port=${PORT:8082}

# ===============================================
# Configuración RapidAPI - Booking Flights ETL
# ===============================================
rapidapi.booking.key=${RAPIDAPI_KEY:}
rapidapi.booking.host=${RAPIDAPI_HOST:booking-com15.p.rapidapi.com}
rapidapi.booking.base-url=${RAPIDAPI_BOOKING_BASE_URL:https://booking-com15.p.rapidapi.com}
rapidapi.booking.endpoint=${RAPIDAPI_BOOKING_ENDPOINT:/api/v1/flights/searchFlights}

# Constante de origen por defecto (Buenos Aires: EZE / BUE)
flights.origin.iata=${FLIGHTS_ORIGIN_IATA:EZE}
flights.origin.city-name=${FLIGHTS_ORIGIN_CITY_NAME:Buenos Aires}
```

> **Resiliencia Operativa**: Si no se define `RAPIDAPI_KEY` o ante errores de cuota (HTTP 429), credenciales (HTTP 401/403) o latencia de red, el microservicio activa automáticamente un generador de vuelos representativos con aerolíneas reales por ruta (Iberia, LATAM, Qatar Airways, Singapore Airlines, etc.), garantizando que el pipeline de persistencia y Upsert funcione sin interrupciones ni excepciones no controladas.

---

## 4. Esquema de Base de Datos y Mapeo JPA

### Tabla `vuelos` &rarr; Entidad JPA `Vuelo`
```sql
CREATE TABLE public.vuelos (
  id_vuelo uuid NOT NULL DEFAULT extensions.uuid_generate_v4 (),
  aerolinea character varying(100) NOT NULL,
  origen_id_ciudad uuid NOT NULL,
  destino_id_ciudad uuid NOT NULL,
  fecha_salida timestamp with time zone NOT NULL,
  fecha_llegada timestamp with time zone NOT NULL,
  precio_usd numeric(10, 2) NOT NULL,
  stock_asientos integer NOT NULL DEFAULT 0,
  created_at timestamp with time zone NULL DEFAULT timezone ('utc'::text, now()),
  CONSTRAINT vuelos_pkey PRIMARY KEY (id_vuelo),
  CONSTRAINT vuelos_destino_id_ciudad_fkey FOREIGN KEY (destino_id_ciudad) REFERENCES ciudades (id_ciudad) ON DELETE RESTRICT,
  CONSTRAINT vuelos_origen_id_ciudad_fkey FOREIGN KEY (origen_id_ciudad) REFERENCES ciudades (id_ciudad) ON DELETE RESTRICT
);
```

- **Mapeo JPA**:
  - `id_vuelo` (`UUID`, PK).
  - `aerolinea` (`String`, `length = 100`, no nulo).
  - `origen_id_ciudad` (`UUID`, FK hacia `ciudades`, no nulo).
  - `destino_id_ciudad` (`UUID`, FK hacia `ciudades`, no nulo).
  - `fecha_salida` (`OffsetDateTime`, timestamptz, no nulo).
  - `fecha_llegada` (`OffsetDateTime`, timestamptz, no nulo).
  - `precio_usd` (`BigDecimal`, `precision = 10, scale = 2`, no nulo).
  - `stock_asientos` (`Integer`, default `0`, simulado con un valor aleatorio de **5 a 50** si la API externa no lo provee).
  - `created_at` (`OffsetDateTime`, timestamptz).

---

## 5. Lógica de Upsert en Persistencia (LOAD)

El proceso de carga aplica la siguiente política en [`VueloRepository`](src/main/java/com/uade/microservices/flights/repository/VueloRepository.java):
* Busca si ya existe un vuelo con:
  1. Misma `aerolínea` (insensible a mayúsculas/minúsculas).
  2. Mismo `origen_id_ciudad`.
  3. Mismo `destino_id_ciudad`.
  4. Misma `fecha_salida` exacta.
* **Si existe**: Actualiza el precio (`precio_usd`), el stock (`stock_asientos`) y la fecha de llegada (`fecha_llegada`).
* **Si no existe**: Inserta el registro como nuevo vuelo en la tabla `vuelos`.

---

## 6. Endpoints REST (Ejecución Manual)

> **Nota**: El servicio no utiliza `@Scheduled`. La ejecución se dispara mediante solicitudes HTTP POST.

### 1. Sincronización Masiva de Todos los Destinos (Recomendado)
- **Método**: `POST`
- **URL**: `http://localhost:8082/api/microservicios/sync-flights/all` *(o `/sync-all`)*
- **Descripción**: Ejecuta el pipeline ETL para los 9 Grandes Premios de forma secuencial, procesando vuelos de ida y vuelta para cada uno.
- **Ejemplo**:
```bash
curl -X POST http://localhost:8082/api/microservicios/sync-flights/all
```

### 2. Sincronización Manual de un Destino Específico
- **Método**: `POST`
- **URL**: `http://localhost:8082/api/microservicios/sync-flights/{target}`
- **Parámetros**: Nombre del enum en `GranPremioTarget` (ej: `MADRID`, `SAO_PAULO`, `BAKU`, etc.)
- **Ejemplo**:
```bash
curl -X POST http://localhost:8082/api/microservicios/sync-flights/SAO_PAULO
```

### 3. Catálogo Informativo de Destinos y Fechas
- **Método**: `GET`
- **URL**: `http://localhost:8082/api/microservicios/sync-flights/targets`
- **Ejemplo**:
```bash
curl -X GET http://localhost:8082/api/microservicios/sync-flights/targets
```

---

## 7. Manejo Centralizado de Excepciones y Resiliencia

El microservicio desacopla las fallas externas de la persistencia interna:

| Escenario de Falla | Causa Raíz | Comportamiento del Microservicio | Respuesta HTTP al Cliente |
|---|---|---|:---:|
| **API Key faltante o inválida** | Variable `RAPIDAPI_KEY` ausente o dummy. | Conmuta a datos de simulación representativos con aerolíneas reales por ruta. | `200 OK` (`success: true`) |
| **Cuota agotada en RapidAPI (HTTP 429)** | Límite mensual o de ráfaga excedido. | Captura `HttpStatusCodeException`, registra `WARN` y activa fallback representativo. | `200 OK` (`success: true`) |
| **Timeout de Conexión/Lectura** | Latencia de red (>10s conectar / >15s lectura). | Captura `ResourceAccessException` y continúa con fallback sin bloquear el hilo. | `200 OK` (`success: true`) |
| **Fallo de Conexión / Timeout de Base de Datos** | Supabase pausado o pooler 6543 bloqueado. | Capturado por `GlobalExceptionHandler` (`CannotCreateTransactionException`). | `503 Service Unavailable` |
| **Violación de Integridad / FK** | `origen_id_ciudad` o `destino_id_ciudad` inexistente. | Capturado por `GlobalExceptionHandler` (`DataIntegrityViolationException`). | `409 Conflict` |
| **Ciudad no Encontrada en Catálogo** | Nombre de ciudad no existente en Supabase. | Capturado por `GlobalExceptionHandler` (`IllegalStateException`). | `422 Unprocessable Entity` |

---

## 8. Pruebas Automatizadas

El proyecto cuenta con **21 pruebas unitarias y de integración** con Mockito y MockMvc que cubren la extracción, transformación con simulación de stock, persistencia Upsert, catálogo Enum y controladores REST.

Para ejecutar todas las pruebas:
```bash
./mvnw clean test
```
