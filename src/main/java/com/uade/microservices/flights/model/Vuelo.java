package com.uade.microservices.flights.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad JPA correspondiente a la tabla public.vuelos.
 * Representa los vuelos ingeridos desde la API de Booking / RapidAPI.
 */
@Entity
@Table(name = "vuelos")
public class Vuelo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_vuelo", nullable = false, updatable = false)
    private UUID idVuelo;

    @Column(name = "aerolinea", nullable = false, length = 100)
    private String aerolinea;

    @Column(name = "origen_id_ciudad", nullable = false)
    private UUID origenIdCiudad;

    @Column(name = "destino_id_ciudad", nullable = false)
    private UUID destinoIdCiudad;

    @Column(name = "fecha_salida", nullable = false)
    private OffsetDateTime fechaSalida;

    @Column(name = "fecha_llegada", nullable = false)
    private OffsetDateTime fechaLlegada;

    @Column(name = "precio_usd", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUsd;

    @Column(name = "stock_asientos", nullable = false)
    private Integer stockAsientos = 0;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origen_id_ciudad", insertable = false, updatable = false)
    private Ciudad ciudadOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destino_id_ciudad", insertable = false, updatable = false)
    private Ciudad ciudadDestino;

    public Vuelo() {
    }

    public Vuelo(UUID idVuelo, String aerolinea, UUID origenIdCiudad, UUID destinoIdCiudad,
                 OffsetDateTime fechaSalida, OffsetDateTime fechaLlegada, BigDecimal precioUsd, Integer stockAsientos) {
        this.idVuelo = idVuelo;
        this.aerolinea = aerolinea;
        this.origenIdCiudad = origenIdCiudad;
        this.destinoIdCiudad = destinoIdCiudad;
        this.fechaSalida = fechaSalida;
        this.fechaLlegada = fechaLlegada;
        this.precioUsd = precioUsd;
        this.stockAsientos = stockAsientos;
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
        if (this.stockAsientos == null) {
            this.stockAsientos = 0;
        }
    }

    public UUID getIdVuelo() {
        return idVuelo;
    }

    public void setIdVuelo(UUID idVuelo) {
        this.idVuelo = idVuelo;
    }

    public String getAerolinea() {
        return aerolinea;
    }

    public void setAerolinea(String aerolinea) {
        this.aerolinea = aerolinea;
    }

    public UUID getOrigenIdCiudad() {
        return origenIdCiudad;
    }

    public void setOrigenIdCiudad(UUID origenIdCiudad) {
        this.origenIdCiudad = origenIdCiudad;
    }

    public UUID getDestinoIdCiudad() {
        return destinoIdCiudad;
    }

    public void setDestinoIdCiudad(UUID destinoIdCiudad) {
        this.destinoIdCiudad = destinoIdCiudad;
    }

    public OffsetDateTime getFechaSalida() {
        return fechaSalida;
    }

    public void setFechaSalida(OffsetDateTime fechaSalida) {
        this.fechaSalida = fechaSalida;
    }

    public OffsetDateTime getFechaLlegada() {
        return fechaLlegada;
    }

    public void setFechaLlegada(OffsetDateTime fechaLlegada) {
        this.fechaLlegada = fechaLlegada;
    }

    public BigDecimal getPrecioUsd() {
        return precioUsd;
    }

    public void setPrecioUsd(BigDecimal precioUsd) {
        this.precioUsd = precioUsd;
    }

    public Integer getStockAsientos() {
        return stockAsientos;
    }

    public void setStockAsientos(Integer stockAsientos) {
        this.stockAsientos = stockAsientos;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Ciudad getCiudadOrigen() {
        return ciudadOrigen;
    }

    public void setCiudadOrigen(Ciudad ciudadOrigen) {
        this.ciudadOrigen = ciudadOrigen;
    }

    public Ciudad getCiudadDestino() {
        return ciudadDestino;
    }

    public void setCiudadDestino(Ciudad ciudadDestino) {
        this.ciudadDestino = ciudadDestino;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vuelo vuelo)) return false;
        return Objects.equals(idVuelo, vuelo.idVuelo);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(idVuelo);
    }
}
