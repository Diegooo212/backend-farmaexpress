#!/usr/bin/env bash
# Si la IP de la EC2 cambió (en Learner Lab pasa al reiniciar el lab si no usas IP elástica),
# actualiza todas las integraciones del API Gateway para que apunten a la nueva IP.
# Uso en CloudShell:  API_ID=xxxx NUEVO_HOST=3.90.1.2 bash actualizar-host-backend.sh
set -euo pipefail
: "${API_ID:?Define API_ID}"
: "${NUEVO_HOST:?Define NUEVO_HOST (IP o DNS público de la EC2)}"

aws apigatewayv2 get-integrations --api-id "$API_ID" \
  --query 'Items[].[IntegrationId,IntegrationUri]' --output text |
while read -r id uri; do
  nueva=$(echo "$uri" | sed -E "s#^http://[^/:]+#http://${NUEVO_HOST}#")
  aws apigatewayv2 update-integration --api-id "$API_ID" --integration-id "$id" --integration-uri "$nueva" >/dev/null
  echo "  $uri  ->  $nueva"
done
echo "Listo (el stage \$default se redespliega solo)."
