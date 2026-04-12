"""User-Permission association model - junction table"""
from datetime import datetime
from sqlalchemy import Column, String, Integer, DateTime, ForeignKey, Index
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class UserPermission(Base):
    """
    Association table between User and Permission
    Allows direct permission assignment to users
    """
    __tablename__ = "user_permissions"

    id = Column(Integer, primary_key=True)
    uid = Column(String(255), ForeignKey("users.uid"), nullable=False)
    permission_id = Column(Integer, ForeignKey("permissions.id"), nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)

    # Relationships
    user = relationship("User")
    permission = relationship("Permission")

    __table_args__ = (
        Index("idx_user_permission_uid", "uid"),
        Index("idx_user_permission_permission_id", "permission_id"),
    )
