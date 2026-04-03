from pydantic import BaseModel
from datetime import datetime
from typing import Optional, List

# ==================== Match Request Models ====================

class MatchRequestCreate(BaseModel):
    """Match request creation"""
    session_type: str  # Debug, Interview, Hackathon, Learning
    message: str
    required_skills: Optional[List[str]] = None

class MatchRequest(BaseModel):
    """Match request response"""
    id: str
    userId: str
    session_type: str
    message: str
    status: str
    created_at: datetime

# ==================== Instant Help Models ====================

class InstantHelpRequestCreate(BaseModel):
    """Instant help request creation"""
    topic: str
    description: str
    required_expertise: Optional[List[str]] = None

class InstantHelpRequest(BaseModel):
    """Instant help request response"""
    id: str
    userId: str
    topic: str
    description: str
    status: str
    created_at: datetime
