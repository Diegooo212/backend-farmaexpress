#!/usr/bin/env bash
# Crea el API Manager de FarmaExpress en AWS API Gateway (HTTP API):
#   - una ruta por endpoint del BFF, con integración HTTP hacia la EC2;
#   - autorizador JWT de Microsoft Entra ID (valida firma, iss, aud, exp) y scope "access_as_user";
#   - CORS para el frontend.
# Ejecutar en AWS CloudShell:
#   export EC2_HOST=<IP elástica de la EC2>
#   export AZURE_TENANT_ID=<tenant id>  AZURE_API_CLIENT_ID=<client id de la API>
#   export FRONTEND_ORIGIN=https://<tu-distribucion>.cloudfront.net
#   bash crear-api-gateway.sh
set -euo pipefail

: "${EC2_HOST:?Define EC2_HOST con la IP elástica (o DNS público) de la EC2}"
: "${AZURE_TENANT_ID:?Define AZURE_TENANT_ID}"
: "${AZURE_API_CLIENT_ID:?Define AZURE_API_CLIENT_ID}"
FRONTEND_ORIGIN=${FRONTEND_ORIGIN:-http://localhost:5173}
NOMBRE=${API_NOMBRE:-farmaexpress-api}
SCOPE=${API_SCOPE:-access_as_user}
REGION=$(aws configure get region || echo "${AWS_REGION:-us-east-1}")

BACKEND="http://${EC2_HOST}:8080"
# Emisor de los tokens: debe ser EXACTAMENTE el claim "iss" del access token (F12 → Network → Authorization → jwt.ms).
#   Tokens v2 (por defecto):  https://login.microsoftonline.com/<tenant>/v2.0
#   Tokens v1:                https://sts.windows.net/<tenant>/     (export AZURE_ISSUER=...)
#   Tenant External ID:       https://<tenant>.ciamlogin.com/<tenant>/v2.0
ISSUER=${AZURE_ISSUER:-"https://login.microsoftonline.com/${AZURE_TENANT_ID}/v2.0"}

echo "==> Creando HTTP API '$NOMBRE' con CORS para $FRONTEND_ORIGIN"
CORS=$(cat <<JSON
{
  "AllowOrigins": ["$FRONTEND_ORIGIN", "http://localhost:5173"],
  "AllowMethods": ["GET", "POST", "PUT", "OPTIONS"],
  "AllowHeaders": ["authorization", "content-type"],
  "ExposeHeaders": ["content-disposition", "www-authenticate"],
  "MaxAge": 3600
}
JSON
)
API_ID=$(aws apigatewayv2 create-api --name "$NOMBRE" --protocol-type HTTP \
  --description "FarmaExpress: API Manager delante del BFF (JWT de Microsoft Entra ID)" \
  --cors-configuration "$CORS" --query ApiId --output text)

echo "==> Autorizador JWT (issuer: $ISSUER)"
AUTH_ID=$(aws apigatewayv2 create-authorizer --api-id "$API_ID" --name entra-id-jwt \
  --authorizer-type JWT --identity-source '$request.header.Authorization' \
  --jwt-configuration "{\"Issuer\":\"$ISSUER\",\"Audience\":[\"$AZURE_API_CLIENT_ID\",\"api://$AZURE_API_CLIENT_ID\"]}" \
  --query AuthorizerId --output text)

# ruta <MÉTODO> <ruta> <publica|protegida>
ruta() {
  local metodo=$1 path=$2 acceso=$3
  local integracion
  integracion=$(aws apigatewayv2 create-integration --api-id "$API_ID" \
    --integration-type HTTP_PROXY --integration-method "$metodo" \
    --integration-uri "${BACKEND}${path}" --payload-format-version 1.0 \
    --timeout-in-millis 29000 --query IntegrationId --output text)
  if [ "$acceso" = "protegida" ]; then
    aws apigatewayv2 create-route --api-id "$API_ID" --route-key "$metodo $path" \
      --target "integrations/$integracion" --authorization-type JWT \
      --authorizer-id "$AUTH_ID" --authorization-scopes "$SCOPE" >/dev/null
  else
    aws apigatewayv2 create-route --api-id "$API_ID" --route-key "$metodo $path" \
      --target "integrations/$integracion" >/dev/null
  fi
  printf '    %-4s %-45s %s\n' "$metodo" "$path" "$acceso"
}

echo "==> Rutas (JWT + scope '$SCOPE' en las protegidas; los roles Admin/Operador los valida el BFF)"
ruta GET  /api/bff/catalog/medicamentos                publica
ruta GET  /api/bff/catalog/medicamentos/{id}           publica
ruta POST /api/bff/catalog/medicamentos                protegida
ruta PUT  /api/bff/catalog/medicamentos/{id}           protegida
ruta GET  /api/bff/auth/me                             protegida
ruta GET  /api/bff/cart                                protegida
ruta PUT  /api/bff/cart                                protegida
ruta GET  /api/bff/orders                              protegida
ruta POST /api/bff/orders                              protegida
ruta GET  /api/bff/prescriptions                       protegida
ruta POST /api/bff/prescriptions                       protegida
ruta GET  /api/bff/prescriptions/{id}                  protegida
ruta GET  /api/bff/prescriptions/{id}/archivo          protegida
ruta PUT  /api/bff/prescriptions/{id}/status           protegida

echo "==> Stage \$default con despliegue automático"
aws apigatewayv2 create-stage --api-id "$API_ID" --stage-name '$default' --auto-deploy >/dev/null

URL="https://${API_ID}.execute-api.${REGION}.amazonaws.com"
echo
echo "API Gateway listo:"
echo "  API_ID = $API_ID"
echo "  URL    = $URL"
echo
echo "Prueba rápida:"
echo "  curl -i $URL/api/bff/catalog/medicamentos   # 200, público"
echo "  curl -i $URL/api/bff/cart                   # 401, falta el JWT"
echo
echo "En el frontend usa:  VITE_API_BASE_URL=$URL"
