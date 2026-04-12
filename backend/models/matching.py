"""Matching model - handles user matching for sprints"""
from datetime import datetime
from sqlalchemy import Column, String, DateTime, Float, Integer, ForeignKey, Index
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class Matching(Base):
    """
    Matching model for matching users with sprints
    Tracks match requests, acceptance, and match quality scores
    """
    __tablename__ = "matchings"

    id = Column(String(255), primary_key=True)
    user_uid = Column(String(255), ForeignKey("users.uid"), nullable=False)
    sprint_id = Column(String(255), ForeignKey("sprints.id"), nullable=False)
    match_type = Column(String(50), default="requested")  # requested, suggested, accepted, rejected
    match_score = Column(Float, default=0.0)  # 0-100 compatibility score
    reason = Column(String(500), nullable=True)
    is_accepted = Column(String(255), default=False)
    accepted_at = Column(DateTime, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    user = relationship("User")
    sprint = relationship("Sprint")

    __table_args__ = (
        Index("idx_matching_user_uid", "user_uid"),
        Index("idx_matching_sprint_id", "sprint_id"),
        Index("idx_matching_is_accepted", "is_accepted"),
    )
