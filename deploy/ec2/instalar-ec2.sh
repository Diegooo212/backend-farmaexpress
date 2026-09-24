#!/usr/bin/env bash
# Prepara una EC2 (Amazon Linux 2023) para correr los 3 microservicios de FarmaExpress.
# Se ejecuta UNA vez, conectado a la EC2:
#   curl -fsSL https://raw.githubusercontent.com/<tu-usuario>/backend-farmaexpress/main/deploy/ec2/instalar-ec2.sh | sudo bash
# o, si ya clonaste el repo:  sudo bash deploy/ec2/instalar-ec2.sh
set -euo pipefail

APP_DIR=/opt/farmaexpress
CONF_DIR=/etc/farmaexpress
APP_USER=farmaexpress

echo "==> Instalando Java 21 (Amazon Corretto), Git y cliente MySQL"
dnf install -y java-21-amazon-corretto-devel git mariadb105 >/dev/null

echo "==> Memoria de intercambio (swap) de 2 GB: 3 JVM en una instancia pequeña"
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

if [ ! -f "$CONF_DIR/farmaexpress.env" ]; then
  cat > "$CONF_DIR/farmaexpress.env" <<'ENV'
# Configuración de los 3 microservicios. Completa los valores y luego:
#   sudo systemctl restart farmaexpress-catalog farmaexpress-prescriptions farmaexpress-bff

# Amazon RDS (MySQL): "Endpoint" de la base en la consola de RDS
DB_HOST=CAMBIAR.xxxxxxxx.us-east-1.rds.amazonaws.com
DB_PORT=3306
DB_USER=admin
DB_PASSWORD=CAMBIAR

# Microsoft Entra External ID (mismos valores que el frontend)
AZURE_TENANT_ID=CAMBIAR
AZURE_API_CLIENT_ID=CAMBIAR

# Orígenes del frontend (solo se usan si el navegador llama directo al BFF; en la nube el CORS lo hace API Gateway)
CORS_ORIGINS=http://localhost:5173
ENV
  chmod 600 "$CONF_DIR/farmaexpress.env"
  echo "    Creado $CONF_DIR/farmaexpress.env  <-- EDÍTALO con tus datos"
fi

echo "==> Servicios systemd"
crear_servicio() {
  local nombre=$1 puerto=$2 despues=$3
  cat > "/etc/systemd/system/farmaexpress-$nombre.service" <<UNIT
[Unit]
Description=FarmaExpress $nombre (puerto $puerto)
After=network-online.target $despues
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
}
crear_servicio catalog 8081 ""
crear_servicio prescriptions 8082 ""
crear_servicio bff 8080 "farmaexpress-catalog.service farmaexpress-prescriptions.service"

systemctl daemon-reload
systemctl enable farmaexpress-catalog farmaexpress-prescriptions farmaexpress-bff >/dev/null

echo
echo "Listo. Siguientes pasos:"
echo "  1. sudo nano $CONF_DIR/farmaexpress.env     (datos de RDS y Entra ID)"
echo "  2. bash deploy/ec2/desplegar.sh             (compila y levanta los 3 servicios)"
