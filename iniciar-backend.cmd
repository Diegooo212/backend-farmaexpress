@echo off
REM Levanta los 3 microservicios de FarmaExpress, cada uno en su propia ventana.
REM   .\iniciar-backend.cmd        -> usa MySQL (localhost:3306, usuario root). Pide la contraseña.
REM   .\iniciar-backend.cmd h2     -> base en memoria, sin MySQL (los datos se pierden al cerrar)
REM (En PowerShell hay que anteponer .\ ; en cmd funciona con o sin él.)
REM Para no escribir la contraseña cada vez:  $env:DB_PASSWORD="tu_clave"  (PowerShell)  o  set DB_PASSWORD=tu_clave  (cmd)

setlocal
cd /d "%~dp0"
set ARGS=
if not "%1"=="" set ARGS=-Dspring-boot.run.profiles=%1
if /i "%1"=="h2" goto azure

REM --- MySQL: contraseña de root ---
if not defined DB_USER set "DB_USER=root"
if not defined DB_PORT set "DB_PORT=3306"
if defined DB_PASSWORD goto probar_mysql
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "$p = Read-Host 'Contrasena de MySQL para el usuario %DB_USER%' -AsSecureString; [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($p))"`) do set "DB_PASSWORD=%%P"

:probar_mysql
REM Si el cliente de MySQL está instalado, se prueba la conexión antes de levantar nada.
set "MYSQL_EXE="
for /d %%D in ("%ProgramFiles%\MySQL\MySQL Server *") do if exist "%%D\bin\mysql.exe" set "MYSQL_EXE=%%D\bin\mysql.exe"
if not defined MYSQL_EXE goto azure
"%MYSQL_EXE%" -u %DB_USER% "--password=%DB_PASSWORD%" -h 127.0.0.1 -P %DB_PORT% -e "SELECT 1" >nul 2>&1
if errorlevel 1 (
  echo.
  echo  No se pudo entrar a MySQL con el usuario %DB_USER%.
  echo  - Revisa la contrasena ^(es la que elegiste al instalar MySQL^).
  echo  - Revisa que el servicio MySQL este iniciado ^(services.msc^).
  echo  - O prueba sin MySQL:  .\iniciar-backend.cmd h2
  echo.
  exit /b 1
)
echo MySQL: conexion OK

:azure
REM Login con Microsoft: el BFF usa el mismo tenant y App Registration que el frontend.
REM Si no están definidas, se leen de ..\FRONTEND\.env (VITE_AZURE_TENANT_ID y VITE_AZURE_API_CLIENT_ID).
if exist "..\FRONTEND\.env" (
  for /f "usebackq tokens=1,* delims==" %%A in ("..\FRONTEND\.env") do (
    if /i "%%A"=="VITE_AZURE_TENANT_ID" if not defined AZURE_TENANT_ID set "AZURE_TENANT_ID=%%B"
    if /i "%%A"=="VITE_AZURE_API_CLIENT_ID" if not defined AZURE_API_CLIENT_ID set "AZURE_API_CLIENT_ID=%%B"
  )
)
if defined AZURE_TENANT_ID (echo Login con Microsoft: activado) else (echo Login con Microsoft: sin configurar)

start "FarmaExpress catalog :8081" cmd /k mvnw.cmd -pl catalog spring-boot:run %ARGS%
start "FarmaExpress prescriptions :8082" cmd /k mvnw.cmd -pl prescriptions spring-boot:run %ARGS%
start "FarmaExpress bff :8080" cmd /k mvnw.cmd -pl bff spring-boot:run %ARGS%

echo Iniciando catalog (8081), prescriptions (8082) y bff (8080) en ventanas separadas...
endlocal
