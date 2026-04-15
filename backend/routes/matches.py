from fastapi import APIRouter, HTTPException, status, Depends
from firebase_init import get_db
from middleware import get_current_user, security
from schemas import MatchRequestCreate, MatchRequest
from services.fcm_service import send_match_accepted_notification, send_match_rejected_notification, send_match_created_notification
from datetime import datetime
import uuid
from google.cloud.firestore_v1 import FieldFilter

router = APIRouter(prefix="/matches", tags=["matches"])


# ==================== Helper Functions ====================

def _to_sort_timestamp(value) -> float:
    if isinstance(value, datetime):
        return value.timestamp()

    if not value:
        return 0.0

    try:
        return datetime.fromisoformat(str(value).replace("Z", "+00:00")).timestamp()
    except ValueError:
        return 0.0

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
            "scheduled_date_time": request_data.scheduled_date_time,
            "accepted_by": None,
            "accepted_at": None,
            "linked_sprint_id": None,
            "sprint_status": None,
            "is_exhausted": False,
            "created_at": datetime.utcnow()
        }
        
        db.collection("matchRequests").document(request_id).set(document_data)
        
        # Get requester's name for notification
        requester_doc = db.collection("users").document(user_id).get()
        requester_name = "Someone"
        if requester_doc.exists:
            requester_data = requester_doc.to_dict()
            requester_name = requester_data.get("full_name", requester_data.get("email", "Someone"))
        
        # Send FCM notifications to users with required skills
        send_match_created_notification(request_id, document_data, requester_name)
        
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
                
                # Fetch user skills from userSkills collection
                user_skills = []
                skills_query = db.collection("userSkills").where(
                    filter=FieldFilter("userId", "==", requester_id)
                ).stream()
                
                for skill_doc in skills_query:
                    skill_data = skill_doc.to_dict()
                    skill_id = skill_data.get("skillId")
                    
                    # Fetch skill details from skills collection
                    skill_ref = db.collection("skills").document(skill_id).get()
                    if skill_ref.exists:
                        skill_info = skill_ref.to_dict()
                        user_skills.append({
                            "id": skill_id,
                            "name": skill_info.get("name"),
                            "proficiency_level": skill_data.get("proficiency_level"),
                            "years_of_experience": skill_data.get("years_of_experience")
                        })
                
                match_with_user["user_skills"] = user_skills
                
                requests.append(match_with_user)

        requests.sort(
            key=lambda match_item: _to_sort_timestamp(match_item.get("created_at")),
            reverse=True,
        )
        
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
        
        request_rows = []
        for doc in db.collection("matchRequests").where(
            filter=FieldFilter("userId", "==", user_id)
        ).stream():
            request_data = doc.to_dict()
            request_data["can_setup_sprint"] = (
                request_data.get("status") == "accepted"
                and not request_data.get("linked_sprint_id")
                and not request_data.get("is_exhausted", False)
            )
            request_rows.append(request_data)

        request_rows.sort(
            key=lambda request_item: _to_sort_timestamp(request_item.get("created_at")),
            reverse=True,
        )

        requests = [MatchRequest(**request_item) for request_item in request_rows]
        
        return requests
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.get("/user/received", response_model=list[dict])
async def get_received_match_requests(credentials = Depends(security)):
    """Get match requests received/accepted by current user (matches they accepted from others)"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        # Get all match requests accepted by current user
        received = []
        
        # Collect all requester IDs first
        requester_ids = []
        match_docs = list(db.collection("matchRequests").where(
            filter=FieldFilter("accepted_by", "==", user_id)
        ).stream())

        relevant_match_rows = []
        
        for doc in match_docs:
            request_data = doc.to_dict()
            if request_data.get("status") not in ["accepted", "exhausted"]:
                continue

            relevant_match_rows.append(request_data)
            requester_ids.append(request_data.get("userId"))
        
        # Batch fetch all user documents
        user_cache = {}
        for requester_id in set(requester_ids):
            user_doc = db.collection("users").document(requester_id).get()
            user_cache[requester_id] = user_doc.to_dict() if user_doc.exists else {}
        
        # Batch fetch all user skills for all requesters
        user_skills_cache = {}
        for requester_id in set(requester_ids):
            user_skills = []
            skills_query = db.collection("userSkills").where(
                filter=FieldFilter("userId", "==", requester_id)
            ).stream()
            
            # Collect skill IDs to fetch skill details in batch
            skill_ids = []
            skill_docs = list(skills_query)
            for skill_doc in skill_docs:
                skill_ids.append(skill_doc.to_dict().get("skillId"))
            
            # Fetch all skill details
            skill_details_cache = {}
            for skill_id in set(skill_ids):
                skill_ref = db.collection("skills").document(skill_id).get()
                if skill_ref.exists:
                    skill_details_cache[skill_id] = skill_ref.to_dict()
            
            # Build user skills with cached skill details
            for skill_doc in skill_docs:
                skill_data = skill_doc.to_dict()
                skill_id = skill_data.get("skillId")
                if skill_id in skill_details_cache:
                    skill_info = skill_details_cache[skill_id]
                    user_skills.append({
                        "id": skill_id,
                        "name": skill_info.get("name"),
                        "proficiency_level": skill_data.get("proficiency_level"),
                        "years_of_experience": skill_data.get("years_of_experience")
                    })
            
            user_skills_cache[requester_id] = user_skills
        
        # Build response using cached data
        for request_data in relevant_match_rows:
            requester_id = request_data.get("userId")
            
            match_with_user = {
                **request_data,
                "user": user_cache.get(requester_id, {}),
                "user_skills": user_skills_cache.get(requester_id, [])
            }
            
            received.append(match_with_user)

        received.sort(
            key=lambda match_item: _to_sort_timestamp(
                match_item.get("accepted_at") or match_item.get("created_at")
            ),
            reverse=True,
        )
        
        return received
    
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
    """Accept a match request"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        print(f"\n{'='*60}")
        print(f"📥 Match Accept Request: {request_id}")
        print(f"   Accepter: {user_id}")
        print(f"{'='*60}")
        
        # Get the match request
        match_doc = db.collection("matchRequests").document(request_id).get()
        if not match_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Match request not found"
            )
        
        match_data = match_doc.to_dict()
        requester_id = match_data.get("userId")

        if requester_id == user_id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Cannot accept your own match request"
            )

        if match_data.get("status") != "pending":
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Only pending match requests can be accepted"
            )
        
        print(f"   Requester: {requester_id}")
        print(f"   Match Status (before): {match_data.get('status')}")
        
        # Update request status
        db.collection("matchRequests").document(request_id).update({
            "status": "accepted",
            "accepted_by": user_id,
            "accepted_at": datetime.utcnow(),
            "is_exhausted": False
        })
        
        # Get accepter's name for notification
        accepter_doc = db.collection("users").document(user_id).get()
        accepter_name = "Someone"  # Default fallback
        if accepter_doc.exists:
            accepter_data = accepter_doc.to_dict()
            accepter_name = accepter_data.get("full_name", accepter_data.get("email", "Someone"))
        
        print(f"   Accepter Name: {accepter_name}")
        print(f"   Sending notification...")
        
        # Send FCM notification to requester
        notification_result = send_match_accepted_notification(requester_id, accepter_name, request_id)
        
        if notification_result:
            print(f"   ✅ Notification sent successfully!")
        else:
            print(f"   ❌ Notification failed to send!")
        
        print(f"{'='*60}\n")
        
        # Optional: Auto-create sprint session
        # This can be extended based on your requirements
        
        updated_doc = db.collection("matchRequests").document(request_id).get()
        return MatchRequest(**updated_doc.to_dict())
    
    except HTTPException:
        raise
    except Exception as e:
        print(f"   ❌ ERROR: {str(e)}")
        print(f"{'='*60}\n")
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
        
        match_data = match_doc.to_dict()
        requester_id = match_data.get("userId")
        
        # Update request status
        db.collection("matchRequests").document(request_id).update({
            "status": "rejected",
            "rejected_by": user_id,
            "rejected_at": datetime.utcnow()
        })
        
        # Get rejecter's name for notification
        rejecter_doc = db.collection("users").document(user_id).get()
        rejecter_name = "Someone"  # Default fallback
        if rejecter_doc.exists:
            rejecter_data = rejecter_doc.to_dict()
            rejecter_name = rejecter_data.get("full_name", rejecter_data.get("email", "Someone"))
        
        # Send FCM notification to requester
        send_match_rejected_notification(requester_id, rejecter_name, request_id)
        
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

@router.get("/{match_id}/details", response_model=dict)
async def get_match_details(
    match_id: str,
    credentials = Depends(security)
):
    """
    Get match request details with partner information
    Used for sprint setup to fetch partner name and FCM token
    """
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        # Get the match request
        match_doc = db.collection("matchRequests").document(match_id).get()
        if not match_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Match request not found"
            )
        
        match_data = match_doc.to_dict()
        
        # Determine who the partner is based on who accepted
        requester_id = match_data.get("userId")
        accepter_id = match_data.get("accepted_by")

        if user_id not in [requester_id, accepter_id]:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Not allowed to access this match"
            )
        
        # Partner is the accepter (user B)
        if accepter_id and accepter_id != user_id:
            partner_id = accepter_id
        elif requester_id and requester_id != user_id:
            partner_id = requester_id
        else:
            partner_id = None
        
        # Fetch partner details if available
        partner_info = {}
        if partner_id:
            partner_doc = db.collection("users").document(partner_id).get()
            if partner_doc.exists:
                partner_data = partner_doc.to_dict()
                partner_info = {
                    "partner_uid": partner_id,
                    "partner_name": partner_data.get("full_name", partner_data.get("email", "Partner")),
                    "partner_email": partner_data.get("email"),
                    "partner_profile_image": partner_data.get("profile_image_url")
                }
                
                # Fetch partner's FCM token
                fcm_token = partner_data.get("fcm_token")
                if fcm_token:
                    partner_info["partner_fcm_token"] = fcm_token
        
        # Return combined match and partner info
        return {
            "match_id": match_id,
            "status": match_data.get("status"),
            "session_type": match_data.get("session_type"),
            "message": match_data.get("message"),
            "accepted_at": match_data.get("accepted_at"),
            "linked_sprint_id": match_data.get("linked_sprint_id"),
            "sprint_status": match_data.get("sprint_status"),
            "is_exhausted": match_data.get("is_exhausted", False),
            **partner_info
        }
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )
