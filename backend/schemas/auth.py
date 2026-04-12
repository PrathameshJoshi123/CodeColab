from pydantic import BaseModel, EmailStr
from datetime import datetime

# ==================== Auth Models - Email/Password ====================

class SignUpRequest(BaseModel):
    """User signup with email and password"""
    email: str
    password: str
    full_name: str | None = None


class LoginRequest(BaseModel):
    """User login with email and password"""
    email: str
    password: str


# ==================== Auth Models - Google OAuth ====================

class GoogleOAuthRequest(BaseModel):
    """Google OAuth login/signup with ID token"""
    id_token: str
    full_name: str | None = None


class GoogleOAuthResponse(BaseModel):
    """Response after Google OAuth authentication"""
    user_id: str
    email: str
    full_name: str | None = None
    is_new_user: bool
    created_at: datetime
    message: str


# ==================== Token & Response Models ====================

class TokenResponse(BaseModel):
    """Authentication token response"""
    access_token: str
    token_type: str
    expires_in: int
    user_id: str
    email: str


class SignUpResponse(BaseModel):
    """Signup response with user data"""
    user_id: str
    email: str
    full_name: str | None = None
    created_at: datetime
    message: str
