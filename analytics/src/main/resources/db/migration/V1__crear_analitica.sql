-- Ventas recibidas desde el tópico Kafka farmaexpress.ventas (una fila por pedido pagado).
CREATE TABLE ventas (
    -- La clave del mensaje es el pedido: si Kafka entrega el evento dos veces, no se cuenta dos veces.
    pedido_id       BIGINT       NOT NULL,
    evento_id       VARCHAR(64)  NOT NULL,
    -- Momento en que se aprobó el pago (viene en el evento).
    fecha           DATETIME(6)  NOT NULL,
    -- Momento en que la analítica procesó el evento (fecha -> recibida_en = demora del streaming).
    recibida_en     DATETIME(6)  NOT NULL,
    entrega         VARCHAR(20)  NOT NULL,
    sucursal_id     BIGINT,
    sucursal_nombre VARCHAR(120),
    metodo_pago     VARCHAR(30)  NOT NULL,
    costo_despacho  INT          NOT NULL,
    total           INT          NOT NULL,
    unidades        INT          NOT NULL,
    -- Partición y offset del mensaje en Kafka (trazabilidad).
    particion       INT          NOT NULL,
    offset_kafka    BIGINT       NOT NULL,
    CONSTRAINT pk_ventas PRIMARY KEY (pedido_id),
    CONSTRAINT uk_ventas_evento UNIQUE (evento_id)
);

CREATE INDEX ix_ventas_fecha ON ventas (fecha);

CREATE TABLE venta_items (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    pedido_id       BIGINT       NOT NULL,
    medicamento_id  BIGINT       NOT NULL,
    nombre          VARCHAR(150) NOT NULL,
    cantidad        INT          NOT NULL,
    precio_unitario INT          NOT NULL,
    subtotal        INT          NOT NULL,
    CONSTRAINT pk_venta_items PRIMARY KEY (id),
    CONSTRAINT fk_venta_items_venta FOREIGN KEY (pedido_id) REFERENCES ventas (pedido_id) ON DELETE CASCADE
);

CREATE INDEX ix_venta_items_medicamento ON venta_items (medicamento_id);

-- Stock vigente por producto, construido desde el tópico compactado farmaexpress.stock.
CREATE TABLE stock_productos (
    medicamento_id   BIGINT      NOT NULL,
    stock_por_pedido INT         NOT NULL,
    stock_total      INT         NOT NULL,
    actualizado_en   DATETIME(6) NOT NULL,
    particion        INT         NOT NULL,
    offset_kafka     BIGINT      NOT NULL,
    CONSTRAINT pk_stock_productos PRIMARY KEY (medicamento_id)
);
