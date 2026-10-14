-- Avisos para los clientes, creados a partir de los eventos que llegan por RabbitMQ.
CREATE TABLE notificaciones (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    -- Id del evento que la originó: evita avisos duplicados si RabbitMQ entrega el mensaje dos veces.
    evento_id     VARCHAR(64)  NOT NULL,
    -- oid de Entra ID del destinatario.
    cuenta_id     VARCHAR(64)  NOT NULL,
    tipo          VARCHAR(20)  NOT NULL,
    referencia_id BIGINT,
    estado        VARCHAR(30),
    titulo        VARCHAR(150) NOT NULL,
    mensaje       VARCHAR(255) NOT NULL,
    detalle       VARCHAR(500),
    leida         BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha         DATETIME(6)  NOT NULL,
    CONSTRAINT pk_notificaciones PRIMARY KEY (id),
    CONSTRAINT uk_notificaciones_evento UNIQUE (evento_id)
);

CREATE INDEX ix_notificaciones_cuenta ON notificaciones (cuenta_id, fecha);
