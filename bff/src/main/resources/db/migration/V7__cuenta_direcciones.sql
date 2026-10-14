-- Mi cuenta: teléfono de contacto (para el despacho) y direcciones guardadas.
ALTER TABLE usuarios ADD COLUMN telefono VARCHAR(20);

CREATE TABLE direcciones (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    usuario_id     BIGINT       NOT NULL,
    alias          VARCHAR(40)  NOT NULL,
    calle          VARCHAR(120) NOT NULL,
    depto          VARCHAR(30),
    comuna         VARCHAR(40)  NOT NULL,
    predeterminada BOOLEAN      NOT NULL DEFAULT FALSE,
    creada_en      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_direcciones PRIMARY KEY (id),
    CONSTRAINT fk_direcciones_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE INDEX ix_direcciones_usuario ON direcciones (usuario_id);
