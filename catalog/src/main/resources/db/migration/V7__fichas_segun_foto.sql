-- Las fichas se ajustan al producto que aparece en la foto: laboratorio, dosis, presentación
-- y sello de bioequivalencia. Los precios no cambian.

UPDATE medicamentos SET laboratorio = 'Andrómaco', bioequivalente = TRUE
WHERE sku = 'MED-001';

UPDATE medicamentos SET laboratorio = 'Laboratorio Chile', presentacion = 'Caja 20 comprimidos', bioequivalente = TRUE
WHERE sku = 'MED-002';

UPDATE medicamentos SET laboratorio = 'OPKO', presentacion = 'Caja 21 cápsulas', bioequivalente = TRUE
WHERE sku = 'MED-003';

UPDATE medicamentos SET laboratorio = 'Laboratorio Chile', presentacion = 'Caja 30 comprimidos', bioequivalente = TRUE
WHERE sku = 'MED-004';

UPDATE medicamentos SET laboratorio = 'Ascend', presentacion = 'Caja 30 cápsulas con recubrimiento entérico', bioequivalente = TRUE
WHERE sku = 'MED-005';

UPDATE medicamentos SET laboratorio = 'Ad Vitam', presentacion = 'Tubo 20 tabletas efervescentes sabor limón'
WHERE sku = 'MED-006';

UPDATE medicamentos SET laboratorio = 'Pasteur', presentacion = 'Caja 10 comprimidos', bioequivalente = TRUE
WHERE sku = 'MED-101';

UPDATE medicamentos SET laboratorio = 'GSK', presentacion = 'Frasco jarabe 90 ml', bioequivalente = FALSE,
    descripcion = 'Jarabe para la fiebre y el dolor en niños. La dosis depende del peso: usa el vasito dosificador incluido.'
WHERE sku = 'MED-102';

UPDATE medicamentos SET laboratorio = 'Ascend', presentacion = 'Caja 30 comprimidos recubiertos', bioequivalente = TRUE
WHERE sku = 'MED-103';

UPDATE medicamentos SET laboratorio = 'Prater', presentacion = 'Caja 30 comprimidos recubiertos', bioequivalente = TRUE
WHERE sku = 'MED-104';

UPDATE medicamentos SET laboratorio = 'OPKO', presentacion = 'Caja 8 sobres',
    descripcion = 'Repone líquidos y sales perdidos por diarrea o vómitos. Disolver un sobre en agua hervida fría según las indicaciones del envase.'
WHERE sku = 'MED-105';

UPDATE medicamentos SET nombre = 'Simeticona 60mg', laboratorio = 'Silesia', presentacion = 'Caja 20 cápsulas',
    descripcion = 'Alivia los gases y la sensación de hinchazón abdominal. Tomar después de las comidas.'
WHERE sku = 'MED-106';

UPDATE medicamentos SET nombre = 'Vitamina D3 800 UI', laboratorio = 'Dr. Simi', presentacion = 'Caja 30 comprimidos',
    descripcion = 'Ayuda a la absorción de calcio y a mantener huesos sanos. Un comprimido al día con alimentos.'
WHERE sku = 'MED-107';

UPDATE medicamentos SET nombre = 'Magnesio 200mg', laboratorio = 'Dr. Simi', presentacion = 'Caja 30 cápsulas',
    principio_activo = 'Carbonato de magnesio'
WHERE sku = 'MED-108';

UPDATE medicamentos SET laboratorio = 'Ascend', presentacion = 'Caja 3 comprimidos recubiertos', bioequivalente = TRUE
WHERE sku = 'MED-109';

UPDATE medicamentos SET laboratorio = 'Prater', presentacion = 'Frasco jarabe 100 ml', bioequivalente = TRUE
WHERE sku = 'MED-110';

UPDATE medicamentos SET laboratorio = 'Hospifarma', presentacion = 'Inhalador 200 dosis', bioequivalente = FALSE
WHERE sku = 'MED-111';

UPDATE medicamentos SET nombre = 'Parches adhesivos impermeables', laboratorio = 'Dr. Simi', presentacion = 'Caja 100 unidades',
    descripcion = 'Parches impermeables y libres de látex para pequeñas heridas.'
WHERE sku = 'MED-113';

UPDATE medicamentos SET laboratorio = 'Leblon', presentacion = 'Pomo crema 50 g'
WHERE sku = 'MED-114';

UPDATE medicamentos SET laboratorio = 'DifemPharma', presentacion = 'Frasco 340 ml',
    descripcion = 'Desinfectante de manos sin enjuague. Aplicar sobre las manos secas y frotar hasta que se evapore.'
WHERE sku = 'MED-115';
