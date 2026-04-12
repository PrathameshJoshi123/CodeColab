from fastapi import APIRouter, HTTPException, status, Depends
from firebase_init import get_db
from middleware import get_current_user, security
from schemas import MatchRequestCreate, MatchRequest
from datetime import datetime
import uuid
from google.cloud.firestore_v1 import FieldFilter

router = APIRouter(prefix="/matches", tags=["matches"])

# ==================== Match Request Management ====================

@router.post("/", response_model=MatchRequest)
async def create_match_request(
    request_data: MatchRequestCreate,
    credentials = Depends(security)
):
    """Create a new match request"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        request_id = str(uuid.uuid4())
        
        document_data = {
            "id": request_id,
            "userId": user_id,
            "session_type": request_data.session_type,
            "message": request_data.message,
            "required_skills": request_data.required_skills or [],
            "status": "pending",
            "created_at": datetime.utcnow()
        }
        
        db.collection("matchRequests").document(request_id).set(document_data)
        
        return MatchRequest(**document_data)
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.get("/browse", response_model=list[dict])
async def browse_match_requests(
    session_type: str = None,
    credentials = Depends(security)
):
    """Browse available match requests with user details and skills"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        # Query pending requests
        query = db.collection("matchRequests").where(
            filter=FieldFilter("status", "==", "pending")
        )
        
        if session_type:
            query = query.where(
                filter=FieldFilter("session_type", "==", session_type)
            )
        
        requests = []
        for doc in query.stream():
            request_data = doc.to_dict()
            # Exclude own requests
            if request_data.get("userId") != user_id:
                # Fetch requester's profile details
                requester_id = request_data.get("userId")
                user_doc = db.collection("users").document(requester_id).get()
                
                match_with_user = {
                    **request_data,
                    "user": user_doc.to_dict() if user_doc.exists else {}
                }
                
                # Fetch user skills if available
                skills_docs = db.collection("users").document(requester_id).collection("skills").stream()
                user_skills = []
                for skill_doc in skills_docs:
                    skill_data = skill_doc.to_dict()
                    user_skills.append({
                        "id": skill_doc.id,
                        **skill_data
                    })
                match_with_user["user_skills"] = user_skills
                
                requests.append(match_with_user)
        
        return requests
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.get("/user/my-requests", response_model=list[MatchRequest])
async def get_user_match_requests(credentials = Depends(security)):
    """Get current user's match requests"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        requests = []
        for doc in db.collection("matchRequests").where(
            filter=FieldFilter("userId", "==", user_id)
        ).stream():
            requests.append(MatchRequest(**doc.to_dict()))
        
        return requests
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.put("/{request_id}/accept", response_model=MatchRequest)
async def accept_match_request(
    request_id: str,
    credentials = Depends(security)
):
    """Accept a match request and create sprint session"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        # Get the match request
        match_doc = db.collection("matchRequests").document(request_id).get()
        if not match_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Match request not found"
            )
        
        match_data = match_doc.to_dict()
        requester_id = match_data.get("userId")
        
        # Update request status
        db.collection("matchRequests").document(request_id).update({
            "status": "accepted",
            "accepted_by": user_id,
            "accepted_at": datetime.utcnow()
        })
        
        # Optional: Auto-create sprint session
        # This can be extended based on your requirements
        
        updated_doc = db.collection("matchRequests").document(request_id).get()
        return MatchRequest(**updated_doc.to_dict())
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.put("/{request_id}/reject", response_model=MatchRequest)
async def reject_match_request(
    request_id: str,
    credentials = Depends(security)
):
    """Reject a match request"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        # Get the match request
        match_doc = db.collection("matchRequests").document(request_id).get()
        if not match_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Match request not found"
            )
        
        # Update request status
        db.collection("matchRequests").document(request_id).update({
            "status": "rejected",
            "rejected_by": user_id,
            "rejected_at": datetime.utcnow()
        })
        
        updated_doc = db.collection("matchRequests").document(request_id).get()
        return MatchRequest(**updated_doc.to_dict())
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.delete("/{request_id}")
async def cancel_match_request(
    request_id: str,
    credentials = Depends(security)
):
    """Cancel a match request"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        # Check ownership
        match_doc = db.collection("matchRequests").document(request_id).get()
        if not match_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Match request not found"
            )
        
        if match_doc.to_dict().get("userId") != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Can only delete own requests"
            )
        
        db.collection("matchRequests").document(request_id).delete()
        
        return {"detail": "Match request deleted"}
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )
