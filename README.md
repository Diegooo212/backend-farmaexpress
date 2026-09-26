# FarmaExpress: Backend

Backend de **FarmaExpress**, plataforma de dispensación y retiro de recetas médicas.
Curso DSY1107 Desarrollo Cloud Native I (DuocUC).

Tres microservicios **Spring Boot 4 (Java 21)** con **MySQL** (en la nube: 3 instancias EC2, una para el BFF, una para catalog + prescriptions y una para MySQL). Van protegidos por
**AWS API Gateway** y usan **Microsoft Entra ID** como IDaaS.

```
Frontend (React + MSAL) ──Bearer JWT de Entra ID──► API Gateway (autorizador JWT)
                                                        │
                                                        ▼
                                                 bff :8080 ──┬──► catalog :8081        ──► MySQL farmaexpress_catalog
                                                             ├──► prescriptions :8082  ──► MySQL farmaexpress_prescriptions
                                                             └──────────────────────────► MySQL farmaexpress_bff
```

| Microservicio | Puerto | Responsabilidad |
|---|---|---|
| `bff` | 8080 | Backend for Frontend: perfil del usuario, carrito, compras, y reenvío a catalog y prescriptions |
| `catalog` | 8081 | Medicamentos y stock (descuento de stock "todo o nada") |
| `prescriptions` | 8082 | Recetas, archivo adjunto (foto o PDF en MySQL) e historial de estados |

## Seguridad: validación del JWT de Entra ID

**Los tres microservicios** son *OAuth2 Resource Servers* y validan el access token de Entra ID en cada petición.
Así nadie puede saltarse el API Gateway o el BFF. La configuración está en `config/EntraJwtConfig.java` y `config/SecurityConfig.java`.

| Validación | Cómo |
|---|---|
| **Firma** | RS256 con las llaves públicas del tenant (JWKS `https://login.microsoftonline.com/<tenant>/discovery/v2.0/keys`) |
| **Emisor (`iss`)** | `https://login.microsoftonline.com/<tenant>/v2.0` (tokens v2) o `https://sts.windows.net/<tenant>/` (v1) |
| **Audiencia (`aud`)** | `<client-id>` o `api://<client-id>` |
| **Vigencia** | `exp` y `nbf` |
| **Scope** | `scp` debe incluir `access_as_user` |
| **Rol** | Claim `roles` (App Roles): `Administrador` para editar el catálogo; `Operador` o `Administrador` para cambiar el estado de una receta |

Respuestas de error, en JSON (Problem Details) y con el header `WWW-Authenticate`:

- **401**: sin token, firma inválida, otro emisor, otra audiencia o vencido.
- **403**: token válido pero sin el scope o sin el rol.
- **404**: receta de otra persona (no se revela que existe).

## Ejecutar en local

Requisitos: Java 21 y MySQL 8. No hace falta Maven, porque cada proyecto trae `mvnw`.

```powershell
.\iniciar-backend.cmd          # MySQL local (pide la contraseña de root)
.\iniciar-backend.cmd h2       # sin MySQL (base en memoria)
```

El script toma `AZURE_TENANT_ID` y `AZURE_API_CLIENT_ID` del `.env` del frontend (carpeta vecina) o de variables de entorno.

| Variable | Por defecto | Uso |
|---|---|---|
| `DB_HOST` / `DB_PORT` | `localhost` / `3306` | MySQL o endpoint de RDS |
| `DB_USER` / `DB_PASSWORD` | `root` / `root` | Credenciales de MySQL |
| `AZURE_TENANT_ID` | — (obligatorio) | Tenant de Entra ID |
| `AZURE_API_CLIENT_ID` | — (obligatorio) | App Registration de la API |
| `AZURE_ISSUER`, `AZURE_JWKS_URI` | derivados del tenant | Solo para otro tipo de tenant (p. ej. External ID en `ciamlogin.com`) |

Las bases y tablas las crea **Flyway** al iniciar (`src/main/resources/db/migration`), incluidos los 6 medicamentos iniciales.

## API del BFF

Base local `http://localhost:8080`. En la nube, la URL del API Gateway.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/bff/catalog/medicamentos` | público | Catálogo con stock |
| GET | `/api/bff/catalog/medicamentos/{id}` | público | Un medicamento |
| POST / PUT | `/api/bff/catalog/medicamentos[/{id}]` | JWT + rol `Administrador` | Crear o editar medicamento |
| GET | `/api/bff/auth/me` | JWT | Perfil (lo crea en el primer ingreso) |
| GET / PUT | `/api/bff/cart` | JWT | Carrito de la cuenta |
| GET / POST | `/api/bff/orders` | JWT | Pedidos / comprar el carrito |
| GET / POST | `/api/bff/prescriptions` | JWT | Recetas (paciente: las suyas; personal: todas) / enviar receta (multipart `datos` + `archivo`) |
| GET | `/api/bff/prescriptions/{id}` y `/{id}/archivo` | JWT (dueño o personal) | Detalle / foto o PDF |
| PUT | `/api/bff/prescriptions/{id}/status` | JWT + rol `Operador`/`Administrador` | Cambiar estado (`nota` obligatoria al rechazar) |

## Pruebas

```bash
./mvnw verify
```

Son 57 pruebas y usan H2, así que no necesitan MySQL. Incluyen tokens firmados de verdad con una llave de prueba, para verificar firma, `iss`, `aud`, `exp`, scope y roles.

## Despliegue en AWS

- 3 EC2 (bff · microservicios · MySQL) y API Gateway: **[docs/DESPLIEGUE-AWS.md](docs/DESPLIEGUE-AWS.md)**
- Guion de la presentación EP2: **[docs/PRESENTACION.md](docs/PRESENTACION.md)**
