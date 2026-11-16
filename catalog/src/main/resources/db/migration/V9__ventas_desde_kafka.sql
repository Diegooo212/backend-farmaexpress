-- Unidades vendidas de cada producto ("más vendidos"), calculadas desde el tópico Kafka farmaexpress.ventas
-- por el consumer group catalog-ventas.
ALTER TABLE medicamentos ADD COLUMN vendidos INT NOT NULL DEFAULT 0;

-- Pedidos ya contados: Kafka entrega "al menos una vez", así que un evento repetido no suma dos veces.
CREATE TABLE ventas_procesadas (
    pedido_id    BIGINT      NOT NULL,
    procesada_en DATETIME(6) NOT NULL,
    CONSTRAINT pk_ventas_procesadas PRIMARY KEY (pedido_id)
);
