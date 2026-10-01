@echo off
title Servidor - Departamento de Psicologia
setlocal

set "JAVA=C:\tools\jdk17_extracted\jdk-17.0.20.1+1\bin\java.exe"
set "JAR=%~dp0target\backend-api-0.1.0.jar"
set "COMPOSE=%~dp0..\SistemaPsicologia\docker-compose.yml"

echo ============================================================
echo   Servidor para la app movil - Departamento de Psicologia
echo ============================================================
echo.

if not exist "%JAVA%" (
  echo [ERROR] No se encontro Java 17 en "%JAVA%".
  goto fin
)
if not exist "%JAR%" (
  echo [ERROR] No se encontro el servidor compilado en "%JAR%".
  goto fin
)

echo [1/3] Encendiendo la base de datos (Docker)...
docker compose -f "%COMPOSE%" up -d >nul 2>&1
if errorlevel 1 (
  echo [ERROR] No se pudo encender la base de datos.
  echo         Abri Docker Desktop, espera a que diga "Engine running" y volve a ejecutar este archivo.
  goto fin
)

echo       Esperando a que MySQL este listo...
set /a intentos=0
:esperar
docker exec psicologia_mysql mysqladmin -uroot ping >nul 2>&1
if not errorlevel 1 goto lista
set /a intentos+=1
if %intentos% geq 30 (
  echo [ERROR] MySQL no respondio a tiempo.
  goto fin
)
timeout /t 2 /nobreak >nul
goto esperar
:lista
echo       Base de datos lista.
echo.

echo [2/3] Direccion para poner en el celular (Configurar servidor):
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4"') do (
  for /f "tokens=* delims= " %%b in ("%%a") do echo          http://%%b:8080
)
echo       (Usa la que empieza con 192.168. La que empieza con 172. no sirve.)
echo       El celular tiene que estar en el MISMO WiFi que esta computadora.
echo.

echo [3/3] Iniciando el servidor... NO cierres esta ventana mientras uses la app.
echo.
"%JAVA%" -jar "%JAR%"

:fin
echo.
pause
