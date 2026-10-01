@echo off
title Compilar servidor + version web
setlocal
set "JAVA_HOME=C:\tools\jdk17_extracted\jdk-17.0.20.1+1"
set "FLUTTER=C:\tools\flutter\bin\flutter.bat"
set "MVN=C:\tools\apache-maven-3.9.16\bin\mvn.cmd"
set "APP=%~dp0..\mobile-app"

echo [1/3] Compilando la version web de la app...
pushd "%APP%"
call "%FLUTTER%" build web --release
if errorlevel 1 ( popd & echo [ERROR] Fallo la compilacion web. & goto fin )
popd

echo [2/3] Copiando la version web dentro del servidor...
if exist "%~dp0src\main\resources\static" rmdir /s /q "%~dp0src\main\resources\static"
xcopy /e /i /q "%APP%\build\web" "%~dp0src\main\resources\static" >nul

echo [3/3] Compilando el servidor...
pushd "%~dp0"
call "%MVN%" -q -o package -DskipTests
if errorlevel 1 ( popd & echo [ERROR] Fallo la compilacion del servidor. & goto fin )
popd

echo.
echo Listo: %~dp0target\backend-api-0.1.0.jar
echo Si el servidor estaba abierto, cerralo y volve a abrir iniciar-servidor.bat.

:fin
echo.
pause
