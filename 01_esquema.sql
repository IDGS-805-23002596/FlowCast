BEGIN;
CREATE SCHEMA media_app;

-- 1. IDENTIDAD, PERFILES Y DISPOSITIVOS
CREATE TABLE media_app.app_user (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name        varchar(120) NOT NULL,
    auth_subject        text UNIQUE,
    email               text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    disabled_at         timestamptz
);
CREATE UNIQUE INDEX app_user_email_uq ON media_app.app_user (lower(email)) WHERE email IS NOT NULL;
COMMENT ON TABLE media_app.app_user IS 'Identidad interna. Puede crearse tras validar el login Xtream; no requiere otro registro.';
COMMENT ON COLUMN media_app.app_user.auth_subject IS 'Identificador opcional de un servicio de autenticación propio. No es una contraseña ni un token de sesión.';

CREATE TABLE media_app.profile (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid NOT NULL REFERENCES media_app.app_user(id) ON DELETE CASCADE,
    name                varchar(80) NOT NULL,
    avatar_url          text,
    is_kids             boolean NOT NULL DEFAULT false,
    parental_pin_hash   text,
    preferred_language  varchar(35) NOT NULL DEFAULT 'es',
    timezone            text NOT NULL DEFAULT 'America/Mexico_City',
    created_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, user_id),
    UNIQUE (user_id, name)
);
COMMENT ON TABLE media_app.profile IS 'Perfiles de un mismo usuario; cada perfil tiene favoritos, listas y progreso propios.';
COMMENT ON COLUMN media_app.profile.parental_pin_hash IS 'Hash del PIN opcional. La API aplica control parental y limita intentos.';

CREATE TABLE media_app.device (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid NOT NULL REFERENCES media_app.app_user(id) ON DELETE CASCADE,
    installation_id     uuid NOT NULL,
    platform            varchar(20) NOT NULL CHECK (platform IN ('android', 'ios', 'web', 'desktop', 'tv')),
    name                varchar(120),
    app_version         varchar(40),
    registered_at       timestamptz NOT NULL DEFAULT now(),
    last_seen_at        timestamptz,
    UNIQUE (id, user_id),
    UNIQUE (user_id, installation_id)
);
COMMENT ON TABLE media_app.device IS 'Instalación de la app. installation_id es un UUID aleatorio, no un identificador físico.';

-- 2. SERVIDORES, CUENTAS Y SINCRONIZACIÓN
CREATE TABLE media_app.media_source (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name                varchar(120) NOT NULL,
    adapter             varchar(20) NOT NULL CHECK (adapter IN ('xtream', 'm3u', 'music_http')),
    base_url            text NOT NULL CHECK (base_url ~ '^https?://'),
    server_timezone     text,
    is_enabled          boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (adapter, base_url)
);
COMMENT ON TABLE media_app.media_source IS 'Servidor IPTV o de música. URL base sin usuario, contraseña, query ni barra final; normalizar en la API.';
COMMENT ON COLUMN media_app.media_source.server_timezone IS 'Zona IANA informada por el proveedor. Los instantes se normalizan a UTC al importar.';

CREATE TABLE media_app.source_connection (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 uuid NOT NULL REFERENCES media_app.app_user(id) ON DELETE CASCADE,
    source_id               uuid NOT NULL REFERENCES media_app.media_source(id),
    label                   varchar(120) NOT NULL,
    auth_type               varchar(20) NOT NULL CHECK (auth_type IN ('user_password', 'token', 'none')),
    credentials_secret_ref  text,
    account_status          varchar(20) NOT NULL DEFAULT 'unknown'
                            CHECK (account_status IN ('unknown', 'active', 'expired', 'disabled')),
    expires_at              timestamptz,
    max_connections         integer CHECK (max_connections IS NULL OR max_connections > 0),
    allowed_output_formats  text[],
    preferred_live_format   varchar(10) NOT NULL DEFAULT 'm3u8'
                            CHECK (preferred_live_format IN ('ts', 'm3u8')),
    last_authenticated_at   timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, user_id),
    CHECK ((auth_type = 'none' AND credentials_secret_ref IS NULL)
        OR (auth_type <> 'none' AND credentials_secret_ref IS NOT NULL AND length(credentials_secret_ref) > 0))
);
CREATE INDEX source_connection_source_idx ON media_app.source_connection(source_id);
CREATE INDEX source_connection_user_idx ON media_app.source_connection(user_id);
COMMENT ON TABLE media_app.source_connection IS 'Una cuenta del proveedor perteneciente a un usuario de la app. Catálogo aislado por conexión.';
COMMENT ON COLUMN media_app.source_connection.credentials_secret_ref IS 'Referencia a un secreto cifrado del backend que contiene username/password o token. La clave queda fuera de PostgreSQL.';
COMMENT ON COLUMN media_app.source_connection.max_connections IS 'Límite informado por Xtream; NULL si desconocido o sin límite. La API debe controlar sesiones activas.';

CREATE TABLE media_app.sync_run (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id       uuid NOT NULL REFERENCES media_app.source_connection(id) ON DELETE CASCADE,
    dataset             varchar(20) NOT NULL CHECK (dataset IN ('catalog', 'series_details', 'epg', 'music')),
    import_method       varchar(20) NOT NULL CHECK (import_method IN ('player_api', 'm3u', 'xmltv', 'music_api', 'manifest')),
    status              varchar(20) NOT NULL DEFAULT 'running' CHECK (status IN ('running', 'succeeded', 'failed')),
    started_at          timestamptz NOT NULL DEFAULT now(),
    finished_at         timestamptz,
    records_processed   integer NOT NULL DEFAULT 0 CHECK (records_processed >= 0),
    error_summary       text,
    CHECK (finished_at IS NULL OR finished_at >= started_at),
    CHECK ((status = 'running' AND finished_at IS NULL) OR (status <> 'running' AND finished_at IS NOT NULL))
);
CREATE INDEX sync_run_connection_idx ON media_app.sync_run(connection_id, dataset, started_at DESC);
CREATE UNIQUE INDEX sync_run_active_uq ON media_app.sync_run(connection_id, dataset) WHERE status = 'running';
COMMENT ON TABLE media_app.sync_run IS 'Registro de importaciones. Recuperar ejecuciones interrumpidas antes de reintentar. No guardar credenciales en errores.';

-- 3. CATÁLOGO COMÚN
CREATE TABLE media_app.category (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id       uuid NOT NULL REFERENCES media_app.source_connection(id) ON DELETE CASCADE,
    media_kind          varchar(12) NOT NULL CHECK (media_kind IN ('live', 'movie', 'series', 'episode', 'track')),
    external_id         text NOT NULL,
    name                text NOT NULL,
    sort_order          integer NOT NULL DEFAULT 0,
    is_available        boolean NOT NULL DEFAULT true,
    UNIQUE (connection_id, media_kind, external_id),
    UNIQUE (id, connection_id, media_kind)
);
COMMENT ON TABLE media_app.category IS 'Categorías importadas. Un mismo category_id puede existir en vivo, películas y series.';

CREATE TABLE media_app.media_item (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id       uuid NOT NULL,
    user_id             uuid NOT NULL,
    media_kind          varchar(12) NOT NULL CHECK (media_kind IN ('live', 'movie', 'series', 'episode', 'track')),
    external_id         text NOT NULL,
    title               text NOT NULL,
    synopsis            text,
    artwork_url         text,
    backdrop_url        text,
    release_date        date,
    duration_seconds    integer CHECK (duration_seconds IS NULL OR duration_seconds >= 0),
    rating_10           numeric(3,1) CHECK (rating_10 BETWEEN 0 AND 10),
    language_code       varchar(35),
    is_adult            boolean NOT NULL DEFAULT false,
    is_available        boolean NOT NULL DEFAULT true,
    metadata            jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(metadata) = 'object'),
    first_seen_at       timestamptz NOT NULL DEFAULT now(),
    last_seen_at        timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (connection_id, user_id) REFERENCES media_app.source_connection(id, user_id) ON DELETE CASCADE,
    UNIQUE (connection_id, media_kind, external_id),
    UNIQUE (id, connection_id, media_kind),
    UNIQUE (id, user_id),
    UNIQUE (id, user_id, media_kind)
);
CREATE INDEX media_item_browse_idx ON media_app.media_item(connection_id, media_kind, title, id) WHERE is_available;
CREATE INDEX media_item_user_idx ON media_app.media_item(user_id);
CREATE INDEX media_item_search_idx ON media_app.media_item USING gin (to_tsvector('simple', title));
COMMENT ON TABLE media_app.media_item IS 'Catálogo base: live=canal, movie=película, series=ficha de serie, episode=episodio, track=canción. Películas y series guardan aquí sus datos comunes.';
COMMENT ON COLUMN media_app.media_item.external_id IS 'ID estable del proveedor, único dentro de conexión y tipo. Para M3U usar una identidad normalizada; nunca incluir credenciales.';
COMMENT ON COLUMN media_app.media_item.metadata IS 'Metadatos adicionales saneados: reparto, director, géneros, IDs externos. No guardar la respuesta cruda ni URLs con secretos.';
COMMENT ON COLUMN media_app.media_item.last_seen_at IS 'Actualizar al importar. Retirar ausentes solo tras completar correctamente una sincronización completa del ámbito correspondiente.';

CREATE TABLE media_app.media_category (
    media_item_id       uuid NOT NULL,
    category_id         uuid NOT NULL,
    connection_id       uuid NOT NULL,
    media_kind          varchar(12) NOT NULL,
    PRIMARY KEY (media_item_id, category_id),
    FOREIGN KEY (media_item_id, connection_id, media_kind) REFERENCES media_app.media_item(id, connection_id, media_kind) ON DELETE CASCADE,
    FOREIGN KEY (category_id, connection_id, media_kind) REFERENCES media_app.category(id, connection_id, media_kind) ON DELETE CASCADE
);
CREATE INDEX media_category_category_idx ON media_app.media_category(category_id, media_item_id);
COMMENT ON TABLE media_app.media_category IS 'Relación N:M. Solo une categorías y contenidos del mismo tipo y conexión.';

-- 4. TELEVISIÓN EN VIVO Y GUÍA EPG
CREATE TABLE media_app.epg_channel (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id       uuid NOT NULL REFERENCES media_app.source_connection(id) ON DELETE CASCADE,
    external_id         text NOT NULL,
    name                text NOT NULL,
    logo_url            text,
    UNIQUE (connection_id, external_id),
    UNIQUE (id, connection_id)
);
COMMENT ON TABLE media_app.epg_channel IS 'Identidad EPG del proveedor/XMLTV. Variantes SD/HD de un canal pueden compartir esta guía.';

CREATE TABLE media_app.live_channel (
    media_item_id       uuid PRIMARY KEY,
    connection_id       uuid NOT NULL,
    media_kind          varchar(12) NOT NULL DEFAULT 'live' CHECK (media_kind = 'live'),
    epg_channel_id      uuid,
    channel_number      integer CHECK (channel_number IS NULL OR channel_number >= 0),
    epg_offset_seconds  integer NOT NULL DEFAULT 0,
    catchup_days        integer NOT NULL DEFAULT 0 CHECK (catchup_days >= 0),
    FOREIGN KEY (media_item_id, connection_id, media_kind) REFERENCES media_app.media_item(id, connection_id, media_kind) ON DELETE CASCADE,
    FOREIGN KEY (epg_channel_id, connection_id) REFERENCES media_app.epg_channel(id, connection_id)
);
CREATE INDEX live_channel_epg_idx ON media_app.live_channel(epg_channel_id);
COMMENT ON TABLE media_app.live_channel IS 'Datos específicos de TV. Puede existir sin EPG; la API no debe ocultar el canal por ello.';
COMMENT ON COLUMN media_app.live_channel.epg_offset_seconds IS 'Corrección manual opcional: el horario mostrado es el instante EPG más este desplazamiento.';

CREATE TABLE media_app.epg_program (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    epg_channel_id      uuid NOT NULL REFERENCES media_app.epg_channel(id) ON DELETE CASCADE,
    external_id         text,
    title               text NOT NULL,
    description         text,
    starts_at           timestamptz NOT NULL,
    ends_at             timestamptz NOT NULL,
    genre               text,
    artwork_url         text,
    imported_at         timestamptz NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at),
    UNIQUE (epg_channel_id, starts_at)
);
CREATE INDEX epg_program_retention_idx ON media_app.epg_program(ends_at);
COMMENT ON TABLE media_app.epg_program IS 'Programación en UTC. La clave canal+inicio permite upsert. Los solapamientos se revisan al importar, pues el proveedor puede emitirlos.';

-- 5. SERIES, TEMPORADAS Y EPISODIOS
CREATE TABLE media_app.season (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id       uuid NOT NULL,
    series_item_id      uuid NOT NULL,
    series_kind         varchar(12) NOT NULL DEFAULT 'series' CHECK (series_kind = 'series'),
    season_number       integer NOT NULL CHECK (season_number >= 0),
    title               text,
    synopsis            text,
    artwork_url         text,
    release_date        date,
    FOREIGN KEY (series_item_id, connection_id, series_kind) REFERENCES media_app.media_item(id, connection_id, media_kind) ON DELETE CASCADE,
    UNIQUE (series_item_id, season_number),
    UNIQUE (id, connection_id)
);
COMMENT ON TABLE media_app.season IS 'Una ficha series tiene N temporadas; la temporada 0 representa especiales.';

CREATE TABLE media_app.episode (
    media_item_id       uuid PRIMARY KEY,
    connection_id       uuid NOT NULL,
    media_kind          varchar(12) NOT NULL DEFAULT 'episode' CHECK (media_kind = 'episode'),
    season_id           uuid NOT NULL,
    episode_number      integer NOT NULL CHECK (episode_number >= 0),
    FOREIGN KEY (media_item_id, connection_id, media_kind) REFERENCES media_app.media_item(id, connection_id, media_kind) ON DELETE CASCADE,
    FOREIGN KEY (season_id, connection_id) REFERENCES media_app.season(id, connection_id) ON DELETE CASCADE,
    UNIQUE (season_id, episode_number)
);
COMMENT ON TABLE media_app.episode IS 'El episodio es reproducible y su ficha media_item contiene título, sinopsis, imagen y duración.';

-- 6. MÚSICA DEL SEGUNDO SERVIDOR
CREATE TABLE media_app.artist (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id       uuid NOT NULL REFERENCES media_app.source_connection(id) ON DELETE CASCADE,
    external_id         text NOT NULL,
    name                text NOT NULL,
    artwork_url         text,
    UNIQUE (connection_id, external_id),
    UNIQUE (id, connection_id)
);
COMMENT ON TABLE media_app.artist IS 'Artista importado del catálogo musical; usar un ID estable, no el nombre como identidad.';

CREATE TABLE media_app.album (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id       uuid NOT NULL REFERENCES media_app.source_connection(id) ON DELETE CASCADE,
    external_id         text NOT NULL,
    title               text NOT NULL,
    primary_artist_id   uuid,
    release_date        date,
    artwork_url         text,
    FOREIGN KEY (primary_artist_id, connection_id) REFERENCES media_app.artist(id, connection_id),
    UNIQUE (connection_id, external_id),
    UNIQUE (id, connection_id)
);
CREATE INDEX album_artist_idx ON media_app.album(primary_artist_id);
COMMENT ON TABLE media_app.album IS 'Álbum de música; el artista principal es opcional para compilaciones.';

CREATE TABLE media_app.music_track (
    media_item_id       uuid PRIMARY KEY,
    connection_id       uuid NOT NULL,
    media_kind          varchar(12) NOT NULL DEFAULT 'track' CHECK (media_kind = 'track'),
    album_id            uuid,
    disc_number         integer CHECK (disc_number IS NULL OR disc_number > 0),
    track_number        integer CHECK (track_number IS NULL OR track_number > 0),
    genre               text,
    FOREIGN KEY (media_item_id, connection_id, media_kind) REFERENCES media_app.media_item(id, connection_id, media_kind) ON DELETE CASCADE,
    FOREIGN KEY (album_id, connection_id) REFERENCES media_app.album(id, connection_id),
    UNIQUE (media_item_id, connection_id)
);
CREATE INDEX music_track_album_idx ON media_app.music_track(album_id, disc_number, track_number);
COMMENT ON TABLE media_app.music_track IS 'Canción alojada en el servidor de música. El archivo se referencia mediante playback_source, no se guarda como BLOB.';

CREATE TABLE media_app.track_artist (
    track_item_id       uuid NOT NULL,
    artist_id           uuid NOT NULL,
    connection_id       uuid NOT NULL,
    role                varchar(20) NOT NULL DEFAULT 'main' CHECK (role IN ('main', 'featured', 'composer')),
    sort_order          integer NOT NULL DEFAULT 0,
    PRIMARY KEY (track_item_id, artist_id, role),
    FOREIGN KEY (track_item_id, connection_id) REFERENCES media_app.music_track(media_item_id, connection_id) ON DELETE CASCADE,
    FOREIGN KEY (artist_id, connection_id) REFERENCES media_app.artist(id, connection_id) ON DELETE CASCADE
);
CREATE INDEX track_artist_artist_idx ON media_app.track_artist(artist_id);
COMMENT ON TABLE media_app.track_artist IS 'Artistas de una canción, con rol y orden de presentación.';

-- 7. VARIANTES Y LOCALIZACIÓN DE REPRODUCCIÓN
CREATE TABLE media_app.playback_source (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    media_item_id       uuid NOT NULL,
    connection_id       uuid NOT NULL,
    user_id             uuid NOT NULL,
    media_kind          varchar(12) NOT NULL CHECK (media_kind IN ('live', 'movie', 'episode', 'track')),
    variant_key         varchar(80) NOT NULL DEFAULT 'original',
    locator_type        varchar(20) NOT NULL CHECK (locator_type IN ('xtream', 'secret_url', 'music_object')),
    provider_stream_id  text,
    url_secret_ref      text,
    object_key          text,
    delivery            varchar(20) NOT NULL CHECK (delivery IN ('hls', 'mpegts', 'file')),
    file_extension      varchar(16) NOT NULL CHECK (file_extension ~ '^[a-z0-9]+$'),
    mime_type           varchar(100),
    bitrate_kbps        integer CHECK (bitrate_kbps IS NULL OR bitrate_kbps > 0),
    file_size_bytes     bigint CHECK (file_size_bytes IS NULL OR file_size_bytes >= 0),
    checksum_sha256     varchar(64) CHECK (checksum_sha256 IS NULL OR checksum_sha256 ~ '^[a-f0-9]{64}$'),
    is_available        boolean NOT NULL DEFAULT true,
    FOREIGN KEY (media_item_id, connection_id, media_kind) REFERENCES media_app.media_item(id, connection_id, media_kind) ON DELETE CASCADE,
    FOREIGN KEY (media_item_id, user_id, media_kind) REFERENCES media_app.media_item(id, user_id, media_kind) ON DELETE CASCADE,
    UNIQUE (media_item_id, variant_key),
    UNIQUE (id, user_id, media_kind),
    CHECK (
        (locator_type = 'xtream' AND media_kind IN ('live', 'movie', 'episode')
         AND provider_stream_id IS NOT NULL AND length(provider_stream_id) > 0 AND url_secret_ref IS NULL AND object_key IS NULL)
        OR
        (locator_type = 'secret_url' AND url_secret_ref IS NOT NULL AND length(url_secret_ref) > 0
         AND provider_stream_id IS NULL AND object_key IS NULL)
        OR
        (locator_type = 'music_object' AND media_kind = 'track' AND object_key IS NOT NULL AND length(object_key) > 0
         AND provider_stream_id IS NULL AND url_secret_ref IS NULL)
    )
);
CREATE INDEX playback_source_connection_idx ON media_app.playback_source(connection_id);
COMMENT ON TABLE media_app.playback_source IS 'Una o más variantes por elemento reproducible. Una ficha de serie no tiene stream; se reproduce cada episodio.';
COMMENT ON COLUMN media_app.playback_source.variant_key IS 'Clave estable: ts, m3u8, original, audio-320, etc. Solo crear variantes soportadas por el proveedor.';
COMMENT ON COLUMN media_app.playback_source.provider_stream_id IS 'ID usado al construir /live/, /movie/ o /series/ con las credenciales recuperadas del almacén seguro.';
COMMENT ON COLUMN media_app.playback_source.url_secret_ref IS 'Referencia segura para URL completa M3U/directa y cabeceras necesarias. No almacenar URLs firmadas como identidad permanente.';
COMMENT ON COLUMN media_app.playback_source.object_key IS 'Ruta estable del archivo musical, relativa al servidor/almacén; la API genera la URL al reproducir.';
COMMENT ON COLUMN media_app.playback_source.file_extension IS 'Extensión informada por el proveedor: ts, m3u8, mp4, mkv, mp3, m4a, flac, etc.; no forzar TS a VOD.';

-- 8. FAVORITOS, CONTINUAR VIENDO E HISTORIAL
CREATE TABLE media_app.favorite (
    profile_id          uuid NOT NULL,
    user_id             uuid NOT NULL,
    media_item_id       uuid NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (profile_id, media_item_id),
    FOREIGN KEY (profile_id, user_id) REFERENCES media_app.profile(id, user_id) ON DELETE CASCADE,
    FOREIGN KEY (media_item_id, user_id) REFERENCES media_app.media_item(id, user_id) ON DELETE CASCADE
);
CREATE INDEX favorite_item_idx ON media_app.favorite(media_item_id);
COMMENT ON TABLE media_app.favorite IS 'Favoritos de cualquier tipo, incluidas series completas. No admite contenido de otro usuario.';

CREATE TABLE media_app.playback_progress (
    profile_id          uuid NOT NULL,
    user_id             uuid NOT NULL,
    media_item_id       uuid NOT NULL,
    media_kind          varchar(12) NOT NULL CHECK (media_kind IN ('movie', 'episode', 'track')),
    position_seconds    numeric(12,3) NOT NULL DEFAULT 0 CHECK (position_seconds >= 0),
    duration_seconds    numeric(12,3) CHECK (duration_seconds IS NULL OR duration_seconds > 0),
    is_completed        boolean NOT NULL DEFAULT false,
    last_played_at      timestamptz NOT NULL DEFAULT now(),
    revision            bigint NOT NULL DEFAULT 1 CHECK (revision > 0),
    PRIMARY KEY (profile_id, media_item_id),
    FOREIGN KEY (profile_id, user_id) REFERENCES media_app.profile(id, user_id) ON DELETE CASCADE,
    FOREIGN KEY (media_item_id, user_id, media_kind) REFERENCES media_app.media_item(id, user_id, media_kind) ON DELETE CASCADE,
    CHECK (duration_seconds IS NULL OR position_seconds <= duration_seconds)
);
CREATE INDEX playback_progress_resume_idx ON media_app.playback_progress(profile_id, last_played_at DESC) WHERE NOT is_completed;
CREATE INDEX playback_progress_item_idx ON media_app.playback_progress(media_item_id);
COMMENT ON TABLE media_app.playback_progress IS 'Un punto de reanudación por perfil y contenido bajo demanda. TV en vivo solo usa historial.';
COMMENT ON COLUMN media_app.playback_progress.revision IS 'Control optimista: actualizar WHERE revision = valor_leído e incrementar. Resolver conflicto de dispositivos en la API.';

CREATE TABLE media_app.playback_session (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id          uuid NOT NULL,
    user_id             uuid NOT NULL,
    device_id           uuid NOT NULL,
    media_item_id       uuid NOT NULL,
    media_kind          varchar(12) NOT NULL CHECK (media_kind IN ('live', 'movie', 'episode', 'track')),
    started_at          timestamptz NOT NULL DEFAULT now(),
    last_heartbeat_at   timestamptz NOT NULL DEFAULT now(),
    ended_at            timestamptz,
    end_reason          varchar(20) CHECK (end_reason IN ('stopped', 'completed', 'error', 'timeout')),
    FOREIGN KEY (profile_id, user_id) REFERENCES media_app.profile(id, user_id) ON DELETE CASCADE,
    FOREIGN KEY (device_id, user_id) REFERENCES media_app.device(id, user_id) ON DELETE CASCADE,
    FOREIGN KEY (media_item_id, user_id, media_kind) REFERENCES media_app.media_item(id, user_id, media_kind) ON DELETE CASCADE,
    CHECK (last_heartbeat_at >= started_at),
    CHECK (ended_at IS NULL OR ended_at >= last_heartbeat_at),
    CHECK ((ended_at IS NULL AND end_reason IS NULL) OR (ended_at IS NOT NULL AND end_reason IS NOT NULL))
);
CREATE INDEX playback_session_history_idx ON media_app.playback_session(profile_id, started_at DESC);
CREATE INDEX playback_session_device_idx ON media_app.playback_session(device_id);
CREATE INDEX playback_session_item_idx ON media_app.playback_session(media_item_id);
CREATE INDEX playback_session_active_idx ON media_app.playback_session(user_id, last_heartbeat_at) WHERE ended_at IS NULL;
COMMENT ON TABLE media_app.playback_session IS 'Historial de reproducciones. Heartbeat con reloj del servidor; cerrar sesiones abandonadas mediante timeout.';

-- 9. LISTAS PERSONALES Y DESCARGAS DE MÚSICA EN DISPOSITIVOS
CREATE TABLE media_app.playlist (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id          uuid NOT NULL,
    user_id             uuid NOT NULL,
    name                varchar(160) NOT NULL,
    description         text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (profile_id, user_id) REFERENCES media_app.profile(id, user_id) ON DELETE CASCADE,
    UNIQUE (id, user_id)
);
CREATE INDEX playlist_profile_idx ON media_app.playlist(profile_id);
COMMENT ON TABLE media_app.playlist IS 'Lista personal de reproducción, distinta de la lista M3U usada para importar el proveedor.';

CREATE TABLE media_app.playlist_item (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    playlist_id         uuid NOT NULL,
    user_id             uuid NOT NULL,
    media_item_id       uuid NOT NULL,
    media_kind          varchar(12) NOT NULL CHECK (media_kind IN ('live', 'movie', 'episode', 'track')),
    position            integer NOT NULL CHECK (position >= 0),
    added_at            timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (playlist_id, user_id) REFERENCES media_app.playlist(id, user_id) ON DELETE CASCADE,
    FOREIGN KEY (media_item_id, user_id, media_kind) REFERENCES media_app.media_item(id, user_id, media_kind) ON DELETE CASCADE,
    UNIQUE (playlist_id, position) DEFERRABLE INITIALLY IMMEDIATE
);
CREATE INDEX playlist_item_media_idx ON media_app.playlist_item(media_item_id);
COMMENT ON TABLE media_app.playlist_item IS 'Orden de reproducción; admite repetir una canción. Diferir la restricción única al reordenar dentro de una transacción.';

CREATE TABLE media_app.music_download (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid NOT NULL,
    device_id           uuid NOT NULL,
    playback_source_id  uuid NOT NULL,
    media_kind          varchar(12) NOT NULL DEFAULT 'track' CHECK (media_kind = 'track'),
    status              varchar(20) NOT NULL DEFAULT 'queued'
                        CHECK (status IN ('queued', 'downloading', 'completed', 'failed', 'removed')),
    local_uri           text,
    bytes_downloaded    bigint NOT NULL DEFAULT 0 CHECK (bytes_downloaded >= 0),
    total_bytes         bigint CHECK (total_bytes IS NULL OR total_bytes >= 0),
    requested_at        timestamptz NOT NULL DEFAULT now(),
    completed_at        timestamptz,
    error_summary       text,
    FOREIGN KEY (device_id, user_id) REFERENCES media_app.device(id, user_id) ON DELETE CASCADE,
    FOREIGN KEY (playback_source_id, user_id, media_kind) REFERENCES media_app.playback_source(id, user_id, media_kind) ON DELETE CASCADE,
    UNIQUE (device_id, playback_source_id),
    CHECK (total_bytes IS NULL OR bytes_downloaded <= total_bytes),
    CHECK (completed_at IS NULL OR completed_at >= requested_at),
    CHECK (status <> 'completed' OR (local_uri IS NOT NULL AND length(local_uri) > 0 AND completed_at IS NOT NULL
           AND total_bytes IS NOT NULL AND bytes_downloaded = total_bytes))
);
CREATE INDEX music_download_source_idx ON media_app.music_download(playback_source_id);
COMMENT ON TABLE media_app.music_download IS 'Opcional: réplica del estado de descargas musicales en el móvil. El dispositivo confirma existencia e integridad del archivo.';
COMMENT ON COLUMN media_app.music_download.local_uri IS 'Referencia opaca al archivo del dispositivo, no una URL pública. El archivo solo existe en ese dispositivo.';

COMMIT;

