-- Las cuentas ahora viven en Microsoft Entra External ID. El BFF guarda un perfil por persona,
-- identificado por el "oid" del token (estable aunque cambie el correo).
ALTER TABLE usuarios ADD COLUMN entra_oid VARCHAR(64) NULL;
CREATE UNIQUE INDEX uk_usuarios_entra_oid ON usuarios (entra_oid);
-- Algunos usuarios de Entra no traen correo en el token.
ALTER TABLE usuarios MODIFY COLUMN email VARCHAR(150) NULL;
ALTER TABLE usuarios ADD COLUMN ultimo_ingreso DATETIME(6) NULL;
