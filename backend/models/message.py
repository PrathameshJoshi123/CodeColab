"""Message model - handles chat messages"""
from datetime import datetime
from sqlalchemy import Column, String, DateTime, ForeignKey, Text, Boolean, Index
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class Message(Base):
    """
    Message model for storing individual messages in conversations
    Supports different message types: text, code, link, file
    """
    __tablename__ = "messages"

    id = Column(String(255), primary_key=True)
    conversation_id = Column(String(255), ForeignKey("conversations.id"), nullable=False)
    sender_uid = Column(String(255), ForeignKey("users.uid"), nullable=False)
    content = Column(Text, nullable=False)
    message_type = Column(String(50), default="text")  # text, code, link, file
    metadata = Column(String(500), nullable=True)  # JSON for file info, code language, etc.
    is_read = Column(Boolean, default=False)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    conversation = relationship("Conversation", back_populates="messages")
    sender = relationship("User")

    __table_args__ = (
        Index("idx_message_conversation_id", "conversation_id"),
        Index("idx_message_sender_uid", "sender_uid"),
        Index("idx_message_created_at", "created_at"),
    )
