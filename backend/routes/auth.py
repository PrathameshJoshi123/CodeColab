"""Authentication routes for email/password and Google OAuth signup/login"""

from fastapi import APIRouter, HTTPException, status
from firebase_init import get_db
from datetime import datetime, timedelta
from schemas import SignUpRequest, LoginRequest, TokenResponse, SignUpResponse, GoogleOAuthRequest, GoogleOAuthResponse
from auth_utils import hash_password, verify_password, create_access_token, verify_google_token
from google.cloud.firestore_v1 import FieldFilter

router = APIRouter(prefix="/auth", tags=["auth"])

# ==================== Auth Endpoints ====================

@router.post("/signup", response_model=SignUpResponse)
async def signup(signup_data: SignUpRequest):
    """
    Register a new user with email and password
    
    Creates a new user document in Firestore with hashed password
    """
    try:
        db = get_db()
        
        # Check if user already exists by email
        users_ref = db.collection("users")
        existing_users = users_ref.where(
            filter=FieldFilter("email", "==", signup_data.email)
        ).stream()
        
        if list(existing_users):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Email already registered"
            )
        
        # Generate a simple user ID (can use Firebase auto ID or email-based)
        user_id = db.collection("users").document().id
        
        # Create user document with hashed password
        now = datetime.utcnow()
        user_doc = {
            "uid": user_id,  # Use 'uid' for consistency with User schema
            "email": signup_data.email,
            "full_name": signup_data.full_name or "",
            "password_hash": hash_password(signup_data.password),
            "oauth_provider": "email",  # Mark as email auth, not OAuth
            "auth_type": "email",
            "is_verified": False,
            "is_active": True,
            "created_at": now,
            "updated_at": now
        }
        
        db.collection("users").document(user_id).set(user_doc)
        
        # Also create profile document with defaults
        profile_doc = {
            "userId": user_id,
            "full_name": signup_data.full_name or "",
            "bio": "",
            "college": "",
            "city": "",
            "github_username": "",
            "linkedin_url": "",
            "profile_image_url": "",
            "karma_score": 0,
            "xp_points": 0,
            "level": 1,
            "streak_count": 0,
            "is_available": True
        }
        
        db.collection("profiles").document(user_id).set(profile_doc)
        
        return SignUpResponse(
            user_id=user_id,
            email=signup_data.email,
            full_name=signup_data.full_name,
            created_at=now,
            message="User registered successfully. You can now login."
        )
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Signup failed: {str(e)}"
        )


@router.post("/login", response_model=TokenResponse)
async def login(login_data: LoginRequest):
    """
    Login user with email and password
    
    Returns JWT access token for authenticated API calls
    """
    try:
        db = get_db()
        
        # Find user by email
        users_ref = db.collection("users")
        existing_users = list(users_ref.where(
            filter=FieldFilter("email", "==", login_data.email)
        ).stream())
        
        if not existing_users:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid email or password"
            )
        
        user_doc = existing_users[0]
        user_data = user_doc.to_dict()
        user_id = user_doc.id
        
        # Verify password
        if not verify_password(login_data.password, user_data.get("password_hash", "")):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid email or password"
            )
        
        # Create access token
        expires_delta = timedelta(hours=24)
        access_token = create_access_token(
            data={"sub": user_id, "email": login_data.email},
            expires_delta=expires_delta
        )
        
        return TokenResponse(
            access_token=access_token,
            token_type="bearer",
            expires_in=int(expires_delta.total_seconds()),
            user_id=user_id,
            email=login_data.email
        )
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Login failed: {str(e)}"
        )


@router.post("/google", response_model=TokenResponse)
async def google_oauth(oauth_data: GoogleOAuthRequest):
    """
    Google OAuth login/signup
    
    Verifies Google ID token from mobile client and returns JWT token
    If user doesn't exist, creates new user account with OAuth provider
    """
    try:
        db = get_db()
        
        # Verify Google ID token and extract user info
        google_user_info = verify_google_token(oauth_data.id_token)
        
        if not google_user_info:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid or expired Google token"
            )
        
        google_email = google_user_info.get("email")
        google_id = google_user_info.get("sub")
        full_name = google_user_info.get("name", oauth_data.full_name or "")
        
        # Find user by email
        users_ref = db.collection("users")
        existing_users = list(users_ref.where(
            filter=FieldFilter("email", "==", google_email)
        ).stream())
        
        is_new_user = False
        now = datetime.utcnow()
        
        if existing_users:
            # User exists - update if needed
            user_doc = existing_users[0]
            user_id = user_doc.id
            user_data = user_doc.to_dict()
        else:
            # Create new user for Google OAuth
            is_new_user = True
            user_id = db.collection("users").document().id
            
            user_doc_data = {
                "uid": user_id,
                "email": google_email,
                "full_name": full_name,
                "oauth_provider": "google",  # Mark as Google OAuth
                "oauth_id": google_id,  # Store Google ID for reference
                "auth_type": "oauth",
                "is_verified": True,  # Google verified
                "is_active": True,
                "created_at": now,
                "updated_at": now
            }
            
            db.collection("users").document(user_id).set(user_doc_data)
            
            # Also create profile document with defaults
            profile_doc = {
                "userId": user_id,
                "full_name": full_name,
                "bio": "",
                "college": "",
                "city": "",
                "github_username": "",
                "linkedin_url": "",
                "profile_image_url": google_user_info.get("picture", ""),
                "karma_score": 0,
                "xp_points": 0,
                "level": 1,
                "streak_count": 0,
                "is_available": True
            }
            
            db.collection("profiles").document(user_id).set(profile_doc)
        
        # Create JWT access token
        expires_delta = timedelta(hours=24)
        access_token = create_access_token(
            data={"sub": user_id, "email": google_email},
            expires_delta=expires_delta
        )
        
        return TokenResponse(
            access_token=access_token,
            token_type="bearer",
            expires_in=int(expires_delta.total_seconds()),
            user_id=user_id,
            email=google_email
        )
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Google OAuth failed: {str(e)}"
        )
