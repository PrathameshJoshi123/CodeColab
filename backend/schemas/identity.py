from pydantic import BaseModel
from datetime import datetime

# ==================== User Models ====================

class UserCreate(BaseModel):
    """User registration data"""
    email: str
    uid: str  # Firebase UID
    oauth_provider: str

class UserLogin(BaseModel):
    """Firebase email/password login data"""
    email: str
    password: str

class UserLoginResponse(BaseModel):
    """Firebase login response payload"""
    idToken: str
    refreshToken: str
    expiresIn: str
    localId: str
    email: str

class User(BaseModel):
    """User response model"""
    uid: str
    email: str
    oauth_provider: str
    is_verified: bool
    is_active: bool
    created_at: datetime
    updated_at: datetime

# ==================== Profile Models ====================

class ProfileUpdate(BaseModel):
    """Profile update data"""
    full_name: str | None = None
    bio: str | None = None
    college: str | None = None
    city: str | None = None
    github_username: str | None = None
    linkedin_url: str | None = None
    profile_image_url: str | None = None
    is_available: bool | None = None

class Profile(BaseModel):
    """User profile response"""
    userId: str
    full_name: str
    bio: str
    college: str
    city: str
    github_username: str
    linkedin_url: str
    profile_image_url: str
    karma_score: int
    xp_points: int
    level: int
    streak_count: int
    is_available: bool
