"""Conversation Participant model - tracks conversation members"""
from datetime import datetime
from sqlalchemy import Column, String, Integer, DateTime, ForeignKey, Index
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class ConversationParticipant(Base):
    """
    ConversationParticipant model for managing participants in conversations
    Tracks which users are part of which conversations
    """
    __tablename__ = "conversation_participants"

    id = Column(Integer, primary_key=True)
    conversation_id = Column(String(255), ForeignKey("conversations.id"), nullable=False)
    uid = Column(String(255), ForeignKey("users.uid"), nullable=False)
    joined_at = Column(DateTime, default=datetime.utcnow)

    # Relationships
    conversation = relationship("Conversation", back_populates="participants")
    user = relationship("User")

    __table_args__ = (
        Index("idx_conversation_participant_conversation_id", "conversation_id"),
        Index("idx_conversation_participant_uid", "uid"),
    )
