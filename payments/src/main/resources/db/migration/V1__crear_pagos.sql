-- Un intento de pago de un pedido. Un pedido puede tener varios (por ejemplo, si el primero se rechazó).
CREATE TABLE pagos (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    pedido_id            BIGINT       NOT NULL,
    reserva_id           BIGINT,
    cuenta_id            VARCHAR(64)  NOT NULL,
    monto                INT          NOT NULL,
    metodo               VARCHAR(20)  NOT NULL,
    modo                 VARCHAR(20)  NOT NULL,
    estado               VARCHAR(20)  NOT NULL,
    -- Token o id de la transacción en el proveedor (token_ws de Webpay, preferencia de Mercado Pago, payment_id de Khipu).
    token_proveedor      VARCHAR(120),
    -- Secreto del simulador: evita que alguien apruebe un pago ajeno adivinando su id.
    token_simulador      VARCHAR(64),
    referencia_proveedor VARCHAR(120),
    detalle              VARCHAR(300),
    creado_en            DATETIME(6)  NOT NULL,
    actualizado_en       DATETIME(6)  NOT NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_pagos PRIMARY KEY (id)
);

CREATE INDEX ix_pagos_pedido ON pagos (pedido_id);
CREATE INDEX ix_pagos_token ON pagos (token_proveedor);
