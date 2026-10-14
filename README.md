# FarmaExpress: Backend

Backend de **FarmaExpress**, plataforma de dispensación y retiro de recetas médicas.
Curso DSY1107 Desarrollo Cloud Native I (DuocUC).

Seis microservicios **Spring Boot 4 (Java 21)**, cada uno con su propia base **MySQL**, que además se comunican de forma
asíncrona con **RabbitMQ**. Van protegidos por **AWS API Gateway** y usan **Microsoft Entra ID** como IDaaS.
Todo el sistema corre en **Docker** (`docker compose up -d --build`).
En la nube hay 3 instancias EC2 (BFF · catalog + prescriptions · MySQL); `notifications`, `payments`, `inventory` y RabbitMQ por ahora corren solo en local.

```
Frontend (React + MSAL) ──Bearer JWT de Entra ID──► API Gateway (autorizador JWT)
                                                        │
                                                        ▼
                                                 bff :8080 ──┬──► catalog :8081        ──► MySQL farmaexpress_catalog
                                                             ├──► prescriptions :8082  ──► MySQL farmaexpress_prescriptions
                                                             ├──► notifications :8083  ──► MySQL farmaexpress_notifications
                                                             ├──► payments :8084       ──► MySQL farmaexpress_payments ──► Webpay / Mercado Pago / Khipu
                                                             ├──► inventory :8085      ──► MySQL farmaexpress_inventory       (circuit breaker por proveedor)
                                                             └──────────────────────────► MySQL farmaexpress_bff

Mensajería asíncrona (RabbitMQ, exchange topic farmaexpress.eventos; cada cola con reintentos y DLQ):
  prescriptions ──receta.estado-cambiado──────► notifications
  payments ───────pago.aprobado───────────────► bff (pedido PAGADO) · inventory (confirma la salida de los lotes) · notifications
  payments ───────pago.rechazado──────────────► bff (reintentar con otro medio) · notifications
  inventory ──────inventario.stock-cambiado───► catalog (stock que ve la tienda)
  inventory ──────reserva.expirada────────────► bff (pedido EXPIRADO)
```

| Microservicio | Puerto | Responsabilidad |
|---|---|---|
| `bff` | 8080 | Backend for Frontend: perfil, carrito, **checkout** (reserva de stock + inicio del pago) y estado de los pedidos |
| `catalog` | 8081 | Medicamentos y precios. El stock lo informa `inventory` |
| `prescriptions` | 8082 | Recetas, archivo adjunto (foto o PDF en MySQL) e historial de estados. Publica `receta.estado-cambiado` |
| `notifications` | 8083 | Avisos para el cliente (campana del frontend). Consume los eventos de RabbitMQ |
| `payments` | 8084 | Pagos con **Webpay**, **Mercado Pago** y **Khipu**; un **circuit breaker** (Resilience4j) por proveedor |
| `inventory` | 8085 | Stock por **sucursal** y **lote** con fecha de vencimiento, regla **FEFO**, reservas durante el pago y alertas de vencimiento |

### Checkout: cómo se compra

1. El cliente elige **retiro en farmacia** (solo se pueden elegir las que tienen todo su carrito) o **despacho** (sale del almacén central).
2. El BFF pide a `inventory` que **reserve** el stock por 15 minutos con **FEFO**: por cada producto se toman primero las
   unidades del lote que vence antes; los lotes vencidos nunca se venden. Es todo o nada.
3. El BFF crea el pedido `PENDIENTE_PAGO` y `payments` inicia el pago en el proveedor elegido. El navegador va a Webpay
   (formulario POST con `token_ws`), Mercado Pago o Khipu, y vuelve a `/api/bff/payments/retorno/{metodo}`.
4. `payments` **confirma con el proveedor** (nunca confía en los parámetros del navegador) y publica `pago.aprobado` o `pago.rechazado`.
5. Con `pago.aprobado`: el pedido pasa a `PAGADO`, se vacía el carrito, `inventory` descuenta los lotes reservados y el cliente
   recibe el aviso. Con `pago.rechazado` puede pagar con otro medio mientras dure la reserva; si no paga, la reserva expira.

### Circuit breaker de pagos

Si un proveedor falla (no responde o responde 5xx), su circuito cuenta la falla. De las últimas 5 llamadas, con al menos 3,
si falla el 50% el circuito se **abre** por 30 segundos: no se le vuelve a llamar y el checkout lo muestra como "no disponible"
para que el cliente elija otro medio. Luego pasa a **semiabierto**, deja pasar una llamada de prueba y, si funciona, se **cierra**.
Un pago rechazado por el banco no cuenta como falla. Para la demo, el Administrador puede simular la caída de un proveedor
(`POST /api/bff/payments/simulacion`, pantalla "Medios de pago").

| Proveedor | Modo por defecto | Para usar la API real de pruebas |
|---|---|---|
| Webpay Plus (Transbank) | Simulador | `WEBPAY_CODIGO_COMERCIO=...` y `WEBPAY_API_KEY=...` |
| Mercado Pago (Checkout Pro) | Simulador | `MERCADOPAGO_ACCESS_TOKEN=TEST-...` |
| Khipu (API v3) | Simulador | `KHIPU_API_KEY=...` |

## Seguridad: validación del JWT de Entra ID

**Los seis microservicios** son *OAuth2 Resource Servers* y validan el access token de Entra ID en cada petición.
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

## Ejecutar con Docker (todo el sistema)

Requisito: Docker Desktop (con WSL2 en Windows).

```powershell
copy docker.env.example .env      # una vez: completa AZURE_TENANT_ID y AZURE_API_CLIENT_ID
docker compose up -d --build      # MySQL + RabbitMQ + los 6 microservicios
docker compose ps                 # "healthy" = /actuator/health responde UP
```

- Desde el PC solo se llega al BFF (`http://localhost:8080`), igual que en la nube. Consola de RabbitMQ: `http://localhost:15672` (farmaexpress / farmaexpress).
- MySQL de Docker queda en el puerto **3307** del PC (para no chocar con un MySQL local).
- El frontend tiene su propio `docker compose up -d --build` (nginx en `http://localhost:5173`).
- Cada microservicio tiene su `Dockerfile` (build en dos etapas: JDK para compilar; JRE y usuario sin privilegios para ejecutar).

## Ejecutar en local (sin contenedores para los microservicios)

Requisitos: Java 21, MySQL 8 y Docker (solo para RabbitMQ). No hace falta Maven, porque cada proyecto trae `mvnw`.

```powershell
docker compose up -d rabbitmq  # RabbitMQ (consola: http://localhost:15672, usuario/clave farmaexpress)
.\iniciar-backend.cmd          # MySQL local (pide la contraseña de root)
.\iniciar-backend.cmd h2       # sin MySQL (base en memoria)
```

El script toma `AZURE_TENANT_ID` y `AZURE_API_CLIENT_ID` del `.env` del frontend (carpeta vecina) o de variables de entorno.

| Variable | Por defecto | Uso |
|---|---|---|
| `DB_HOST` / `DB_PORT` | `localhost` / `3306` | MySQL |
| `DB_USER` / `DB_PASSWORD` | `root` / `root` | Credenciales de MySQL |
| `AZURE_TENANT_ID` | — (obligatorio) | Tenant de Entra ID |
| `AZURE_API_CLIENT_ID` | — (obligatorio) | App Registration de la API |
| `AZURE_ISSUER`, `AZURE_JWKS_URI` | derivados del tenant | Solo para otro tipo de tenant (p. ej. External ID en `ciamlogin.com`) |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | Broker de mensajes |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `farmaexpress` / `farmaexpress` | Credenciales de RabbitMQ (las de `docker-compose.yml`) |
| `EVENTOS_HABILITADOS` | `true` | `false` = no se publican eventos (servidor sin RabbitMQ) |
| `NOTIFICATIONS_URL`, `PAYMENTS_URL`, `INVENTORY_URL` | `http://localhost:808x` | Dónde el BFF encuentra a cada servicio |
| `PAGOS_URL_RETORNO` | `http://localhost:8080/api/bff/payments/retorno` | A dónde vuelve el navegador desde el proveedor |
| `FRONTEND_URL` | `http://localhost:5173` | Página de resultado del pago y simulador |
| `WEBPAY_CODIGO_COMERCIO` + `WEBPAY_API_KEY`, `MERCADOPAGO_ACCESS_TOKEN`, `KHIPU_API_KEY` | vacío (simulador) | Credenciales de cada proveedor: sin ellas, el pago se confirma en la pantalla propia de FarmaExpress |
| `MINUTOS_RESERVA`, `DIAS_ALERTA_VENCIMIENTO` | `15`, `30` | Duración de la reserva de stock y alerta de lotes por vencer |

Si RabbitMQ no está corriendo, recetas y compras **siguen funcionando** (queda un warning en el log); los pedidos se actualizan
cuando vuelve, porque los eventos esperan en sus colas.

Las bases y tablas las crea **Flyway** al iniciar (`src/main/resources/db/migration`), incluidos los 6 medicamentos iniciales,
las 4 sucursales y sus lotes (algunos por vencer y otros vencidos, para mostrar FEFO y las alertas).

## API del BFF

Base local `http://localhost:8080`. En la nube, la URL del API Gateway.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/bff/catalog/medicamentos` | público | Catálogo con stock |
| GET | `/api/bff/catalog/medicamentos/{id}` | público | Un medicamento |
| POST / PUT | `/api/bff/catalog/medicamentos[/{id}]` | JWT + rol `Administrador` | Crear o editar medicamento |
| GET | `/api/bff/auth/me` | JWT | Perfil (lo crea en el primer ingreso) |
| GET / PUT | `/api/bff/cart` | JWT | Carrito de la cuenta |
| GET / POST | `/api/bff/orders` | JWT | Pedidos / checkout: `{ entrega: RETIRO o DESPACHO, sucursalId, direccion, metodoPago }` |
| GET / DELETE | `/api/bff/orders/{id}` | JWT (dueño) | Detalle / cancelar (libera la reserva) |
| POST | `/api/bff/orders/{id}/pago` | JWT (dueño) | Pagar de nuevo con otro medio mientras dure la reserva |
| GET / POST | `/api/bff/inventory/sucursales`, `/disponibilidad/{id}`, `/cobertura` | público | Farmacias, stock por farmacia y qué farmacias cubren el carrito |
| GET / POST | `/api/bff/inventory/lotes`, `/lotes/{id}/merma`, `/lotes/{id}/movimientos`, `/resumen` | JWT + rol `Operador`/`Administrador` | Gestión de lotes y alertas de vencimiento |
| GET | `/api/bff/payments/metodos` | público | Medios de pago y estado de su circuit breaker |
| GET / POST | `/api/bff/payments/retorno/{metodo}` | público | Regreso del navegador desde el proveedor (303 al frontend) |
| POST | `/api/bff/payments/simulacion` | JWT + rol `Administrador` | Simular la caída de un proveedor (demo del circuit breaker) |
| GET / POST | `/api/bff/prescriptions` | JWT | Recetas (paciente: las suyas; personal: todas) / enviar receta (multipart `datos` + `archivo`) |
| GET | `/api/bff/prescriptions/{id}` y `/{id}/archivo` | JWT (dueño o personal) | Detalle / foto o PDF |
| PUT | `/api/bff/prescriptions/{id}/status` | JWT + rol `Operador`/`Administrador` | Cambiar estado (`nota` obligatoria al rechazar) |
| GET | `/api/bff/notifications` | JWT | Avisos de la cuenta (últimos 30) |
| PUT | `/api/bff/notifications/leidas` | JWT | Marcar todos los avisos como leídos |

## Pruebas

```bash
./mvnw verify
```

Son 111 pruebas y usan H2 y un RabbitTemplate simulado, así que no necesitan MySQL, RabbitMQ ni internet.
Incluyen FEFO y lotes vencidos, reservas que expiran, el circuit breaker abriéndose y recuperándose, y el contrato con la API de Webpay. Incluyen tokens firmados de verdad con una llave de prueba, para verificar firma, `iss`, `aud`, `exp`, scope y roles.

## Despliegue en AWS

- 3 EC2 (bff · microservicios · MySQL) y API Gateway: **[docs/DESPLIEGUE-AWS.md](docs/DESPLIEGUE-AWS.md)**
- Guion de la presentación EP2: **[docs/PRESENTACION.md](docs/PRESENTACION.md)**
