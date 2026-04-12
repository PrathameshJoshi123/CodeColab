"""Sprint Task model - handles tasks within sprints"""
from datetime import datetime
from sqlalchemy import Column, String, DateTime, Integer, ForeignKey, Text, Index
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class SprintTask(Base):
    """
    SprintTask model for managing individual tasks within a sprint
    Tracks task completion and assignment
    """
    __tablename__ = "sprint_tasks"

    id = Column(String(255), primary_key=True)
    sprint_id = Column(String(255), ForeignKey("sprints.id"), nullable=False)
    title = Column(String(255), nullable=False)
    description = Column(Text, nullable=True)
    assignee_uid = Column(String(255), ForeignKey("users.uid"), nullable=True)
    status = Column(String(50), default="pending")  # pending, in_progress, completed, blocked
    priority = Column(String(50), default="medium")  # low, medium, high, critical
    is_completed = Column(String(255), default=False)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    sprint = relationship("Sprint", back_populates="tasks")
    assignee = relationship("User")

    __table_args__ = (
        Index("idx_sprint_task_sprint_id", "sprint_id"),
        Index("idx_sprint_task_assignee_uid", "assignee_uid"),
        Index("idx_sprint_task_status", "status"),
    )
