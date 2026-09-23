-- Cuentas que entran con Microsoft (Entra ID): no tienen contraseña en FarmaExpress.
ALTER TABLE usuarios ADD COLUMN proveedor VARCHAR(20) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE usuarios MODIFY COLUMN password_hash VARCHAR(100) NULL;
