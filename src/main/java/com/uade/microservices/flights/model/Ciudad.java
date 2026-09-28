package com.uade.microservices.flights.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidad JPA mapeada a la tabla public.ciudades.
 * Permite consultar el id_ciudad real almacenado en Supabase a partir del nombre de la ciudad.
 */
@Entity
@Table(name = "ciudades")
public class Ciudad {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_ciudad", nullable = false, updatable = false)
    private UUID idCiudad;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "id_pais")
    private UUID idPais;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public Ciudad() {
    }

    public Ciudad(UUID idCiudad, String nombre) {
        this.idCiudad = idCiudad;
        this.nombre = nombre;
    }

    public Ciudad(UUID idCiudad, String nombre, UUID idPais, OffsetDateTime createdAt) {
        this.idCiudad = idCiudad;
        this.nombre = nombre;
        this.idPais = idPais;
        this.createdAt = createdAt;
    }

    public UUID getIdCiudad() {
        return idCiudad;
    }

    public void setIdCiudad(UUID idCiudad) {
        this.idCiudad = idCiudad;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public UUID getIdPais() {
        return idPais;
    }

    public void setIdPais(UUID idPais) {
        this.idPais = idPais;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
