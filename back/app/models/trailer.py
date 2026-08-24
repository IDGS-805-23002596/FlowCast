from datetime import datetime

from sqlalchemy import DateTime, String, text
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class TrailerTmdb(Base):
    __tablename__ = "trailer_tmdb"

    tipo_contenido: Mapped[str] = mapped_column(String(10), primary_key=True)
    referencia: Mapped[str] = mapped_column(primary_key=True)
    video_id: Mapped[str | None]
    idioma: Mapped[str | None]
    consultado_en: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=text("now()")
    )


class VideoNoIncrustable(Base):
    __tablename__ = "video_no_incrustable"

    video_id: Mapped[str] = mapped_column(primary_key=True)
    codigo: Mapped[str | None]
    registrado_en: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=text("now()")
    )
