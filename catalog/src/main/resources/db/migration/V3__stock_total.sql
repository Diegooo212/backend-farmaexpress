-- El inventario informa dos cifras por producto:
--   stock       = lo que se puede comprar en un solo pedido (la sucursal con más unidades); limita el carrito.
--   stock_total = unidades vendibles sumando todas las farmacias y el almacén central; es lo que ve el cliente.
ALTER TABLE medicamentos ADD COLUMN stock_total INT NOT NULL DEFAULT 0;
UPDATE medicamentos SET stock_total = stock;
