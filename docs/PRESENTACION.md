# Guion de la presentación EP2 (5 a 10 minutos)

Hilo conductor: **"cómo viaja una petición segura en FarmaExpress"**. Parte en la identidad (Entra ID), pasa por el API Manager (API Gateway) y llega a los microservicios y a la base de datos en AWS.

Diapositivas de apoyo: `FarmaExpress-EP2-Presentacion.pptx`, con 16 diapositivas y notas del orador. El orden de este guion es el mismo de las diapositivas.

## Datos del despliegue

| Qué | Valor |
|---|---|
| Frontend (AWS Amplify, HTTPS) | https://main.d1gism6shpd81i.amplifyapp.com |
| API Gateway (`farmaexpress-api`) | https://7x1481plk6.execute-api.us-east-1.amazonaws.com |
| BFF (EC2 `farmaexpress-bff`, IP elástica) | http://52.44.208.17:8080 |
| Tenant Entra ID `diegotenant` (fuerza de trabajo) | `877c9191-0be9-47e2-9bb9-2f7afa2b8acd` |
| App Registration (SPA + API) | `4be1ea5d-0a74-4d56-8f00-6ce68a8e4b5b` · scope `access_as_user` |

## Antes de empezar (checklist)

- [ ] Learner Lab **iniciado** (Start Lab) y las 3 EC2 **en ejecución**: `farmaexpress-db`, `farmaexpress-microservicios` y `farmaexpress-bff`. Dales 1 a 2 minutos para que arranquen los servicios.
- [ ] `curl https://7x1481plk6.execute-api.us-east-1.amazonaws.com/api/bff/catalog/medicamentos` responde 200.
- [ ] Frontend abierto en Amplify, **sin sesión iniciada**.
- [ ] Pestañas abiertas:
  - portal de Azure: Entra ID (usuarios, App Registration, Aplicaciones empresariales);
  - API Gateway (rutas, integraciones, autorización, CORS);
  - EC2 (instancias y security groups) y Amplify;
  - AWS CloudShell con `backend-farmaexpress/deploy/api-gateway` y un token listo.
- [ ] Una cuenta con rol **Cliente** y otra con **Operador** o **Administrador**.
- [ ] DevTools del navegador en la pestaña **Network**, con **Preserve log** activado.
- [ ] La captura del error `AccessDenied` de CloudFront, por si preguntan.

## 1. Arquitectura (1 min) · diapositivas 2 y 3

- React + MSAL servido por **AWS Amplify** (HTTPS).
- **Microsoft Entra ID** como IDaaS: emite el JWT.
- **API Gateway** (HTTP API) como API Manager: rutas, CORS y autorizador JWT.
- **3 EC2** en la VPC:
  - `farmaexpress-bff`: BFF en el puerto 8080, la única expuesta a Internet;
  - `farmaexpress-microservicios`: catalog :8081 y prescriptions :8082;
  - `farmaexpress-db`: MySQL 8.4.
- Entre instancias se usa la IP privada. Los security groups solo dejan pasar tráfico entre capas: Internet → BFF → microservicios → MySQL.

Recorre los 10 pasos de la diapositiva 3. Los dos puntos de control son el **paso 6** (el Gateway valida la identidad) y el **paso 8** (el BFF valida el rol).

## 2. IDaaS: tenant, aplicación y usuarios (indicadores 3 y 4, 20%) · diapositivas 6 y 7

En el portal de Azure:

1. **Microsoft Entra ID → Información general:** el Tenant ID. El tenant es de **fuerza de trabajo**.
2. **Usuarios:** las cuentas de prueba del tenant.
3. **Registros de aplicaciones → FarmaExpress:**
   - *Autenticación*: plataforma **SPA** con las Redirect URIs de Amplify y `http://localhost:5173`, sin tokens implícitos;
   - *Exponer una API*: `api://<client-id>` con el scope **`access_as_user`**;
   - *Roles de aplicación*: **Administrador** (valor `Admin`), **Operador** y **Cliente**.
4. **Aplicaciones empresariales → FarmaExpress → Usuarios y grupos:** qué rol tiene cada usuario.

Frase clave: *"el scope y los roles que definí aquí son los que después validan el API Gateway y el backend"*.

## 3. Flujo de usuario e inicio de sesión con OIDC + PKCE (indicadores 5 y 6, 25%) · diapositivas 8 y 9

1. En el frontend presiona **Iniciar sesión**. En Network, la petición a `login.microsoftonline.com/…/oauth2/v2.0/authorize` muestra:
   - `response_type=code`: es **Authorization Code**;
   - `code_challenge` y `code_challenge_method=S256`: es **PKCE**;
   - `state` y `nonce`.
2. Inicia sesión con una cuenta del tenant.
3. Al volver a FarmaExpress, en Network aparece `POST …/oauth2/v2.0/token` con el **`code_verifier`**. MSAL validó el `state` y el `nonce`.
4. En Network, abre una llamada a `execute-api…/api/bff/...` → *Request Headers* → `Authorization: Bearer …`. Copia el token y pégalo en **https://jwt.ms**. Muestra:
   - `iss`: `https://sts.windows.net/<tenant>/`;
   - `aud`: `api://<client-id>`;
   - `scp`: `access_as_user`;
   - `roles`, `name` y `exp`.

**Registro de cuentas:** el frontend tiene el botón "Crear cuenta" preparado (usa `prompt=create` y se activa con `VITE_AZURE_SIGNUP=true`). El autoregistro con el user flow *Sign up and sign in* es propio de un tenant **Entra External ID**. En el tenant de fuerza de trabajo las cuentas se crean en el portal. Explícalo con esas palabras si el docente lo pregunta.

## 4. API Manager: rutas, CORS y JWT (indicadores 1, 2 y 7, 40%) · diapositivas 10 a 12

En la consola de API Gateway, **farmaexpress-api**:

1. **Rutas:** son 15, cada una con su método exacto (sin `/{proxy+}` y sin PATCH, porque el backend actualiza con PUT). Los dos GET del catálogo son públicos; las demás llevan el autorizador **JWT** y el scope `access_as_user`.
2. **Autorización → `entra-id-jwt`:**
   - origen de identidad: `$request.header.Authorization`;
   - issuer: `https://sts.windows.net/877c9191-0be9-47e2-9bb9-2f7afa2b8acd/`;
   - audience: `<client-id>` y `api://<client-id>`.
3. **Integraciones:** `HTTP_PROXY` hacia `http://52.44.208.17:8080/<misma ruta>`, es decir, el BFF.
4. **CORS:** orígenes Amplify y `http://localhost:5173` (nunca `*`); métodos GET, POST, PUT, DELETE y OPTIONS; headers `authorization` y `content-type`.
5. En el frontend, **Network**: las llamadas van a `execute-api…amazonaws.com` con `Authorization: Bearer …`, y la respuesta trae `access-control-allow-origin` con el dominio de Amplify.

Hay **dos capas de validación**:

- **API Gateway** valida la identidad: firma, `iss`, `aud`, `exp` y scope. Si falla, responde **401**.
- **BFF y microservicios** validan lo mismo y además el **rol** (claim `roles`). Si falta el rol, responden **403**. Así el backend sigue protegido aunque alguien llame directo a la IP de la EC2.
- El BFF también filtra el `Origin`: un dominio que no está en `CORS_ORIGINS` recibe `403 Invalid CORS request`.

## 5. Evidencia de cada ruta, con y sin token (indicador 8, 15%) · diapositiva 13

En CloudShell, copia el token de una cuenta **Cliente** desde F12 → Network → header `Authorization`, sin la palabra `Bearer`:

```bash
cd ~/backend-farmaexpress/deploy/api-gateway
export API=https://7x1481plk6.execute-api.us-east-1.amazonaws.com
export TOKEN=<access token de una cuenta Cliente>
bash probar-api.sh
```

> Usa el token de un **Cliente**. Con un token de Administrador la prueba 7 crearía de verdad el medicamento "Prueba 10mg", y con uno de Operador la prueba 8 cambiaría el estado de la receta 1.

| # | Llamada | Esperado | Quién responde |
|---|---|---|---|
| 1 | GET catálogo, sin token | **200** + JSON | BFF → catalog (ruta pública) |
| 2 | GET carrito, sin token | **401** | API Gateway |
| 3 | GET carrito, token alterado | **401** | API Gateway (firma inválida) |
| 4 | GET `/auth/me`, Cliente | **200** + perfil | BFF |
| 5 | GET carrito, Cliente | **200** + JSON | BFF |
| 6 | GET recetas, Cliente | **200** + JSON | BFF → prescriptions |
| 7 | POST medicamento, Cliente | **403** | BFF (falta el rol Admin) |
| 8 | PUT estado de receta, Cliente | **403** | BFF (falta el rol Operador) |

## 6. Todo integrado en la nube (cierre, 2 min) · diapositiva 14

1. Inicia sesión como **Cliente**:
   - agrega productos al carrito y **compra**: el stock baja;
   - envía una **receta con foto**.
2. Cierra sesión e inicia como **Operador**. Se abre el **Panel de farmacia**:
   - abre la receta: se ven la foto y los datos del paciente;
   - **rechaza** la receta con un motivo, o valídala.
3. Vuelve a la cuenta del Cliente: la **campana** muestra el aviso con el motivo.
4. Con la cuenta de **Administrador**, edita o elimina un producto del catálogo.
5. En la consola de AWS muestra:
   - EC2: las 3 instancias en ejecución y sus security groups;
   - Amplify: la app `farmaexpress-frontend` y su dominio.
6. Opcional: los datos en MySQL. Conéctate a `farmaexpress-db` con EC2 Instance Connect:

   ```bash
   mysql -u root -p -e "SELECT id, paciente_nombre, status FROM farmaexpress_prescriptions.recetas;"
   ```

**¿Por qué Amplify y no S3 + CloudFront?** AWS Academy bloquea `cloudfront:CreateDistribution` (`AccessDenied` tanto en la consola como en la CLI). Amplify Hosting también sirve el frontend por HTTPS, que es lo que exige Entra ID para la Redirect URI.

## Preguntas probables del docente

- **¿Por qué PKCE si es un SPA?** Un SPA no puede guardar un client secret. PKCE amarra el `code` al `code_verifier` que solo tiene el navegador que inició el login, así que un `code` interceptado no sirve.
- **¿Qué valida el API Gateway y qué el backend?** El Gateway valida firma, emisor, audiencia, vigencia y scope. El backend valida lo mismo (defensa en profundidad) y además los roles. El Gateway HTTP API no evalúa roles, solo scopes.
- **¿Dónde se guardan los tokens?** En `sessionStorage`, y lo gestiona MSAL. Se borran al cerrar la pestaña. El interceptor los renueva sin pedir login con `acquireTokenSilent`.
- **¿Por qué un BFF?** Da un solo punto de entrada al frontend, adapta las respuestas (carrito con precios actuales) y coordina varios microservicios (una compra toca catálogo y pedidos).
- **¿Por qué rutas explícitas y no `/{proxy+}`?** Se ve qué es público y qué está protegido, y no se expone nada que el frontend no use.
- **¿Cómo se crean las tablas?** Con migraciones **Flyway** versionadas (`db/migration`) al iniciar cada microservicio. Hibernate solo valida el esquema (`ddl-auto=validate`). Hay una base por servicio en la EC2 de MySQL.
- **¿Por qué el issuer es `sts.windows.net`?** Los access tokens de esta API son **v1**. El autorizador usa exactamente el `iss` que trae el token, y el backend acepta tanto el formato v1 como el v2.
