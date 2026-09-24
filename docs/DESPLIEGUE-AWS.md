# Despliegue en AWS Academy (Learner Lab)

Arquitectura final:

```
Navegador ──HTTPS──► CloudFront + S3 (frontend React + MSAL)
    │
    │  Authorization: Bearer <access token de Entra ID>
    ▼
API Gateway (HTTP API) ── autorizador JWT: firma, iss, aud, exp + scope access_as_user
    │  HTTP :8080
    ▼
EC2 (Amazon Linux 2023) ── bff :8080 ──► catalog :8081 / prescriptions :8082   (valida el mismo JWT)
    │  MySQL :3306
    ▼
Amazon RDS for MySQL ── farmaexpress_bff · farmaexpress_catalog · farmaexpress_prescriptions
```

> En Learner Lab todo se crea en **us-east-1** y con el rol **LabRole** (no se pueden crear roles IAM).
> Al terminar la sesión del lab las instancias se detienen: vuelve a iniciar el lab antes de la presentación.

Anota estos valores a medida que avances:

| Dato | Dónde se obtiene |
|---|---|
| `AZURE_TENANT_ID`, `AZURE_API_CLIENT_ID` | `FRONTEND/.env` (`VITE_AZURE_TENANT_ID`, `VITE_AZURE_API_CLIENT_ID`) |
| Endpoint de RDS | Paso 1 |
| IP elástica de la EC2 | Paso 2 |
| `API_ID` y URL del API Gateway | Paso 4 |
| Dominio de CloudFront | Paso 5 (repo del frontend) |

---

## 1. Base de datos: Amazon RDS for MySQL

Consola → **RDS → Create database**:

| Campo | Valor |
|---|---|
| Creation method | Standard create |
| Engine | MySQL 8.4 |
| Templates | **Free tier** (o Dev/Test) |
| DB instance identifier | `farmaexpress-db` |
| Master username | `admin` |
| Master password | una contraseña segura (anótala) |
| Instance class | `db.t3.micro` o `db.t4g.micro` |
| Storage | 20 GB gp3, sin autoscaling |
| Public access | **No** |
| VPC security group | Create new → `farmaexpress-rds-sg` |
| Initial database name | *(vacío: cada microservicio crea la suya)* |

Cuando quede **Available**, copia el **Endpoint** (algo como `farmaexpress-db.xxxx.us-east-1.rds.amazonaws.com`).

## 2. Servidor: EC2 con los 3 microservicios

Consola → **EC2 → Launch instance**:

| Campo | Valor |
|---|---|
| Name | `farmaexpress-backend` |
| AMI | Amazon Linux 2023 |
| Instance type | **t3.medium** (3 JVM; con t3.small también funciona gracias al swap) |
| Key pair | `vockey` (la del lab) |
| Security group | Create → `farmaexpress-ec2-sg` con reglas de entrada: **SSH 22** desde *My IP* y **TCP 8080** desde *Anywhere* (API Gateway llega por Internet) |
| Advanced → IAM instance profile | `LabInstanceProfile` |

Después:

1. **IP elástica** (para que la IP no cambie al reiniciar el lab): EC2 → Elastic IPs → *Allocate* → *Associate* a la instancia. Anota la IP.
2. **Permitir que la EC2 hable con RDS**: RDS → tu base → *VPC security groups* → `farmaexpress-rds-sg` → *Edit inbound rules* → **MySQL/Aurora 3306** con origen el security group **`farmaexpress-ec2-sg`**.

> El puerto 8080 queda abierto a Internet porque API Gateway (HTTP API) llama a la EC2 por su IP pública.
> Aun así, el BFF valida el mismo JWT de Entra ID: llamarlo directo sin token responde 401.
> Los puertos 8081 y 8082 no se abren; solo el BFF los usa, dentro de la misma instancia.

## 3. Instalar y levantar el backend en la EC2

Conéctate: EC2 → instancia → **Connect → EC2 Instance Connect** (o `ssh -i labsuser.pem ec2-user@<IP>`).

```bash
sudo dnf install -y git
git clone https://github.com/<tu-usuario>/backend-farmaexpress.git
cd backend-farmaexpress
sudo bash deploy/ec2/instalar-ec2.sh        # Java 21, swap, servicios systemd
sudo nano /etc/farmaexpress/farmaexpress.env
```

Completa en `farmaexpress.env`:

```properties
DB_HOST=<endpoint de RDS>
DB_USER=admin
DB_PASSWORD=<contraseña de RDS>
AZURE_TENANT_ID=<tenant id>
AZURE_API_CLIENT_ID=<client id de la API>
```

Compila y levanta (la primera vez Flyway crea las 3 bases, las tablas y los 6 medicamentos iniciales):

```bash
bash deploy/ec2/desplegar.sh
```

Comprueba:

```bash
curl http://localhost:8080/actuator/health                # {"status":"UP"}
curl http://localhost:8080/api/bff/catalog/medicamentos    # JSON con 6 medicamentos
curl -i http://localhost:8080/api/bff/cart                 # 401 (falta el JWT)
sudo journalctl -u farmaexpress-bff -f                     # logs en vivo
```

Para ver la base de RDS desde la EC2: `mysql -h <endpoint> -u admin -p`.

Cada vez que subas cambios al repo: `bash deploy/ec2/desplegar.sh`.

## 4. API Manager: AWS API Gateway (HTTP API)

Abre **AWS CloudShell** (ícono `>_` arriba a la derecha de la consola) y sube la carpeta `deploy/api-gateway`
(o clona el repo):

```bash
git clone https://github.com/<tu-usuario>/backend-farmaexpress.git
cd backend-farmaexpress/deploy/api-gateway
export EC2_HOST=<IP elástica>
export AZURE_TENANT_ID=<tenant id>
export AZURE_API_CLIENT_ID=<client id de la API>
export FRONTEND_ORIGIN=http://localhost:5173     # se cambia en el paso 6
bash crear-api-gateway.sh
```

El script crea:

- **14 rutas**, una por endpoint del BFF, cada una con integración `HTTP_PROXY` hacia `http://<IP>:8080/...`.
- **Autorizador JWT `entra-id-jwt`**:
  - issuer `https://login.microsoftonline.com/<tenant>/v2.0` (debe ser igual al claim `iss` de tus tokens; si ves `https://sts.windows.net/<tenant>/`, exporta `AZURE_ISSUER` con ese valor antes de ejecutar el script);
  - audience `<client id>` y `api://<client id>`;
  - identity source `$request.header.Authorization`.
- **Rutas protegidas:** usan el autorizador y exigen el scope `access_as_user`.
  - Sin token, o con token inválido o vencido: **401**.
  - Con un token que no trae ese scope: **403**.
- **Rutas públicas:** `GET` del catálogo.
- **CORS** y el stage `$default` con auto-deploy.

Anota `API_ID` y la URL `https://<api-id>.execute-api.us-east-1.amazonaws.com`.

> **Roles:** el autorizador JWT de API Gateway valida firma, emisor, audiencia, vigencia y **scopes**.
> Los **App Roles** (`Administrador`, `Operador`, `Cliente`) viajan en el claim `roles`, y los valida el BFF (y cada microservicio).
> Por eso, un Cliente que intenta crear un medicamento pasa el Gateway (tiene el scope), pero el BFF responde **403**.

Consola para mostrarlo en la presentación: **API Gateway → farmaexpress-api → Routes / Authorization / CORS / Integrations**.

## 5. Frontend: S3 + CloudFront

Ver `docs/DESPLIEGUE-FRONTEND.md` en el repo del frontend (script `deploy/publicar-cloudfront.sh`).
Al terminar tendrás un dominio `https://dxxxx.cloudfront.net`.

## 6. Conectar todo

1. **CORS del API Gateway con el dominio de CloudFront** (en CloudShell):

   ```bash
   API_ID=<api id> FRONTEND_ORIGIN=https://dxxxx.cloudfront.net bash actualizar-cors.sh
   ```

2. **Entra ID**: agrega `https://dxxxx.cloudfront.net` como *Redirect URI* de la plataforma **SPA** (ver `docs/ENTRA-ID.md` del frontend).

3. Prueba las rutas con y sin token (evidencia para la presentación):

   ```bash
   export API=https://<api-id>.execute-api.us-east-1.amazonaws.com
   export TOKEN=<access token: F12 → Network → header Authorization de una llamada a /api/bff/...>
   bash probar-api.sh
   ```

## Si algo falla

| Síntoma | Causa probable |
|---|---|
| `desplegar.sh` dice que un puerto no responde | `sudo journalctl -u farmaexpress-bff -n 80`. Si dice `Communications link failure`, revisa el security group de RDS (paso 2.2) y `DB_HOST`. |
| API Gateway responde `503 Service Unavailable` | La EC2 está apagada o cambió de IP → inicia el lab, o `actualizar-host-backend.sh` con la IP nueva. |
| `401` con un token recién obtenido | El `issuer` o `audience` del autorizador no calza con el token. Compara con los claims `iss` y `aud` del token (F12 → Network → header `Authorization` → https://jwt.ms). |
| Error de CORS en el navegador | Falta el dominio de CloudFront en el CORS del API Gateway (paso 6.1). |
| `AADSTS50011` al iniciar sesión | Falta la Redirect URI de CloudFront en la App Registration (paso 6.2). |
