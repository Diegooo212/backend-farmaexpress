CREATE TABLE medicamentos (
    id     BIGINT       NOT NULL AUTO_INCREMENT,
    sku    VARCHAR(40)  NOT NULL,
    nombre VARCHAR(150) NOT NULL,
    precio INT          NOT NULL,
    stock  INT          NOT NULL,
    CONSTRAINT pk_medicamentos PRIMARY KEY (id),
    CONSTRAINT uk_medicamentos_sku UNIQUE (sku),
    CONSTRAINT ck_medicamentos_precio CHECK (precio >= 0),
    CONSTRAINT ck_medicamentos_stock CHECK (stock >= 0)
);
