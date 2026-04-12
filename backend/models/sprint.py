"""Sprint model - handles sprint/project information"""
from datetime import datetime
from sqlalchemy import Column, String, DateTime, Integer, ForeignKey, Text, Index
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class Sprint(Base):
    """
    Sprint model for managing sprints/projects
    Contains sprint metadata and references to tasks and sessions
    """
    __tablename__ = "sprints"

    id = Column(String(255), primary_key=True)
    name = Column(String(255), nullable=False)
    description = Column(Text, nullable=True)
    owner_uid = Column(String(255), ForeignKey("users.uid"), nullable=False)
    repo_link = Column(String(255), nullable=True)
    status = Column(String(50), default="active")  # active, completed, paused
    start_time = Column(DateTime, nullable=False)
    end_time = Column(DateTime, nullable=True)
    is_open = Column(String(255), default=True)
    max_participants = Column(Integer, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    owner = relationship("User")
    tasks = relationship("SprintTask", back_populates="sprint")
    sessions = relationship("SprintSession", back_populates="sprint")

    __table_args__ = (
        Index("idx_sprint_owner_uid", "owner_uid"),
        Index("idx_sprint_status", "status"),
        Index("idx_sprint_created_at", "created_at"),
    )
