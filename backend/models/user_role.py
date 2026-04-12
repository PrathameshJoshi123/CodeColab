"""User-Role association model - junction table"""
from datetime import datetime
from sqlalchemy import Column, String, Integer, DateTime, ForeignKey, Index
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class UserRole(Base):
    """
    Association table between User and Role
    Allows users to have multiple roles
    """
    __tablename__ = "user_roles"

    id = Column(Integer, primary_key=True)
    uid = Column(String(255), ForeignKey("users.uid"), nullable=False)
    role_id = Column(Integer, ForeignKey("roles.id"), nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)

    # Relationships
    user = relationship("User", back_populates="roles")
    role = relationship("Role", back_populates="users")

    __table_args__ = (
        Index("idx_user_role_uid", "uid"),
        Index("idx_user_role_role_id", "role_id"),
    )
