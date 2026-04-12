"""Moderation model - handles content moderation and reports"""
from datetime import datetime
from sqlalchemy import Column, String, DateTime, Integer, ForeignKey, Text, Index
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class Moderation(Base):
    """
    Moderation model for handling user reports and content moderation
    Tracks reported content and moderation actions
    """
    __tablename__ = "moderations"

    id = Column(String(255), primary_key=True)
    reporter_uid = Column(String(255), ForeignKey("users.uid"), nullable=False)
    reported_uid = Column(String(255), ForeignKey("users.uid"), nullable=True)  # User being reported
    resource_type = Column(String(100), nullable=False)  # user, message, comment, profile, etc.
    resource_id = Column(String(255), nullable=True)
    report_reason = Column(String(255), nullable=False)
    description = Column(Text, nullable=True)
    report_status = Column(String(50), default="open")  # open, investigating, resolved, dismissed
    action_taken = Column(String(255), nullable=True)  # warning, suspension, ban, none
    resolved_by = Column(String(255), nullable=True)
    notes = Column(Text, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    reporter = relationship("User", foreign_keys=[reporter_uid])
    reported_user = relationship("User", foreign_keys=[reported_uid])

    __table_args__ = (
        Index("idx_moderation_reporter_uid", "reporter_uid"),
        Index("idx_moderation_reported_uid", "reported_uid"),
        Index("idx_moderation_status", "report_status"),
        Index("idx_moderation_created_at", "created_at"),
    )
