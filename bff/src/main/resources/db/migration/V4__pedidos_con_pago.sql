-- Checkout con pago: el pedido nace "pendiente de pago" con stock reservado en inventory, y pasa a
-- PAGADO, PAGO_RECHAZADO, EXPIRADO o CANCELADO según los eventos de pagos e inventario.
-- Los pedidos anteriores ya estaban pagados (la compra era inmediata).
ALTER TABLE pedidos ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'PAGADO';
ALTER TABLE pedidos ADD COLUMN entrega VARCHAR(20);
ALTER TABLE pedidos ADD COLUMN sucursal_id BIGINT;
ALTER TABLE pedidos ADD COLUMN sucursal_nombre VARCHAR(100);
ALTER TABLE pedidos ADD COLUMN direccion VARCHAR(200);
ALTER TABLE pedidos ADD COLUMN costo_despacho INT NOT NULL DEFAULT 0;
ALTER TABLE pedidos ADD COLUMN metodo_pago VARCHAR(20);
ALTER TABLE pedidos ADD COLUMN reserva_id BIGINT;
ALTER TABLE pedidos ADD COLUMN pago_id BIGINT;
ALTER TABLE pedidos ADD COLUMN detalle_pago VARCHAR(300);
ALTER TABLE pedidos ADD COLUMN expira_en DATETIME(6);
ALTER TABLE pedidos ADD COLUMN actualizado_en DATETIME(6);
