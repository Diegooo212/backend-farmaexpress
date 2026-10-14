# Despliegue EP4: mensajería (RabbitMQ en clúster) y microservicios nuevos en AWS

Parte de lo que ya tienes funcionando (DESPLIEGUE-AWS.md: 3 EC2 + API Gateway + Amplify) y agrega una
**4ª EC2, `farmaexpress-mensajeria`**, que corre con **docker compose**:

```
Frontend (Amplify) ──JWT──► API Gateway (autorizador JWT de Entra ID)
                                │
                                ▼
EC2 farmaexpress-bff ── bff :8080 ──────────────┬──► EC2 farmaexpress-microservicios ── catalog :8081 · prescriptions :8082
        │                                        │
        │  HTTP (IP privada)                     └──► EC2 farmaexpress-db ── MySQL :3306 (una base por microservicio)
        ▼                                                      ▲
EC2 farmaexpress-mensajeria (docker compose) ───────────────────┘
   ├─ rabbitmq1 :5672 / :15672 ┐ clúster RabbitMQ (2 nodos)
   ├─ rabbitmq2 :5673 / :15673 ┘ exchanges farmaexpress.eventos (topic) y farmaexpress.eventos.dlx (direct)
   ├─ notifications :8083 · payments :8084 · inventory :8085 · rabbit-admin :8086
```

Todos los microservicios (también bff, catalog y prescriptions) se conectan a **los dos nodos** con
`RABBITMQ_ADDRESSES`: si uno cae, se reconectan al otro.

> **Tiempo estimado:** 45 a 60 minutos. Hazlo **antes** del día de la evaluación y deja todo probado.

---

## 0. Antes de empezar

1. Sube a GitHub los cambios del backend y del frontend (commit + push).
2. Inicia el Learner Lab y espera a que las 3 EC2 estén *Running*.
3. Anota las **IP privadas** de `farmaexpress-db`, `farmaexpress-microservicios` y `farmaexpress-bff`.

## 1. Security group `sg-mensajeria`

EC2 → Security Groups → Create security group (VPC *default*). Reglas de entrada:

| Puerto | Origen | Para qué |
|---|---|---|
| 22 | My IP | SSH |
| 5672, 5673 | `sg-bff` y `sg-microservicios` | AMQP: los microservicios publican y consumen |
| 8083-8086 | `sg-bff` | El BFF llama a notifications, payments, inventory y rabbit-admin |
| 15672, 15673 | My IP | Consola web de RabbitMQ (para la demo) |

Y en **`sg-db`** agrega: puerto 3306 desde `sg-mensajeria` (los servicios nuevos usan MySQL).

## 2. Crear la EC2 `farmaexpress-mensajeria`

EC2 → Launch instance:
- **AMI:** Amazon Linux 2023 · **Tipo:** `t3.medium` (4 GB: 2 nodos RabbitMQ + 4 microservicios)
- **Key pair:** la misma de las otras · **Security group:** `sg-mensajeria`
- **Almacenamiento:** 20 GB

Anota su **IP privada** (la usan las otras EC2) y su **IP pública** (para abrir la consola de RabbitMQ).

## 3. Instalar Docker y levantar el compose

Conéctate por SSH (o EC2 Instance Connect) y ejecuta:

```bash
git clone https://github.com/Diegooo212/backend-farmaexpress.git
cd backend-farmaexpress
sudo bash deploy/ec2/instalar-docker.sh
exit          # vuelve a entrar para usar docker sin sudo
```

```bash
cd backend-farmaexpress
cp deploy/ec2/nube.env.example .env
nano .env     # completa los CAMBIAR (IP privada de la BD, contraseñas, Entra ID, URL del API Gateway)
docker compose -f docker-compose.nube.yml up -d --build      # la primera vez tarda ~10 min (compila)
docker compose -f docker-compose.nube.yml ps                 # todos "healthy"
docker exec farmaexpress-rabbitmq1 rabbitmqctl cluster_status
```

En `cluster_status` deben aparecer **Running Nodes: rabbit@rabbitmq1, rabbit@rabbitmq2**.

## 4. Conectar las EC2 existentes al clúster

En **`farmaexpress-bff`** y en **`farmaexpress-microservicios`**:

```bash
cd backend-farmaexpress && git pull
sudo nano /etc/farmaexpress/farmaexpress.env
```

Agrega (con la IP privada de `farmaexpress-mensajeria`):

```properties
RABBITMQ_ADDRESSES=<IP_MENSAJERIA>:5672,<IP_MENSAJERIA>:5673
RABBITMQ_USER=farmaexpress
RABBITMQ_PASSWORD=<la misma de .env>
```

Solo en **`farmaexpress-bff`**, agrega además:

```properties
NOTIFICATIONS_URL=http://<IP_MENSAJERIA>:8083
PAYMENTS_URL=http://<IP_MENSAJERIA>:8084
INVENTORY_URL=http://<IP_MENSAJERIA>:8085
RABBIT_ADMIN_URL=http://<IP_MENSAJERIA>:8086
CORS_ORIGINS=http://localhost:5173,https://main.d1gism6shpd81i.amplifyapp.com
```

Luego, en cada una: `bash deploy/ec2/desplegar.sh` (compila la versión nueva y reinicia).

## 5. API Gateway: agregar las rutas nuevas

En **AWS CloudShell** (sube la carpeta `deploy/api-gateway` o clona el repo):

```bash
git clone https://github.com/Diegooo212/backend-farmaexpress.git && cd backend-farmaexpress/deploy/api-gateway
export API_ID=7x1481plk6
export EC2_HOST=<IP elástica de farmaexpress-bff>
bash actualizar-rutas.sh
```

Agrega solo las rutas de `rutas.txt` que falten (categorías, checkout, cuenta, favoritos, pagos,
inventario, gestión de pedidos, avisos y `/api/bff/admin/rabbit/*`), todas con el autorizador JWT salvo
las públicas.

## 6. Frontend en Amplify

Sube el frontend a GitHub y vuelve a publicarlo (igual que la primera vez: `deploy/publicar-amplify.sh`
o desde la consola de Amplify). Verifica que `VITE_API_BASE_URL` sea la URL del API Gateway.

## 7. Prueba final (antes de la evaluación)

- [ ] `https://main.d1gism6shpd81i.amplifyapp.com` abre y muestra el catálogo con fotos.
- [ ] Iniciar sesión → agregar al carrito → pagar con Webpay → **confeti** → el pedido aparece en Mis pedidos.
- [ ] La campana muestra "Tu pago fue aprobado".
- [ ] Consola RabbitMQ (`http://<IP_PUBLICA_MENSAJERIA>:15672`): pestaña *Overview* con **2 nodos**.
- [ ] Panel Admin → **RabbitMQ**: crear y eliminar una cola de prueba.

## Si algo falla

| Síntoma | Causa probable | Solución |
|---|---|---|
| `PRECONDITION_FAILED - inequivalent arg` en los logs | Una cola ya existía con otra configuración | Bórrala (vacía) en la consola de RabbitMQ y reinicia el servicio |
| El nodo 2 no aparece en `cluster_status` | Cookie distinta o arrancó antes que el nodo 1 | `docker compose -f docker-compose.nube.yml restart rabbitmq2` |
| El BFF responde 502 en pagos o inventario | Falta la regla 8083-8086 en `sg-mensajeria` | Revisa el paso 1 |
| Los servicios nuevos no arrancan | No llegan a MySQL | Regla 3306 desde `sg-mensajeria` en `sg-db` y `DB_HOST` en `.env` |
| Cambió la IP del BFF al reiniciar el lab | El API Gateway apunta a la IP vieja | `bash actualizar-host-backend.sh` en CloudShell |
