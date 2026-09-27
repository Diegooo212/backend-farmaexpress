#!/usr/bin/env bash
# Actualiza el CORS del API Gateway con el dominio del frontend (por ejemplo, el de CloudFront,
# que se conoce después de crear el API).
# Uso en CloudShell:  API_ID=xxxx FRONTEND_ORIGIN=https://dxxxx.cloudfront.net bash actualizar-cors.sh
set -euo pipefail
: "${API_ID:?Define API_ID}"
: "${FRONTEND_ORIGIN:?Define FRONTEND_ORIGIN, por ejemplo https://dxxxx.cloudfront.net}"

# localhost:5173 siempre queda permitido (pruebas locales); sin repetirlo, porque AWS rechaza duplicados.
ORIGENES="\"http://localhost:5173\""
[ "$FRONTEND_ORIGIN" != "http://localhost:5173" ] && ORIGENES="\"$FRONTEND_ORIGIN\", $ORIGENES"
aws apigatewayv2 update-api --api-id "$API_ID" --cors-configuration "$(cat <<JSON
{
  "AllowOrigins": [$ORIGENES],
  "AllowMethods": ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
  "AllowHeaders": ["authorization", "content-type"],
  "ExposeHeaders": ["content-disposition", "www-authenticate"],
  "MaxAge": 3600
}
JSON
)" --query 'CorsConfiguration' --output json
echo "CORS actualizado para $FRONTEND_ORIGIN"
