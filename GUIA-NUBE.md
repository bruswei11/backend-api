# Poner el sistema en Internet: Supabase + Render + UptimeRobot

Resultado: un link fijo `https://….onrender.com` que abre el sistema desde cualquier celular o
computadora, sin depender de la PC del Departamento. Todo gratis.

| Servicio | Para qué | Qué guarda |
|---|---|---|
| **Supabase** | Base de datos (PostgreSQL) | Estudiantes, citas, atenciones, adjuntos |
| **Render** | Tiene encendido el sistema (servidor + versión web) | Nada: solo corre el programa |
| **UptimeRobot** | Entra cada 5 minutos para que Render no se "duerma" | Nada |
| **GitHub** | Desde ahí Render toma el código | El código (sin datos ni claves) |

> ⚠️ **Datos reales.** Son datos de salud de menores (dato sensible, Ley 7.593/2025). Empezá con
> datos de prueba (`APP_DATOS_DEMO=true`). Para cargar estudiantes reales, primero la autorización
> de la dirección del colegio; después cambiás a `APP_DATOS_DEMO=false` en una base nueva.

---

## Paso 1 — Supabase (base de datos)

1. Entrá a **supabase.com** → *Start your project* → registrate (podés usar tu cuenta de GitHub).
2. *New project*:
   - **Name:** `psicologia`
   - **Database Password:** inventá una larga y **anotala** (la vas a necesitar en el paso 3).
   - **Region:** *South America (São Paulo)*.
3. Esperá a que termine de crearse (1–2 minutos).
4. Arriba tocá **Connect** → pestaña **Connection string** → tipo **JDBC** → modo
   **Session pooler** (el que dice que funciona con IPv4; Render gratis no usa IPv6). Vas a ver
   algo como:

   ```
   jdbc:postgresql://aws-0-sa-east-1.pooler.supabase.com:5432/postgres?user=postgres.abcdefghij&password=[YOUR-PASSWORD]
   ```

   Anotá por separado:
   - **DB_URL:** la parte hasta `postgres` + `?sslmode=require` →
     `jdbc:postgresql://aws-0-sa-east-1.pooler.supabase.com:5432/postgres?sslmode=require`
   - **DB_USUARIO:** lo que va después de `user=` → `postgres.abcdefghij`
   - **DB_PASSWORD:** la contraseña del punto 2.

5. **Cargar la base inicial (cuentas reales):** en Supabase → **SQL Editor** → *New query* →
   abrí con el Bloc de notas el archivo **`supabase-inicial.sql` del Escritorio**, copiá TODO,
   pegalo y tocá **Run**. Al final tiene que mostrar `admin` y `josechavez`. Crea todas las tablas
   (con la protección RLS activada) y las dos cuentas, con **las mismas contraseñas que en la PC**.
   Después **borrá ese archivo** del Escritorio: tiene datos de las cuentas.

## Paso 2 — GitHub (el código)

1. Registrate en **github.com** e instalá **GitHub Desktop** (desktop.github.com).
2. GitHub Desktop → *File → Add local repository* → elegí la carpeta
   `NetBeansProjects\backend-api` → *create a repository* → *Create repository*.
3. *Publish repository* → **dejá marcado "Keep this code private"** → *Publish*.

(Antes de subir, doble clic en `compilar-todo.bat` para que vaya la última versión web.)

## Paso 3 — Render (el sistema encendido)

1. Entrá a **render.com** → registrate con tu cuenta de GitHub.
2. *New +* → **Web Service** → elegí el repositorio `backend-api`.
3. Configuración:
   - **Name:** `psicologia-colegio` (el link será `https://psicologia-colegio.onrender.com`)
   - **Region:** la más cercana disponible (ej. *Ohio* o *Virginia*)
   - **Language / Runtime:** **Docker**
   - **Instance type:** **Free**
4. **Environment Variables** (*Add Environment Variable*), una por una:

   | Variable | Valor |
   |---|---|
   | `DB_URL` | la del paso 1 (con `?sslmode=require`) |
   | `DB_USUARIO` | la del paso 1 |
   | `DB_PASSWORD` | la del paso 1 |
   | `JWT_SECRET` | 64 caracteres al azar (ver abajo) |
   | `APP_CREAR_TABLAS` | `true` |
   | `APP_DATOS_DEMO` | `false` (ya cargaste las cuentas reales en el paso 1) |
   | `ADJUNTOS_EN_BASE` | `true` (Render gratis no guarda archivos: van a la base) |
   | `DB_MAX_CONEXIONES` | `3` |

   Para generar el `JWT_SECRET` en PowerShell:
   ```powershell
   -join ((48..57)+(97..102) | Get-Random -Count 64 | % {[char]$_})
   ```
5. En *Advanced* → **Health Check Path:** `/api/health`.
6. *Create Web Service*. La primera vez tarda 5–10 minutos (compila todo). Cuando diga **Live**,
   abrí el link: aparece el login. Entrá con **josechavez** o **admin** y su contraseña de siempre.

## Paso 4 — UptimeRobot (que no se duerma)

1. Registrate en **uptimerobot.com**.
2. *New monitor* → tipo **HTTP(s)** →
   - **URL:** `https://psicologia-colegio.onrender.com/api/health`
   - **Monitoring interval:** 5 minutes
3. *Create monitor*. Además de mantenerlo despierto, te avisa por correo si el sistema se cae.

`/api/health` consulta la base, así también mantiene activo el proyecto de Supabase (los proyectos
gratuitos se pausan tras una semana sin uso).

---

## Actualizar el sistema después de un cambio

1. Doble clic en `compilar-todo.bat`.
2. GitHub Desktop → escribí qué cambió → *Commit to main* → *Push origin*.
3. Render lo detecta y se actualiza solo (5–10 min).

## Pasar a datos reales (cuando el colegio lo autorice)

1. En Supabase, creá un **proyecto nuevo** (base vacía).
2. En Render cambiá `DB_URL`/`DB_USUARIO`/`DB_PASSWORD` a los del proyecto nuevo y
   `APP_DATOS_DEMO` a `false`.
3. Las cuentas nuevas las crea **josechavez** desde Configuración → Usuarios. Pedime ayuda para pasar los estudiantes de la PC.
4. Descargá un respaldo seguido: Estudiantes → *Reportes y archivos* → **Respaldar mis datos**.

## Límites del plan gratuito

- Render gratis: 512 MB de memoria (el sistema usa ~180 MB) y 750 horas por mes (alcanza para
  tenerlo encendido todo el mes con un solo servicio).
- Supabase gratis: 500 MB de base de datos. Los adjuntos ocupan lugar: preferí PDF/imágenes livianas.
- Las condiciones de los planes gratuitos cambian: revisalas en cada sitio.

## Verificado en esta PC (2026-09-30)

Simulación con Docker igual a Render (512 MB, variable `PORT`) contra PostgreSQL 16: crea las
tablas con RLS, carga los datos de prueba, 32 pruebas de la API sin fallos (login y bloqueo por
intentos, permisos por rol, citas y superposición, seguimiento, atenciones, adjuntos guardados en
la base, exportar CSV/ficha/nota, importar desde Excel, auditoría, respaldo), hora de Paraguay.
