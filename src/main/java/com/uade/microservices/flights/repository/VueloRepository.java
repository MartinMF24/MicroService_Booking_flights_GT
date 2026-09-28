package com.uade.microservices.flights.repository;

import com.uade.microservices.flights.model.Vuelo;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para la entidad Vuelo (tabla vuelos).
 */
@Repository
public interface VueloRepository extends JpaRepository<Vuelo, UUID> {

    /**
     * Búsqueda para lógica Upsert: busca si ya existe un vuelo con la misma aerolínea,
     * misma ruta (origen -> destino) y misma fecha/hora exacta de salida.
     */
    @Query("""
        SELECT v FROM Vuelo v 
        WHERE LOWER(v.aerolinea) = LOWER(:aerolinea) 
          AND v.origenIdCiudad = :origenIdCiudad 
          AND v.destinoIdCiudad = :destinoIdCiudad 
          AND v.fechaSalida = :fechaSalida
    """)
    Optional<Vuelo> findFlightForUpsert(
            @Param("aerolinea") String aerolinea,
            @Param("origenIdCiudad") UUID origenIdCiudad,
            @Param("destinoIdCiudad") UUID destinoIdCiudad,
            @Param("fechaSalida") OffsetDateTime fechaSalida
    );

    List<Vuelo> findByOrigenIdCiudadAndDestinoIdCiudad(UUID origenIdCiudad, UUID destinoIdCiudad);

    List<Vuelo> findByDestinoIdCiudad(UUID destinoIdCiudad);
}
