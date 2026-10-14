# Guion de la presentación EP4 (5 a 10 minutos) · FarmaExpress

Sigue el orden de la pauta. Cada bloque dice **qué decir** y **qué mostrar**. Ten abiertas las pestañas
de la checklist antes de empezar para no perder tiempo navegando.

## Antes de empezar (checklist, 30 minutos antes)

- [ ] Learner Lab iniciado y las **4 EC2** en *Running* (db, microservicios, bff, mensajeria).
- [ ] Si la IP pública del BFF cambió: `bash actualizar-host-backend.sh` en CloudShell.
- [ ] `docker compose -f docker-compose.nube.yml ps` en `farmaexpress-mensajeria`: todo *healthy*.
- [ ] Pestañas abiertas:
  1. Frontend: `https://main.d1gism6shpd81i.amplifyapp.com`
  2. AWS → API Gateway → `farmaexpress-api` (Routes y Authorizers)
  3. AWS → EC2 → Instances (las 4)
  4. Entra admin center → tu tenant → Users y App registrations
  5. Consola RabbitMQ nodo 1: `http://<IP_PUBLICA_MENSAJERIA>:15672` (usuario `farmaexpress`)
  6. Terminal SSH en `farmaexpress-mensajeria`
  7. `https://jwt.ms`
- [ ] Usuarios listos: uno **Cliente** y uno **Admin** (en ventanas distintas o perfiles del navegador).

---

## 1. Introducción y arquitectura (1 min)

**Decir:** "FarmaExpress es una farmacia en línea: catálogo, carrito, pago, recetas validadas por un
químico farmacéutico, inventario por lotes con regla FEFO y avisos al cliente. El backend son **7
microservicios Spring Boot**, el frontend es React desplegado en Amplify, la identidad la maneja **Microsoft
Entra ID** (IDaaS), **API Gateway** es el API Manager y **RabbitMQ en clúster** la mensajería asíncrona."

**Mostrar:** diapositiva de arquitectura.

## 2. IDaaS: tenant y usuarios (1 min)

**Mostrar:** Entra admin center → el tenant → **Users** (los usuarios registrados) → **App registrations**
(la app del frontend y la de la API con el scope `access_as_user` y los App Roles Admin y Operador).

**Decir:** "El tenant emite los JWT; los roles viajan en el claim `roles` y el scope en `scp`."

## 3. Login con OAuth 2.0 / OpenID Connect (1 min)

**Mostrar:** en el frontend, *Iniciar sesión* → pantalla de Microsoft → vuelve a la tienda con "Hola, …".
Luego F12 → Network → una llamada a `/api/bff/...` → header `Authorization: Bearer …` → copiar el token
y pegarlo en **jwt.ms**: mostrar `iss`, `aud`, `scp`, `roles`, `exp`.

**Decir:** "Es el flujo Authorization Code con PKCE (MSAL). El frontend nunca ve la contraseña: recibe un
access token firmado que envía en cada llamada."

## 4. API Manager: instancia, configuración y validación del JWT (2 min)

**Mostrar:** API Gateway → `farmaexpress-api`:
- **Routes:** las rutas del BFF; las protegidas tienen el candado del autorizador.
- **Authorizers:** `entra-id-jwt` (issuer del tenant, audience = la API).
- **Integrations:** HTTP proxy hacia la EC2 del BFF.

**Demostrar el rechazo y la aceptación** (CloudShell o terminal):

```bash
API=https://7x1481plk6.execute-api.us-east-1.amazonaws.com
curl -i $API/api/bff/catalog/medicamentos            # 200: ruta pública
curl -i $API/api/bff/orders                          # 401: sin token, lo rechaza API Gateway
curl -i -H "Authorization: Bearer abc.def.ghi" $API/api/bff/orders   # 401: token inválido
curl -i -H "Authorization: Bearer $TOKEN" $API/api/bff/orders        # 200: token válido (el de jwt.ms)
```

**Decir:** "El JWT se valida dos veces: en el API Gateway (firma, emisor, audiencia, vigencia y scope) y
de nuevo en cada microservicio (defensa en profundidad), que además revisa el rol."

## 5. Todo desplegado e integrado en la nube (1 min)

**Mostrar:** EC2 → las 4 instancias. En el frontend: agregar un producto al carrito (vuela al carrito) →
**Ir a pagar** → Webpay → **Pagar** → pantalla de pago aprobado con confeti → la campana muestra
"Tu pago fue aprobado" → *Mis pedidos* con el seguimiento.

**Decir:** "Esa compra recorrió frontend → API Gateway → BFF → inventory (reserva FEFO) → payments, y
el resultado volvió por RabbitMQ."

## 6. RabbitMQ: docker compose en la nube y clúster de 2 nodos (1 min)

**Mostrar** en la terminal SSH de `farmaexpress-mensajeria`:

```bash
docker compose -f docker-compose.nube.yml ps
docker exec farmaexpress-rabbitmq1 rabbitmqctl cluster_status
```

Y en la consola web, pestaña **Overview** → *Nodes*: `rabbit@rabbitmq1` y `rabbit@rabbitmq2`.

**Decir:** "docker compose levanta los dos nodos con el mismo secreto de Erlang; se descubren solos
(`rabbitmq.conf`, peer discovery) y forman un clúster. Los microservicios conocen los dos nodos
(`RABBITMQ_ADDRESSES`): si uno cae, se reconectan al otro."

**(Opcional, impresiona):** `docker stop farmaexpress-rabbitmq2` → hacer una acción en la tienda → sigue
funcionando → `docker start farmaexpress-rabbitmq2`.

## 7. Exchanges direct y topic, colas y DLQ (1 min)

**Mostrar** en la consola: **Exchanges**
- `farmaexpress.eventos` (**topic**): todos los eventos (`pago.aprobado`, `receta.estado-cambiado`…).
- `farmaexpress.eventos.dlx` (**direct**): el exchange de mensajes fallidos.

**Queues:** cada cola con su DLQ, por ejemplo `notifications.recetas` → `notifications.recetas.dlq`
(columna *Features*: `DLX`, `DLK`). Hay 9 colas, cada una con su DLQ.

**Decir:** "Cada consumidor confirma con **ACK manual**: si procesa bien hace `basicAck`; si el error es
recuperable (base de datos caída un momento) hace `basicNack` con reencolado una vez; si no es
recuperable (dato inválido) o vuelve a fallar, `basicNack` sin reencolar: RabbitMQ lo manda por el
exchange direct a la DLQ y queda registrado en el log con nivel ERROR."

**(Opcional):** en `notifications.recetas.dlq` → *Get messages* para mostrar un mensaje fallido.

## 8. Microservicio administrador de RabbitMQ (1 min)

**Mostrar** en el frontend con el usuario Admin → panel → **RabbitMQ**:
1. Crear la cola `tickets.soporte` (con DLQ) → aparece en la tabla y en la consola de RabbitMQ.
2. Crear el exchange `tickets.eventos` tipo **direct** → conectar la cola con routing key `ticket.creado`.
3. Intentar crear una cola con espacios → **400** con el mensaje de validación.
4. Intentar borrar `notifications.recetas` → no se puede (recurso del sistema, candado).
5. Eliminar `tickets.soporte` → desaparece.

**Decir:** "El microservicio `rabbit-admin` expone `POST/DELETE /queues`, `/exchanges` y `/bindings`.
La lógica está en `RabbitAdminService`; el controlador no conoce RabbitMQ. Los DTO se validan con Bean
Validation (nombres, tipos de exchange, routing keys, TTL) y solo un Admin con JWT puede usarlo."

## 9. Cierre (30 s)

"En resumen: login con OIDC en Entra ID, API Gateway validando el JWT, 7 microservicios en AWS
comunicados por REST y por RabbitMQ en clúster, con DLQ, ACK manual y un administrador de colas.
El código tiene 140 pruebas automatizadas."

---

## Preguntas probables del docente

**¿Por qué topic y no direct para los eventos?** Porque un mismo evento lo consumen varios servicios y
cada uno elige qué le interesa por routing key (`pago.*`). El direct se usa para la DLQ, donde cada
mensaje fallido va exactamente a la DLQ de su cola.

**¿Qué pasa si notifications está caído?** Las compras siguen funcionando. Los eventos esperan en su cola
(durable) y se procesan cuando vuelve.

**¿Por qué ACK manual y no automático?** Con ACK automático el mensaje se borra al entregarlo, aunque el
procesamiento falle. Con ACK manual solo se borra cuando realmente se procesó; si no, se decide entre
reintentar o mandarlo a la DLQ.

**¿Dónde están los nombres de colas y exchanges?** En una sola clase `config/RabbitConfig` por
microservicio (constantes + beans `Queue`, `Exchange`, `Binding`). El código de negocio solo usa esas
constantes.

**¿Por qué 2 nodos y no 3?** Es lo que pide la evaluación. En producción se usan 3 nodos con colas
*quorum* (replicadas por mayoría), que con 2 nodos no tolerarían la caída de uno.

**¿Cómo evitan que el administrador borre colas del sistema?** `RabbitAdminService` compara cada nombre
con los prefijos protegidos de `farmaexpress.rabbit.protegidos` y responde 409.
