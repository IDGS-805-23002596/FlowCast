# FlowCast

FlowCast reúne una API para televisión en vivo, películas, series y música y una aplicación Android nativa. El catálogo se consulta mediante el proveedor Xtream de cada cuenta. FlowCast guarda perfiles, favoritos, historial, progreso y preferencias.

| Carpeta | Contenido |
| --- | --- |
| [`back/`](back/) | API FastAPI, esquema PostgreSQL y panel de administración. |
| [`front/`](front/) | Aplicación Android en Kotlin con Jetpack Compose y Media3. |

Para levantar la API, sigue las [instrucciones del backend](back/README.md). Para trabajar en Android, abre `front/` en Android Studio y sincroniza Gradle. La URL que consume la app se define como `URL_BASE_API` en [`front/app/build.gradle.kts`](front/app/build.gradle.kts).

El esquema vigente está en [`back/db/`](back/db/).

## Integrantes

- Álvaro Gómez ([@Pegazusk](https://github.com/Pegazusk))
- Saúl Villeda ([@IDGS-805-23002596](https://github.com/IDGS-805-23002596))
- [@IDGS1005-Teran](https://github.com/IDGS1005-Teran)
- [@IDGS-805-19002100](https://github.com/IDGS-805-19002100)
