import uuid
from datetime import datetime

from sqlalchemy import BigInteger, DateTime, ForeignKey, String, Text, text
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class EventoReproduccion(Base):
    __tablename__ = "evento_reproduccion"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    usuario_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("usuario.id", ondelete="CASCADE"))
    perfil_id: Mapped[uuid.UUID | None] = mapped_column(
        ForeignKey("perfil.id", ondelete="SET NULL")
    )
    conexion_id: Mapped[uuid.UUID | None] = mapped_column(
        ForeignKey("conexion_xtream.id", ondelete="SET NULL")
    )
    cuenta_proveedor_id: Mapped[uuid.UUID | None] = mapped_column(
        ForeignKey("cuenta_proveedor.id", ondelete="SET NULL")
    )
    resultado: Mapped[str] = mapped_column(String(12))
    tipo_contenido: Mapped[str] = mapped_column(String(10))
    contenido_id: Mapped[str] = mapped_column(Text)
    serie_id: Mapped[str | None] = mapped_column(Text)
    titulo: Mapped[str | None] = mapped_column(Text)
    detalle: Mapped[str | None] = mapped_column(Text)
    dispositivo: Mapped[str] = mapped_column(String(20))
    modelo: Mapped[str | None] = mapped_column(Text)
    version_app: Mapped[str | None] = mapped_column(String(40))
    codigo_error: Mapped[str | None] = mapped_column(String(60))
    mensaje: Mapped[str | None] = mapped_column(Text)
    segundos_inicio: Mapped[float | None]
    creado_en: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=text("now()")
    )
