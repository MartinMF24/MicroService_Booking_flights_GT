package com.uade.microservices.flights.repository;

import com.uade.microservices.flights.model.Ciudad;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para la entidad Ciudad (tabla ciudades).
 */
@Repository
public interface CiudadRepository extends JpaRepository<Ciudad, UUID> {

    /**
     * Búsqueda exacta insensible a mayúsculas y minúsculas.
     */
    Optional<Ciudad> findFirstByNombreIgnoreCase(String nombre);

    /**
     * Búsqueda parcial por término en el nombre de la ciudad.
     */
    @Query("SELECT c FROM Ciudad c WHERE LOWER(c.nombre) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<Ciudad> searchByTermIgnoreCase(@Param("term") String term);
}
