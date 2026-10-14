#!/usr/bin/env bash
# Funciones para crear las rutas del API Gateway a partir de rutas.txt.
# Las usan crear-api-gateway.sh y actualizar-rutas.sh (no se ejecuta solo).
# Requiere: API_ID, AUTH_ID, BACKEND (http://<ec2>:8080) y SCOPE.

RUTAS_TXT="$(dirname "${BASH_SOURCE[0]}")/rutas.txt"

# ruta <MÉTODO> <ruta> <publica|protegida>
ruta() {
  local metodo=$1 path=$2 acceso=$3
  # {proxy+} (todo lo que siga) se reenvía al BFF como {proxy}.
  local destino=${path//\{proxy+\}/\{proxy\}}
  local integracion
  integracion=$(aws apigatewayv2 create-integration --api-id "$API_ID" \
    --integration-type HTTP_PROXY --integration-method "$metodo" \
    --integration-uri "${BACKEND}${destino}" --payload-format-version 1.0 \
    --timeout-in-millis 29000 --query IntegrationId --output text)
  if [ "$acceso" = "protegida" ]; then
    aws apigatewayv2 create-route --api-id "$API_ID" --route-key "$metodo $path" \
      --target "integrations/$integracion" --authorization-type JWT \
      --authorizer-id "$AUTH_ID" --authorization-scopes "$SCOPE" >/dev/null
  else
    aws apigatewayv2 create-route --api-id "$API_ID" --route-key "$metodo $path" \
      --target "integrations/$integracion" >/dev/null
  fi
  printf '    + %-6s %-50s %s\n' "$metodo" "$path" "$acceso"
}

# crear_rutas [rutas existentes, una "MÉTODO ruta" por línea]: crea las de rutas.txt que no estén.
crear_rutas() {
  local existentes=${1:-}
  local metodo path acceso
  while read -r metodo path acceso; do
    [ -z "$metodo" ] && continue
    case "$metodo" in \#*) continue ;; esac
    if printf '%s\n' "$existentes" | grep -qxF "$metodo $path"; then
      printf '      %-6s %-50s ya existe\n' "$metodo" "$path"
      continue
    fi
    ruta "$metodo" "$path" "$acceso"
  done < "$RUTAS_TXT"
}
