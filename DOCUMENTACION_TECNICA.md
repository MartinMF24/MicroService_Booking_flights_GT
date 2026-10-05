# Documentación Técnica — Microservicio de Integración de Vuelos (`microservice_flights_gp`)

**Proyecto:** Microservicio ETL de Vuelos Comerciales — Grand Prix Tracker  
**Autor:** Arquitectura de Software & Technical Writing  
**Estándares:** RESTful Maturity Model (Nivel 2 de Richardson), OpenAPI 3.1.0, RFC 9457 (Problem Details for HTTP APIs)  
**Fecha:** Octubre 2026  

---

## 1. Análisis del Contexto y Arquitectura del Dominio

El microservicio `microservice_flights_gp` opera como un **proceso ETL (Extract, Transform, Load) Standalone y desacoplado**, cuyo objetivo primordial es la ingesta periódica y a demanda de inventario de vuelos comerciales reales desde proveedores externos (RapidAPI / Booking Flights API) hacia el repositorio relacional central alojado en Supabase (PostgreSQL).

```
┌─────────────────────────────────┐
│     RapidAPI Booking Flights    │  (Proveedor Externo de Vuelos)
└────────────────┬────────────────┘
                 │ Extracción HTTP (Timeout 5s / Fallback resiliente)
                 ▼
┌─────────────────────────────────┐
│     microservice_flights_gp     │  (ETL Standalone - Spring Boot)
│  - Orquestador de Rutas         │
│  - Adaptador de Datos & Normaliz│
│  - Simulación de Stock          │
└────────────────┬────────────────┘
                 │ Carga Idempotente (Upsert / Claves Naturales)
                 ▼
┌─────────────────────────────────┐
│      Supabase (PostgreSQL)      │  (Inventario local: tabla `vuelos`)
│  - tabla `ciudades` (FK)        │
│  - tabla `vuelos` (Cache local) │
└─────────────────────────────────┘
```

### Características Críticas del Negocio
1. **Lógica de Rutas Asimétricas (Ida y Vuelta):**
   * **Ciudad de Origen Predeterminada:** Buenos Aires, Argentina (código IATA `EZE` / `BUE`).
   * **Vuelo de Ida:** Buenos Aires (`EZE`) &rarr; Aeropuerto de la Ciudad Sede del Gran Premio. La fecha de viaje se sincroniza con el arribo previo a las actividades del fin de semana de carrera.
   * **Vuelo de Vuelta:** Aeropuerto de la Ciudad Sede &rarr; Buenos Aires (`EZE`). La fecha de regreso se coordina con la finalización del evento deportivo.
2. **Operación a Demanda y Desacoplamiento:**
   * La aplicación principal (backend transaccional y frontend de usuario) **nunca consulta la API de RapidAPI en tiempo real** durante la navegación del cliente.
   * Las consultas de los usuarios impactan únicamente contra la tabla `vuelos` en Supabase, garantizando latencias en milisegundos y aislando al sistema de caídas de proveedores, cobros excesivos por petición y bloqueos por cuotas agotadas.
3. **Rol del Microservicio:**
   * Proceso de aprovisionamiento de inventario a demanda, exponiendo una interfaz HTTP RESTful bajo el Nivel 2 del Modelo de Madurez de Richardson para la administración, sincronización y depuración de vuelos.

---

## 2. Entregable 1: Catálogo de Endpoints REST (Nivel 2 de Richardson)

El diseño de la API cumple con el **Nivel 2 de Richardson**:
* Emplea **URIs basadas en sustantivos** que representan recursos (`/api/v1/vuelos`, `/api/v1/vuelos/sync`).
* Utiliza los **verbos HTTP estándar** (`GET`, `POST`, `PATCH`, `DELETE`) según su semántica de lectura, creación/ejecución, modificación parcial y borrado.
* Aplica **códigos de estado HTTP significativos** (`200 OK`, `202 Accepted`, `204 No Content`, `400 Bad Request`, `404 Not Found`, `409 Conflict`, `422 Unprocessable Entity`, `429 Too Many Requests`, `504 Gateway Timeout`).
* Incorpora **versionado semántico explícito en la ruta (`/api/v1/`)**.

### Matriz de Endpoints de Gestión ETL

| Método | URI | Descripción Breve | Códigos HTTP Éxito | Códigos HTTP Error |
|---|---|---|---|---|
| **POST** | `/api/v1/vuelos/sync` | Dispara la sincronización masiva en segundo plano de todos los Grandes Premios configurados en el calendario oficial. | `202 Accepted` | `409 Conflict`, `429 Too Many Requests`, `503 Service Unavailable`, `504 Gateway Timeout` |
| **POST** | `/api/v1/vuelos/sync/{codigoGP}` | Inicia el proceso ETL de búsqueda de vuelos de ida y vuelta para una sede específica (ej. `SAO`, `MAD`, `BAK`). | `202 Accepted` | `400 Bad Request`, `404 Not Found`, `422 Unprocessable Entity`, `429 Too Many Requests`, `504 Gateway Timeout` |
| **GET** | `/api/v1/vuelos` | Consulta el catálogo de vuelos cacheados localmente en Supabase, con filtros opcionales de ruta, fecha y paginación. | `200 OK` | `400 Bad Request`, `500 Internal Server Error` |
| **GET** | `/api/v1/vuelos/{id}` | Recupera la información detallada de un vuelo puntual mediante su identificador único (UUID). | `200 OK` | `400 Bad Request`, `404 Not Found` |
| **PATCH** | `/api/v1/vuelos/{id}` | Actualización granular y manual del precio (`precio_usd`) o stock de asientos (`stock_asientos`) de un vuelo existente. | `200 OK` | `400 Bad Request`, `404 Not Found`, `422 Unprocessable Entity` |
| **DELETE** | `/api/v1/vuelos/expirados` | Tarea de mantenimiento que purga de la base de datos aquellos vuelos cuya fecha de salida sea anterior al momento actual. | `200 OK`, `204 No Content` | `500 Internal Server Error` |

---

### Detalle Específico por Endpoint

#### 1. Sincronización Masiva de Todos los Grandes Premios
* **Método y URI:** `POST /api/v1/vuelos/sync`
* **Descripción:** Dispara un trabajo asíncrono para recorrer todos los circuitos del calendario, extrayendo las tarifas y horarios tanto de ida como de vuelta para cada destino.
* **Cabeceras Opcionales:** `Idempotency-Key` (UUID).
* **Códigos de Respuesta:**
  * `202 Accepted`: Sincronización masiva encolada y procesándose en segundo plano.
  * `409 Conflict`: Ya existe un proceso de sincronización masiva en ejecución.
  * `429 Too Many Requests`: Cuota mensual agotada en el proveedor externo.
  * `504 Gateway Timeout`: Timeout al contactar al gateway de RapidAPI.

#### 2. Sincronización de un Gran Premio Específico
* **Método y URI:** `POST /api/v1/vuelos/sync/{codigoGP}`
* **Descripción:** Ejecuta la extracción y carga ETL exclusivamente para el destino indicado en el parámetro de ruta (ej. `SAO`, `MADRID`, `AUSTIN`). Realiza dos consultas al proveedor: Buenos Aires &rarr; Sede (ida) y Sede &rarr; Buenos Aires (vuelta).
* **Parámetros de Ruta:** `codigoGP` (string, obligatorio; código IATA o nemotécnico del destino).
* **Códigos de Respuesta:**
  * `202 Accepted`: Proceso de sincronización aceptado para ejecución en background.
  * `400 Bad Request`: Parámetro `codigoGP` con formato inválido o no reconocido.
  * `422 Unprocessable Entity`: La ciudad destino no existe en la tabla de referencia `ciudades` de la base de datos.
  * `429 Too Many Requests`: Límite de tasa excedido en el proveedor de vuelos.
  * `504 Gateway Timeout`: La API de RapidAPI no respondió dentro de la ventana de 5 segundos.

#### 3. Listar Catálogo de Vuelos Locales
* **Método y URI:** `GET /api/v1/vuelos`
* **Descripción:** Retorna los vuelos persistidos en Supabase. Soporta filtros por ciudad origen (`origenId`), destino (`destinoId`), rango de fechas (`desde`, `hasta`), dirección (`IDA`, `VUELTA`) y parámetros de paginación (`page`, `size`).
* **Códigos de Respuesta:**
  * `200 OK`: Lista paginada de vuelos obtenida exitosamente.
  * `400 Bad Request`: Parámetros de consulta con formato de fecha o UUID inválido.

#### 4. Detalle de un Vuelo Específico
* **Método y URI:** `GET /api/v1/vuelos/{id}`
* **Descripción:** Obtiene la ficha completa de un vuelo individual persistido en Supabase, incluyendo aerolínea, horarios en UTC, precio en USD y stock de asientos.
* **Parámetros de Ruta:** `id` (UUIDv4 del vuelo).
* **Códigos de Respuesta:**
  * `200 OK`: Vuelo encontrado y retornado.
  * `400 Bad Request`: El identificador provisto no corresponde a un UUID válido.
  * `404 Not Found`: No existe ningún vuelo con el UUID provisto.

#### 5. Actualización Manual de Precio y Stock
* **Método y URI:** `PATCH /api/v1/vuelos/{id}`
* **Descripción:** Permite a un operador administrativo o servicio de reservas ajustar de manera directa el precio o el cupo disponible de un vuelo sin alterar el resto de las propiedades.
* **Cuerpo de la Petición (Request Body):**
  ```json
  {
    "precioUsd": 780.50,
    "stockAsientos": 18
  }
  ```
* **Códigos de Respuesta:**
  * `200 OK`: Vuelo actualizado correctamente.
  * `400 Bad Request`: Valores negativos en precio o stock.
  * `404 Not Found`: Identificador de vuelo no encontrado.
  * `422 Unprocessable Entity`: Incumplimiento de reglas de negocio en los valores numéricos.

#### 6. Purgar Vuelos con Fechas Expiradas
* **Método y URI:** `DELETE /api/v1/vuelos/expirados`
* **Descripción:** Elimina de la base de datos aquellos registros cuya `fecha_salida` sea anterior a la fecha y hora actual en UTC, liberando espacio y manteniendo el catálogo actualizado.
* **Códigos de Respuesta:**
  * `200 OK`: Vuelos obsoletos eliminados, retornando el número de filas borradas.
  * `204 No Content`: No existían vuelos expirados para purgar.

---

## 3. Entregable 2: Contrato OpenAPI 3.1.0 (YAML)

El siguiente fragmento describe formalmente los dos endpoints clave (`POST /api/v1/vuelos/sync/{codigoGP}` y `GET /api/v1/vuelos`), integrando esquemas de respuesta exitosa y de error bajo la especificación **RFC 9457 (Problem Details for HTTP APIs - `application/problem+json`)**.

```yaml
openapi: 3.1.0
info:
  title: API REST de Sincronización de Vuelos — Grand Prix Tracker
  description: Microservicio ETL standalone para la extracción, transformación y persistencia de vuelos hacia y desde las sedes del campeonato de Fórmula 1.
  version: 1.0.0
  contact:
    name: Equipo de Arquitectura de Software
    email: arquitectura@grandprixtracker.com

servers:
  - url: https://api.grandprixtracker.com
    description: Servidor de Producción
  - url: http://localhost:8082
    description: Entorno de Desarrollo Local

paths:
  /api/v1/vuelos/sync/{codigoGP}:
    post:
      summary: Disparar sincronización ETL para un Gran Premio específico
      description: |
        Inicia un trabajo asíncrono para extraer ofertas de vuelos de ida (Buenos Aires -> GP) 
        y de vuelta (GP -> Buenos Aires) desde el proveedor RapidAPI y persistirlas en la base de datos.
      operationId: sincronizarVuelosPorDestino
      tags:
        - Sincronización ETL
      parameters:
        - name: codigoGP
          in: path
          required: true
          description: Nemotécnico o código de circuito del Gran Premio (ej. SAO, MAD, BAK, SAKHIR, MIAMI).
          schema:
            type: string
            examples:
              - SAO
              - MAD
              - SAKHIR
        - name: Idempotency-Key
          in: header
          required: false
          description: Clave UUID de idempotencia para prevenir dobles ejecuciones concurrentes.
          schema:
            type: string
            format: uuid
      responses:
        '202':
          description: Proceso ETL aceptado para ejecución en background.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/SyncJobAcceptedResponse'
        '400':
          description: Parámetro de ruta inválido o no reconocido.
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetails'
        '422':
          description: Entidad no procesable; la ciudad sede no existe en la tabla de ciudades.
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetails'
        '429':
          description: Cuota mensual o límite de peticiones excedido en el proveedor de vuelos.
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetails'
        '504':
          description: Timeout superado al comunicarse con el proveedor externo de vuelos.
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetails'

  /api/v1/vuelos:
    get:
      summary: Consultar catálogo de vuelos persistidos
      description: Recupera una colección paginada de vuelos previamente ingeridos en la base de datos Supabase.
      operationId: listarVuelos
      tags:
        - Catálogo de Vuelos
      parameters:
        - name: destinoId
          in: query
          required: false
          description: UUID de la ciudad destino para filtrar los vuelos.
          schema:
            type: string
            format: uuid
        - name: direccion
          in: query
          required: false
          description: Dirección del trayecto respecto a Buenos Aires.
          schema:
            type: string
            enum: [IDA, VUELTA]
        - name: fechaDesde
          in: query
          required: false
          description: Filtro de fecha mínima de salida (ISO 8601).
          schema:
            type: string
            format: date
        - name: page
          in: query
          required: false
          description: Número de página (0-indexed).
          schema:
            type: integer
            default: 0
            minimum: 0
        - name: size
          in: query
          required: false
          description: Cantidad de registros por página.
          schema:
            type: integer
            default: 20
            maximum: 100
      responses:
        '200':
          description: Catálogo de vuelos obtenido exitosamente.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/VueloCollectionResponse'
        '400':
          description: Parámetros de consulta inválidos.
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetails'
        '500':
          description: Error interno del servidor o falla de acceso a base de datos.
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetails'

components:
  schemas:
    SyncJobAcceptedResponse:
      type: object
      required:
        - jobId
        - status
        - target
        - message
        - submittedAt
        - trackingUrl
      properties:
        jobId:
          type: string
          format: uuid
          example: 9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d
        status:
          type: string
          enum: [ACCEPTED, RUNNING]
          example: ACCEPTED
        target:
          type: string
          example: SAO
        message:
          type: string
          example: La sincronización de vuelos para São Paulo (EZE <-> GRU) ha iniciado en background.
        submittedAt:
          type: string
          format: date-time
          example: "2026-10-05T14:30:00Z"
        trackingUrl:
          type: string
          format: uri
          example: /api/v1/vuelos/sync/jobs/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d

    VueloItem:
      type: object
      required:
        - idVuelo
        - aerolinea
        - origenIdCiudad
        - destinoIdCiudad
        - fechaSalida
        - fechaLlegada
        - precioUsd
        - stockAsientos
        - direccion
      properties:
        idVuelo:
          type: string
          format: uuid
          example: 3fa85f64-5717-4562-b3fc-2c963f66afa6
        aerolinea:
          type: string
          example: LATAM Airlines
        origenIdCiudad:
          type: string
          format: uuid
          example: b14f828a-6617-48f8-8422-5441a11ff497
        destinoIdCiudad:
          type: string
          format: uuid
          example: 4501eeb9-010b-468a-9024-4343f698cf58
        fechaSalida:
          type: string
          format: date-time
          example: "2026-11-05T07:15:00Z"
        fechaLlegada:
          type: string
          format: date-time
          example: "2026-11-05T10:15:00Z"
        precioUsd:
          type: number
          format: double
          example: 320.00
        stockAsientos:
          type: integer
          example: 25
        direccion:
          type: string
          enum: [IDA, VUELTA]
          example: IDA

    VueloCollectionResponse:
      type: object
      required:
        - content
        - totalElements
        - totalPages
        - page
        - size
      properties:
        content:
          type: array
          items:
            $ref: '#/components/schemas/VueloItem'
        totalElements:
          type: integer
          example: 54
        totalPages:
          type: integer
          example: 3
        page:
          type: integer
          example: 0
        size:
          type: integer
          example: 20

    ProblemDetails:
      type: object
      required:
        - type
        - title
        - status
        - detail
        - instance
      properties:
        type:
          type: string
          format: uri
          description: Referencia URI que identifica la tipología del error (RFC 9457).
          example: https://api.grandprixtracker.com/errors/gateway-timeout
        title:
          type: string
          description: Breve resumen legible del tipo de problema.
          example: Gateway Timeout
        status:
          type: integer
          description: Código de estado HTTP generado por el servidor de origen.
          example: 504
        detail:
          type: string
          description: Explicación humana detallada y específica de la ocurrencia.
          example: El proveedor externo de vuelos excedió el umbral máximo de espera de 5000 ms.
        instance:
          type: string
          format: uri
          description: URI que identifica la ocurrencia específica del problema.
          example: /api/v1/vuelos/sync/SAO
        timestamp:
          type: string
          format: date-time
          description: Extensión RFC 9457 indicando el instante exacto del error.
          example: "2026-10-05T14:32:00Z"
        provider:
          type: string
          description: Extensión RFC 9457 identificando el servicio upstream que falló.
          example: rapidapi-booking-com15
```

---

## 4. Entregable 3: Ejemplos JSON Concretos

### Ejemplo 1: Disparo de Sincronización Exitoso (`202 Accepted`)

Este escenario representa una invocación para el Gran Premio de São Paulo (`SAO`). Dado que la búsqueda involucra dos operaciones HTTP externas de latencia variable (ida Buenos Aires &rarr; São Paulo y vuelta São Paulo &rarr; Buenos Aires), el microservicio responde con `202 Accepted` indicando que el trabajo ha sido registrado y encolado en background.

#### Petición HTTP:
```http
POST /api/v1/vuelos/sync/SAO HTTP/1.1
Host: api.grandprixtracker.com
Idempotency-Key: e82b7145-6cb3-4881-bfda-99da0781e050
Content-Type: application/json
Accept: application/json
```

#### Respuesta HTTP:
```http
HTTP/1.1 202 Accepted
Content-Type: application/json
Location: /api/v1/vuelos/sync/jobs/8fc34910-449e-4a67-b52b-42fa112de840
X-Job-Id: 8fc34910-449e-4a67-b52b-42fa112de840

{
  "jobId": "8fc34910-449e-4a67-b52b-42fa112de840",
  "status": "ACCEPTED",
  "target": "SAO",
  "circuito": "Gran Premio de São Paulo (Autódromo de Interlagos)",
  "rutasProgramadas": [
    {
      "direccion": "IDA",
      "origenIata": "EZE",
      "destinoIata": "GRU",
      "fechaVuelo": "2026-11-05"
    },
    {
      "direccion": "VUELTA",
      "origenIata": "GRU",
      "destinoIata": "EZE",
      "fechaVuelo": "2026-11-08"
    }
  ],
  "message": "La búsqueda de vuelos de ida (EZE -> GRU) y vuelta (GRU -> EZE) ha comenzado en background.",
  "submittedAt": "2026-10-05T14:35:10Z",
  "trackingUrl": "/api/v1/vuelos/sync/jobs/8fc34910-449e-4a67-b52b-42fa112de840"
}
```

---

### Ejemplo 2: Respuesta de Error bajo Estándar Problem Details (`RFC 9457`)

Este escenario simula el agotamiento de la cuota mensual en la cuenta de RapidAPI Booking Flights (`429 Too Many Requests`), o bien un timeout estricto donde no fue posible completar la operación (`504 Gateway Timeout`). Se formatea con el tipo de contenido estándar `application/problem+json`.

#### Petición HTTP:
```http
POST /api/v1/vuelos/sync/SAO HTTP/1.1
Host: api.grandprixtracker.com
Accept: application/json
```

#### Respuesta HTTP (Ejemplo Cuota Agotada — `429 Too Many Requests`):
```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/problem+json
Retry-After: 3600
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0

{
  "type": "https://api.grandprixtracker.com/errors/quota-exceeded",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "El cupo gratuito de peticiones asignado en RapidAPI para el proveedor Booking Flights ha sido consumido en su totalidad para el período mensual actual.",
  "instance": "/api/v1/vuelos/sync/SAO",
  "timestamp": "2026-10-05T14:36:22Z",
  "provider": "booking-com15.p.rapidapi.com",
  "retryAfterSeconds": 3600,
  "fallbackAvailable": true,
  "recommendation": "Active el parámetro 'allowFallback=true' o verifique la renovación de la clave RAPIDAPI_KEY en las propiedades de entorno del microservicio."
}
```

#### Respuesta HTTP Alternativa (Timeout con Proveedor Externo — `504 Gateway Timeout`):
```http
HTTP/1.1 504 Gateway Timeout
Content-Type: application/problem+json

{
  "type": "https://api.grandprixtracker.com/errors/gateway-timeout",
  "title": "Gateway Timeout",
  "status": 504,
  "detail": "El proveedor externo de vuelos (RapidAPI) no respondió dentro del umbral de tolerancia fijado en 5000 ms al consultar la ruta EZE -> GRU.",
  "instance": "/api/v1/vuelos/sync/SAO",
  "timestamp": "2026-10-05T14:36:50Z",
  "provider": "booking-com15.p.rapidapi.com",
  "timeoutLimitMs": 5000,
  "circuitBreakerState": "OPEN"
}
```

---

## 5. Entregable 4: Decisiones Justificadas y Manejo de Fallas

### 5.1. Implementación de Idempotencia en Sincronizaciones Manuales (`POST`)

Para evitar que múltiples peticiones manuales concurrentes o reintentos automáticos generen llamadas duplicadas a RapidAPI consumiendo la cuota innecesariamente, implementamos una **estrategia de idempotencia en dos niveles**:

1. **Idempotency Key en la Capa HTTP:**  
   El cliente puede enviar una cabecera `Idempotency-Key: <UUID>`. El microservicio registra esta clave en un almacén en memoria o caché distribuida (Redis / tabla de control en base de datos) con un tiempo de vida (TTL) de 10 a 15 minutos, asociándole el estado del trabajo (`PROCESSING`, `COMPLETED`, `FAILED`). Si ingresa una segunda petición con la misma clave mientras la primera está en curso, el microservicio no dispara llamadas externas y retorna inmediatamente el código `202 Accepted` original con el mismo identificador de trabajo (`jobId`), protegiendo el cupo de la API.
2. **Idempotencia a Nivel de Base de Datos (Clave Natural y Upsert):**  
   Aun si dos sincronizaciones se ejecutaran sobre el mismo Gran Premio, la persistencia en Supabase se rige por una clave natural compuesta:
   $$\text{LOWER}(\text{aerolínea}) + \text{origen\_id\_ciudad} + \text{destino\_id\_ciudad} + \text{fecha\_salida}$$
   Si el registro ya existe, la base de datos ejecuta una actualización (`UPDATE` de precio y stock) en lugar de un `INSERT`, asegurando que la base de datos permanezca en un estado determinista y sin duplicados.

---

### 5.2. Manejo de Timeouts (5s), Resiliencia y Fallback Agraciado

#### El Problema
Las APIs externas de agregación de vuelos son propensas a picos de latencia de red, límites de cuota (HTTP 429) o caídas del gateway. Si el cliente o el microservicio esperan indefinidamente, se agotan los hilos del servidor web y la base de datos queda desprovista de inventario para la aplicación principal.

#### Mecanismo de Resiliencia Implementado
1. **Configuración Estricta de Timeouts:**  
   El cliente HTTP (`RestTemplate` o `WebClient`) se inicializa con una política rígida:
   * **Connect Timeout:** 3000 ms.
   * **Read Timeout:** 5000 ms (umbral máximo de 5 segundos).
2. **Degradación Agraciada (Fallback Pattern):**  
   Si la petición supera los 5 segundos (`ResourceAccessException` / `SocketTimeoutException`) o retorna un código de error de proveedor (`429 Too Many Requests`, `500`, `502`, `503`), un interceptor o bloque de recuperación activa de manera inmediata el **Generador de Vuelos Representativos**.  
   Este componente sintetiza ofertas de vuelos con aerolíneas reales por trayecto (ej. LATAM y Gol para São Paulo; Iberia y Air Europa para Madrid; Qatar Airways para Doha), horarios plausibles y duraciones acordes a la distancia geográfica, junto a un stock simulado de 5 a 50 asientos.
3. **Persistencia Exitosa y Continuidad Operativa:**  
   La fase de persistencia (Load) almacena estos vuelos en la base de datos Supabase con absoluta normalidad e integridad referencial, asegurando que los usuarios de la aplicación central puedan continuar cotizando y armando paquetes de viaje sin bloqueos.

#### ¿Qué código HTTP devuelve y cómo utiliza Problem Details para advertir la contingencia?

En una arquitectura REST Nivel 2, existen dos modalidades operativas según el modo de invocación:

##### Modalidad A: Sincronización Tolerante / Degradada (Comportamiento por Defecto)
* **Código HTTP:** **`200 OK`** (o **`202 Accepted`** si es asíncrono).
* **Mecanismo de Notificación:**  
  La operación concluye con éxito a nivel de persistencia de datos. Para transparentar que los datos son simulados, el microservicio añade cabeceras HTTP de metadatos y un bloque de advertencia en el cuerpo JSON:
  * Cabecera: `X-Data-Source: FALLBACK_SIMULATED`
  * Cabecera: `X-Provider-Status: TIMEOUT_EXCEEDED`
  * Cuerpo de respuesta:
    ```json
    {
      "success": true,
      "message": "Sincronización completada con datos de contingencia. El proveedor externo excedió el tiempo límite de 5000 ms.",
      "dataSource": "FALLBACK_SIMULATED",
      "data": { ... }
    }
    ```

##### Modalidad B: Sincronización Estricta (`strictMode=true`) con Notificación RFC 9457
Si el cliente administrativo configuró que no acepta datos simulados bajo ninguna circunstancia:
* **Código HTTP:** **`504 Gateway Timeout`**.
* **Estructura RFC 9457 (`application/problem+json`):**  
  El microservicio no inserta datos simulados y responde con un documento Problem Details oficial que especifica la causa, el proveedor involucrado y la posibilidad de ejecutar el reintento con fallback habilitado:
  ```json
  {
    "type": "https://api.grandprixtracker.com/errors/gateway-timeout",
    "title": "Gateway Timeout",
    "status": 504,
    "detail": "La consulta hacia RapidAPI Booking Flights excedió el límite de 5000 ms para la ruta EZE -> GRU. La persistencia fue cancelada en modo estricto.",
    "instance": "/api/v1/vuelos/sync/SAO",
    "timestamp": "2026-10-05T14:38:00Z",
    "provider": "booking-com15.p.rapidapi.com",
    "timeoutMs": 5000,
    "fallbackAvailable": true
  }
  ```

Esta dualidad garantiza que el microservicio sea **totalmente resiliente en producción** (la aplicación nunca se queda sin datos de vuelos) y a la vez **transparente y auditable** ante los operadores del sistema.
