CREATE TABLE usuarios (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    nombre        VARCHAR(150) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    -- Hash BCrypt; la contraseña nunca se guarda en texto plano.
    password_hash VARCHAR(100) NOT NULL,
    rol           VARCHAR(20)  NOT NULL,
    creado_en     DATETIME(6)  NOT NULL,
    CONSTRAINT pk_usuarios PRIMARY KEY (id),
    CONSTRAINT uk_usuarios_email UNIQUE (email)
);

-- Carrito guardado por cuenta: el mismo carrito en cualquier dispositivo.
-- medicamento_id apunta al microservicio de catálogo (otra base), por eso no tiene FK.
CREATE TABLE carrito_items (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    usuario_id     BIGINT NOT NULL,
    medicamento_id BIGINT NOT NULL,
    cantidad       INT    NOT NULL,
    CONSTRAINT pk_carrito_items PRIMARY KEY (id),
    CONSTRAINT uk_carrito_usuario_medicamento UNIQUE (usuario_id, medicamento_id),
    CONSTRAINT fk_carrito_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT ck_carrito_cantidad CHECK (cantidad > 0)
);

CREATE TABLE pedidos (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    usuario_id BIGINT      NOT NULL,
    total      INT         NOT NULL,
    creado_en  DATETIME(6) NOT NULL,
    CONSTRAINT pk_pedidos PRIMARY KEY (id),
    CONSTRAINT fk_pedido_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
);

-- Nombre y precio se copian al comprar: el pedido no cambia si después cambia el catálogo.
CREATE TABLE pedido_items (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    pedido_id       BIGINT       NOT NULL,
    medicamento_id  BIGINT       NOT NULL,
    nombre          VARCHAR(150) NOT NULL,
    precio_unitario INT          NOT NULL,
    cantidad        INT          NOT NULL,
    CONSTRAINT pk_pedido_items PRIMARY KEY (id),
    CONSTRAINT fk_item_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos (id) ON DELETE CASCADE
);

CREATE INDEX ix_pedidos_usuario ON pedidos (usuario_id);
