from datetime import datetime
from typing import Any

from sqlalchemy import DateTime, String, text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class SubtituloExterno(Base):
    __tablename__ = "subtitulo_externo"

    tipo_contenido: Mapped[str] = mapped_column(String(10), primary_key=True)
    referencia: Mapped[str] = mapped_column(primary_key=True)
    idioma: Mapped[str] = mapped_column(String(10), primary_key=True)
    archivo_id: Mapped[str | None]
    lineas: Mapped[list[Any] | None] = mapped_column(JSONB)
    consultado_en: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=text("now()")
    )
