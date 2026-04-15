"""Sprint Todo model - handles dynamic todo items within sprints"""
from datetime import datetime
from sqlalchemy import Column, String, DateTime, Boolean, ForeignKey, Index
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class SprintTodo(Base):
    """
    SprintTodo model for managing dynamic todo checklist items within a sprint
    Users can create, update, and mark todos as complete
    """
    __tablename__ = "sprint_todos"

    id = Column(String(255), primary_key=True)
    sprint_id = Column(String(255), ForeignKey("sprints.id"), nullable=False)
    title = Column(String(255), nullable=False)
    description = Column(String(500), nullable=True)
    is_completed = Column(Boolean, default=False)
    created_by = Column(String(255), ForeignKey("users.uid"), nullable=False)
    completed_at = Column(DateTime, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    sprint = relationship("Sprint", back_populates="todos")
    creator = relationship("User")

    __table_args__ = (
        Index("idx_sprint_todo_sprint_id", "sprint_id"),
        Index("idx_sprint_todo_created_by", "created_by"),
        Index("idx_sprint_todo_is_completed", "is_completed"),
    )
