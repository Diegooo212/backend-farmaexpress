# Despliegue en AWS Academy (Learner Lab): 3 instancias EC2

```
Frontend (local o CloudFront)
    │  Authorization: Bearer <access token de Microsoft Entra ID>
    ▼
[API Gateway: opcional, paso 6]
    │
    ▼
EC2 #1 farmaexpress-bff ── bff :8080 (IP elástica, única EC2 expuesta a Internet)
    │  red privada de la VPC
    ├──► EC2 #2 farmaexpress-microservicios ── catalog :8081 + prescriptions :8082
    │
    └──► EC2 #3 farmaexpress-db ── MySQL 8.4 :3306 ◄── también la usan catalog y prescriptions
            farmaexpress_bff · farmaexpress_catalog · farmaexpress_prescriptions
```

- **Autenticación en todas las capas:** los 3 microservicios validan el JWT de Entra ID (firma, `iss`, `aud`, vigencia, scope y rol).
- **Entre instancias se usan IPs privadas.** No cambian aunque se detenga el lab.
- **Solo el BFF necesita IP elástica,** porque es la única dirección que usa el frontend.

> En Learner Lab todo va en **us-east-1**. Al terminar la sesión del lab las EC2 se detienen solas: antes de
> usar la app (o presentar), inicia el lab y espera a que las 3 instancias estén *Running*. Los servicios
> arrancan solos (systemd).

**Anota a medida que avances:**

| Dato | Dónde |
|---|---|
| IP privada de `farmaexpress-db` | Paso 2 (EC2 → instancia → *Private IPv4 address*) |
| IP privada de `farmaexpress-microservicios` | Paso 2 |
| IP elástica de `farmaexpress-bff` | Paso 2 |
| Contraseñas de MySQL (root y `farmaexpress`) | Paso 3 |
| `AZURE_TENANT_ID`, `AZURE_API_CLIENT_ID` | `FRONTEND/.env` (`VITE_AZURE_TENANT_ID`, `VITE_AZURE_API_CLIENT_ID`) |

---

## 1. Security groups (EC2 → Security Groups → Create security group)

Créalos **en este orden**, porque los últimos hacen referencia a los primeros. En todos, VPC = la *default*.

| Nombre | Reglas de entrada (*Inbound rules*) |
|---|---|
| `farmaexpress-bff-sg` | **SSH 22** desde *My IP* · **Custom TCP 8080** desde *Anywhere-IPv4* (`0.0.0.0/0`) |
| `farmaexpress-ms-sg` | **SSH 22** desde *My IP* · **Custom TCP 8081-8082** con origen el security group **`farmaexpress-bff-sg`** |
| `farmaexpress-db-sg` | **SSH 22** desde *My IP* · **MYSQL/Aurora 3306** con origen **`farmaexpress-bff-sg`** · otra regla **MYSQL/Aurora 3306** con origen **`farmaexpress-ms-sg`** |

Así, desde Internet solo se llega al puerto 8080 del BFF. Los microservicios solo aceptan al BFF, y MySQL solo acepta al BFF y a los microservicios.

> Si usas EC2 Instance Connect y "My IP" te bloquea, cambia temporalmente la regla SSH a *Anywhere-IPv4*.

## 2. Crear las 3 instancias (EC2 → Launch instance)

Configuración común para las tres:

| Campo | Valor |
|---|---|
| AMI | **Amazon Linux 2023** |
| Instance type | **t3.small** |
| Key pair | **vockey** |
| Network settings → *Select existing security group* | el de la tabla de abajo |
| Storage | 8 GB gp3 (por defecto) |
| Advanced details → IAM instance profile | **LabInstanceProfile** |

| Name | Security group |
|---|---|
| `farmaexpress-db` | `farmaexpress-db-sg` |
| `farmaexpress-microservicios` | `farmaexpress-ms-sg` |
| `farmaexpress-bff` | `farmaexpress-bff-sg` |

Después: **EC2 → Elastic IPs → Allocate Elastic IP address → Allocate**, luego *Actions → Associate* a `farmaexpress-bff`.
Anota esa IP y las **IP privadas** de `farmaexpress-db` y `farmaexpress-microservicios`.

**Conectarse a una instancia:** selecciónala → **Connect → EC2 Instance Connect → Connect**. Se abre una terminal en el navegador.

## 3. EC2 `farmaexpress-db`: MySQL 8.4

```bash
sudo dnf install -y git
git clone https://github.com/<tu-usuario>/backend-farmaexpress.git
sudo bash backend-farmaexpress/deploy/ec2/instalar-mysql.sh
```

El script pide dos contraseñas. MySQL exige 8+ caracteres con mayúscula, minúscula, número y símbolo, **sin comillas**:

- **root:** para administrar MySQL.
- **farmaexpress:** el usuario que usan los microservicios. Solo tiene permisos sobre las bases `farmaexpress_*`.

Al final muestra la **IP privada** de esta EC2, que es el `DB_HOST` de las otras dos.

## 4. EC2 `farmaexpress-microservicios`: catalog + prescriptions

```bash
sudo dnf install -y git
git clone https://github.com/<tu-usuario>/backend-farmaexpress.git
cd backend-farmaexpress
sudo bash deploy/ec2/instalar-ec2.sh microservicios
sudo nano /etc/farmaexpress/farmaexpress.env
```

Completa (guarda con `Ctrl+O`, `Enter`, y sal con `Ctrl+X`):

```properties
DB_HOST=<IP privada de farmaexpress-db>
DB_USER=farmaexpress
DB_PASSWORD=<contraseña del usuario farmaexpress>
AZURE_TENANT_ID=<tenant id>
AZURE_API_CLIENT_ID=<client id de la API>
```

```bash
bash deploy/ec2/desplegar.sh
```

La primera compilación tarda unos minutos. Al final debe decir `catalog :8081 UP` y `prescriptions :8082 UP`.

## 5. EC2 `farmaexpress-bff`

```bash
sudo dnf install -y git
git clone https://github.com/<tu-usuario>/backend-farmaexpress.git
cd backend-farmaexpress
sudo bash deploy/ec2/instalar-ec2.sh bff
sudo nano /etc/farmaexpress/farmaexpress.env
```

```properties
DB_HOST=<IP privada de farmaexpress-db>
DB_USER=farmaexpress
DB_PASSWORD=<contraseña del usuario farmaexpress>
AZURE_TENANT_ID=<tenant id>
AZURE_API_CLIENT_ID=<client id de la API>
CATALOG_URL=http://<IP privada de farmaexpress-microservicios>:8081
PRESCRIPTIONS_URL=http://<IP privada de farmaexpress-microservicios>:8082
CORS_ORIGINS=http://localhost:5173
```

```bash
bash deploy/ec2/desplegar.sh
```

**Comprobar desde tu PC** (PowerShell):

```powershell
curl.exe http://<IP elástica del BFF>:8080/api/bff/catalog/medicamentos   # JSON con 6 medicamentos
curl.exe -i http://<IP elástica del BFF>:8080/api/bff/cart                # 401 (falta el JWT)
```

## 6. Probar con el frontend local

En `FRONTEND/.env` cambia solo la URL de la API:

```properties
VITE_API_BASE_URL=http://<IP elástica del BFF>:8080
```

Reinicia `npm run dev` (Vite lee el `.env` al arrancar) y abre http://localhost:5173.
El login con Microsoft sigue igual (la Redirect URI es `http://localhost:5173`).
Prueba: catálogo, carrito, compra, enviar receta con foto y, con la cuenta de Operador o Administrador, el panel de farmacia.

## 7. (Opcional) API Gateway delante del BFF

```bash
# En AWS CloudShell
git clone https://github.com/<tu-usuario>/backend-farmaexpress.git
cd backend-farmaexpress/deploy/api-gateway
export EC2_HOST=<IP elástica del BFF>
export AZURE_TENANT_ID=<tenant id>
export AZURE_API_CLIENT_ID=<client id de la API>
export AZURE_ISSUER=https://sts.windows.net/<tenant id>/     # claim "iss" de tus tokens (v1)
export FRONTEND_ORIGIN=http://localhost:5173
bash crear-api-gateway.sh
```

El script crea:

- **14 rutas** con integración `HTTP_PROXY` hacia `http://<IP del BFF>:8080/...`.
- El **autorizador JWT `entra-id-jwt`**: valida issuer y audience (`<client id>` y `api://<client id>`) y exige el scope `access_as_user`.
- El **CORS** y el stage `$default`.

Después:

- En el frontend usa `VITE_API_BASE_URL=<URL del API Gateway>`.
- Prueba las rutas con y sin token usando `probar-api.sh`.

> **Roles:** el autorizador JWT de API Gateway valida firma, emisor, audiencia, vigencia y **scopes**.
> Los **App Roles** (`Administrador`/`Admin`, `Operador`, `Cliente`) viajan en el claim `roles`, y los valida el BFF (y cada microservicio).

Para el frontend en la nube (S3 + CloudFront) ver `docs/DESPLIEGUE-FRONTEND.md` del repo del frontend. Después:

- CORS: `API_ID=<id> FRONTEND_ORIGIN=https://dxxxx.cloudfront.net bash actualizar-cors.sh`
- Entra ID: agrega `https://dxxxx.cloudfront.net` como *Redirect URI* de la plataforma **SPA**.

## Operación diaria

| Qué | Comando (en la EC2 que corresponda) |
|---|---|
| Ver logs en vivo | `sudo journalctl -u farmaexpress-bff -f` (o `-catalog`, `-prescriptions`) |
| Estado de un servicio | `sudo systemctl status farmaexpress-bff` |
| Subir una versión nueva | `cd backend-farmaexpress && bash deploy/ec2/desplegar.sh` |
| Entrar a MySQL (EC2 db) | `mysql -u root -p` → `SHOW DATABASES;` |
| Ver MySQL desde tu PC con Workbench | túnel SSH: `ssh -i labsuser.pem -L 3307:<IP privada db>:3306 ec2-user@<IP elástica BFF>` y en Workbench conecta a `127.0.0.1:3307` |

## Si algo falla

| Síntoma | Causa probable |
|---|---|
| `desplegar.sh`: un servicio no responde | `sudo journalctl -u farmaexpress-<servicio> -n 80 --no-pager`. Si dice `Communications link failure`, revisa `DB_HOST` y el security group de la EC2 db (paso 1). Si dice `Access denied`, revisa `DB_USER`/`DB_PASSWORD`. |
| El BFF responde `503` ("servicio no disponible") | El BFF no llega a los microservicios: revisa `CATALOG_URL`/`PRESCRIPTIONS_URL` (IP **privada**) y la regla 8081-8082 de `farmaexpress-ms-sg`. |
| `curl` al BFF desde tu PC no responde | La instancia está detenida (inicia el lab), falta la regla 8080 en `farmaexpress-bff-sg`, o la IP elástica no está asociada. |
| Error de CORS en el navegador | `CORS_ORIGINS` del BFF no incluye el origen del frontend (`http://localhost:5173`). Corrige y ejecuta `sudo systemctl restart farmaexpress-bff`. |
| `401` con sesión iniciada | `AZURE_TENANT_ID`/`AZURE_API_CLIENT_ID` mal escritos en la EC2. Compara con el `iss` y el `aud` del token (F12 → Network → header `Authorization` → https://jwt.ms). |
| `desplegar.sh`: `git pull` falla | El repo en GitHub es privado o no subiste los últimos cambios. |
