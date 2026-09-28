# MicroService Flights GP (Grand Prix Tracker)

Microservicio ETL de ingesta de vuelos para el proyecto Grand Prix Tracker, desarrollado con **Spring Boot 4.1.1** y **Java 24**.

## Características Principales

- **Arquitectura Package by Feature**: Organizado en `adapter`, `config`, `controller`, `dto`, `model`, `repository`, `service` y `shared`.
- **Integración con Booking Flights (RapidAPI)**: Cliente HTTP con `RestTemplate` tolerante a fallos, soporte de cuota y fallback representativo con aerolíneas reales por sede.
- **Lógica de Rutas Ida y Vuelta**:
  - Origen predeterminado: **Buenos Aires** (`EZE` / `BUE`).
  - Vuelo de Ida: Buenos Aires &rarr; Ciudad GP (1 día antes de la carrera).
  - Vuelo de Vuelta: Ciudad GP &rarr; Buenos Aires (2 días después de la carrera).
- **Entidad JPA `Vuelo` y Base de Datos**: Mapeo estricto del esquema SQL con claves foráneas a la tabla `ciudades` en Supabase PostgreSQL.
- **Stock de Asientos**: Si la API no lo informa, simulación de stock entre 5 y 50 asientos.
- **Upsert Atómico**: Actualización automática de precio y stock cuando ya existe un vuelo con misma aerolínea, ruta y fecha/hora exacta de salida.
- **Ejecución Manual sin `@Scheduled`**: Endpoints REST `POST /api/microservicios/sync-flights/all` y `POST /api/microservicios/sync-flights/{target}`.

## Documentación Completa

Para revisar los detalles arquitectónicos, configuración, endpoints y tolerancia a fallas, consulta [BOOKING_ETL.md](BOOKING_ETL.md).

## Ejecución de Pruebas

```bash
./mvnw clean test
```
