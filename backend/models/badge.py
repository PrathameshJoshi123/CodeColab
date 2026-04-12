"""Badge model - handles achievements and badges"""
from datetime import datetime
from sqlalchemy import Column, String, Integer, DateTime, ForeignKey, Text, Index
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class Badge(Base):
    """
    Badge model for gamification and user achievements
    Users earn badges based on various accomplishments
    """
    __tablename__ = "badges"

    id = Column(String(255), primary_key=True)
    reputation_id = Column(Integer, ForeignKey("reputations.id"), nullable=False)
    name = Column(String(255), nullable=False)
    description = Column(Text, nullable=True)
    icon_url = Column(String(255), nullable=True)
    awarded_at = Column(DateTime, default=datetime.utcnow)

    # Relationships
    reputation = relationship("Reputation", back_populates="badges")

    __table_args__ = (
        Index("idx_badge_reputation_id", "reputation_id"),
    )
