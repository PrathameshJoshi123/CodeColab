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

@router.get("/me/profile", response_model=Profile)
async def get_current_user_profile(credentials = Depends(security)):
    """Get current authenticated user's profile"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        profile_doc = db.collection("profiles").document(user_id).get()
        
        if not profile_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Profile not found"
            )
        
        profile_dict = profile_doc.to_dict()
        
        # Ensure all required fields exist with defaults
        if "userId" not in profile_dict:
            profile_dict["userId"] = user_id
        if "full_name" not in profile_dict:
            profile_dict["full_name"] = ""
        if "bio" not in profile_dict:
            profile_dict["bio"] = ""
        if "college" not in profile_dict:
            profile_dict["college"] = ""
        if "city" not in profile_dict:
            profile_dict["city"] = ""
        if "github_username" not in profile_dict:
            profile_dict["github_username"] = ""
        if "linkedin_url" not in profile_dict:
            profile_dict["linkedin_url"] = ""
        if "profile_image_url" not in profile_dict:
            profile_dict["profile_image_url"] = ""
        if "karma_score" not in profile_dict:
            profile_dict["karma_score"] = 0
        if "xp_points" not in profile_dict:
            profile_dict["xp_points"] = 0
        if "level" not in profile_dict:
            profile_dict["level"] = 1
        if "streak_count" not in profile_dict:
            profile_dict["streak_count"] = 0
        if "is_available" not in profile_dict:
            profile_dict["is_available"] = True
        
        return Profile(**profile_dict)
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

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
        
        profile_dict = profile_doc.to_dict()
        
        # Ensure all required fields exist with defaults
        if "userId" not in profile_dict:
            profile_dict["userId"] = user_id
        if "full_name" not in profile_dict:
            profile_dict["full_name"] = ""
        if "bio" not in profile_dict:
            profile_dict["bio"] = ""
        if "college" not in profile_dict:
            profile_dict["college"] = ""
        if "city" not in profile_dict:
            profile_dict["city"] = ""
        if "github_username" not in profile_dict:
            profile_dict["github_username"] = ""
        if "linkedin_url" not in profile_dict:
            profile_dict["linkedin_url"] = ""
        if "profile_image_url" not in profile_dict:
            profile_dict["profile_image_url"] = ""
        if "karma_score" not in profile_dict:
            profile_dict["karma_score"] = 0
        if "xp_points" not in profile_dict:
            profile_dict["xp_points"] = 0
        if "level" not in profile_dict:
            profile_dict["level"] = 1
        if "streak_count" not in profile_dict:
            profile_dict["streak_count"] = 0
        if "is_available" not in profile_dict:
            profile_dict["is_available"] = True
        
        return Profile(**profile_dict)
    
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
        update_data["updated_at"] = datetime.utcnow()
        
        if update_data:
            # Use set with merge=True to create if not exists, or update if exists
            db.collection("profiles").document(user_id).set(update_data, merge=True)
        
        # Return updated profile with default values for missing fields
        profile_doc = db.collection("profiles").document(user_id).get()
        profile_dict = profile_doc.to_dict()
        
        # Ensure all required fields exist with defaults
        if "userId" not in profile_dict:
            profile_dict["userId"] = user_id
        if "full_name" not in profile_dict:
            profile_dict["full_name"] = ""
        if "bio" not in profile_dict:
            profile_dict["bio"] = ""
        if "college" not in profile_dict:
            profile_dict["college"] = ""
        if "city" not in profile_dict:
            profile_dict["city"] = ""
        if "github_username" not in profile_dict:
            profile_dict["github_username"] = ""
        if "linkedin_url" not in profile_dict:
            profile_dict["linkedin_url"] = ""
        if "profile_image_url" not in profile_dict:
            profile_dict["profile_image_url"] = ""
        if "karma_score" not in profile_dict:
            profile_dict["karma_score"] = 0
        if "xp_points" not in profile_dict:
            profile_dict["xp_points"] = 0
        if "level" not in profile_dict:
            profile_dict["level"] = 1
        if "streak_count" not in profile_dict:
            profile_dict["streak_count"] = 0
        if "is_available" not in profile_dict:
            profile_dict["is_available"] = True
        
        return Profile(**profile_dict)
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.get("/me/profile/status", response_model=dict)
async def get_profile_completion_status(credentials = Depends(security)):
    """
    Check if current user's profile is complete
    Returns status indicating which fields are missing
    """
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        profile_doc = db.collection("profiles").document(user_id).get()
        
        if not profile_doc.exists:
            return {
                "is_complete": False,
                "message": "Profile not found",
                "missing_fields": ["all"]
            }
        
        profile_data = profile_doc.to_dict()
        
        # Define required fields for a complete profile
        required_fields = ["full_name", "city", "college", "github_username"]
        missing_fields = []
        
        for field in required_fields:
            value = profile_data.get(field, "")
            if not value or str(value).strip() == "":
                missing_fields.append(field)
        
        is_complete = len(missing_fields) == 0
        
        return {
            "is_complete": is_complete,
            "missing_fields": missing_fields if missing_fields else [],
            "message": "Profile complete" if is_complete else f"Missing {len(missing_fields)} fields"
        }
    
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

# ==================== FCM Notifications ====================

@router.post("/me/fcm-token")
async def register_fcm_token(
    token_data: dict,
    credentials = Depends(security)
):
    """Register FCM token for current user to receive push notifications"""
    try:
        user_id = await get_current_user(credentials)
        fcm_token = token_data.get("fcm_token")
        
        if not fcm_token:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="FCM token is required"
            )
        
        db = get_db()
        
        # Update user document with FCM token
        db.collection("users").document(user_id).update({
            "fcm_token": fcm_token,
            "fcm_updated_at": datetime.utcnow()
        })
        
        print(f"\n{'='*60}")
        print(f"✅ FCM Token registered for user: {user_id}")
        print(f"   Token: {fcm_token[:30]}...")
        print(f"{'='*60}\n")
        
        return {
            "success": True,
            "message": "FCM token registered successfully"
        }
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )


# ==================== Test Endpoints ====================

@router.post("/test/send-notification")
async def test_send_notification(
    notification_data: dict,
    credentials = Depends(security)
):
    """
    TEST ENDPOINT: Send a test notification to verify FCM setup
    
    Usage:
    POST /users/test/send-notification
    {
        "user_id": "target_user_id",
        "title": "Test Title",
        "body": "Test Message"
    }
    """
    try:
        from services.fcm_service import FCMService
        
        current_user = await get_current_user(credentials)
        target_user_id = notification_data.get("user_id")
        title = notification_data.get("title", "Test Notification")
        body = notification_data.get("body", "This is a test notification")
        
        print(f"\n{'='*60}")
        print(f"🧪 TEST NOTIFICATION REQUEST")
        print(f"   From: {current_user}")
        print(f"   To: {target_user_id}")
        print(f"   Title: {title}")
        print(f"   Body: {body}")
        print(f"{'='*60}")
        
        db = get_db()
        
        # Verify target user exists and has FCM token
        target_user_doc = db.collection("users").document(target_user_id).get()
        if not target_user_doc.exists:
            print(f"   ❌ User {target_user_id} not found")
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"User {target_user_id} not found"
            )
        
        target_user_data = target_user_doc.to_dict()
        fcm_token = target_user_data.get("fcm_token")
        
        if not fcm_token:
            print(f"   ⚠️  User {target_user_id} has NO FCM token registered!")
            print(f"   Available fields: {list(target_user_data.keys())}")
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"User {target_user_id} has no FCM token"
            )
        
        print(f"   ✅ User found with token: {fcm_token[:30]}...")
        
        # Send test notification manually using FCMService
        from firebase_admin import messaging
        
        message = messaging.Message(
            notification=messaging.Notification(
                title=title,
                body=body
            ),
            data={
                "type": "TEST",
                "timestamp": datetime.utcnow().isoformat()
            },
            token=fcm_token
        )
        
        print(f"   📤 Sending message via FCM...")
        response = messaging.send(message)
        
        print(f"   ✅ Message sent successfully!")
        print(f"   Message ID: {response}")
        print(f"{'='*60}\n")
        
        return {
            "success": True,
            "message": "Test notification sent successfully",
            "message_id": response,
            "user_id": target_user_id,
            "fcm_token": fcm_token[:30] + "..."
        }
        
    except HTTPException:
        raise
    except Exception as e:
        print(f"   ❌ ERROR: {str(e)}")
        print(f"{'='*60}\n")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )


@router.get("/test/fcm-token-status")
async def check_fcm_tokens(credentials = Depends(security)):
    """
    TEST ENDPOINT: Check which users have FCM tokens registered
    Useful for debugging why notifications aren't being sent
    """
    try:
        current_user = await get_current_user(credentials)
        db = get_db()
        
        print(f"\n{'='*60}")
        print(f"🔍 FCM Token Status Check")
        print(f"   Requested by: {current_user}")
        print(f"{'='*60}")
        
        # Get all users and check their FCM tokens
        users_with_tokens = []
        users_without_tokens = []
        total_users = 0
        
        for user_doc in db.collection("users").stream():
            total_users += 1
            user_data = user_doc.to_dict()
            user_id = user_doc.id
            
            fcm_token = user_data.get("fcm_token")
            email = user_data.get("email", "N/A")
            full_name = user_data.get("full_name", "N/A")
            
            if fcm_token:
                users_with_tokens.append({
                    "user_id": user_id,
                    "email": email,
                    "full_name": full_name,
                    "fcm_token": fcm_token[:40] + "..." if len(fcm_token) > 40 else fcm_token,
                    "token_length": len(fcm_token),
                    "fcm_updated_at": user_data.get("fcm_updated_at", "N/A")
                })
                print(f"   ✅ {user_id[:20]}... -> Token registered")
            else:
                users_without_tokens.append({
                    "user_id": user_id,
                    "email": email,
                    "full_name": full_name
                })
                print(f"   ❌ {user_id[:20]}... -> NO TOKEN")
        
        print(f"\n📊 Summary:")
        print(f"   Total Users: {total_users}")
        print(f"   Users with FCM tokens: {len(users_with_tokens)}")
        print(f"   Users WITHOUT FCM tokens: {len(users_without_tokens)}")
        print(f"{'='*60}\n")
        
        return {
            "summary": {
                "total_users": total_users,
                "users_with_tokens": len(users_with_tokens),
                "users_without_tokens": len(users_without_tokens)
            },
            "users_with_tokens": users_with_tokens,
            "users_without_tokens": users_without_tokens
        }
        
    except Exception as e:
        print(f"   ❌ ERROR: {str(e)}")
        print(f"{'='*60}\n")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )
