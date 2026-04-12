"""User-Skill association model - junction table"""
from datetime import datetime
from sqlalchemy import Column, String, Integer, DateTime, ForeignKey, Index
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class UserSkill(Base):
    """
    Association table between User and Skill
    Tracks skills that users have and their proficiency level
    """
    __tablename__ = "user_skills"

    id = Column(Integer, primary_key=True)
    uid = Column(String(255), ForeignKey("users.uid"), nullable=False)
    skill_id = Column(String(255), ForeignKey("skills.id"), nullable=False)
    proficiency_level = Column(String(50), default="beginner")  # beginner, intermediate, advanced, expert
    years_of_experience = Column(Integer, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    user = relationship("User", back_populates="skills")
    skill = relationship("Skill", back_populates="users")

    __table_args__ = (
        Index("idx_user_skill_uid", "uid"),
        Index("idx_user_skill_skill_id", "skill_id"),
    )
