from fastapi import Request, HTTPException, status, Depends
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from typing import Optional
import firebase_admin.auth as firebase_auth
from firebase_init import init_firebase
from auth_utils import decode_access_token

security = HTTPBearer()

async def get_current_user(credentials: HTTPAuthorizationCredentials = Depends(security)) -> str:
    """
    Verify authentication token (Firebase OR JWT) and return user ID
    
    Supports both:
    - Firebase ID tokens (from OAuth login)
    - JWT access tokens (from email/password login)
    """
    token = credentials.credentials
    
    # Try JWT token first (email/password auth)
    jwt_payload = decode_access_token(token)
    if jwt_payload:
        user_id = jwt_payload.get("sub")
        if user_id:
            return user_id
    
    # Fall back to Firebase token (OAuth auth)
    try:
        init_firebase()
        decoded_token = firebase_auth.verify_id_token(token)
        user_id = decoded_token.get("uid")
        
        if not user_id:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid token"
            )
        
        return user_id
    
    except firebase_auth.InvalidIdTokenError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid authentication credentials"
        )
    except firebase_auth.ExpiredIdTokenError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token expired"
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid authentication credentials"
        )

