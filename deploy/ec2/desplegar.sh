#!/usr/bin/env bash
# Compila la última versión del repo y (re)inicia los servicios de ESTA EC2
# (los que eligió instalar-ec2.sh: "bff" o "catalog prescriptions").
# Uso, desde la carpeta del repo clonado en la EC2:  bash deploy/ec2/desplegar.sh
set -euo pipefail

cd "$(dirname "$0")/../.."
APP_DIR=/opt/farmaexpress
CONF_DIR=/etc/farmaexpress

if [ ! -f "$CONF_DIR/servicios" ]; then
  echo "Primero ejecuta: sudo bash deploy/ec2/instalar-ec2.sh bff|microservicios"
  exit 1
fi
SERVICIOS=$(cat "$CONF_DIR/servicios")
if sudo grep -q CAMBIAR "$CONF_DIR/farmaexpress.env"; then
  echo "Faltan datos en $CONF_DIR/farmaexpress.env (hay valores CAMBIAR). Edítalo con: sudo nano $CONF_DIR/farmaexpress.env"
  exit 1
fi

echo "==> Actualizando el código"
git pull --ff-only

echo "==> Compilando: $SERVICIOS (sin tests; los tests se corren en local con ./mvnw verify)"
chmod +x mvnw
./mvnw -q -B -DskipTests -pl "$(echo "$SERVICIOS" | tr ' ' ',')" package

echo "==> Copiando los .jar a $APP_DIR y reiniciando"
for svc in $SERVICIOS; do
  sudo cp "$svc/target/farmaexpress-$svc-0.0.1-SNAPSHOT.jar" "$APP_DIR/farmaexpress-$svc.jar"
  sudo chown farmaexpress:farmaexpress "$APP_DIR/farmaexpress-$svc.jar"
  sudo systemctl restart "farmaexpress-$svc"
done

echo "==> Esperando que respondan (la primera vez Flyway crea las tablas en MySQL)"
for svc in $SERVICIOS; do
  case "$svc" in bff) puerto=8080 ;; catalog) puerto=8081 ;; prescriptions) puerto=8082 ;; esac
  for _ in $(seq 1 60); do
    if curl -fs "http://localhost:$puerto/actuator/health" >/dev/null; then
      echo "    $svc :$puerto UP"
      continue 2
    fi
    sleep 2
  done
  echo "    $svc :$puerto NO responde. Revisa:  sudo journalctl -u farmaexpress-$svc -n 80 --no-pager"
  exit 1
done
echo
echo "Listo: $SERVICIOS"
