"""Chat model - handles chat metadata"""
from datetime import datetime
from sqlalchemy import Column, String, DateTime, ForeignKey, Index
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class Chat(Base):
    """
    Chat model for storing chat history metadata
    References conversations for detailed messaging
    """
    __tablename__ = "chats"

    id = Column(String(255), primary_key=True)
    conversation_id = Column(String(255), ForeignKey("conversations.id"), unique=True, nullable=False)
    last_message_id = Column(String(255), ForeignKey("messages.id"), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    conversation = relationship("Conversation")
    last_message = relationship("Message")

    __table_args__ = (
        Index("idx_chat_conversation_id", "conversation_id"),
    )
