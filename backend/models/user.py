"""User model - handles user profile information"""
from datetime import datetime
from sqlalchemy import Column, String, Boolean, DateTime, Text, ForeignKey, Index
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class User(Base):
    """
    User model for storing user profile information
    References the Identity for authentication
    """
    __tablename__ = "users"

    uid = Column(String(255), primary_key=True)
    email = Column(String(255), unique=True, nullable=False)
    full_name = Column(String(255), nullable=True)
    bio = Column(Text, nullable=True)
    college = Column(String(255), nullable=True)
    city = Column(String(255), nullable=True)
    github_username = Column(String(255), nullable=True)
    linkedin_url = Column(String(255), nullable=True)
    profile_image_url = Column(String(255), nullable=True)
    is_available = Column(Boolean, default=True)
    is_active = Column(Boolean, default=True)
    theme = Column(String(50), default="light")  # light, dark
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    roles = relationship("UserRole", back_populates="user")
    skills = relationship("UserSkill", back_populates="user")
    reputation = relationship("Reputation", uselist=False, back_populates="user")

    __table_args__ = (
        Index("idx_user_email", "email"),
        Index("idx_user_is_available", "is_available"),
        Index("idx_user_is_active", "is_active"),
    )
