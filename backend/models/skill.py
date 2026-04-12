"""Skill model - handles skill definitions"""
from datetime import datetime
from sqlalchemy import Column, String, DateTime, Index, Boolean
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class Skill(Base):
    """
    Skill model for storing skill information
    Skills can be assigned to users via UserSkill
    """
    __tablename__ = "skills"

    id = Column(String(255), primary_key=True)
    name = Column(String(255), nullable=False)
    description = Column(String(500), nullable=True)
    category = Column(String(100), nullable=True)
    is_available = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    users = relationship("UserSkill", back_populates="skill")

    __table_args__ = (
        Index("idx_skill_name", "name"),
        Index("idx_skill_category", "category"),
    )
