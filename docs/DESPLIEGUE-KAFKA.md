# Despliegue de Kafka en AWS (EP5 y EP6)

Parte de lo que ya funciona en la nube (4 EC2 + API Gateway + Amplify, ver DESPLIEGUE-EP4.md) y agrega una
**5ª EC2, `farmaexpress-kafka`**, que corre con **docker compose** la plataforma Kafka y sus dos microservicios:

```
Frontend (Amplify) ──JWT──► API Gateway ──► EC2 farmaexpress-bff (bff :8080)
                                                │  publica ventas          │ HTTP
                                                ▼                          ▼
EC2 farmaexpress-kafka (docker compose) ◄───────┴──────────────── kafka-admin :8087 · analytics :8088
   ├─ zookeeper1 · zookeeper2 · zookeeper3        (ensamble de 3: quórum)
   ├─ kafka1 :9092 · kafka2 :9093 · kafka3 :9094  (3 brokers; tópicos de 3 particiones y 3 réplicas)
   ├─ kafka-init                                   (crea los tópicos y termina)
   ├─ Kafka UI :8090
   └─ analytics :8088 · kafka-admin :8087 ──► EC2 farmaexpress-db (MySQL)

EC2 farmaexpress-microservicios (catalog)  ──consume ventas──► brokers  (IP privada de farmaexpress-kafka)
EC2 farmaexpress-mensajeria (inventory)     ──publica stock───► brokers
```

> **Tiempo estimado:** 45 a 60 minutos. Hazlo antes del día de la presentación y deja todo probado.

---

## 0. Antes de empezar

1. Sube a GitHub los cambios del backend y del frontend (commit + push).
2. Inicia el Learner Lab y espera a que las 4 EC2 estén *En ejecución*.
3. Anota los **ID de los grupos de seguridad** de `farmaexpress-bff`, `farmaexpress-microservicios` y
   `farmaexpress-mensajeria` (EC2 → la instancia → pestaña *Seguridad*).

## 1. Grupo de seguridad `kafka-sg`

EC2 → **Grupos de seguridad** → **Crear grupo de seguridad** (VPC predeterminada). Nombre `kafka-sg`
(AWS no permite nombres que empiecen con `sg-`; la descripción sin tildes). **Reglas de entrada:**

| Tipo | Puertos | Origen | Para qué |
|---|---|---|---|
| SSH | 22 | Anywhere-IPv4 (0.0.0.0/0) | EC2 Instance Connect |
| TCP personalizado | 9092-9094 | grupo de seguridad de `farmaexpress-bff` | El BFF publica las ventas |
| TCP personalizado | 9092-9094 | grupo de seguridad de `farmaexpress-microservicios` | catalog consume las ventas |
| TCP personalizado | 9092-9094 | `mensajeria-sg` | inventory publica el stock |
| TCP personalizado | 8087-8088 | grupo de seguridad de `farmaexpress-bff` | El BFF llama a kafka-admin y analytics |
| TCP personalizado | 8090 | Mi IP | Kafka UI (para la demo) |

Reglas de salida: dejar **Todo el tráfico** a 0.0.0.0/0.

Y en el grupo de seguridad de **`farmaexpress-db`** agrega una regla de entrada: **MySQL/Aurora (3306)** desde
`kafka-sg` (analytics guarda las ventas en su propia base `farmaexpress_analytics`).

## 2. Crear la EC2 `farmaexpress-kafka`

EC2 → **Lanzar instancias**:
- **Nombre:** `farmaexpress-kafka`
- **AMI:** Amazon Linux 2023
- **Tipo de instancia:** `t3.large` (8 GB: 3 ZooKeeper + 3 brokers + Kafka UI + 2 microservicios).
  Si el Learner Lab no lo permite, usa `t3.medium` (4 GB; el script agrega 2 GB de swap).
- **Par de claves:** el mismo de las otras · **Grupo de seguridad existente:** `kafka-sg`
- **Almacenamiento:** 20 GiB gp3

Cuando esté *En ejecución*, anota su **IP privada** (la usan las otras EC2) y su **IP pública** (Kafka UI).

## 3. Instalar Docker y levantar Kafka

Conéctate (botón **Conectar** → **EC2 Instance Connect**) y ejecuta:

```bash
git clone https://github.com/Diegooo212/backend-farmaexpress.git
cd backend-farmaexpress
sudo bash deploy/ec2/instalar-docker.sh
exit          # vuelve a conectarte para usar docker sin sudo
```

```bash
cd backend-farmaexpress
cp deploy/ec2/kafka.env.example .env
nano .env     # KAFKA_HOST = IP privada de ESTA EC2 (hostname -I), DB_HOST, DB_PASSWORD y Entra ID
docker compose -f docker-compose.kafka-nube.yml up -d --build     # la primera vez tarda ~10 min (compila)
docker compose -f docker-compose.kafka-nube.yml ps
```

Debe quedar así: 3 `zookeeper` y 3 `kafka` **healthy**, `kafka-init` **Exited (0)**, `kafka-ui` **Up**, y
`analytics` y `kafka-admin` **healthy**. Comprueba los tópicos:

```bash
docker exec farmaexpress-kafka1 bash /scripts/kafka.sh describir
```

Abre Kafka UI en `http://<IP pública de farmaexpress-kafka>:8090`: 3 brokers y los tópicos
`farmaexpress.ventas` y `farmaexpress.stock` con 3 particiones y 3 réplicas.

## 4. Conectar las otras EC2 a Kafka

Usa la **IP privada de `farmaexpress-kafka`** (en los ejemplos, `<IP_KAFKA>`).

**`farmaexpress-bff`** y **`farmaexpress-microservicios`**:

```bash
cd backend-farmaexpress && git pull
sudo nano /etc/farmaexpress/farmaexpress.env
```

Agrega (o cambia) estas líneas:

```properties
KAFKA_HABILITADO=true
KAFKA_BOOTSTRAP_SERVERS=<IP_KAFKA>:9092,<IP_KAFKA>:9093,<IP_KAFKA>:9094
```

Solo en **`farmaexpress-bff`**, agrega además:

```properties
KAFKA_ADMIN_URL=http://<IP_KAFKA>:8087
ANALYTICS_URL=http://<IP_KAFKA>:8088
```

Luego, en cada una: `bash deploy/ec2/desplegar.sh` (compila la versión nueva y reinicia; el catálogo agrega
la columna `vendidos` con Flyway).

**`farmaexpress-mensajeria`** (inventory corre ahí con docker compose):

```bash
cd backend-farmaexpress && git pull
nano .env
```

```properties
KAFKA_HABILITADO=true
KAFKA_BOOTSTRAP_SERVERS=<IP_KAFKA>:9092,<IP_KAFKA>:9093,<IP_KAFKA>:9094
```

```bash
docker compose -f docker-compose.nube.yml up -d --build inventory
```

**Comprobación** (en `farmaexpress-kafka`):

```bash
docker exec farmaexpress-kafka1 bash /scripts/kafka.sh grupos
```

Deben aparecer `analytics-ventas`, `analytics-stock` y **`catalog-ventas`** (este viene de la EC2 de
microservicios) en estado **Stable**. Y `kafka.sh leer farmaexpress.stock 5` muestra el stock que publicó inventory.

## 5. API Gateway: agregar las rutas nuevas

En **AWS CloudShell**:

```bash
rm -rf backend-farmaexpress
git clone https://github.com/Diegooo212/backend-farmaexpress.git && cd backend-farmaexpress/deploy/api-gateway
export API_ID=7x1481plk6
export EC2_HOST=<IP elástica de farmaexpress-bff>
bash actualizar-rutas.sh
```

Agrega las rutas que faltan: `/api/bff/admin/kafka/{proxy+}` (GET, POST, PUT, DELETE) y
`/api/bff/analytics/{proxy+}` (GET), todas con el autorizador JWT.

## 6. Frontend en Amplify

En **AWS CloudShell**, publica la versión nueva (con las páginas *Analítica en vivo* y *Kafka*):

```bash
rm -rf frontend-farmaexpress
git clone https://github.com/Diegooo212/frontend-farmaexpress.git && cd frontend-farmaexpress
export API_URL=https://7x1481plk6.execute-api.us-east-1.amazonaws.com
export AZURE_TENANT_ID=<tenant id>
export AZURE_CLIENT_ID=<client id de FarmaExpress Web>
export AZURE_API_CLIENT_ID=<client id de FarmaExpress API>
export APP_ID=d1gism6shpd81i
bash deploy/publicar-amplify.sh
```

## 7. Prueba final

- [ ] Kafka UI (`http://<IP pública kafka>:8090`): 3 brokers, 2 tópicos con 3 particiones y 3 réplicas, 3 consumer groups.
- [ ] En Amplify, como Admin: panel → **Kafka** muestra los 3 brokers, los tópicos y los consumer groups.
- [ ] Hacer una compra → panel → **Analítica en vivo**: la venta aparece en segundos (partición, offset, demora).
- [ ] Kafka UI → *Consumers* → `analytics-ventas`: el offset avanzó y el lag volvió a 0.
- [ ] Tienda → catálogo → ordenar por **Más vendidos**: el producto comprado sube.

## Si algo falla

- **Un contenedor de Kafka se reinicia o queda unhealthy:** falta memoria. Revisa con `free -h` y
  `docker stats`; usa `t3.large` o confirma que el swap esté activo (`swapon --show`).
- **El BFF o el catálogo no se conectan a Kafka** (`Connection to node ... could not be established`):
  revisa la regla 9092-9094 de `kafka-sg` y que `KAFKA_HOST` en el `.env` de `farmaexpress-kafka` sea su IP
  **privada**. Si cambiaste `KAFKA_HOST`: `docker compose -f docker-compose.kafka-nube.yml up -d`.
- **analytics no arranca** (`Communications link failure`): falta la regla 3306 desde `kafka-sg` en el grupo
  de seguridad de `farmaexpress-db`.
- **Kafka UI no abre:** la regla 8090 usa *Mi IP*; si cambiaste de red (por ejemplo, en DuocUC), actualízala.
- **Al reiniciar el Learner Lab:** las IP privadas no cambian (nada que reconfigurar); la IP pública de
  `farmaexpress-kafka` sí cambia (para Kafka UI). Los contenedores arrancan solos (`restart: unless-stopped`).
