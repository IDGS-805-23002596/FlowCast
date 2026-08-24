from datetime import datetime

from sqlalchemy import DateTime, text
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class IntroSerie(Base):
    __tablename__ = "intro_serie"

    huella: Mapped[str] = mapped_column(primary_key=True)
    serie_id: Mapped[str] = mapped_column(primary_key=True)
    inicio: Mapped[float]
    fin: Mapped[float]
    actualizado_en: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=text("now()")
    )
