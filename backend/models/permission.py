"""Permission model - handles user permissions"""
from datetime import datetime
from sqlalchemy import Column, Integer, String, DateTime, Index, Boolean
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class Permission(Base):
    """
    Permission model for fine-grained access control
    Defines specific permissions that can be assigned to roles
    """
    __tablename__ = "permissions"

    id = Column(Integer, primary_key=True)
    name = Column(String(255), unique=True, nullable=False)
    description = Column(String(500), nullable=True)
    is_admin = Column(Boolean, default=False)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    roles = relationship("Role", secondary="role_permissions", back_populates="permissions")

    __table_args__ = (
        Index("idx_permission_name", "name"),
    )
