# Flow Cast

Aplicación móvil para Android e iOS orientada a la reproducción de televisión en vivo, películas, series y música.

El proyecto integrará servidores compatibles con Xtream Codes para el contenido IPTV y un servidor independiente para el catálogo musical. La aplicación permitirá acceder mediante URL, usuario y contraseña, organizar el contenido y conservar la actividad de cada perfil.

## Estado del proyecto

El proyecto está en fase de diseño. Actualmente dispone de una arquitectura documentada y un esquema inicial de PostgreSQL con 25 tablas.

La aplicación móvil, el backend y los procesos de sincronización están pendientes de implementación.

## Funcionalidades previstas

| Área | Funcionalidades |
|---|---|
| Acceso | Inicio de sesión con Xtream Codes, conexiones y perfiles. |
| TV en vivo | Canales por categoría, favoritos y guía EPG. |
| Películas | Catálogo, búsqueda, ficha y reproducción. |
| Series | Temporadas, episodios y reanudación. |
| Música | Canciones, artistas, álbumes y cola de reproducción. |
| Biblioteca | Favoritos, historial y listas personales. |
| Descargas | Música disponible sin conexión en cada dispositivo. |
| Preferencias | Idioma, zona horaria y control parental. |

## Tecnologías propuestas

| Componente | Tecnología |
|---|---|
| Aplicación móvil | Flutter y Dart |
| Backend | NestJS y TypeScript |
| Base de datos central | PostgreSQL |
| Caché del dispositivo | SQLite |
| Reproducción en Android | Media3 y ExoPlayer |
| Reproducción en iOS | AVPlayer |
| Contrato de API | OpenAPI |
| Despliegue inicial | Docker Compose y proxy HTTPS |

## Funcionamiento

1. El usuario introduce los datos de su conexión IPTV.
2. El backend valida la cuenta y crea una sesión propia de la aplicación.
3. Un proceso de sincronización importa el catálogo y la guía EPG.
4. La aplicación consulta los datos mediante la API.
5. Al seleccionar un contenido, el backend autoriza la reproducción y resuelve su origen.
6. El reproductor recibe el audio o vídeo y la aplicación sincroniza el progreso.

PostgreSQL almacena usuarios, metadatos y actividad. Los archivos multimedia permanecen en sus servidores. SQLite conserva la caché local y el estado de las descargas del dispositivo.

### Integración IPTV

La integración utilizará `player_api.php` para autenticación y catálogo, `get.php` como alternativa de importación M3U y `xmltv.php` para programación EPG.

La reproducción priorizará HLS cuando esté disponible. Los streams MPEG-TS y los formatos de películas y episodios se validarán en dispositivos reales. Si un origen necesita adaptación, se incorporará un gateway multimedia para entregar un formato compatible.

### Integración musical

El servidor musical proporcionará una API o un manifiesto con canciones, artistas, álbumes y referencias de archivos. La aplicación podrá reproducir desde el servidor o descargar canciones para escucharlas sin conexión.

## Estructura prevista

```text
media-app/
├── .github/
│   └── workflows/
├── apps/
│   ├── mobile/
│   │   ├── android/
│   │   ├── ios/
│   │   ├── assets/
│   │   ├── lib/
│   │   │   ├── app/
│   │   │   ├── core/
│   │   │   └── features/
│   │   ├── test/
│   │   └── integration_test/
│   └── backend/
│       ├── src/
│       │   ├── main.ts
│       │   ├── worker.ts
│       │   ├── config/
│       │   ├── database/
│       │   ├── common/
│       │   ├── modules/
│       │   └── integrations/
│       ├── migrations/
│       └── test/
├── packages/
│   └── native_player/
│       ├── lib/
│       ├── android/
│       └── ios/
├── contracts/
│   └── openapi.yaml
├── infrastructure/
│   ├── compose.yaml
│   ├── proxy/
│   └── postgres/
├── docs/
└── README.md
```

| Directorio | Responsabilidad |
|---|---|
| `apps/mobile` | Interfaz, navegación, caché y funciones del dispositivo. |
| `apps/backend` | API, permisos, sincronización e integraciones. |
| `packages/native_player` | Interfaz común del reproductor y adaptadores nativos. |
| `contracts` | Definición de peticiones y respuestas de la API. |
| `infrastructure` | Servicios, configuración de despliegue y copias de seguridad. |
| `docs` | Documentación técnica del proyecto durante la implementación. |

El árbol detallado y la organización interna de cada módulo se encuentran en [ARQUITECTURA.md](ARQUITECTURA.md).

## Documentación disponible

| Archivo | Contenido |
|---|---|
| [ARQUITECTURA.md](ARQUITECTURA.md) | Componentes, pantallas, carpetas, API y plan de implementación. |
| [01_esquema.sql](01_esquema.sql) | Esquema inicial con tablas, relaciones, restricciones e índices. |
| [02_guia.md](02_guia.md) | Explicación del modelo de datos y visualización en DataGrip. |
| [03_validacion.txt](03_validacion.txt) | Resultados de validación del SQL con datos sintéticos. |

Los enlaces corresponden a los documentos actuales, ubicados junto a este README.

## Base de datos

El esquema contempla identidad, conexiones, catálogo, televisión, EPG, series, música, reproducción, favoritos, listas y descargas.

La arquitectura prevé migraciones adicionales para las sesiones de autenticación y los trabajos de sincronización. Estas migraciones todavía no forman parte del SQL inicial.

El esquema se puede visualizar en DataGrip como una fuente DDL sin instalar PostgreSQL. Para crear las tablas y guardar datos será necesario un servidor PostgreSQL 16 o posterior.

## Plan de desarrollo

| Fase | Entrega |
|---|---|
| 1 | Validación del proveedor, EPG, formatos y reproducción en Android e iOS. |
| 2 | Proyectos base, migraciones, autenticación, conexiones y perfiles. |
| 3 | Sincronización del catálogo, TV en vivo y guía EPG. |
| 4 | Películas, series, favoritos, historial y reanudación. |
| 5 | Catálogo musical, reproducción de audio y descargas. |
| 6 | Pruebas en dispositivos, rendimiento y preparación de una beta. |

## Desarrollo local

La documentación actual puede consultarse con un visor Markdown. Los comandos de instalación, configuración y ejecución se añadirán cuando existan los proyectos de Flutter y NestJS.

El entorno de desarrollo previsto utilizará Flutter, herramientas Android/iOS, Node.js y PostgreSQL. El desarrollo y la compilación para iOS requerirán macOS y Xcode.

Las credenciales de proveedores y los secretos de despliegue se mantendrán fuera del repositorio. Los archivos `.env.example` documentarán únicamente las variables necesarias y valores de ejemplo.
