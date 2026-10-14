-- Categorías para navegar la tienda (como en las farmacias online).
CREATE TABLE categorias (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    slug        VARCHAR(40)  NOT NULL,
    nombre      VARCHAR(60)  NOT NULL,
    descripcion VARCHAR(200) NOT NULL,
    icono       VARCHAR(40)  NOT NULL,
    orden       INT          NOT NULL,
    CONSTRAINT pk_categorias PRIMARY KEY (id),
    CONSTRAINT uk_categorias_slug UNIQUE (slug)
);

INSERT INTO categorias (id, slug, nombre, descripcion, icono, orden) VALUES
    (1, 'dolor-y-fiebre', 'Dolor y fiebre', 'Analgésicos y antipiréticos para dolores comunes y fiebre.', 'thermometer', 1),
    (2, 'alergias', 'Alergias', 'Antihistamínicos para rinitis, picazón y alergias estacionales.', 'flower', 2),
    (3, 'salud-digestiva', 'Salud digestiva', 'Acidez, gases, deshidratación y malestar estomacal.', 'soup', 3),
    (4, 'vitaminas', 'Vitaminas y suplementos', 'Vitaminas, minerales y suplementos para tu día a día.', 'sparkles', 4),
    (5, 'antibioticos', 'Antibióticos', 'Solo con receta médica: los valida nuestro químico farmacéutico.', 'shield-plus', 5),
    (6, 'respiratorio', 'Respiratorio', 'Tos, congestión y tratamientos respiratorios.', 'wind', 6),
    (7, 'primeros-auxilios', 'Primeros auxilios', 'Antisépticos, parches y todo para tu botiquín.', 'bandage', 7),
    (8, 'cuidado-personal', 'Cuidado personal', 'Protección solar, higiene y cuidado de la piel.', 'sun', 8);

-- Ficha completa del producto.
ALTER TABLE medicamentos ADD COLUMN categoria_id BIGINT;
ALTER TABLE medicamentos ADD COLUMN descripcion VARCHAR(1000);
ALTER TABLE medicamentos ADD COLUMN laboratorio VARCHAR(100);
ALTER TABLE medicamentos ADD COLUMN principio_activo VARCHAR(150);
ALTER TABLE medicamentos ADD COLUMN presentacion VARCHAR(100);
ALTER TABLE medicamentos ADD COLUMN requiere_receta BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE medicamentos ADD COLUMN bioequivalente BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE medicamentos ADD COLUMN imagen_url VARCHAR(500);
ALTER TABLE medicamentos ADD CONSTRAINT fk_medicamentos_categoria FOREIGN KEY (categoria_id) REFERENCES categorias (id);

-- Los 6 productos iniciales (se identifican por su SKU).
UPDATE medicamentos SET categoria_id = 1, laboratorio = 'Lab. Andino', principio_activo = 'Paracetamol',
    presentacion = 'Caja 16 comprimidos', bioequivalente = TRUE,
    descripcion = 'Alivia dolores leves a moderados (cabeza, muscular, dental) y baja la fiebre. Adultos: 1 a 2 comprimidos cada 6 a 8 horas, sin superar 8 al día.'
    WHERE sku = 'MED-001';
UPDATE medicamentos SET categoria_id = 1, laboratorio = 'FarmaGen', principio_activo = 'Ibuprofeno',
    presentacion = 'Caja 20 comprimidos recubiertos', bioequivalente = TRUE,
    descripcion = 'Antiinflamatorio para dolor, inflamación y fiebre. Tomar con alimentos. No usar en caso de úlcera gástrica.'
    WHERE sku = 'MED-002';
UPDATE medicamentos SET categoria_id = 5, laboratorio = 'Bioquímica Sur', principio_activo = 'Amoxicilina',
    presentacion = 'Caja 21 cápsulas', requiere_receta = TRUE, bioequivalente = TRUE,
    descripcion = 'Antibiótico de amplio espectro para infecciones bacterianas. Venta bajo receta médica: completa el tratamiento indicado por tu médico.'
    WHERE sku = 'MED-003';
UPDATE medicamentos SET categoria_id = 2, laboratorio = 'Lab. Andino', principio_activo = 'Loratadina',
    presentacion = 'Caja 30 comprimidos', bioequivalente = TRUE,
    descripcion = 'Antihistamínico de 24 horas para rinitis alérgica, estornudos y picazón. Generalmente no produce somnolencia.'
    WHERE sku = 'MED-004';
UPDATE medicamentos SET categoria_id = 3, laboratorio = 'FarmaGen', principio_activo = 'Omeprazol',
    presentacion = 'Caja 28 cápsulas', bioequivalente = TRUE,
    descripcion = 'Reduce la producción de ácido del estómago. Alivia la acidez y el reflujo. Tomar en ayunas.'
    WHERE sku = 'MED-005';
UPDATE medicamentos SET categoria_id = 4, laboratorio = 'Nutrivida', principio_activo = 'Ácido ascórbico',
    presentacion = 'Tubo 10 comprimidos efervescentes',
    descripcion = 'Suplemento de vitamina C que ayuda al sistema inmune. Disolver un comprimido en un vaso de agua al día.'
    WHERE sku = 'MED-006';

-- Más productos para que cada categoría tenga contenido. Ids fijos (101+) para que el inventario
-- los encuentre igual en cualquier base, aunque ya existan productos creados a mano.
INSERT INTO medicamentos (id, sku, nombre, precio, stock, stock_total, categoria_id, laboratorio, principio_activo, presentacion, requiere_receta, bioequivalente, descripcion) VALUES
    (101, 'MED-101', 'Ketorolaco 10mg', 3490, 0, 0, 1, 'Bioquímica Sur', 'Ketorolaco', 'Caja 10 comprimidos', TRUE, TRUE,
     'Analgésico potente para dolor agudo de corta duración. Venta bajo receta médica; no usar más de 5 días.'),
    (102, 'MED-102', 'Paracetamol infantil 160mg/5ml', 2890, 0, 0, 1, 'Lab. Andino', 'Paracetamol', 'Frasco jarabe 100 ml', FALSE, TRUE,
     'Jarabe sabor frutilla para fiebre y dolor en niños. La dosis depende del peso: usa la jeringa dosificadora incluida.'),
    (103, 'MED-103', 'Cetirizina 10mg', 2990, 0, 0, 2, 'FarmaGen', 'Cetirizina', 'Caja 20 comprimidos', FALSE, TRUE,
     'Antihistamínico para alergias de la piel y rinitis. Puede causar somnolencia leve.'),
    (104, 'MED-104', 'Desloratadina 5mg', 4590, 0, 0, 2, 'Lab. Andino', 'Desloratadina', 'Caja 30 comprimidos', FALSE, TRUE,
     'Antihistamínico de nueva generación para rinitis alérgica y urticaria, con efecto de 24 horas.'),
    (105, 'MED-105', 'Sales de rehidratación oral', 1590, 0, 0, 3, 'Nutrivida', 'Glucosa y electrolitos', 'Caja 4 sobres', FALSE, FALSE,
     'Repone líquidos y sales perdidos por diarrea o vómitos. Disolver un sobre en un litro de agua hervida fría.'),
    (106, 'MED-106', 'Simeticona 80mg', 2390, 0, 0, 3, 'FarmaGen', 'Simeticona', 'Caja 20 comprimidos masticables', FALSE, FALSE,
     'Alivia los gases y la sensación de hinchazón abdominal. Masticar después de las comidas.'),
    (107, 'MED-107', 'Vitamina D3 1000 UI', 6990, 0, 0, 4, 'Nutrivida', 'Colecalciferol', 'Frasco 60 cápsulas blandas', FALSE, FALSE,
     'Ayuda a la absorción de calcio y a mantener huesos sanos. Una cápsula al día con alimentos.'),
    (108, 'MED-108', 'Magnesio 300mg', 5990, 0, 0, 4, 'Nutrivida', 'Citrato de magnesio', 'Frasco 60 comprimidos', FALSE, FALSE,
     'Suplemento que contribuye al funcionamiento normal de músculos y sistema nervioso.'),
    (109, 'MED-109', 'Azitromicina 500mg', 7990, 0, 0, 5, 'Bioquímica Sur', 'Azitromicina', 'Caja 3 comprimidos', TRUE, TRUE,
     'Antibiótico para infecciones respiratorias y de la piel. Venta bajo receta médica.'),
    (110, 'MED-110', 'Ambroxol jarabe 30mg/5ml', 3790, 0, 0, 6, 'Lab. Andino', 'Ambroxol', 'Frasco 120 ml', FALSE, TRUE,
     'Mucolítico que ayuda a eliminar las flemas en la tos con mucosidad.'),
    (111, 'MED-111', 'Salbutamol inhalador 100mcg', 6490, 0, 0, 6, 'FarmaGen', 'Salbutamol', 'Inhalador 200 dosis', TRUE, TRUE,
     'Broncodilatador de acción rápida para crisis de asma o broncoespasmo. Venta bajo receta médica.'),
    (112, 'MED-112', 'Povidona yodada 10%', 2790, 0, 0, 7, 'Bioquímica Sur', 'Povidona yodada', 'Frasco solución 60 ml', FALSE, FALSE,
     'Antiséptico para limpiar heridas, cortes y raspones. Uso externo.'),
    (113, 'MED-113', 'Parches adhesivos surtidos', 1990, 0, 0, 7, 'Curamed', NULL, 'Caja 20 unidades', FALSE, FALSE,
     'Parches transpirables en distintos tamaños para pequeñas heridas.'),
    (114, 'MED-114', 'Protector solar FPS 50', 9990, 0, 0, 8, 'Dermacare', NULL, 'Pomo crema 120 ml', FALSE, FALSE,
     'Protección muy alta contra rayos UVA y UVB, resistente al agua. Reaplicar cada 2 horas.'),
    (115, 'MED-115', 'Alcohol gel 70%', 3290, 0, 0, 8, 'Curamed', 'Alcohol etílico', 'Frasco 500 ml', FALSE, FALSE,
     'Desinfectante de manos sin enjuague con aloe vera.');
