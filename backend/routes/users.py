from fastapi import APIRouter, HTTPException, status, Depends
from firebase_init import get_db
from middleware import get_current_user, security
from schemas import UserCreate, UserLogin, UserLoginResponse, User, ProfileUpdate, Profile
from datetime import datetime
from google.cloud.firestore_v1 import FieldFilter
import os
import requests

router = APIRouter(prefix="/users", tags=["users"])

# ==================== User Management ====================

@router.post("/register", response_model=User)
async def register_user(user_data: UserCreate):
    """
    Register a new user
    
    The Firebase mobile app handles authentication.
    This endpoint creates the user document in Firestore.
    """
    try:
        db = get_db()
        
        # Check if user already exists
        user_ref = db.collection("users").document(user_data.uid)
        existing_user = user_ref.get()
        
        if existing_user.exists:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="User already exists"
            )
        
        # Create user document
        user_doc = {
            "uid": user_data.uid,
            "email": user_data.email,
            "oauth_provider": user_data.oauth_provider,
            "is_verified": False,
            "is_active": True,
            "created_at": datetime.utcnow(),
            "updated_at": datetime.utcnow()
        }
        
        user_ref.set(user_doc)
        
        # Also create profile document
        profile_doc = {
            "userId": user_data.uid,
            "full_name": "",
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
        
        db.collection("profiles").document(user_data.uid).set(profile_doc)
        
        return User(
            uid=user_data.uid,
            email=user_data.email,
            oauth_provider=user_data.oauth_provider,
            is_verified=False,
            is_active=True,
            created_at=user_doc["created_at"],
            updated_at=user_doc["updated_at"]
        )

    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.post("/login", response_model=UserLoginResponse)
async def login_user(login_data: UserLogin):
    """Login user with Firebase email/password and get ID token"""
    api_key = os.getenv("FIREBASE_WEB_API_KEY")
    if not api_key:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="FIREBASE_WEB_API_KEY is not configured"
        )

    url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={api_key}"
    payload = {
        "email": login_data.email,
        "password": login_data.password,
        "returnSecureToken": True
    }

    try:
        resp = requests.post(url, json=payload)
        resp_data = resp.json()

        if resp.status_code != 200:
            error_msg = resp_data.get("error", {}).get("message", "Login failed")
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail=error_msg
            )

        return UserLoginResponse(
            idToken=resp_data["idToken"],
            refreshToken=resp_data["refreshToken"],
            expiresIn=resp_data["expiresIn"],
            localId=resp_data["localId"],
            email=resp_data["email"]
        )

    except requests.RequestException as e:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=str(e)
        )

@router.get("/me", response_model=User)
async def get_current_user_profile(credentials = Depends(security)):
    """Get current authenticated user's profile"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        user_doc = db.collection("users").document(user_id).get()
        
        if not user_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="User not found"
            )
        
        user_data = user_doc.to_dict()
        return User(**user_data)
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.get("/{user_id}", response_model=User)
async def get_user(user_id: str):
    """Get user by ID (public)"""
    try:
        db = get_db()
        user_doc = db.collection("users").document(user_id).get()
        
        if not user_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="User not found"
            )
        
        user_data = user_doc.to_dict()
        return User(**user_data)
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

# ==================== Profile Management ====================

@router.get("/{user_id}/profile", response_model=Profile)
async def get_user_profile(user_id: str):
    """Get user profile (public)"""
    try:
        db = get_db()
        profile_doc = db.collection("profiles").document(user_id).get()
        
        if not profile_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Profile not found"
            )
        
        profile_data = profile_doc.to_dict()
        return Profile(**profile_data)
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.put("/me/profile", response_model=Profile)
async def update_profile(
    profile_data: ProfileUpdate,
    credentials = Depends(security)
):
    """Update current user's profile"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        # Prepare update data (only non-None values)
        update_data = profile_data.model_dump(exclude_unset=True)
        
        if update_data:
            db.collection("profiles").document(user_id).update(update_data)
        
        # Return updated profile
        profile_doc = db.collection("profiles").document(user_id).get()
        return Profile(**profile_doc.to_dict())
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

# ==================== User Skills ====================

@router.put("/{user_id}/skills", response_model=list)
async def update_user_skills(
    user_id: str,
    skills_data: dict = None,
    credentials = Depends(security)
):
    """Update multiple skills for a user at once"""
    try:
        current_user_id = await get_current_user(credentials)
        
        # Users can only update their own skills
        if current_user_id != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Cannot update skills for another user"
            )
        
        db = get_db()
        
        # First, remove all existing skills for this user
        existing_skills = db.collection("userSkills").where(
            filter=FieldFilter("userId", "==", user_id)
        ).stream()
        
        for skill_doc in existing_skills:
            skill_doc.reference.delete()
        
        # Add new skills
        updated_skills = []
        
        if skills_data and "skills" in skills_data:
            for skill_item in skills_data["skills"]:
                skill_id = skill_item.get("skillId") if isinstance(skill_item, dict) else None
                proficiency = skill_item.get("proficiency_level", "beginner") if isinstance(skill_item, dict) else "beginner"
                
                if not skill_id:
                    continue
                
                # Verify skill exists
                skill_doc = db.collection("skills").document(skill_id).get()
                if not skill_doc.exists:
                    continue
                
                skill_data = skill_doc.to_dict()
                
                # Create userSkill entry
                user_skill_id = f"{user_id}_{skill_id}"
                db.collection("userSkills").document(user_skill_id).set({
                    "userId": user_id,
                    "skillId": skill_id,
                    "proficiency_level": proficiency,
                    "created_at": datetime.utcnow(),
                    "updated_at": datetime.utcnow()
                })
                
                updated_skills.append({
                    "skillId": skill_id,
                    "name": skill_data.get("name", ""),
                    "category": skill_data.get("category", ""),
                    "proficiency_level": proficiency
                })
        
        return updated_skills
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )
