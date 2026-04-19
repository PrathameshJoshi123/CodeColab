from pydantic import BaseModel, EmailStr
from datetime import datetime
from typing import Optional

# ==================== User Models ====================

class UserCreate(BaseModel):
    """User data for registration"""
    uid: str
    email: str
    oauth_provider: str


class UserLogin(BaseModel):
    """User login credentials"""
    email: str
    password: str


class UserLoginResponse(BaseModel):
    """Response after user login"""
    user_id: str
    email: str
    access_token: str
    token_type: str


class User(BaseModel):
    """User model"""
    uid: str
    email: str
    oauth_provider: str
    is_verified: bool
    is_active: bool
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


# ==================== Profile Models ====================

class ProfileUpdate(BaseModel):
    """Profile update data"""
    full_name: Optional[str] = None
    bio: Optional[str] = None
    college: Optional[str] = None
    city: Optional[str] = None
    github_username: Optional[str] = None
    linkedin_url: Optional[str] = None
    profile_image_url: Optional[str] = None
    is_available: Optional[bool] = None


class Profile(BaseModel):
    """User profile model"""
    userId: str
    full_name: str = ""
    bio: str = ""
    college: str = ""
    city: str = ""
    github_username: str = ""
    linkedin_url: str = ""
    profile_image_url: str = ""
    karma_score: int = 0
    xp_points: int = 0
    level: int = 1
    streak_count: int = 0
    is_available: bool = True

    class Config:
        from_attributes = True
        populate_by_name = True
