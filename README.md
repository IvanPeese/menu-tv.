# Menú TV (launcher propio)

Launcher para Android TV hecho con una interfaz HTML. Muestra reloj, tiempo real con fondos animados
(sol, parcialmente nublado, nublado, lluvia, nieve, y versión de noche), entradas HDMI/AV, tus apps con su
icono REAL (se lee de cada app instalada) y una pantalla "Todas las apps".

## Cómo conseguir el APK (sin instalar nada en el PC)
1. Crea un repositorio nuevo en github.com (privado vale).
2. Sube TODO el contenido de esta carpeta (incluida la carpeta oculta `.github`). En la web: "uploading an existing file" y arrastra los archivos y carpetas.
3. Pestaña **Actions** → "Compilar APK" → espera a que termine (unos 3-5 min).
4. Entra en esa ejecución y descarga **MenuTV-apk** (un zip con `app-debug.apk`).

## Instalar en la tele
```
.\adb connect 192.168.1.146:5555
.\adb install -r app-debug.apk
```
Pulsa el botón Inicio del mando: Android preguntará qué launcher usar → elige **Menú TV** → "Siempre".
Si no pregunta: `.\adb shell pm disable-user --user 0 com.google.android.tvlauncher` (se revierte con `pm enable`).
No toques `com.google.android.leanbacklauncher`: queda de recambio.

## Personalizar (todo en `app/src/main/assets/index.html`, bloque CONFIGURACIÓN)
- `city`, `lat`, `lon`: ubicación del tiempo.
- `favs`: paquetes de las apps de la fila inferior, en orden.
- `lite: true`: menos animaciones si la tele va justa.
Tras editar, sube el cambio y vuelve a descargar el APK.
