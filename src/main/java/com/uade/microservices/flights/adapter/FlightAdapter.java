package com.uade.microservices.flights.adapter;

import com.uade.microservices.flights.dto.rapidapi.FlightRawDto;
import com.uade.microservices.flights.model.Vuelo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Fase TRANSFORM del módulo ETL.
 * Implementa el Patrón Adapter para transformar los datos crudos y heterogéneos de vuelos
 * en la entidad JPA Vuelo, validando restricciones de base de datos y simulando stock_asientos si falta.
 */
@Component
public class FlightAdapter {

    private final Random random = new Random();

    /**
     * Transforma una lista de DTOs crudos en entidades Vuelo para la ruta especificada.
     */
    public List<Vuelo> toEntityList(List<FlightRawDto> rawDtos, UUID origenIdCiudad, UUID destinoIdCiudad, LocalDate flightDate) {
        if (rawDtos == null || rawDtos.isEmpty()) {
            return new ArrayList<>();
        }
        return rawDtos.stream()
                .map(rawDto -> toEntity(rawDto, origenIdCiudad, destinoIdCiudad, flightDate))
                .toList();
    }

    /**
     * Transforma un DTO crudo individual en una entidad Vuelo.
     * Si la API no provee stock_asientos, genera un valor simulado entre 5 y 50.
     */
    public Vuelo toEntity(FlightRawDto rawDto, UUID origenIdCiudad, UUID destinoIdCiudad, LocalDate flightDate) {
        if (rawDto == null) {
            throw new IllegalArgumentException("El DTO de vuelo no puede ser nulo.");
        }
        if (origenIdCiudad == null) {
            throw new IllegalArgumentException("El origen_id_ciudad no puede ser nulo.");
        }
        if (destinoIdCiudad == null) {
            throw new IllegalArgumentException("El destino_id_ciudad no puede ser nulo.");
        }

        Vuelo vuelo = new Vuelo();

        // 1. Aerolínea (resuelto y truncado a 100 caracteres)
        String aerolinea = rawDto.resolveAirline("Aerolínea Internacional");
        vuelo.setAerolinea(aerolinea);

        // 2. Claves foráneas a ciudades (Supabase)
        vuelo.setOrigenIdCiudad(origenIdCiudad);
        vuelo.setDestinoIdCiudad(destinoIdCiudad);

        // 3. Fecha y hora de salida
        OffsetDateTime fechaSalida = rawDto.resolveFechaSalida(flightDate, 10, 0);
        vuelo.setFechaSalida(fechaSalida);

        // 4. Fecha y hora de llegada (estimando una duración promedio de 12 horas si no se provee)
        OffsetDateTime fechaLlegada = rawDto.resolveFechaLlegada(fechaSalida, 12);
        vuelo.setFechaLlegada(fechaLlegada);

        // 5. Precio en USD
        BigDecimal precioUsd = rawDto.resolvePrecio(750.00);
        vuelo.setPrecioUsd(precioUsd);

        // 6. Stock de asientos: si la API no lo provee, genera un valor aleatorio entre 5 y 50
        Integer stock = rawDto.resolveStockAsientos();
        if (stock == null || stock <= 0) {
            stock = 5 + random.nextInt(46); // [5 .. 50]
        }
        vuelo.setStockAsientos(stock);

        // 7. Auditoría
        vuelo.setCreatedAt(OffsetDateTime.now());

        return vuelo;
    }
}
