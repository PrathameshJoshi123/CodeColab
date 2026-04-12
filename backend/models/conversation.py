"""Conversation model - handles chat conversations"""
from datetime import datetime
from sqlalchemy import Column, String, DateTime, Index
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class Conversation(Base):
    """
    Conversation model for storing conversation/chat room information
    Can be DirectMessage, GroupChat, or SessionChat
    """
    __tablename__ = "conversations"

    id = Column(String(255), primary_key=True)
    conversation_type = Column(String(50), default="DirectMessage")  # DirectMessage, GroupChat, SessionChat
    title = Column(String(255), nullable=True)
    description = Column(String(500), nullable=True)
    is_active = Column(String(255), default=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    messages = relationship("Message", back_populates="conversation")
    participants = relationship("ConversationParticipant", back_populates="conversation")

    __table_args__ = (
        Index("idx_conversation_type", "conversation_type"),
        Index("idx_conversation_created_at", "created_at"),
    )
