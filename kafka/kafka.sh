#!/usr/bin/env bash
# Administración de Kafka por consola (CLI oficial de Kafka). Se ejecuta dentro de un broker:
#
#   docker exec farmaexpress-kafka1 bash /scripts/kafka.sh <comando> [argumentos]
#
# Comandos:
#   crear-topicos                          crea los tópicos de FarmaExpress si no existen (lo usa kafka-init)
#   topicos                                lista los tópicos
#   describir [tópico]                     particiones, líder, réplicas e ISR (todos o uno)
#   config <tópico>                        configuración propia del tópico (retención, cleanup policy...)
#   retencion <tópico> <ms>                cambia retention.ms
#   particiones <tópico> <total>           aumenta las particiones (Kafka no permite reducirlas)
#   grupos                                 consumer groups con su estado
#   lag [grupo]                            offsets, lag y consumidor asignado por partición
#   reiniciar <grupo> <tópico> <earliest|latest>   mueve los offsets del grupo (el grupo debe estar detenido)
#   leer <tópico> [cantidad]               muestra mensajes con su clave, partición y offset
set -euo pipefail

BOOTSTRAP="${BOOTSTRAP:-kafka1:29092,kafka2:29092,kafka3:29092}"

# Tópicos de FarmaExpress: 3 particiones (paralelismo: hasta 3 consumidores por grupo) y 3 réplicas
# (una copia en cada broker). min.insync.replicas=2: con acks=all se tolera la caída de un broker.
PARTICIONES=3
REPLICAS=3

esperar_brokers() {
  local intentos=0
  until [ "$(kafka-broker-api-versions --bootstrap-server "$BOOTSTRAP" 2>/dev/null | grep -c '(id: ')" -ge 3 ]; do
    intentos=$((intentos + 1))
    if [ "$intentos" -ge 60 ]; then
      echo "Los 3 brokers no respondieron a tiempo." >&2
      exit 1
    fi
    echo "Esperando a los 3 brokers de Kafka..."
    sleep 3
  done
}

crear() {
  local topico="$1"
  shift
  kafka-topics --bootstrap-server "$BOOTSTRAP" --create --if-not-exists --topic "$topico" \
    --partitions "$PARTICIONES" --replication-factor "$REPLICAS" "$@"
}

crear_topicos() {
  esperar_brokers

  # Ventas confirmadas: historial de eventos para analítica. Se guarda 7 días (o 1 GiB por partición)
  # para poder reprocesarlo, por ejemplo si se agrega un consumidor nuevo o se corrige uno.
  crear farmaexpress.ventas \
    --config cleanup.policy=delete \
    --config retention.ms=604800000 \
    --config retention.bytes=1073741824 \
    --config min.insync.replicas=2

  # Stock vigente por producto (clave = id del producto). Compactado: Kafka conserva el último valor
  # de cada clave, así un consumidor nuevo reconstruye el stock actual sin leer todo el historial.
  crear farmaexpress.stock \
    --config cleanup.policy=compact \
    --config min.compaction.lag.ms=60000 \
    --config segment.ms=3600000 \
    --config min.cleanable.dirty.ratio=0.1 \
    --config delete.retention.ms=86400000 \
    --config min.insync.replicas=2

  # Dead letter topics: eventos que un consumidor no pudo procesar. Se guardan 30 días para revisarlos.
  for dlt in farmaexpress.ventas.dlt farmaexpress.stock.dlt; do
    crear "$dlt" \
      --config cleanup.policy=delete \
      --config retention.ms=2592000000 \
      --config min.insync.replicas=2
  done

  kafka-topics --bootstrap-server "$BOOTSTRAP" --describe --topics-with-overrides
}

comando="${1:-}"
shift || true

case "$comando" in
  crear-topicos)
    crear_topicos
    ;;
  topicos)
    kafka-topics --bootstrap-server "$BOOTSTRAP" --list
    ;;
  describir)
    if [ $# -ge 1 ]; then
      kafka-topics --bootstrap-server "$BOOTSTRAP" --describe --topic "$1"
    else
      kafka-topics --bootstrap-server "$BOOTSTRAP" --describe --exclude-internal
    fi
    ;;
  config)
    kafka-configs --bootstrap-server "$BOOTSTRAP" --entity-type topics --entity-name "${1:?Indica el tópico}" --describe
    ;;
  retencion)
    kafka-configs --bootstrap-server "$BOOTSTRAP" --entity-type topics --entity-name "${1:?Indica el tópico}" \
      --alter --add-config "retention.ms=${2:?Indica la retención en ms}"
    ;;
  particiones)
    kafka-topics --bootstrap-server "$BOOTSTRAP" --alter --topic "${1:?Indica el tópico}" \
      --partitions "${2:?Indica el total de particiones}"
    ;;
  grupos)
    kafka-consumer-groups --bootstrap-server "$BOOTSTRAP" --list --state
    ;;
  lag)
    if [ $# -ge 1 ]; then
      kafka-consumer-groups --bootstrap-server "$BOOTSTRAP" --describe --group "$1"
    else
      kafka-consumer-groups --bootstrap-server "$BOOTSTRAP" --describe --all-groups
    fi
    ;;
  reiniciar)
    kafka-consumer-groups --bootstrap-server "$BOOTSTRAP" --group "${1:?Indica el grupo}" \
      --topic "${2:?Indica el tópico}" --reset-offsets "--to-${3:?Indica earliest o latest}" --execute
    ;;
  leer)
    kafka-console-consumer --bootstrap-server "$BOOTSTRAP" --topic "${1:?Indica el tópico}" --from-beginning \
      --max-messages "${2:-10}" --timeout-ms 10000 \
      --property print.key=true --property print.partition=true --property print.offset=true || true
    ;;
  *)
    sed -n '2,18p' "$0"
    exit 1
    ;;
esac
