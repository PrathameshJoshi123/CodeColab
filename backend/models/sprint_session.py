"""Sprint Session model - handles sprint session/meetings"""
from datetime import datetime
from sqlalchemy import Column, String, DateTime, Integer, ForeignKey, Text, Index
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class SprintSession(Base):
    """
    SprintSession model for managing sprint meetings/sessions
    Tracks session metadata and participant information
    """
    __tablename__ = "sprint_sessions"

    id = Column(String(255), primary_key=True)
    sprint_id = Column(String(255), ForeignKey("sprints.id"), nullable=False)
    session_type = Column(String(100), nullable=False)  # standup, retrospective, planning, review
    scheduled_time = Column(DateTime, nullable=False)
    duration_minutes = Column(Integer, nullable=True)
    location = Column(String(255), nullable=True)  # virtual link or physical location
    notes = Column(Text, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    sprint = relationship("Sprint", back_populates="sessions")

    __table_args__ = (
        Index("idx_sprint_session_sprint_id", "sprint_id"),
        Index("idx_sprint_session_scheduled_time", "scheduled_time"),
    )
