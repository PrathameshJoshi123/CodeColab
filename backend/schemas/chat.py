from pydantic import BaseModel
from datetime import datetime

# ==================== Message Models ====================

class MessageCreate(BaseModel):
    """Message creation"""
    content: str
    message_type: str = "text"  # text, code, link, file

class Message(BaseModel):
    """Message response"""
    id: str
    senderId: str
    content: str
    message_type: str
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
