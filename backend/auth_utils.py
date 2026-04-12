"""Password hashing and token utilities for standard email/password auth"""

from passlib.context import CryptContext
from datetime import datetime, timedelta
import jwt
from config import settings
from google.auth.transport import requests
from google.oauth2 import id_token

# Password hashing context
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

def hash_password(password: str) -> str:
    """Hash a password using bcrypt"""
    return pwd_context.hash(password)

def verify_password(plain_password: str, hashed_password: str) -> bool:
    """Verify a plain password against its hash"""
    return pwd_context.verify(plain_password, hashed_password)

def create_access_token(data: dict, expires_delta: timedelta | None = None) -> str:
    """Create a JWT access token"""
    to_encode = data.copy()
    
    if expires_delta:
        expire = datetime.utcnow() + expires_delta
    else:
        expire = datetime.utcnow() + timedelta(hours=24)
    
    to_encode.update({"exp": expire})
    encoded_jwt = jwt.encode(
        to_encode, 
        settings.JWT_SECRET, 
        algorithm=settings.JWT_ALGORITHM
    )
    return encoded_jwt

def decode_access_token(token: str) -> dict | None:
    """Decode and validate JWT access token"""
    try:
        payload = jwt.decode(
            token, 
            settings.JWT_SECRET, 
            algorithms=[settings.JWT_ALGORITHM]
        )
        return payload
    except jwt.ExpiredSignatureError:
        return None
    except jwt.InvalidTokenError:
        return None


# ==================== Google OAuth ====================

def verify_google_token(id_token_str: str) -> dict | None:
    """
    Verify Google ID token from mobile client
    
    Returns decoded token data with user info if valid
    Returns None if token is invalid or expired
    """
    try:
        # Get the public certificates from Google
        request_object = requests.Request()
        
        # Verify the token signature using Google's public keys
        # Note: For production, specify the expected client ID for extra security
        id_info = id_token.verify_oauth2_token(
            id_token_str, 
            request_object
        )
        
        # Verify token hasn't expired (additional check)
        if id_info.get("exp", 0) < datetime.utcnow().timestamp():
            return None
        
        return id_info
    
    except Exception as e:
        print(f"Google token verification failed: {str(e)}")
        return None
