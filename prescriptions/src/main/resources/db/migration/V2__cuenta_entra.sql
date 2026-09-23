-- El dueño de la receta se identifica por el "oid" de Entra ID (estable aunque cambie el correo).
ALTER TABLE recetas ADD COLUMN cuenta_id VARCHAR(64) NULL;
-- Algunos usuarios de Entra no traen correo en el token.
ALTER TABLE recetas MODIFY COLUMN cuenta_email VARCHAR(150) NULL;
CREATE INDEX ix_recetas_cuenta_id ON recetas (cuenta_id);
