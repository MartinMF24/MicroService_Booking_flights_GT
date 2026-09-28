-- ====================================================================
-- microservice_flights_gp - Script DDL e Índices para Supabase
-- Ejecutar en: Supabase Dashboard -> SQL Editor
-- ====================================================================

-- 1. Tabla: vuelos
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

-- Índice para búsquedas rápidas por ruta (origen, destino)
CREATE INDEX IF NOT EXISTS idx_vuelos_origen_destino 
    ON public.vuelos(origen_id_ciudad, destino_id_ciudad);

-- Índice compuesto para agilizar el Upsert (aerolínea, ruta y fecha de salida exacta)
CREATE INDEX IF NOT EXISTS idx_vuelos_upsert 
    ON public.vuelos(LOWER(aerolinea), origen_id_ciudad, destino_id_ciudad, fecha_salida);

-- Índice para ordenamiento o filtros por fecha de salida
CREATE INDEX IF NOT EXISTS idx_vuelos_fecha_salida 
    ON public.vuelos(fecha_salida);
