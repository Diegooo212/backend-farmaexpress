# Streaming con Apache Kafka · FarmaExpress

Kafka **complementa** a RabbitMQ; no lo reemplaza:

| | RabbitMQ (ya existente) | Kafka (nuevo) |
|---|---|---|
| Para qué | Coordinar el flujo del pedido: pago aprobado → pedido pagado, stock descontado, aviso al cliente | Streaming y analítica: historial de ventas y stock vigente |
| Modelo | Cola: el mensaje se borra cuando el consumidor lo confirma (ACK) | Log: el mensaje **se conserva** (retención) y cada grupo lleva su propio offset |
| Reprocesar | No (el mensaje ya no está) | Sí: se mueve el offset del grupo al inicio |
| Consumidor nuevo | Solo ve los mensajes desde que existe su cola | Lee todo lo que el tópico conserva |

Nada de lo que usa RabbitMQ cambió: el BFF publica en Kafka **después** de marcar el pedido como pagado (que sigue
llegando por RabbitMQ), e inventory publica en Kafka **además** del evento `inventario.stock-cambiado` de RabbitMQ.

## 1. Arquitectura (docker-compose.kafka.yml)

```
                 ZooKeeper (ensamble de 3: quórum = 2)
          zookeeper1:2181   zookeeper2:2181   zookeeper3:2181
                │  metadatos del clúster, elección del controlador
   ┌────────────┼──────────────────┬──────────────────┐
kafka1 :9092              kafka2 :9093              kafka3 :9094        ◄── Kafka UI :8090
(broker 1)                (broker 2)                (broker 3)          ◄── kafka-admin :8087 (API REST)
   └── cada tópico: 3 particiones × 3 réplicas: cada broker es líder de una y guarda copia de las otras dos
```

- **ZooKeeper (3 nodos)**: guarda qué brokers están vivos, la configuración de los tópicos y elige al
  **controlador** (el broker que asigna líderes de partición). Con 3 nodos hay mayoría (2 de 3): si cae un nodo
  ZooKeeper, el ensamble sigue funcionando. Con 2 nodos, la caída de uno lo detendría.
- **Brokers (3)**: guardan las particiones. `KAFKA_DEFAULT_REPLICATION_FACTOR=3`, `KAFKA_MIN_INSYNC_REPLICAS=2`,
  `unclean.leader.election=false` (nunca se elige como líder una réplica desactualizada) y `auto.create.topics=false`.
- **Dos listeners**: `INTERNO` (`kafkaN:29092`) para los contenedores y `EXTERNO` (`localhost:909X`) para el PC.
- **kafka-init**: crea los tópicos con `kafka/kafka.sh crear-topicos` y termina; los microservicios esperan a que
  termine (`service_completed_successfully`).
- **Kafka UI** (`http://localhost:8090`): brokers, tópicos, particiones, mensajes y consumer groups (offsets, lag).

Se usa Confluent Platform **7.7** (Kafka 3.7): la última línea que funciona con ZooKeeper (Kafka 4 solo usa KRaft).

## 2. Tópicos y su configuración

| Tópico | Clave | Particiones · réplicas | Política | Por qué |
|---|---|---|---|---|
| `farmaexpress.ventas` | id del pedido | 3 · 3 | `delete`, 7 días o 1 GiB por partición | Historial de ventas: se conserva para poder **reprocesarlo** (un consumidor nuevo o corregido) |
| `farmaexpress.stock` | id del producto | 3 · 3 | `compact` | Solo importa el **último** stock de cada producto: la compactación descarta los valores viejos |
| `farmaexpress.ventas.dlt` · `farmaexpress.stock.dlt` | la original | 3 · 3 | `delete`, 30 días | Eventos que no se pudieron procesar: más tiempo para revisarlos |

Todos con `min.insync.replicas=2`. Configuración extra de `farmaexpress.stock`: `min.compaction.lag.ms=60000`
(los cambios del último minuto no se compactan), `segment.ms=3600000` (la compactación actúa sobre segmentos
cerrados), `min.cleanable.dirty.ratio=0.1` y `delete.retention.ms=86400000` (las lápidas se guardan 1 día).

**¿Por qué 3 particiones?** Una partición es la unidad de paralelismo: un grupo puede tener como máximo un
consumidor activo por partición. Con 3 particiones, `analytics` procesa 3 ventas a la vez (3 hilos) y el
volumen se reparte entre los 3 brokers (cada uno es líder de una partición).

**¿Por qué 3 réplicas?** Cada partición tiene una copia en cada broker. Con `acks=all` y
`min.insync.replicas=2`, una escritura se confirma cuando 2 réplicas la tienen: si cae un broker, otra réplica
pasa a ser líder **sin perder mensajes** y se sigue escribiendo. Solo si caen 2 brokers se rechazan las
escrituras (se prefiere no aceptar a perder datos).

**¿Por qué esa clave?** Kafka garantiza el orden **dentro de una partición**, y una misma clave cae siempre en la
misma partición. Todos los cambios de stock de un producto quedan en orden; un evento de venta repetido llega
al mismo consumidor, que lo descarta.

Los tópicos están declarados en dos lugares, con los mismos valores: `kafka/kafka.sh` (infraestructura) y los
beans `NewTopic` de `bff` e `inventory` (`config/KafkaConfig`), que los crean si no existen al arrancar.

## 3. Flujo de eventos

```
Cliente paga ─► payments ──RabbitMQ pago.aprobado──► bff: pedido PAGADO (commit)
                                                       │ @TransactionalEventListener(AFTER_COMMIT)
                                                       ▼
                              Kafka farmaexpress.ventas (clave = pedido)
                                  │                                   │
                  grupo analytics-ventas (3 hilos)          grupo catalog-ventas (3 hilos)
                  analytics: ventas de hoy, ingresos,       catalog: suma "vendidos" de cada producto
                  más vendidos, por sucursal y por hora     (orden "Más vendidos" en la tienda)

inventory: cambia el stock (commit) ─► Kafka farmaexpress.stock (clave = producto, compactado)
                                                       │
                                        grupo analytics-stock ─► stock vigente y alertas de stock bajo
```

**Consumer groups**: cada grupo recibe **todos** los mensajes del tópico y avanza con su propio offset
(`analytics-ventas` y `catalog-ventas` leen las mismas ventas sin estorbarse). **Dentro** de un grupo, las
particiones se reparten entre sus consumidores (balanceo de carga); si un consumidor muere, Kafka reasigna sus
particiones a los demás (asignación `CooperativeStickyAssignor`: solo se mueven las particiones necesarias).

## 4. Diseño de los eventos

`VentaRegistrada` (tópico `farmaexpress.ventas`):

| Campo | Para qué |
|---|---|
| `eventoId` | Trazabilidad (identifica el evento en logs y en el DLT) |
| `version` | Versión del esquema: permite agregar campos sin romper a los consumidores (ignoran lo que no conocen) |
| `pedidoId` | Clave del mensaje e **idempotencia**: el consumidor no cuenta dos veces el mismo pedido |
| `fecha` | Momento del pago: ventas por hora, ventas de hoy y demora del streaming |
| `entrega`, `sucursalId`, `sucursalNombre` | Ventas por sucursal o por despacho |
| `metodoPago` | Ventas por medio de pago |
| `costoDespacho`, `total` | Ingresos; además se valida que `total = Σ(cantidad × precio) + costoDespacho` |
| `items[]`: `medicamentoId`, `nombre`, `cantidad`, `precioUnitario` | Más vendidos (unidades e ingresos por producto) |

**No** lleva datos personales (nombre, correo, dirección del cliente): la analítica no los necesita.

`StockActualizado` (tópico `farmaexpress.stock`): `eventoId`, `version`, `medicamentoId` (clave),
`stockPorPedido`, `stockTotal`, `fecha`. Uno por producto (no uno por movimiento) para que la compactación
conserve exactamente el último stock de cada producto. Un mensaje **sin contenido** (lápida) indica que el
producto dejó de existir y la analítica lo borra.

## 5. Configuración de los clientes

**Productores** (`bff`, `inventory`): `acks=all`, `enable.idempotence=true` (los reintentos no duplican ni
desordenan), `delivery.timeout.ms=30000`, `max.block.ms=5000` (sin Kafka el envío falla rápido y el pedido sigue),
`linger.ms=10` + `batch.size=32KB` + `compression.type=lz4` (lotes comprimidos), serializer de clave `String`
y de valor `JacksonJsonSerializer` (DTO a JSON). Se publica **después del commit** y de forma asíncrona.

**Consumidores** (`analytics`, `catalog`): `group-id` por listener, `auto-offset-reset=earliest`,
`enable-auto-commit=false` + `ack-mode=record` (el offset se confirma después de procesar cada mensaje),
`ErrorHandlingDeserializer` → `JacksonJsonDeserializer` con el tipo definido en cada `@KafkaListener`,
`isolation.level=read_committed`, `CooperativeStickyAssignor`, `session.timeout.ms=15000`, `concurrency=3`.

## 6. Manejo de errores (`config/KafkaConfig` de analytics y catalog)

| Error | Ejemplo | Qué pasa |
|---|---|---|
| Mensaje ilegible | JSON mal formado | `ErrorHandlingDeserializer` lo atrapa: va **directo** al DLT con sus bytes originales |
| Datos inválidos | falta `items`, el total no cuadra, la clave no es el pedido | `EventoInvalidoException` (no se reintenta) → DLT |
| Falla transitoria | MySQL no responde un momento | 3 reintentos con espera exponencial (0,5 s, 1 s, 2 s); si sigue fallando → DLT |
| Evento repetido | el mismo pedido llega dos veces | Se ignora (idempotencia por `pedidoId`), queda en el log |

Cada envío al DLT deja un log `ERROR` con tópico, partición, offset, clave y causa, y Kafka agrega headers
(`kafka_dlt-original-topic`, `kafka_dlt-original-offset`, `kafka_dlt-exception-message`...). Un mensaje defectuoso
**nunca detiene** la partición: el consumidor sigue con el siguiente.

## 7. Administración

**Microservicio `kafka-admin`** (`:8087`, solo Admin; desde el frontend: panel → *Kafka*):

```
GET    /api/admin/kafka/cluster                                brokers y controlador
GET    /api/admin/kafka/topics                                 tópicos con particiones, réplicas, mensajes y configuración
GET    /api/admin/kafka/topics/{nombre}                        líder, réplicas, ISR y offsets por partición; configuración
POST   /api/admin/kafka/topics                                 crear (nombre, particiones, réplicas, configuraciones)
PUT    /api/admin/kafka/topics/{nombre}/config                 modificar configuraciones (vacío = valor por defecto)
POST   /api/admin/kafka/topics/{nombre}/partitions             aumentar particiones
DELETE /api/admin/kafka/topics/{nombre}                        eliminar
GET    /api/admin/kafka/consumer-groups                        grupos con estado, consumidores y lag total
GET    /api/admin/kafka/consumer-groups/{grupo}                consumidores, particiones asignadas, offsets y lag
POST   /api/admin/kafka/consumer-groups/{grupo}/reset-offsets  mover offsets a EARLIEST (reprocesar) o LATEST (saltar)
DELETE /api/admin/kafka/consumer-groups/{grupo}                eliminar un grupo sin consumidores
```

Reglas: nombres válidos de Kafka; réplicas ≤ brokers disponibles; `min.insync.replicas` ≤ réplicas; solo 10
configuraciones permitidas, cada una validada; los tópicos `farmaexpress.*` y los internos (`__*`) no se pueden
eliminar ni cambiar sus particiones (cambiaría la partición de cada clave); los offsets solo se mueven con el grupo
detenido. Errores en Problem Details: 400, 404, 409, 502, 503.

**Por consola** (`kafka/kafka.sh`, dentro de un broker):

```bash
docker exec farmaexpress-kafka1 bash /scripts/kafka.sh describir              # líder, réplicas e ISR
docker exec farmaexpress-kafka1 bash /scripts/kafka.sh config farmaexpress.stock
docker exec farmaexpress-kafka1 bash /scripts/kafka.sh grupos                 # consumer groups y estado
docker exec farmaexpress-kafka1 bash /scripts/kafka.sh lag analytics-ventas   # offsets, lag y consumidor por partición
docker exec farmaexpress-kafka1 bash /scripts/kafka.sh leer farmaexpress.ventas 5
docker exec farmaexpress-kafka1 bash /scripts/kafka.sh retencion farmaexpress.ventas 259200000
```

## 8. Monitoreo: offsets y lag

- **Offset final** (log-end): posición del próximo mensaje que se escriba en la partición.
- **Offset consumido** (committed): hasta dónde confirmó el grupo.
- **Lag** = offset final − offset consumido = mensajes pendientes del grupo.

Un lag que **crece** indica que el grupo no da abasto (más consumidores, hasta 3 por las 3 particiones) o que está
detenido. Un lag **concentrado en una partición** indica una clave "caliente" o un consumidor lento. Una partición
con **ISR menor que sus réplicas** indica un broker caído o atrasado (sub-replicada).

## 9. Demostraciones

1. **Clúster**: `docker compose ps` → 3 ZooKeeper, 3 brokers, kafka-init `Exited (0)`, Kafka UI.
2. **Kafka UI** → *Topics*: los 2 tópicos con 3 particiones y 3 réplicas; *Brokers*: los 3; *Consumers*: los 3 grupos.
3. **Tiempo real**: abrir *Analítica en vivo*, hacer una compra y ver la venta aparecer (partición, offset y
   demora en ms); en Kafka UI → *Consumers* → `analytics-ventas`, el offset avanza y el lag vuelve a 0.
4. **Lag**: `docker stop farmaexpress-analytics` → otra compra → el lag de `analytics-ventas` sube a 1, pero
   `catalog-ventas` (otro grupo) la procesa igual → `docker start farmaexpress-analytics` → procesa lo pendiente.
5. **Tolerancia a fallos**: `docker stop farmaexpress-kafka2` → `kafka.sh describir`: las particiones que lideraba
   tienen otro líder y el ISR baja a 2; se puede seguir comprando → `docker start farmaexpress-kafka2`.
6. **Errores**: publicar un mensaje ilegible (Kafka UI → *Produce Message* en `farmaexpress.ventas`) → aparece en
   `farmaexpress.ventas.dlt` y en el log de analytics con nivel ERROR.
7. **Administración**: panel → *Kafka*: crear un tópico, cambiar su retención, aumentar particiones, eliminarlo;
   reprocesar un grupo (detenido) desde el inicio.
