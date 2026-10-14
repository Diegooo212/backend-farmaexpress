-- Fotos de Paracetamol e Ibuprofeno con el mismo estilo que el resto (caja sobre fondo blanco).
-- En una base nueva V5 les había asignado una foto genérica de Wikimedia.
UPDATE medicamentos SET imagen_url = 'https://farmaciasdeldrsimicl.vtexassets.com/arquivos/ids/157731/BE0039-1.jpg?v=638353341832330000'
WHERE sku = 'MED-001' AND (imagen_url IS NULL OR imagen_url = '' OR imagen_url LIKE 'https://upload.wikimedia.org/%');
UPDATE medicamentos SET imagen_url = 'https://farmaciasdeldrsimicl.vtexassets.com/arquivos/ids/159826/BE0022-1V1.jpg?v=638953557759730000'
WHERE sku = 'MED-002' AND (imagen_url IS NULL OR imagen_url = '' OR imagen_url LIKE 'https://upload.wikimedia.org/%');
