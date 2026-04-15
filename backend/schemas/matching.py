from pydantic import BaseModel
from datetime import datetime
from typing import Optional, List

# ==================== Match Request Models ====================

class MatchRequestCreate(BaseModel):
    """Match request creation"""
    session_type: str  # Debug, Interview, Hackathon, Learning
    message: str
    required_skills: Optional[List[str]] = None
    scheduled_date_time: Optional[datetime] = None  # When the sprint is scheduled to happen

class MatchRequest(BaseModel):
    """Match request response"""
    id: str
    userId: str
    session_type: str
    message: str
    required_skills: Optional[List[str]] = None
    status: str
    scheduled_date_time: Optional[datetime] = None
    accepted_by: Optional[str] = None
    accepted_at: Optional[datetime] = None
    rejected_by: Optional[str] = None
    rejected_at: Optional[datetime] = None
    linked_sprint_id: Optional[str] = None
    sprint_status: Optional[str] = None
    is_exhausted: Optional[bool] = False
    can_setup_sprint: Optional[bool] = False
    created_at: datetime

# ==================== Match User Profile Models ====================

class UserSkillInfo(BaseModel):
    """User skill information in match context"""
    id: str
    name: str
    proficiency_level: Optional[str] = "beginner"
    years_of_experience: Optional[int] = None

class UserMatchProfile(BaseModel):
    """User profile information shown in match browse"""
    uid: str
    email: str
    full_name: Optional[str] = None
    bio: Optional[str] = None
    college: Optional[str] = None
    city: Optional[str] = None
    profile_image_url: Optional[str] = None
    is_available: Optional[bool] = True
    reputation_score: Optional[float] = 0.0

class MatchRequestWithUser(BaseModel):
    """Match request with requester's user details and skills"""
    id: str
    userId: str
    session_type: str
    message: str
    required_skills: Optional[List[str]] = None
    status: str
    created_at: datetime
    user: Optional[UserMatchProfile] = None
    user_skills: Optional[List[UserSkillInfo]] = None

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
