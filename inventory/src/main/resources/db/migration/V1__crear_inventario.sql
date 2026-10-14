-- Sucursales: el almacén central (despachos a domicilio) y las farmacias donde se retira.
CREATE TABLE sucursales (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    codigo    VARCHAR(20)  NOT NULL,
    nombre    VARCHAR(100) NOT NULL,
    direccion VARCHAR(200) NOT NULL,
    comuna    VARCHAR(80)  NOT NULL,
    tipo      VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_sucursales PRIMARY KEY (id),
    CONSTRAINT uk_sucursales_codigo UNIQUE (codigo)
);

-- Cada ingreso de mercadería es un lote con su número y fecha de vencimiento (regla FEFO).
CREATE TABLE lotes (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    medicamento_id    BIGINT      NOT NULL,
    sucursal_id       BIGINT      NOT NULL,
    numero_lote       VARCHAR(40) NOT NULL,
    fecha_vencimiento DATE        NOT NULL,
    cantidad_inicial  INT         NOT NULL,
    disponible        INT         NOT NULL,
    reservada         INT         NOT NULL DEFAULT 0,
    ingresado_en      DATETIME(6) NOT NULL,
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_lotes PRIMARY KEY (id),
    CONSTRAINT fk_lotes_sucursal FOREIGN KEY (sucursal_id) REFERENCES sucursales (id),
    CONSTRAINT uk_lotes_numero UNIQUE (medicamento_id, sucursal_id, numero_lote),
    CONSTRAINT ck_lotes_cantidades CHECK (disponible >= 0 AND reservada >= 0)
);

-- Índice para la consulta FEFO: lotes de un producto en una sucursal ordenados por vencimiento.
CREATE INDEX ix_lotes_fefo ON lotes (medicamento_id, sucursal_id, fecha_vencimiento);

-- Trazabilidad: todo lo que entra o sale de un lote.
CREATE TABLE movimientos (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    lote_id    BIGINT       NOT NULL,
    tipo       VARCHAR(20)  NOT NULL,
    cantidad   INT          NOT NULL,
    referencia VARCHAR(150),
    usuario    VARCHAR(150),
    fecha      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_movimientos PRIMARY KEY (id),
    CONSTRAINT fk_movimientos_lote FOREIGN KEY (lote_id) REFERENCES lotes (id)
);

CREATE INDEX ix_movimientos_lote ON movimientos (lote_id, fecha);

-- Reserva de stock mientras el cliente paga: se confirma con el pago o se libera al expirar.
CREATE TABLE reservas (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    referencia  VARCHAR(60) NOT NULL,
    cuenta_id   VARCHAR(64),
    sucursal_id BIGINT      NOT NULL,
    estado      VARCHAR(20) NOT NULL,
    creada_en   DATETIME(6) NOT NULL,
    expira_en   DATETIME(6) NOT NULL,
    CONSTRAINT pk_reservas PRIMARY KEY (id),
    CONSTRAINT fk_reservas_sucursal FOREIGN KEY (sucursal_id) REFERENCES sucursales (id)
);

CREATE INDEX ix_reservas_estado ON reservas (estado, expira_en);

CREATE TABLE reserva_items (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    reserva_id     BIGINT NOT NULL,
    lote_id        BIGINT NOT NULL,
    medicamento_id BIGINT NOT NULL,
    cantidad       INT    NOT NULL,
    CONSTRAINT pk_reserva_items PRIMARY KEY (id),
    CONSTRAINT fk_reserva_items_reserva FOREIGN KEY (reserva_id) REFERENCES reservas (id) ON DELETE CASCADE,
    CONSTRAINT fk_reserva_items_lote FOREIGN KEY (lote_id) REFERENCES lotes (id)
);
