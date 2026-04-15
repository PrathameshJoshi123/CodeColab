from pydantic import BaseModel
from datetime import datetime
from typing import Optional

# ==================== Message Models ====================

class MessageCreate(BaseModel):
    """Message creation"""
    content: Optional[str] = ""
    message_type: str = "text"  # text, image, video, code, link, file
    media_url: Optional[str] = None
    media_name: Optional[str] = None

class Message(BaseModel):
    """Message response"""
    id: str
    senderId: str
    sender_name: Optional[str] = None
    content: str
    message_type: str
    media_url: Optional[str] = None
    media_name: Optional[str] = None
    created_at: datetime
    is_read: bool

# ==================== Conversation Models ====================

class ConversationCreate(BaseModel):
    """Conversation creation"""
    type: str  # DirectMessage, GroupChat, SessionChat
    participants: list[str]

class Conversation(BaseModel):
    """Conversation response"""
    id: str
    type: str
    created_at: datetime
    participants: list[str]

class ConversationParticipant(BaseModel):
    """Conversation participant"""
    conversation_id: str
    user_id: str
