# MicroService Flights GP — Módulo ETL (RapidAPI Booking Flights)

Documentación técnica y operativa del microservicio ETL de vuelos para la plataforma Grand Prix Tracker.

---

## 1. Funcionalidad del Microservicio

### Propósito Fundamental
La función primordial de este microservicio es **cargar datos reales de vuelos comerciales en nuestra base de datos relacional para que posteriormente sean consumidos de manera eficiente y confiable por la aplicación principal (frontend y backend central)**.

### ¿Por qué existe este microservicio y qué problemática resuelve?
1. **Desacoplamiento de APIs Externas**: En lugar de que los usuarios finales o los servicios de negocio consulten directamente proveedores externos de vuelos (con el costo económico, lentitud, límites de tasa y fallos de disponibilidad que ello implica), este microservicio asume la responsabilidad exclusiva de ingestar, transformar y persistir los vuelos de antemano.
2. **Disponibilidad Inmediata para la Aplicación**: La aplicación cliente consulta directamente la base de datos interna (PostgreSQL / Supabase), obteniendo tiempos de respuesta inmediatos (milisegundos) y eliminando la dependencia en tiempo real de servicios de terceros durante la navegación del usuario.
3. **Normalización y Estandarización**: Convierte estructuras de datos heterogéneas, variantes y complejas provenientes del proveedor externo en un modelo de dominio uniforme, consistente y con integridad referencial hacia el catálogo de ciudades y circuitos del campeonato.
4. **Alimentación del Ecosistema de Reservas**: Permite que el sistema de paquetes turísticos y reservas de la plataforma disponga de opciones de transporte aéreo (ida y vuelta) coordinadas con las fechas oficiales de cada Gran Premio de Fórmula 1.

---

## 2. Explicación Conceptual de los Patrones Utilizados

> **Nota de Diseño**: En esta sección se explican los fundamentos, motivaciones y resolución conceptual de cada patrón adoptado en la solución, sin atarse a rutas físicas de archivos ni números de línea.

### 2.1. Patrón ETL (Extract, Transform, Load)
* **Concepto**: Es un patrón de integración y procesamiento de datos dividido en tres fases secuenciales y bien delimitadas:
  * **Extracción (Extract)**: Recupera datos crudos desde una o más fuentes externas (en este caso, proveedores de vuelos vía HTTP), manejando la comunicación de red, cabeceras, parámetros de búsqueda y tolerancia a fallos de conectividad.
  * **Transformación (Transform)**: Limpia, filtra, valida y transforma los datos crudos hacia las estructuras internas del dominio. Aquí se unifican formatos de fecha y hora (conversión a UTC con zona horaria), se normalizan nombres de aerolíneas, se calcula la duración estimada, se fija la escala de precios y se generan valores derivados o complementarios (como el stock de asientos disponibles cuando la fuente externa no lo provee).
  * **Carga (Load)**: Persiste los datos transformados en el repositorio de destino asegurando integridad relacional (claves foráneas), atomicidad de operaciones e idempotencia.

### 2.2. Patrón Adapter (Adaptador)
* **Concepto**: Permite que dos interfaces o estructuras incompatibles trabajen juntas. En este contexto, el proveedor externo entrega esquemas JSON cambiantes, anidados y con nomenclaturas arbitrarias. El patrón Adaptador actúa como un traductor entre esa representación externa y la entidad del modelo de datos interno.
* **Beneficio**: Aislar completamente el modelo de dominio interno de las mutaciones o inconsistencias de la API externa. Si el proveedor cambia sus nombres de campos o estructura, el núcleo del sistema no se ve afectado; solo la lógica de adaptación absorbe el cambio.

### 2.3. Patrón Repository (Repositorio)
* **Concepto**: Media entre la capa de lógica de negocio y la capa de acceso a datos, encapsulando las operaciones de consulta y persistencia en una colección conceptual de objetos en memoria.
* **Beneficio**: Oculta los detalles del motor de base de datos relacional y el lenguaje SQL. Proporciona métodos específicos para realizar operaciones de búsqueda avanzada mediante claves naturales de negocio, facilitando la detección de registros existentes para operaciones de inserción o actualización.

### 2.4. Patrón Data Transfer Object (DTO)
* **Concepto**: Objetos simples destinados a transportar información entre capas o subsistemas sin contener lógica de negocio.
* **Beneficio**: 
  * Se utilizan DTOs tolerantes de entrada para recibir la respuesta cruda de proveedores externos ignorando atributos irrelevantes.
  * Se utilizan DTOs de salida para estructurar las respuestas REST enviadas a los clientes, evitando exponer directamente las entidades de base de datos y ocultando detalles internos o relaciones no deseadas.

### 2.5. Patrón Fallback / Graceful Degradation (Degradación Agraciada)
* **Concepto**: Estrategia de diseño orientada a la resiliencia en la cual, ante la falla, indisponibilidad, agotamiento de cuota o falta de credenciales de un servicio externo dependiente, el sistema no colapsa ni interrumpe el flujo general, sino que conmuta de forma transparente a una fuente alternativa de datos representativos y coherentes.
* **Beneficio**: Garantiza la continuidad operativa del pipeline. Incluso en entornos locales de desarrollo sin llaves de API o ante límites de peticiones excedidos, el microservicio sigue cargando vuelos válidos en la base de datos para que la aplicación consumidora nunca se quede sin datos.

### 2.6. Patrón Orchestrator / Arquitectura en Capas
* **Concepto**: Desacopla la coordinación de alto nivel de las tareas operativas individuales. Un componente orquestador gestiona el flujo secuencial completo: consulta las ciudades en la base de datos, dispara la extracción de ida y de vuelta, invoca la transformación y delega la persistencia.
* **Beneficio**: Alta cohesión y bajo acoplamiento. Cada etapa tiene una única responsabilidad y el orquestador se limita a dirigir el flujo de ejecución y coordinar las transacciones.

### 2.7. Patrón de Idempotencia y Upsert (Update or Insert)
* **Concepto**: Garantiza que la ejecución repetida de un proceso de sincronización con los mismos parámetros produzca el mismo resultado en el almacenamiento de datos, sin duplicar registros.
* **Beneficio**: Si un vuelo con la misma aerolínea, ruta y horario exacto ya existe en la base de datos, el sistema actualiza su tarifa y disponibilidad en lugar de insertar un duplicado. Si no existe, lo inserta como nuevo registro.

---

## 3. Catálogo de Destinos, Fechas y Reglas de Vuelo

El microservicio sincroniza vuelos para los Grandes Premios oficiales de Fórmula 1: tanto los destinos del calendario 2026 como los nuevos destinos de la temporada 2027.

### Reglas de Ruta y Calendario
* **Ciudad y Aeropuerto de Origen**: Siempre es **Buenos Aires** (`EZE` / `BUE`).
* **Vuelo de Ida**: Buenos Aires (`EZE`) &rarr; Aeropuerto de la Ciudad Sede (en la fecha de llegada especificada).
* **Vuelo de Vuelta**: Aeropuerto de la Ciudad Sede &rarr; Buenos Aires (`EZE`) (en la fecha de salida/regreso especificada).

### 3.1. Tabla Maestra de Destinos Originales (Temporada 2026)

| Identificador Target | Ciudad Sede | Fecha Carrera | Fecha Ida (-1 día) | Fecha Vuelta (+2 días) | Código IATA Destino | Origen |
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

### 3.2. Tabla Maestra de Nuevos Destinos (Temporada 2027)

| Identificador Target | Ciudad Sede / País | Fecha Ida (Llegada) | Fecha Vuelta (Salida) | Código IATA Destino | Aeropuerto Principal / Referencia | Origen |
|---|---|:---:|:---:|:---:|:---:|:---:|
| `SAKHIR` | Sakhir (Bahréin) | 2027-03-11 | 2027-03-15 | `BAH` | Bahrain International Airport | EZE (Buenos Aires) |
| `YEDA` | Yeda (Arabia Saudita) | 2027-03-18 | 2027-03-22 | `JED` | King Abdulaziz International Airport | EZE (Buenos Aires) |
| `MELBOURNE` | Melbourne (Australia) | 2027-04-01 | 2027-04-05 | `MEL` | Melbourne Airport (Tullamarine) | EZE (Buenos Aires) |
| `SUZUKA` | Suzuka (Japón) | 2027-04-08 | 2027-04-12 | `NGO` | Chubu Centrair International Airport (Nagoya) | EZE (Buenos Aires) |
| `SHANGHAI` | Shanghái (China) | 2027-04-15 | 2027-04-19 | `PVG` | Shanghai Pudong International Airport | EZE (Buenos Aires) |
| `MIAMI` | Miami (Estados Unidos) | 2027-04-29 | 2027-05-03 | `MIA` | Miami International Airport | EZE (Buenos Aires) |
| `MONTREAL` | Montreal (Canadá) | 2027-05-20 | 2027-05-24 | `YUL` | Montréal-Pierre Elliott Trudeau Airport | EZE (Buenos Aires) |
| `MONTECARLO` | Montecarlo (Mónaco) | 2027-06-03 | 2027-06-07 | `NCE` | Nice Côte d'Azur Airport (acceso Mónaco) | EZE (Buenos Aires) |
| `PORTIMAO` | Portimão (Portugal) | 2027-06-17 | 2027-06-21 | `FAO` | Faro Airport (Algarve / Portimão) | EZE (Buenos Aires) |
| `SILVERSTONE` | Silverstone (Reino Unido) | 2027-07-01 | 2027-07-05 | `LHR` | London Heathrow Airport | EZE (Buenos Aires) |

---

## 4. Configuración y Entorno de Ejecución

### Variables de Entorno y Propiedades (`application.properties`)

```properties
# Puerto de escucha del microservicio (default 8082 para convivir con backend en 8080 y hoteles en 8081)
server.port=${PORT:8082}

# Base de Datos PostgreSQL (Supabase Transaction Pooler en puerto 6543)
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://aws-0-us-west-2.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:postgres.zprznayvpeijjoiknird}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:uade123uade}
spring.datasource.driver-class-name=org.postgresql.Driver

# Configuración del Pool HikariCP
spring.datasource.hikari.maximum-pool-size=${HIKARI_MAX_POOL_SIZE:3}
spring.datasource.hikari.minimum-idle=${HIKARI_MIN_IDLE:1}
spring.datasource.hikari.idle-timeout=${HIKARI_IDLE_TIMEOUT:30000}
spring.datasource.hikari.max-lifetime=${HIKARI_MAX_LIFETIME:60000}
spring.datasource.hikari.connection-timeout=${HIKARI_CONNECTION_TIMEOUT:20000}
spring.datasource.hikari.pool-name=BookingFlightsHikariPool
spring.datasource.hikari.data-source-properties.prepareThreshold=0

# Proveedor de Datos Externo (RapidAPI Booking Flights)
rapidapi.booking.key=${RAPIDAPI_KEY:}
rapidapi.booking.host=${RAPIDAPI_HOST:booking-com15.p.rapidapi.com}
rapidapi.booking.base-url=${RAPIDAPI_BOOKING_BASE_URL:https://booking-com15.p.rapidapi.com}
rapidapi.booking.endpoint=${RAPIDAPI_BOOKING_ENDPOINT:/api/v1/flights/searchFlights}

# Parámetros por Defecto de Origen
flights.origin.iata=${FLIGHTS_ORIGIN_IATA:EZE}
flights.origin.city-name=${FLIGHTS_ORIGIN_CITY_NAME:Buenos Aires}
```

---

## 5. Estructura de la Base de Datos y Persistencia

### Definición DDL de la Tabla `vuelos` (PostgreSQL)

```sql
CREATE TABLE IF NOT EXISTS public.vuelos (
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
  CONSTRAINT vuelos_destino_id_ciudad_fkey FOREIGN KEY (destino_id_ciudad) REFERENCES public.ciudades (id_ciudad) ON DELETE RESTRICT,
  CONSTRAINT vuelos_origen_id_ciudad_fkey FOREIGN KEY (origen_id_ciudad) REFERENCES public.ciudades (id_ciudad) ON DELETE RESTRICT
);
```

### Índices de Rendimiento
1. **`idx_vuelos_origen_destino`**: Optimiza las búsquedas de vuelos por trayecto (`origen_id_ciudad`, `destino_id_ciudad`).
2. **`idx_vuelos_upsert`**: Optimiza la verificación de duplicados para el proceso de Upsert (`LOWER(aerolinea)`, `origen_id_ciudad`, `destino_id_ciudad`, `fecha_salida`).
3. **`idx_vuelos_fecha_salida`**: Agiliza los filtros y ordenamiento cronológico por fecha de partida.

---

## 6. Contrato de la API REST

Este apartado define detalladamente **qué necesita el microservicio para operar**, **cómo entrega los datos a los consumidores** y **cuáles son las reglas técnicas y de negocio** aplicadas.

### 6.1. ¿Qué Necesitamos? (Requisitos de Entrada / Request Contract)

1. **Protocolo y Método**:
   * Operaciones de sincronización: **`POST`** (las peticiones modifican el estado de la base de datos).
   * Operaciones de catálogo / consulta informativa: **`GET`**.
2. **Cuerpo de la Petición (Request Body)**:
   * **No se requiere cuerpo (`empty body`)** para disparar la sincronización. Los parámetros se deducen del catálogo preestablecido del calendario o del parámetro en la URL.
3. **Parámetros de Ruta (Path Variables)**:
   * Para sincronización individual (`/{target}`): Se debe enviar el identificador del destino.
   * **Tolerancia de escritura**: Es insensible a mayúsculas/minúsculas y admite guiones o guiones bajos (por ejemplo: `sao_paulo`, `SAO_PAULO`, `sao-paulo`, `MADRID`, `madrid`).
4. **Cabeceras HTTP**:
   * `Accept: application/json`
   * CORS habilitado universalmente (`*`) para admitir invocaciones desde paneles administrativos web o aplicaciones cliente.
5. **Requisitos de Datos Previos en Base de Datos**:
   * Las ciudades correspondientes a Buenos Aires y las sedes de los Grandes Premios deben existir en la tabla `ciudades` de PostgreSQL. El servicio resuelve los identificadores UUID mediante coincidencia exacta, normalización sin tildes, lista de alias conocidos o identificadores preconfigurados de contingencia.

---

### 6.2. ¿Cómo nos lo Brinda? (Formato de Salida / Response Contract)

Todas las respuestas del microservicio siguen un contrato uniforme estándar estructurado a través del objeto envoltorio `ApiResponse<T>`:

```json
{
  "success": true,
  "message": "Descripción clara del resultado de la operación",
  "data": { ... }
}
```

#### Estructura de Campos del Envoltorio:
* **`success`** (`boolean`): Indica si la operación concluyó exitosamente (`true`) o si ocurrió una falla (`false`).
* **`message`** (`string`): Mensaje descriptivo con el resumen de la acción realizada o la causa del fallo.
* **`data`** (`object` / `array` / `null`): Carga útil de respuesta. En caso de error, su valor es `null`.

---

### 6.3. Detalle de Endpoints y Ejemplos de Respuesta

#### 1. Sincronización Masiva de Todos los Destinos (Recomendado)
* **Método**: `POST`
* **Rutas disponibles**: 
  * `/api/microservicios/sync-flights/all`
  * `/api/microservicios/sync-flights/sync-all`
  * `/api/microservicios/sync-flights`
* **Descripción**: Dispara secuencialmente la extracción, transformación y persistencia de vuelos de ida y vuelta para las 9 sedes de Gran Premio del calendario 2026.
* **Respuesta Exitosa (`200 OK`)**:

```json
{
  "success": true,
  "message": "Sincronización masiva de vuelos completada: 9 destinos exitosos de 9 procesados (36 vuelos creados, 18 actualizados)",
  "data": {
    "totalDestinosProcesados": 9,
    "destinosExitosos": 9,
    "destinosConError": 0,
    "totalVuelosExtraidos": 54,
    "totalVuelosCreados": 36,
    "totalVuelosActualizados": 18,
    "fechaEjecucion": "2026-09-29T16:50:00Z",
    "resultados": [
      {
        "target": "MADRID",
        "nombreCiudad": "Madrid",
        "codigoAeropuertoDestino": "MAD",
        "fechaCarrera": "2026-09-11",
        "fechaIda": "2026-09-10",
        "fechaVuelta": "2026-09-13",
        "totalVuelosExtraidos": 6,
        "vuelosCreados": 4,
        "vuelosActualizados": 2,
        "vuelosIda": 2,
        "vuelosVuelta": 2,
        "estado": "SUCCESS",
        "mensaje": "Sincronización ETL de vuelos para Madrid completada con éxito. Vuelos creados: 4 (Ida: 2, Vuelta: 2), Vuelos actualizados: 2.",
        "fechaSincronizacion": "2026-09-29T16:50:02Z",
        "vuelos": [
          {
            "idVuelo": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
            "aerolinea": "Iberia",
            "origenIdCiudad": "b14f828a-6617-48f8-8422-5441a11ff497",
            "destinoIdCiudad": "34053323-6e73-47d9-b306-b4dc8df920cb",
            "fechaSalida": "2026-09-10T07:15:00Z",
            "fechaLlegada": "2026-09-10T19:15:00Z",
            "precioUsd": 820.00,
            "stockAsientos": 23,
            "direccion": "IDA"
          },
          {
            "idVuelo": "a18ef930-11cc-4299-b123-9c88b3f4d112",
            "aerolinea": "Aerolíneas Argentinas",
            "origenIdCiudad": "34053323-6e73-47d9-b306-b4dc8df920cb",
            "destinoIdCiudad": "b14f828a-6617-48f8-8422-5441a11ff497",
            "fechaSalida": "2026-09-13T08:30:00Z",
            "fechaLlegada": "2026-09-13T20:30:00Z",
            "precioUsd": 925.00,
            "stockAsientos": 31,
            "direccion": "VUELTA"
          }
        ]
      }
    ]
  }
}
```

---

#### 2. Sincronización Masiva Exclusiva de los Nuevos Destinos (Temporada 2027)
* **Método**: `POST`
* **Rutas disponibles**:
  * `/api/microservicios/sync-flights/new-destinations`
  * `/api/microservicios/sync-flights/nuevos-destinos`
  * `/api/microservicios/sync-flights/sync-new`
  * `/api/microservicios/sync-flights/new`
* **Descripción**: Dispara secuencialmente la extracción, transformación y persistencia de vuelos de ida y vuelta **únicamente para los 10 nuevos destinos de la temporada 2027** (Sakhir, Yeda, Melbourne, Suzuka, Shanghái, Miami, Montreal, Montecarlo, Portimão y Silverstone), sin reprocesar los destinos anteriores.
* **Respuesta Exitosa (`200 OK`)**:

```json
{
  "success": true,
  "message": "Sincronización de nuevos destinos completada: 10 destinos exitosos de 10 procesados (40 vuelos creados, 20 actualizados)",
  "data": {
    "totalDestinosProcesados": 10,
    "destinosExitosos": 10,
    "destinosConError": 0,
    "totalVuelosExtraidos": 60,
    "totalVuelosCreados": 40,
    "totalVuelosActualizados": 20,
    "fechaEjecucion": "2026-09-29T17:10:00Z",
    "resultados": [
      {
        "target": "SAKHIR",
        "nombreCiudad": "Sakhir",
        "codigoAeropuertoDestino": "BAH",
        "fechaCarrera": "2027-03-14",
        "fechaIda": "2027-03-11",
        "fechaVuelta": "2027-03-15",
        "totalVuelosExtraidos": 6,
        "vuelosCreados": 4,
        "vuelosActualizados": 2,
        "vuelosIda": 2,
        "vuelosVuelta": 2,
        "estado": "SUCCESS",
        "mensaje": "Sincronización ETL de vuelos para Sakhir completada con éxito. Vuelos creados: 4 (Ida: 2, Vuelta: 2), Vuelos actualizados: 2.",
        "fechaSincronizacion": "2026-09-29T17:10:03Z",
        "vuelos": [
          {
            "idVuelo": "e4b0c201-1111-4444-8888-000000000001",
            "aerolinea": "Gulf Air",
            "origenIdCiudad": "b14f828a-6617-48f8-8422-5441a11ff497",
            "destinoIdCiudad": "e4b0c201-1111-4444-8888-000000000001",
            "fechaSalida": "2027-03-11T07:15:00Z",
            "fechaLlegada": "2027-03-12T02:15:00Z",
            "precioUsd": 950.00,
            "stockAsientos": 25,
            "direccion": "IDA"
          }
        ]
      }
    ]
  }
}
```

---

#### 3. Sincronización Manual de un Destino Específico
* **Método**: `POST`
* **Ruta**: `/api/microservicios/sync-flights/{target}`
* **Ejemplo**: `POST http://localhost:8082/api/microservicios/sync-flights/SAO_PAULO`
* **Respuesta Exitosa (`200 OK`)**:

```json
{
  "success": true,
  "message": "Sincronización ETL de vuelos ejecutada correctamente para São Paulo",
  "data": {
    "target": "SAO_PAULO",
    "nombreCiudad": "São Paulo",
    "codigoAeropuertoDestino": "GRU",
    "fechaCarrera": "2026-11-06",
    "fechaIda": "2026-11-05",
    "fechaVuelta": "2026-11-08",
    "totalVuelosExtraidos": 6,
    "vuelosCreados": 6,
    "vuelosActualizados": 0,
    "vuelosIda": 3,
    "vuelosVuelta": 3,
    "estado": "SUCCESS",
    "mensaje": "Sincronización ETL de vuelos para São Paulo completada con éxito. Vuelos creados: 6 (Ida: 3, Vuelta: 3), Vuelos actualizados: 0.",
    "fechaSincronizacion": "2026-09-29T16:51:15Z",
    "vuelos": [
      {
        "idVuelo": "c83f9821-22ab-41dd-9124-7efbc3456789",
        "aerolinea": "LATAM Airlines",
        "origenIdCiudad": "b14f828a-6617-48f8-8422-5441a11ff497",
        "destinoIdCiudad": "4501eeb9-010b-468a-9024-4343f698cf58",
        "fechaSalida": "2026-11-05T07:15:00Z",
        "fechaLlegada": "2026-11-05T10:15:00Z",
        "precioUsd": 320.00,
        "stockAsientos": 15,
        "direccion": "IDA"
      }
    ]
  }
}
```

---

#### 3. Catálogo Informativo de Destinos y Fechas
* **Método**: `GET`
* **Ruta**: `/api/microservicios/sync-flights/targets`
#### 4. Catálogo Informativo de Destinos y Fechas
* **Método**: `GET`
* **Ruta**: `/api/microservicios/sync-flights/targets`
* **Descripción**: Expone el listado completo de los 19 Grandes Premios soportados (2026 y 2027), sus aeropuertos, si es nuevo destino, temporada y el cálculo de fechas de ida y vuelta.
* **Respuesta Exitosa (`200 OK`)**:

```json
{
  "success": true,
  "message": "Listado de destinos disponibles para sincronización de vuelos",
  "data": [
    {
      "enum": "MADRID",
      "ciudad": "Madrid",
      "codigoAeropuerto": "MAD",
      "fechaCarrera": "2026-09-11",
      "fechaVueloIda": "2026-09-10",
      "fechaVueloVuelta": "2026-09-13",
      "origen": "Buenos Aires (EZE/BUE)",
      "nuevoDestino": false,
      "temporada": "2026"
    },
    {
      "enum": "SAKHIR",
      "ciudad": "Sakhir",
      "codigoAeropuerto": "BAH",
      "fechaCarrera": "2027-03-14",
      "fechaVueloIda": "2027-03-11",
      "fechaVueloVuelta": "2027-03-15",
      "origen": "Buenos Aires (EZE/BUE)",
      "nuevoDestino": true,
      "temporada": "2027"
    }
  ]
}
```

---

### 6.4. ¿Cuáles son las Reglas? (Reglas de Negocio, Operativas y de Validación)

1. **Regla de Unicidad y Criterio de Upsert**:
   * La clave natural que identifica unívocamente a un vuelo es la combinación de:
     $$\text{LOWER}(\text{aerolínea}) + \text{origen\_id\_ciudad} + \text{destino\_id\_ciudad} + \text{fecha\_salida}$$
   * **Si ya existe**: Se conservan la clave primaria (`id_vuelo`) y la fecha de creación original (`created_at`), y se actualizan el precio (`precio_usd`), la fecha de llegada (`fecha_llegada`) y la disponibilidad de asientos (`stock_asientos`).
   * **Si no existe**: Se crea un nuevo registro con UUID autogenerado y marca temporal actual en `created_at`.

2. **Regla de Cálculo de Fechas (Ida y Vuelta)**:
   * **Destinos Originales (2026)**:
     * $\text{Fecha de Ida} = \text{Fecha Oficial del GP} - 1 \text{ día}$
     * $\text{Fecha de Vuelta} = \text{Fecha Oficial del GP} + 2 \text{ días}$
   * **Nuevos Destinos (2027)**:
     * $\text{Fecha de Ida} = \text{Fecha de Llegada exacta solicitada}$ (Buenos Aires &rarr; Destino).
     * $\text{Fecha de Vuelta} = \text{Fecha de Salida exacta solicitada}$ (Destino &rarr; Buenos Aires).
   * Ambas búsquedas se ejecutan de manera independiente como vuelos individuales ("one-way") para garantizar la captura de disponibilidad real por tramo.

3. **Regla de Simulación de Stock de Asientos**:
   * Si la API externa no provee el campo de asientos disponibles o informa un valor menor o igual a cero, el sistema asigna de forma determinista un número pseudoaleatorio en el rango de **5 a 50 asientos**.
   * Esta regla asegura que la aplicación consumidora siempre disponga de inventario transaccional para simular o procesar reservas.

4. **Regla de Normalización y Truncamiento de Datos**:
   * Los nombres de aerolínea se recortan a un máximo de **100 caracteres** para cumplir estrictamente con la restricción de longitud de la base de datos.
   * Los precios se convierten a `BigDecimal` con **escala de 2 decimales y redondeo HALF_UP**.
   * Las fechas y horas se normalizan y almacenan con zona horaria UTC (`OffsetDateTime` / `timestamptz`).

5. **Regla de Aislamiento de Fallos en Sincronización Masiva**:
   * Durante la ejecución de `/all` o `/new-destinations`, si el procesamiento de un Gran Premio particular falla, el error se captura y aísla localmente registrando el estado `"ERROR"` para ese destino en el resumen, permitiendo que el resto de las sedes continúe procesándose normalmente sin abortar el lote.

6. **Regla de Degradación Agraciada ante Proveedor Externo**:
   * Si la clave `RAPIDAPI_KEY` no está configurada, es inválida, se agota la cuota mensual (HTTP 429), o la llamada externa sufre un tiempo de espera excedido (>10s conexión, >15s lectura), el servicio activa automáticamente la generación de vuelos representativos con aerolíneas auténticas según el destino y duraciones coherentes por ruta, retornando un código HTTP `200 OK` con persistencia exitosa.

---

### 6.5. Códigos de Estado HTTP y Manejo de Errores

| Código HTTP | Nombre | Escenario de Aplicación | Estructura de Respuesta JSON |
|---|---|---|---|
| **`200 OK`** | Operación Exitosa | Sincronización (individual, masiva o de nuevos destinos) o consulta de catálogo completada exitosamente. | `{"success": true, "message": "...", "data": {...}}` |
| **`400 BAD REQUEST`** | Petición Inválida | El parámetro `{target}` en la URL no coincide con ningún Gran Premio soportado en el catálogo. | `{"success": false, "message": "Destino inválido: 'XYZ'. Los destinos válidos son: [...]", "data": null}` |
| **`409 CONFLICT`** | Conflicto de Integridad | Violación de restricciones de base de datos relacional (ej: claves foráneas o llaves únicas). | `{"success": false, "message": "Error de restricción en base de datos: ...", "data": null}` |
| **`422 UNPROCESSABLE ENTITY`** | Entidad no Procesable | La ciudad de destino configurada en el enum no pudo ser resuelta ni encontrada en la tabla `ciudades` de la base de datos. | `{"success": false, "message": "No se encontró la ciudad destino '...' en la tabla 'ciudades'...", "data": null}` |
| **`503 SERVICE UNAVAILABLE`** | Servicio no Disponible | Pérdida de conectividad con la base de datos PostgreSQL (Supabase), pooler inalcanzable o pausa del servidor. | `{"success": false, "message": "Error de conexión con la base de datos (Supabase): no se pudo abrir la transacción...", "data": null}` |
| **`500 INTERNAL SERVER ERROR`** | Error Interno | Cualquier otra excepción no anticipada producida durante la ejecución. | `{"success": false, "message": "Ha ocurrido un error interno en el microservicio: ...", "data": null}` |

---

## 7. Ejecución de Pruebas Automatizadas

El proyecto incluye una suite integral de **24 pruebas unitarias y de integración** basadas en Mockito, JUnit 5 y Spring MockMvc, que validan la lógica de adaptación, los controladores REST, los endpoints masivos e individuales, el catálogo de 19 destinos y el orquestador ETL.

Para ejecutar la suite completa:
```bash
./mvnw clean test
```
