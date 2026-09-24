#!/usr/bin/env bash
# Compila la última versión del repo y (re)inicia los 3 microservicios en la EC2.
# Uso, desde la carpeta del repo clonado en la EC2:  bash deploy/ec2/desplegar.sh
set -euo pipefail

cd "$(dirname "$0")/../.."
APP_DIR=/opt/farmaexpress

echo "==> Actualizando el código"
git pull --ff-only

echo "==> Compilando (sin tests para ir rápido; los tests se corren en local con ./mvnw verify)"
chmod +x mvnw
./mvnw -q -B -DskipTests package

echo "==> Copiando los .jar a $APP_DIR"
for svc in catalog prescriptions bff; do
  sudo cp "$svc/target/farmaexpress-$svc-0.0.1-SNAPSHOT.jar" "$APP_DIR/farmaexpress-$svc.jar"
done
sudo chown farmaexpress:farmaexpress "$APP_DIR"/*.jar

echo "==> Reiniciando servicios"
sudo systemctl restart farmaexpress-catalog farmaexpress-prescriptions
sudo systemctl restart farmaexpress-bff

echo "==> Esperando que respondan (la primera vez Flyway crea las tablas en RDS)"
for puerto in 8081 8082 8080; do
  for _ in $(seq 1 60); do
    if curl -fs "http://localhost:$puerto/actuator/health" >/dev/null; then
      echo "    :$puerto UP"
      continue 2
    fi
    sleep 2
  done
  echo "    :$puerto NO responde. Revisa:  sudo journalctl -u farmaexpress-* -n 80 --no-pager"
  exit 1
done

echo
echo "Backend arriba. Prueba pública (reemplaza por tu IP elástica):"
echo "  curl http://<IP-ELASTICA>:8080/api/bff/catalog/medicamentos"
