#!/usr/bin/env bash
# Evidencia para la presentación (EP2, indicadores 7 y 8): cada ruta con y sin token,
# mostrando el código HTTP y el JSON que devuelve.
# Uso:
#   export API=https://xxxx.execute-api.us-east-1.amazonaws.com
#   export TOKEN=<access token: F12 → Network → header Authorization de una llamada a /api/bff/...>   (opcional)
#   bash probar-api.sh
set -uo pipefail
: "${API:?Define API con la URL del API Gateway}"

llamar() {
  local titulo=$1; shift
  echo
  echo "### $titulo"
  echo "\$ curl $*" | sed -E 's/Bearer [A-Za-z0-9._-]{20,}/Bearer <TOKEN>/'
  curl -s -w '\n--> HTTP %{http_code}\n' "$@" | head -c 600
  echo
}

llamar "1. Catálogo público, sin token (esperado 200)" \
  "$API/api/bff/catalog/medicamentos"

llamar "2. Ruta protegida sin token (esperado 401, lo rechaza API Gateway)" \
  "$API/api/bff/cart"

llamar "3. Token falso o alterado (esperado 401, firma inválida)" \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ4In0.firma-falsa" "$API/api/bff/cart"

if [ -z "${TOKEN:-}" ]; then
  echo
  echo "(Define TOKEN para probar las llamadas autenticadas: F12 → Network → header Authorization de una llamada a /api/bff/...)"
  exit 0
fi

llamar "4. Perfil con token válido (esperado 200)" \
  -H "Authorization: Bearer $TOKEN" "$API/api/bff/auth/me"

llamar "5. Carrito con token válido (esperado 200)" \
  -H "Authorization: Bearer $TOKEN" "$API/api/bff/cart"

llamar "6. Mis recetas con token válido (esperado 200)" \
  -H "Authorization: Bearer $TOKEN" "$API/api/bff/prescriptions"

llamar "7. Crear medicamento con token de Cliente (esperado 403: falta el rol Admin, lo rechaza el BFF)" \
  -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"sku":"MED-900","nombre":"Prueba 10mg","precio":1000,"stock":5}' "$API/api/bff/catalog/medicamentos"

llamar "8. Validar una receta con token de Cliente (esperado 403: falta rol Operador/Admin)" \
  -X PUT -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"status":"VALIDADA"}' "$API/api/bff/prescriptions/1/status"
