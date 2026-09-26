#!/usr/bin/env bash
# Prepara una EC2 (Amazon Linux 2023) para correr microservicios de FarmaExpress.
# Se ejecuta UNA vez, conectado a la EC2, desde la carpeta del repo clonado:
#   sudo bash deploy/ec2/instalar-ec2.sh bff              # EC2 solo con el BFF (:8080)
#   sudo bash deploy/ec2/instalar-ec2.sh microservicios   # EC2 con catalog (:8081) y prescriptions (:8082)
set -euo pipefail

ROL=${1:-}
case "$ROL" in
  bff) SERVICIOS="bff" ;;
  microservicios) SERVICIOS="catalog prescriptions" ;;
  *) echo "Uso: sudo bash deploy/ec2/instalar-ec2.sh bff|microservicios"; exit 1 ;;
esac

APP_DIR=/opt/farmaexpress
CONF_DIR=/etc/farmaexpress
APP_USER=farmaexpress

echo "==> Instalando Java 21 (Amazon Corretto), Git y cliente MySQL"
dnf install -y java-21-amazon-corretto-devel git mariadb105 >/dev/null

echo "==> Memoria de intercambio (swap) de 2 GB"
if ! swapon --show | grep -q /swapfile; then
  dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "==> Usuario y carpetas"
id -u "$APP_USER" >/dev/null 2>&1 || useradd --system --home "$APP_DIR" --shell /sbin/nologin "$APP_USER"
mkdir -p "$APP_DIR" "$CONF_DIR"
chown "$APP_USER:$APP_USER" "$APP_DIR"
# desplegar.sh lee este archivo para saber qué compilar y reiniciar en esta EC2.
echo "$SERVICIOS" > "$CONF_DIR/servicios"

if [ ! -f "$CONF_DIR/farmaexpress.env" ]; then
  {
    cat <<'ENV'
# Configuración de FarmaExpress en esta EC2. Después de editar:
#   bash deploy/ec2/desplegar.sh      (o: sudo systemctl restart farmaexpress-*)

# MySQL: IP PRIVADA de la EC2 de base de datos y el usuario creado por instalar-mysql.sh
DB_HOST=CAMBIAR_IP_PRIVADA_BASE_DE_DATOS
DB_PORT=3306
DB_USER=farmaexpress
DB_PASSWORD=CAMBIAR

# Microsoft Entra ID (mismos valores que el frontend)
AZURE_TENANT_ID=CAMBIAR
AZURE_API_CLIENT_ID=CAMBIAR
ENV
    if [ "$ROL" = "bff" ]; then
      cat <<'ENV'

# IP PRIVADA de la EC2 de microservicios (catalog y prescriptions)
CATALOG_URL=http://CAMBIAR_IP_PRIVADA_MICROSERVICIOS:8081
PRESCRIPTIONS_URL=http://CAMBIAR_IP_PRIVADA_MICROSERVICIOS:8082

# Orígenes del frontend que pueden llamar al BFF (frontend local de Vite)
CORS_ORIGINS=http://localhost:5173
ENV
    fi
  } > "$CONF_DIR/farmaexpress.env"
  chmod 600 "$CONF_DIR/farmaexpress.env"
  echo "    Creado $CONF_DIR/farmaexpress.env  <-- EDÍTALO con tus datos"
fi

echo "==> Servicios systemd: $SERVICIOS"
for nombre in $SERVICIOS; do
  case "$nombre" in
    bff) puerto=8080 ;;
    catalog) puerto=8081 ;;
    prescriptions) puerto=8082 ;;
  esac
  cat > "/etc/systemd/system/farmaexpress-$nombre.service" <<UNIT
[Unit]
Description=FarmaExpress $nombre (puerto $puerto)
After=network-online.target
Wants=network-online.target

[Service]
User=$APP_USER
WorkingDirectory=$APP_DIR
EnvironmentFile=$CONF_DIR/farmaexpress.env
ExecStart=/usr/bin/java -Xms128m -Xmx384m -jar $APP_DIR/farmaexpress-$nombre.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
UNIT
done
systemctl daemon-reload
for nombre in $SERVICIOS; do systemctl enable "farmaexpress-$nombre" >/dev/null; done

echo
echo "Listo (rol: $ROL). IP privada de esta EC2: $(hostname -I | awk '{print $1}')"
echo "Siguientes pasos:"
echo "  1. sudo nano $CONF_DIR/farmaexpress.env     (completa los CAMBIAR)"
echo "  2. bash deploy/ec2/desplegar.sh             (compila y levanta: $SERVICIOS)"
