"""Activity Logging model - tracks user activities"""
from datetime import datetime
from sqlalchemy import Column, String, DateTime, ForeignKey, Text, Index
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class ActivityLogging(Base):
    """
    ActivityLogging model for tracking user activities
    Records actions, metadata, and timestamps for auditing
    """
    __tablename__ = "activity_logs"

    id = Column(String(255), primary_key=True)
    uid = Column(String(255), ForeignKey("users.uid"), nullable=False)
    action = Column(String(255), nullable=False)  # created_sprint, joined_sprint, completed_task, etc.
    resource_type = Column(String(100), nullable=True)  # sprint, task, message, etc.
    resource_id = Column(String(255), nullable=True)
    metadata = Column(Text, nullable=True)  # JSON format for additional info
    created_at = Column(DateTime, default=datetime.utcnow)

    # Relationships
    user = relationship("User")

    __table_args__ = (
        Index("idx_activity_logging_uid", "uid"),
        Index("idx_activity_logging_action", "action"),
        Index("idx_activity_logging_created_at", "created_at"),
    )
