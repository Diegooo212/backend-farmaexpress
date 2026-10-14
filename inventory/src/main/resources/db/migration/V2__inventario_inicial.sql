INSERT INTO sucursales (codigo, nombre, direccion, comuna, tipo) VALUES
    ('CENTRAL', 'Almacén Central', 'Av. Américo Vespucio 1500', 'Pudahuel', 'CENTRAL'),
    ('CENTRO', 'Farmacia Centro', 'Av. Libertador Bernardo O''Higgins 1200', 'Santiago', 'FARMACIA'),
    ('NORTE', 'Farmacia Norte', 'Av. Independencia 850', 'Independencia', 'FARMACIA'),
    ('SUR', 'Farmacia Sur', 'Gran Avenida José Miguel Carrera 5400', 'San Miguel', 'FARMACIA');

-- Lotes de los 6 medicamentos iniciales del catálogo. Hay lotes por vencer y vencidos para
-- mostrar la regla FEFO y las alertas. El stock vendible (lotes no vencidos) coincide con el catálogo:
-- Paracetamol 120, Ibuprofeno 8, Amoxicilina 0, Loratadina 45, Omeprazol 15, Vitamina C 60.
INSERT INTO lotes (medicamento_id, sucursal_id, numero_lote, fecha_vencimiento, cantidad_inicial, disponible, reservada, ingresado_en) VALUES
    (1, 1, 'PAR-2408-A', '2026-11-15', 40, 30, 0, '2026-06-01 10:00:00'),
    (1, 1, 'PAR-2502-B', '2027-08-31', 50, 50, 0, '2026-08-01 10:00:00'),
    (1, 2, 'PAR-2408-A', '2026-11-15', 20, 20, 0, '2026-06-01 10:00:00'),
    (1, 3, 'PAR-2502-B', '2027-08-31', 10, 10, 0, '2026-08-01 10:00:00'),
    (1, 4, 'PAR-2503-C', '2027-12-31', 10, 10, 0, '2026-09-01 10:00:00'),
    (1, 4, 'PAR-2312-Z', '2026-08-31', 12, 6, 0, '2026-01-10 10:00:00'),
    (2, 1, 'IBU-2501-A', '2027-03-31', 30, 5, 0, '2026-05-01 10:00:00'),
    (2, 2, 'IBU-2501-A', '2027-03-31', 5, 3, 0, '2026-05-01 10:00:00'),
    (3, 1, 'AMX-2310-A', '2026-09-15', 20, 10, 0, '2025-12-01 10:00:00'),
    (4, 1, 'LOR-2409-A', '2026-10-20', 15, 15, 0, '2026-03-01 10:00:00'),
    (4, 1, 'LOR-2506-B', '2028-01-31', 20, 20, 0, '2026-09-01 10:00:00'),
    (4, 3, 'LOR-2409-A', '2026-10-20', 10, 10, 0, '2026-03-01 10:00:00'),
    (5, 1, 'OME-2502-A', '2027-06-30', 10, 10, 0, '2026-07-01 10:00:00'),
    (5, 2, 'OME-2502-A', '2027-06-30', 5, 5, 0, '2026-07-01 10:00:00'),
    (6, 1, 'VIC-2411-A', '2026-12-31', 25, 25, 0, '2026-04-01 10:00:00'),
    (6, 1, 'VIC-2505-B', '2027-11-30', 25, 25, 0, '2026-09-01 10:00:00'),
    (6, 2, 'VIC-2411-A', '2026-12-31', 5, 5, 0, '2026-04-01 10:00:00'),
    (6, 4, 'VIC-2505-B', '2027-11-30', 5, 5, 0, '2026-09-01 10:00:00');

INSERT INTO movimientos (lote_id, tipo, cantidad, referencia, usuario, fecha)
    SELECT id, 'INGRESO', cantidad_inicial, 'Carga inicial', 'sistema', ingresado_en FROM lotes;

INSERT INTO movimientos (lote_id, tipo, cantidad, referencia, usuario, fecha)
    SELECT id, 'SALIDA', cantidad_inicial - disponible, 'Ventas anteriores a la carga', 'sistema', '2026-09-01 09:00:00'
    FROM lotes WHERE cantidad_inicial > disponible;
