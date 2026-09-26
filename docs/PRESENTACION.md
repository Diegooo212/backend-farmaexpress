# Guion de la presentación EP2 (5 a 10 minutos)

Hilo conductor: **"cómo viaja una petición segura en FarmaExpress"**. Empieza en la identidad (Entra ID), pasa por el API Manager (API Gateway) y termina en los microservicios y la base de datos en la nube.

## Antes de empezar (checklist)

- [ ] Learner Lab **iniciado**: EC2 *running* y RDS *available*.
- [ ] `curl https://<api-id>.execute-api.us-east-1.amazonaws.com/api/bff/catalog/medicamentos` responde 200.
- [ ] Frontend abierto en `https://dxxxx.cloudfront.net`, **sin sesión**.
- [ ] Pestañas abiertas:
  - Entra admin center (tenant, usuarios, App Registration, user flow);
  - API Gateway (rutas, autorizador, CORS);
  - EC2 y RDS;
  - AWS CloudShell con `probar-api.sh`.
- [ ] Un usuario con rol **Operador** asignado y un correo nuevo, sin usar, para mostrar el registro.
- [ ] DevTools del navegador abierto en la pestaña **Network**.

## 1. Arquitectura (30 s)

Muestra el diagrama de `DESPLIEGUE-AWS.md`:

- React + MSAL servido por **CloudFront**.
- **API Gateway** con autorizador JWT.
- **EC2** con 3 microservicios Spring Boot (bff, catalog, prescriptions).
- **RDS MySQL**.
- **Entra External ID** como IDaaS.

## 2. IDaaS: tenant, aplicación y usuarios (indicadores 3 y 4, 20%)

En el Entra admin center:

1. **Tenant externo**: *Overview* → Tenant ID y tipo *External*.
2. **Users → All users**: los usuarios registrados, con *Creation type: Self-service sign-up*.
3. **App registration FarmaExpress**:
   - *Authentication*: plataforma **SPA** con redirect URIs de CloudFront y localhost, sin implicit grant.
   - *Expose an API*: `api://<client-id>` con el scope **`access_as_user`**.
   - *App roles*: **Administrador**, **Operador**, **Cliente**.
   - *Token configuration*: claim opcional `email`.
4. **Enterprise app → Users and groups**: quién tiene cada rol.

Frase clave: *"los roles y el scope que definí aquí son los que después validan el API Gateway y el backend"*.

## 3. Registro e inicio de sesión con OIDC + PKCE (indicadores 5 y 6, 25%)

1. **User flow** `SignUpSignIn-FarmaExpress`: *Email with password* y aplicación asociada.
2. En el frontend: **Iniciar sesión**. En Network, el request a `login.microsoftonline.com/…/oauth2/v2.0/authorize` muestra:
   - `response_type=code` → **Authorization Code**;
   - `code_challenge` + `code_challenge_method=S256` → **PKCE**;
   - `state` y `nonce`;
3. Inicia sesión con una de las cuentas del tenant.
4. Al volver a FarmaExpress, en Network se ve el `POST …/oauth2/v2.0/token` con **`code_verifier`**. MSAL validó el `state` y el `nonce` del ID token.
5. Muestra en **Users** del tenant las cuentas y, en *Enterprise applications → Users and groups*, su rol.
6. En **Network**, abre una llamada a `/api/bff/...` → *Request Headers* → `Authorization: Bearer …` (lo agrega el interceptor). Copia el token y pégalo en **https://jwt.ms**: muestra `iss`, `aud` (tu API), `scp: access_as_user`, `roles`, `exp`.

## 4. API Manager: rutas, CORS y JWT (indicadores 1, 2 y 7, 40%)

En la consola de API Gateway, **farmaexpress-api**:

1. **Routes**: las 15 rutas. Las públicas (GET catálogo) no llevan autorizador. Las demás llevan **JWT** + scope `access_as_user`.
2. **Authorization**: el autorizador `entra-id-jwt`, con:
   - issuer `https://login.microsoftonline.com/<tenant>/v2.0`;
   - audience = client id;
   - identity source `$request.header.Authorization`.
3. **Integrations**: `HTTP_PROXY` hacia `http://<IP elástica>:8080/...`, es decir, el BFF en EC2.
4. **CORS**: origen de CloudFront y localhost, headers `authorization` y `content-type`.
5. En el frontend, **Network**: las llamadas van a `execute-api…amazonaws.com` con `Authorization: Bearer …`, y la respuesta trae `access-control-allow-origin` = CloudFront.

## 5. Evidencia de cada ruta, con y sin token (indicador 8, 15%)

En CloudShell (copia el token desde F12 → Network → header `Authorization`, sin la palabra `Bearer`):

```bash
export API=https://<api-id>.execute-api.us-east-1.amazonaws.com
export TOKEN=<access token>
bash probar-api.sh
```

| # | Llamada | Esperado | Quién responde |
|---|---|---|---|
| 1 | GET catálogo sin token | **200** + JSON | BFF (ruta pública) |
| 2 | GET carrito sin token | **401** `Unauthorized` | API Gateway |
| 3 | Token alterado | **401** | API Gateway (firma inválida) |
| 4 | GET `/auth/me` con token | **200** + perfil | BFF |
| 5 | GET carrito con token | **200** + JSON | BFF |
| 6 | GET recetas con token | **200** + JSON | BFF → prescriptions |
| 7 | POST medicamento con token de **Cliente** | **403** | BFF (falta rol `Administrador`) |
| 8 | PUT estado de receta con token de **Cliente** | **403** | BFF (falta rol `Operador`) |

Explica **por qué hay dos capas**:

- **API Gateway** valida la identidad: firma, `iss`, `aud`, `exp` y scope.
- **BFF y microservicios** validan otra vez y además el **rol** (claim `roles`). Así el backend queda protegido aunque alguien lo llame directo por la IP de la EC2.

## 6. Todo integrado en la nube (cierre, 1 a 2 min)

1. Inicia sesión como **paciente**:
   - agrega productos al carrito y **compra**: baja el stock;
   - envía una **receta con foto**.
2. Cierra sesión e inicia como **Operador**: el guard le abre el **Panel de farmacia**.
   - Abre la receta: ve la foto y los datos del paciente.
   - **Rechaza** con motivo (o valida).
3. Vuelve al paciente: la **campana** muestra el aviso con el motivo.
4. Muestra la base en **RDS** desde la EC2:

   ```bash
   mysql -h <endpoint> -u admin -p -e "SELECT id, paciente_nombre, status FROM farmaexpress_prescriptions.recetas;"
   ```

## Preguntas probables del docente

- **¿Por qué PKCE si es un SPA?** Un SPA no puede guardar un client secret. PKCE amarra el `code` al `code_verifier` que solo tiene el navegador que inició el login, así que un `code` interceptado no sirve.
- **¿Qué valida el API Gateway y qué el backend?** El Gateway valida firma, emisor, audiencia, vigencia y scope. El backend valida lo mismo (defensa en profundidad) y además los roles.
- **¿Dónde se guardan los tokens?** En `sessionStorage`, lo gestiona MSAL. Se borran al cerrar la pestaña. El interceptor los renueva en silencio con `acquireTokenSilent`.
- **¿Por qué un BFF?** Da un solo punto de entrada al frontend, adapta las respuestas (carrito con precios actuales) y coordina varios microservicios (una compra = catálogo + pedido).
- **¿Cómo se crean las tablas en RDS?** Con migraciones **Flyway** versionadas (`db/migration`) al iniciar cada microservicio. Hibernate solo valida el esquema (`ddl-auto=validate`).
