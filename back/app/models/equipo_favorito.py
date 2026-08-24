import uuid
from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, String, Text, text
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class EquipoFavorito(Base):
    __tablename__ = "equipo_favorito"

    perfil_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("perfil.id", ondelete="CASCADE"), primary_key=True
    )
    liga: Mapped[str] = mapped_column(String(40), primary_key=True)
    equipo_id: Mapped[str] = mapped_column(String(20), primary_key=True)
    deporte: Mapped[str] = mapped_column(String(20))
    nombre: Mapped[str] = mapped_column(Text)
    logo: Mapped[str | None] = mapped_column(Text)
    creado_en: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=text("now()")
    )
