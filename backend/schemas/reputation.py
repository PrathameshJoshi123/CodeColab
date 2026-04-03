from pydantic import BaseModel
from datetime import datetime
from typing import Optional

# ==================== Session Feedback Models ====================

class SessionFeedbackCreate(BaseModel):
    """Session feedback creation"""
    session_id: str
    receiver_id: str
    technical_rating: int  # 1-5
    communication_rating: int  # 1-5
    helpfulness_rating: int  # 1-5
    goal_completed: bool
    comment: Optional[str] = None

class SessionFeedback(BaseModel):
    """Session feedback response"""
    id: str
    session_id: str
    giver_id: str
    receiver_id: str
    technical_rating: int
    communication_rating: int
    helpfulness_rating: int
    goal_completed: bool
    comment: str
    created_at: datetime

# ==================== Badge Models ====================

class BadgeCreate(BaseModel):
    """Badge creation"""
    name: str
    description: str
    xp_required: int

class Badge(BaseModel):
    """Badge response"""
    id: str
    name: str
    description: str
    xp_required: int

class UserBadgeAward(BaseModel):
    """User badge award"""
    badge_id: str
    user_id: str

class UserBadge(BaseModel):
    """User badge response"""
    id: str
    user_id: str
    badge_id: str
    badge_name: str
    awarded_at: datetime
