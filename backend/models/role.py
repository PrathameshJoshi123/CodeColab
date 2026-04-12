"""Role model - handles user roles"""
from datetime import datetime
from sqlalchemy import Column, Integer, String, DateTime, Index
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class Role(Base):
    """
    Role model for role-based access control (RBAC)
    Defines different roles that users can have
    """
    __tablename__ = "roles"

    id = Column(Integer, primary_key=True)
    name = Column(String(255), unique=True, nullable=False)
    description = Column(String(500), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    permissions = relationship("Permission", secondary="role_permissions", back_populates="roles")
    users = relationship("UserRole", back_populates="role")

    __table_args__ = (
        Index("idx_role_name", "name"),
    )
