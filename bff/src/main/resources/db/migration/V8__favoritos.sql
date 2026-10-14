-- Productos favoritos de cada cuenta.
CREATE TABLE favoritos (
    usuario_id     BIGINT      NOT NULL,
    medicamento_id BIGINT      NOT NULL,
    creado_en      DATETIME(6) NOT NULL,
    CONSTRAINT pk_favoritos PRIMARY KEY (usuario_id, medicamento_id),
    CONSTRAINT fk_favoritos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
);
