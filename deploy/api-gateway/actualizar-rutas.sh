#!/usr/bin/env bash
# Agrega a un API Gateway YA CREADO las rutas de rutas.txt que todavía no tiene
# (por ejemplo, las de los microservicios nuevos). No borra ni modifica las existentes.
# Ejecutar en AWS CloudShell:
#   export API_ID=7x1481plk6            (el id de tu API: aws apigatewayv2 get-apis)
#   export EC2_HOST=<IP de la EC2 del BFF>
#   bash actualizar-rutas.sh
set -euo pipefail

: "${API_ID:?Define API_ID con el id de tu HTTP API (aws apigatewayv2 get-apis --query 'Items[].[Name,ApiId]')}"
: "${EC2_HOST:?Define EC2_HOST con la IP (o DNS) de la EC2 donde corre el BFF}"
SCOPE=${API_SCOPE:-access_as_user}
BACKEND="http://${EC2_HOST}:8080"

AUTH_ID=$(aws apigatewayv2 get-authorizers --api-id "$API_ID" \
  --query "Items[?AuthorizerType=='JWT'] | [0].AuthorizerId" --output text)
if [ -z "$AUTH_ID" ] || [ "$AUTH_ID" = "None" ]; then
  echo "La API $API_ID no tiene autorizador JWT. Créala con crear-api-gateway.sh." >&2
  exit 1
fi

EXISTENTES=$(aws apigatewayv2 get-routes --api-id "$API_ID" --query 'Items[].RouteKey' --output text | tr '\t' '\n')

# shellcheck source=rutas.sh
source "$(dirname "$0")/rutas.sh"
echo "==> Agregando rutas nuevas a $API_ID (autorizador $AUTH_ID, backend $BACKEND)"
crear_rutas "$EXISTENTES"
echo
echo "Listo. El stage \$default tiene despliegue automático: las rutas ya están activas."
echo "Si cambió la IP de la EC2, actualiza también las rutas antiguas con actualizar-host-backend.sh."
