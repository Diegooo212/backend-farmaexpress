-- Seguimiento del pedido: cada cambio de estado, cuándo y quién lo hizo (cliente, sistema o personal).
CREATE TABLE pedido_historial (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    pedido_id BIGINT       NOT NULL,
    estado    VARCHAR(20)  NOT NULL,
    fecha     DATETIME(6)  NOT NULL,
    nota      VARCHAR(300),
    por       VARCHAR(150),
    CONSTRAINT pk_pedido_historial PRIMARY KEY (id),
    CONSTRAINT fk_historial_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos (id) ON DELETE CASCADE
);

CREATE INDEX ix_pedido_historial ON pedido_historial (pedido_id, fecha);
