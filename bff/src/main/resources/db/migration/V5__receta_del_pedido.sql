-- Pedidos con productos de venta bajo receta: la receta validada que los respalda.
ALTER TABLE pedidos ADD COLUMN receta_id BIGINT;
CREATE INDEX ix_pedidos_receta ON pedidos (receta_id);
