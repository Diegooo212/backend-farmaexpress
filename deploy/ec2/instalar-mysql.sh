#!/usr/bin/env bash
# EC2 de BASE DE DATOS: instala MySQL 8.4 (Amazon Linux 2023) y crea el usuario de la aplicación.
# Se ejecuta UNA vez, conectado a esa EC2:
#   sudo bash instalar-mysql.sh
# Pide dos contraseñas (o tómalas de DB_ROOT_PASSWORD y DB_APP_PASSWORD):
#   - root de MySQL (solo para administrar);
#   - usuario "farmaexpress" (el que usan los microservicios).
# MySQL exige contraseñas fuertes: 8+ caracteres con mayúscula, minúscula, número y símbolo. Evita comillas.
set -euo pipefail

APP_USER=farmaexpress

if [ -z "${DB_ROOT_PASSWORD:-}" ]; then
  read -rsp "Contraseña para root de MySQL: " DB_ROOT_PASSWORD; echo
fi
if [ -z "${DB_APP_PASSWORD:-}" ]; then
  read -rsp "Contraseña para el usuario '$APP_USER' (la usarán los microservicios): " DB_APP_PASSWORD; echo
fi

echo "==> Memoria de intercambio (swap) de 1 GB"
if ! swapon --show | grep -q /swapfile; then
  dd if=/dev/zero of=/swapfile bs=1M count=1024 status=none
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "==> Instalando MySQL 8.4 Community (repositorio oficial de Oracle)"
if ! rpm -q mysql-community-server >/dev/null 2>&1; then
  rpm --import https://repo.mysql.com/RPM-GPG-KEY-mysql-2023
  rpm -q mysql84-community-release >/dev/null 2>&1 \
    || dnf install -y https://repo.mysql.com/mysql84-community-release-el9.rpm >/dev/null
  # Amazon Linux 2023 informa su versión como "2023.x", pero MySQL publica los paquetes
  # como EL9 (compatibles): sin este cambio el repositorio responde 404.
  sed -i 's/\$releasever/9/g' /etc/yum.repos.d/mysql-community*.repo
  dnf install -y mysql-community-server >/dev/null
fi

echo "==> Escuchando en todas las interfaces (el acceso lo limita el security group)"
grep -q '^bind-address' /etc/my.cnf || echo 'bind-address=0.0.0.0' >> /etc/my.cnf
systemctl enable --now mysqld

echo "==> Contraseña de root"
if MYSQL_PWD="$DB_ROOT_PASSWORD" mysql -uroot -e 'SELECT 1' >/dev/null 2>&1; then
  echo "    root ya tenía esa contraseña"
else
  # En la primera instalación MySQL genera una contraseña temporal en el log.
  TEMPORAL=$(grep 'temporary password' /var/log/mysqld.log | tail -1 | awk '{print $NF}')
  MYSQL_PWD="$TEMPORAL" mysql --connect-expired-password -uroot \
    -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '${DB_ROOT_PASSWORD}';"
  echo "    root configurado"
fi

echo "==> Usuario '$APP_USER' con permisos SOLO sobre las bases farmaexpress_*"
MYSQL_PWD="$DB_ROOT_PASSWORD" mysql -uroot <<SQL
CREATE USER IF NOT EXISTS '${APP_USER}'@'%' IDENTIFIED BY '${DB_APP_PASSWORD}';
ALTER USER '${APP_USER}'@'%' IDENTIFIED BY '${DB_APP_PASSWORD}';
GRANT ALL PRIVILEGES ON \`farmaexpress\\_%\`.* TO '${APP_USER}'@'%';
FLUSH PRIVILEGES;
SQL

IP_PRIVADA=$(hostname -I | awk '{print $1}')
echo
echo "MySQL listo."
echo "  IP privada de esta EC2 : $IP_PRIVADA   <-- DB_HOST en las otras EC2"
echo "  Usuario de la app      : $APP_USER"
echo
echo "Las bases (farmaexpress_bff, farmaexpress_catalog, farmaexpress_prescriptions) y sus tablas"
echo "las crean los microservicios con Flyway la primera vez que arrancan."
echo "Para entrar a MySQL:  mysql -u root -p"
