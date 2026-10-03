# FlowCast · Landing Android

Landing estática en español, con capturas reales del emulador Android y precio de **$200 MXN por cuenta**. La periodicidad no fue especificada; no hay renovación automática.

## Vista local

```bash
cd landing/dist
python3 -m http.server 4173 --bind 127.0.0.1
```

Abre http://localhost:4173. El backend FastAPI también la sirve en `/` y `/landing/` al iniciarse normalmente.

## Pagos

El checkout es una **simulación local inspirada en Stripe**, no una integración con Stripe ni con su sandbox. No necesita claves, no recoge tarjetas reales, no envía datos y no activa cuentas. Permite probar aprobación y rechazo, cerrar, reabrir y reintentar. Los campos son editables. El procesamiento simulado tarda 3 segundos. Al aprobar entrega siempre estas credenciales ficticias:

- Usuario: `dario261111`
- Contraseña: `dario261111`

No son credenciales válidas de la app real.

Para cobrar o probar contra Stripe de verdad se requiere una cuenta Stripe, claves de prueba en el servidor y una ruta que cree Checkout Sessions, con verificación de webhooks antes de activar cuentas. Nunca colocar claves secretas en `dist/`.

## Archivos

- `dist/index.html`: contenido, galería, precio, preguntas y checkout.
- `dist/styles.css`: diseño adaptable, identidad carbón/lima de la app Android.
- `dist/app.js`: galería y estados del pago simulado.
- `dist/assets/`: capturas de la app Android; no se utilizan imágenes de TV.
La versión de trabajo se ejecuta localmente desde esta carpeta.

El catálogo depende del proveedor de cada cuenta. La landing no promete cantidades de contenido ni disponibilidad de títulos.

## Clips automáticos

Tres clips locales de 10 segundos, sin audio: fantasía (Sintel), naturaleza (Jellyfish) e infantil (Big Buck Bunny). Fuente: https://test-videos.co.uk/, que distribuye las muestras bajo atribución Creative Commons. Créditos enlazados en cada tarjeta. Son ejemplos visuales, no una verificación del catálogo del proveedor. Los archivos están en `dist/assets/videos/`. Se pausan fuera de pantalla y respetan la preferencia de movimiento reducido.

## Reproductor real y rendimiento Android

`dist/assets/videos/reproductor-android.mp4` es una grabación real de 18 segundos del reproductor Android en horizontal, con sus controles originales. El botón web pausa la grabación; los controles dentro de la grabación no son interactivos.

Emulador optimizado a 540 × 1200, densidad 210, 4 núcleos, RAM 2048 MB, GPU del Mac y Vulkan desactivado. DNS explícitos; snapshots desactivados para evitar consumo de disco. Animaciones Android a 0.5 y FlowCast precompilado con `cmd package compile -m speed`. Configuración anterior respaldada en `~/.android/avd/Medium_Phone.avd/config.ini.before-flowcast-performance`.

Para arrancar con los parámetros de rendimiento: `zsh landing/tools/iniciar-android-fluido.zsh` desde la raíz del proyecto. No abrir simultáneamente otra instancia del mismo AVD.
