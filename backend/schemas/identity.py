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
    avatar_url: Optional[str] = None
    expertise_areas: Optional[list[str]] = None
    location: Optional[str] = None
    linkedin_url: Optional[str] = None
    github_url: Optional[str] = None
    website_url: Optional[str] = None


class Profile(BaseModel):
    """User profile model"""
    user_id: str
    full_name: Optional[str] = None
    bio: Optional[str] = None
    avatar_url: Optional[str] = None
    expertise_areas: Optional[list[str]] = None
    location: Optional[str] = None
    linkedin_url: Optional[str] = None
    github_url: Optional[str] = None
    website_url: Optional[str] = None
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True
