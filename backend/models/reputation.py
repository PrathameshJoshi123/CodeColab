"""Reputation model - handles user reputation and ratings"""
from datetime import datetime
from sqlalchemy import Column, String, Integer, Float, DateTime, ForeignKey, Index
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class Reputation(Base):
    """
    Reputation model for tracking user reputation scores
    Based on session feedback, badges, and community ratings
    """
    __tablename__ = "reputations"

    id = Column(Integer, primary_key=True)
    uid = Column(String(255), ForeignKey("users.uid"), unique=True, nullable=False)
    total_points = Column(Integer, default=0)
    communication_rating = Column(Float, default=0.0)  # 0-5
    skill_rating = Column(Float, default=0.0)  # 0-5
    reliability_rating = Column(Float, default=0.0)  # 0-5
    overall_rating = Column(Float, default=0.0)  # 0-5
    total_feedback_count = Column(Integer, default=0)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    user = relationship("User", back_populates="reputation")
    feedbacks = relationship("SessionFeedback", back_populates="reputation")
    badges = relationship("Badge", back_populates="reputation")

    __table_args__ = (
        Index("idx_reputation_uid", "uid"),
    )
