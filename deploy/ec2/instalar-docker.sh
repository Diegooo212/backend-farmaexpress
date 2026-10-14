#!/usr/bin/env bash
# Prepara la EC2 farmaexpress-mensajeria (Amazon Linux 2023) para correr docker-compose.nube.yml:
# Docker, el plugin "docker compose", Buildx, Git y 2 GB de swap.
# Se ejecuta UNA vez, conectado a la EC2:
#   sudo bash deploy/ec2/instalar-docker.sh
set -euo pipefail

echo "==> Docker y Git"
dnf install -y docker git >/dev/null
systemctl enable --now docker >/dev/null
usermod -aG docker ec2-user

echo "==> Plugins docker compose y buildx"
PLUGINS=/usr/local/lib/docker/cli-plugins
mkdir -p "$PLUGINS"
ARQ=$(uname -m)
curl -fsSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-${ARQ}" -o "$PLUGINS/docker-compose"
BUILDX=$(curl -fsSL https://api.github.com/repos/docker/buildx/releases/latest | grep -oE '"tag_name": *"[^"]+"' | cut -d'"' -f4)
case "$ARQ" in x86_64) BARQ=amd64 ;; aarch64) BARQ=arm64 ;; *) BARQ=$ARQ ;; esac
curl -fsSL "https://github.com/docker/buildx/releases/download/${BUILDX}/buildx-${BUILDX}.linux-${BARQ}" -o "$PLUGINS/docker-buildx"
chmod +x "$PLUGINS/docker-compose" "$PLUGINS/docker-buildx"

echo "==> Memoria de intercambio (swap) de 2 GB"
if ! swapon --show | grep -q /swapfile; then
  dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

docker compose version
echo
echo "Listo. IP privada de esta EC2: $(hostname -I | awk '{print $1}')"
echo "Cierra la sesión SSH y vuelve a entrar (para usar docker sin sudo). Luego, en la carpeta BACKEND:"
echo "  cp deploy/ec2/nube.env.example .env && nano .env"
echo "  docker compose -f docker-compose.nube.yml up -d --build"
