"""Identity model - handles authentication credentials"""
from datetime import datetime
from sqlalchemy import Column, String, Boolean, DateTime, Index
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class Identity(Base):
    """
    Identity model for user authentication
    Stores credentials and OAuth provider information
    """
    __tablename__ = "identities"

    uid = Column(String(255), primary_key=True)
    email = Column(String(255), unique=True, nullable=False)
    password_hash = Column(String(255), nullable=True)  # NULL for OAuth users
    oauth_provider = Column(String(50), nullable=True)  # google, github, facebook, etc.
    is_verified = Column(Boolean, default=False)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    __table_args__ = (
        Index("idx_identity_email", "email"),
        Index("idx_identity_oauth_provider", "oauth_provider"),
    )
