CREATE TABLE recetas (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    paciente_nombre VARCHAR(150) NOT NULL,
    rut             VARCHAR(12)  NOT NULL,
    email           VARCHAR(150) NOT NULL,
    telefono        VARCHAR(30)  NOT NULL,
    tiempo_entrega  VARCHAR(20)  NOT NULL,
    metodo_despacho VARCHAR(30)  NOT NULL,
    farmacia        VARCHAR(100),
    direccion       VARCHAR(255),
    comentarios     VARCHAR(250),
    status          VARCHAR(20)  NOT NULL,
    -- Cuenta que envió la receta (viene del JWT, no del formulario).
    cuenta_email    VARCHAR(150) NOT NULL,
    cuenta_nombre   VARCHAR(150),
    archivo_nombre  VARCHAR(255),
    archivo_tipo    VARCHAR(100),
    archivo_tamano  BIGINT,
    creada_en       DATETIME(6)  NOT NULL,
    CONSTRAINT pk_recetas PRIMARY KEY (id)
);

CREATE INDEX ix_recetas_cuenta_email ON recetas (cuenta_email);
CREATE INDEX ix_recetas_status ON recetas (status);

-- Cada cambio de estado queda registrado, con el mensaje para el paciente y quién lo hizo.
CREATE TABLE receta_historial (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    receta_id BIGINT       NOT NULL,
    status    VARCHAR(20)  NOT NULL,
    fecha     DATETIME(6)  NOT NULL,
    nota      VARCHAR(300),
    por       VARCHAR(150),
    CONSTRAINT pk_receta_historial PRIMARY KEY (id),
    CONSTRAINT fk_historial_receta FOREIGN KEY (receta_id) REFERENCES recetas (id) ON DELETE CASCADE
);

CREATE INDEX ix_historial_receta ON receta_historial (receta_id);

-- El archivo va en su propia tabla para que listar recetas no cargue las imágenes.
CREATE TABLE receta_archivos (
    receta_id BIGINT   NOT NULL,
    contenido LONGBLOB NOT NULL,
    CONSTRAINT pk_receta_archivos PRIMARY KEY (receta_id),
    CONSTRAINT fk_archivo_receta FOREIGN KEY (receta_id) REFERENCES recetas (id) ON DELETE CASCADE
);
