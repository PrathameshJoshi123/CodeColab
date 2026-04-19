from fastapi import APIRouter, HTTPException, status, Depends
from firebase_init import get_db
from middleware import get_current_user, security
from schemas import MatchRequestCreate, MatchRequest
from services.fcm_service import (
    send_match_rejected_notification,
    send_match_created_notification,
    send_match_selected_notification,
    send_match_not_selected_notification,
)
from datetime import datetime
import uuid
from google.cloud.firestore_v1 import FieldFilter, Query

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


def _get_user_display_name(db, user_id: str) -> str:
    user_doc = db.collection("users").document(user_id).get()
    profile_doc = db.collection("profiles").document(user_id).get()

    user_data = user_doc.to_dict() if user_doc.exists else {}
    profile_data = profile_doc.to_dict() if profile_doc.exists else {}

    full_name = (
        user_data.get("full_name")
        or user_data.get("name")
        or profile_data.get("full_name")
    )
    if full_name:
        return full_name

    email = user_data.get("email")
    if email and "@" in email:
        return email.split("@")[0]

    return "Someone"


def _normalize_join_requests(match_data: dict) -> list[dict]:
    join_requests = match_data.get("join_requests") or []
    normalized = []

    for entry in join_requests:
        if not isinstance(entry, dict):
            continue

        user_id = entry.get("user_id") or entry.get("userId")
        if not user_id:
            continue

        normalized.append({
            "user_id": user_id,
            "accepted_at": entry.get("accepted_at") or entry.get("acceptedAt"),
            "selection_status": entry.get("selection_status") or "pending",
            "selected_at": entry.get("selected_at"),
            "rejected_at": entry.get("rejected_at"),
        })

    existing_ids = {entry.get("user_id") for entry in normalized}
    for legacy_user_id in match_data.get("interested_user_ids") or []:
        if legacy_user_id and legacy_user_id not in existing_ids:
            normalized.append({
                "user_id": legacy_user_id,
                "accepted_at": None,
                "selection_status": "pending",
                "selected_at": None,
                "rejected_at": None,
            })

    return normalized


def _get_user_skills(db, user_id: str) -> list[dict]:
    user_skills = []

    for skill_doc in db.collection("userSkills").where(
        filter=FieldFilter("userId", "==", user_id)
    ).stream():
        skill_data = skill_doc.to_dict()
        skill_id = skill_data.get("skillId")
        if not skill_id:
            continue

        skill_ref = db.collection("skills").document(skill_id).get()
        if not skill_ref.exists:
            continue

        skill_info = skill_ref.to_dict()
        user_skills.append({
            "id": skill_id,
            "name": skill_info.get("name", ""),
            "proficiency_level": skill_data.get("proficiency_level", "beginner"),
            "years_of_experience": skill_data.get("years_of_experience"),
        })

    return user_skills


def _get_user_match_profile(db, user_id: str) -> dict:
    user_doc = db.collection("users").document(user_id).get()
    profile_doc = db.collection("profiles").document(user_id).get()

    user_data = user_doc.to_dict() if user_doc.exists else {}
    profile_data = profile_doc.to_dict() if profile_doc.exists else {}

    email = user_data.get("email", "")
    full_name = (
        user_data.get("full_name")
        or user_data.get("name")
        or profile_data.get("full_name")
        or (email.split("@")[0] if email and "@" in email else user_id)
    )

    karma_score = profile_data.get("karma_score")
    if karma_score is None:
        karma_score = 0

    return {
        "uid": user_id,
        "email": email,
        "full_name": full_name,
        "bio": profile_data.get("bio", ""),
        "college": profile_data.get("college", ""),
        "city": profile_data.get("city", ""),
        "profile_image_url": profile_data.get("profile_image_url") or user_data.get("profile_image_url", ""),
        "is_available": profile_data.get("is_available", True),
        "reputation_score": float(karma_score or 0),
        "github_username": profile_data.get("github_username", ""),
        "linkedin_url": profile_data.get("linkedin_url", ""),
        "level": int(profile_data.get("level", 1) or 1),
        "streak_count": int(profile_data.get("streak_count", 0) or 0),
        "xp_points": int(profile_data.get("xp_points", 0) or 0),
        "karma_score": int(karma_score or 0),
    }

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
            "selected_user_id": None,
            "selected_at": None,
            "interested_user_ids": [],
            "rejected_user_ids": [],
            "join_requests": [],
            "linked_sprint_id": None,
            "sprint_status": None,
            "is_exhausted": False,
            "created_at": datetime.utcnow(),
            "updated_at": datetime.utcnow(),
        }
        
        db.collection("matchRequests").document(request_id).set(document_data)
        
        # Get requester's name for notification
        requester_name = _get_user_display_name(db, user_id)
        
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
    limit: int = 20,
    offset: int = 0,
    credentials = Depends(security)
):
    """Browse available match requests with user details and skills - PAGINATED & OPTIMIZED"""
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
        
        # First pass: filter out own requests and collect requester IDs
        requests = []
        requester_ids = []
        
        for doc in query.stream():
            request_data = doc.to_dict()
            
            join_requests = _normalize_join_requests(request_data)
            interested_user_ids = {
                entry.get("user_id") for entry in join_requests if entry.get("user_id")
            }
            
            # Exclude own requests
            if request_data.get("userId") != user_id and user_id not in interested_user_ids:
                requests.append(request_data)
                requester_ids.append(request_data.get("userId"))
        
        # Sort by created_at (most recent first)
        requests.sort(
            key=lambda match_item: _to_sort_timestamp(match_item.get("created_at")),
            reverse=True,
        )
        
        # BATCH FETCH: Get all user profiles at once instead of one-by-one
        user_cache = {}
        for requester_id in set(requester_ids):
            user_cache[requester_id] = _get_user_match_profile(db, requester_id)
        
        # BATCH FETCH: Get all user skills at once
        user_skills_cache = {}
        for requester_id in set(requester_ids):
            user_skills_cache[requester_id] = _get_user_skills(db, requester_id)
        
        # Apply pagination and build response
        paginated_requests = requests[offset : offset + limit]
        
        response = []
        for request_data in paginated_requests:
            requester_id = request_data.get("userId")
            
            match_with_user = {
                **request_data,
                "user": user_cache.get(requester_id, {}),
                "user_skills": user_skills_cache.get(requester_id, [])
            }
            
            response.append(match_with_user)
        
        return response
    
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
async def get_received_match_requests(
    limit: int = 20,
    offset: int = 0,
    credentials = Depends(security)
):
    """Get match requests received/accepted by current user - PAGINATED"""
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
            user_cache[requester_id] = _get_user_match_profile(db, requester_id)
        
        # Batch fetch all user skills for all requesters
        user_skills_cache = {}
        for requester_id in set(requester_ids):
            user_skills_cache[requester_id] = _get_user_skills(db, requester_id)
        
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
        
        # Apply pagination
        return received[offset : offset + limit]
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )


@router.get("/user/join-requests", response_model=list[dict])
async def get_join_requests_for_my_matches(credentials = Depends(security)):
    """Get pending join requests for current user's matches."""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()

        join_request_rows = []
        match_docs = db.collection("matchRequests").where(
            filter=FieldFilter("userId", "==", user_id)
        ).where(
            filter=FieldFilter("status", "==", "pending")
        ).stream()

        for match_doc in match_docs:
            match_data = match_doc.to_dict()
            join_requests = _normalize_join_requests(match_data)
            pending_join_requests = [
                entry for entry in join_requests
                if (entry.get("selection_status") or "pending") == "pending"
            ]

            if not pending_join_requests:
                continue

            interested_users = []
            for entry in pending_join_requests:
                interested_user_id = entry.get("user_id")
                if not interested_user_id:
                    continue

                profile_data = _get_user_match_profile(db, interested_user_id)
                profile_data["user_id"] = interested_user_id
                profile_data["accepted_at"] = entry.get("accepted_at")
                profile_data["skills"] = _get_user_skills(db, interested_user_id)
                interested_users.append(profile_data)

            interested_users.sort(
                key=lambda user_item: _to_sort_timestamp(user_item.get("accepted_at")),
                reverse=True,
            )

            join_request_rows.append({
                "match_id": match_data.get("id"),
                "session_type": match_data.get("session_type"),
                "message": match_data.get("message"),
                "required_skills": match_data.get("required_skills") or [],
                "status": match_data.get("status"),
                "scheduled_date_time": match_data.get("scheduled_date_time"),
                "created_at": match_data.get("created_at"),
                "interested_count": len(interested_users),
                "interested_users": interested_users,
            })

        join_request_rows.sort(
            key=lambda row: _to_sort_timestamp(row.get("created_at")),
            reverse=True,
        )

        return join_request_rows

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
    """Express interest in a pending match request (join request)."""
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

        if requester_id == user_id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Cannot accept your own match request"
            )

        if match_data.get("status") != "pending":
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="This match request is closed"
            )

        join_requests = _normalize_join_requests(match_data)
        interested_user_ids = {
            entry.get("user_id") for entry in join_requests if entry.get("user_id")
        }

        if user_id in interested_user_ids:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="You have already sent a join request for this match"
            )

        accepted_at = datetime.utcnow()
        join_requests.append({
            "user_id": user_id,
            "accepted_at": accepted_at,
            "selection_status": "pending",
            "selected_at": None,
            "rejected_at": None,
        })

        db.collection("matchRequests").document(request_id).update({
            "join_requests": join_requests,
            "interested_user_ids": [
                entry.get("user_id") for entry in join_requests if entry.get("user_id")
            ],
            "updated_at": accepted_at,
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


@router.put("/{request_id}/select/{selected_user_id}", response_model=MatchRequest)
async def select_match_partner(
    request_id: str,
    selected_user_id: str,
    credentials = Depends(security)
):
    """Requester selects one interested user and finalizes the match."""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()

        match_ref = db.collection("matchRequests").document(request_id)
        match_doc = match_ref.get()
        if not match_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Match request not found"
            )

        match_data = match_doc.to_dict()

        if match_data.get("userId") != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Only the match requester can select a collaborator"
            )

        if match_data.get("status") != "pending":
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="This match request has already been finalized"
            )

        join_requests = _normalize_join_requests(match_data)
        pending_user_ids = {
            entry.get("user_id")
            for entry in join_requests
            if entry.get("user_id") and (entry.get("selection_status") or "pending") == "pending"
        }

        if selected_user_id not in pending_user_ids:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Selected user does not have a pending join request"
            )

        selected_at = datetime.utcnow()
        updated_join_requests = []
        rejected_user_ids = []

        for entry in join_requests:
            entry_copy = {
                "user_id": entry.get("user_id"),
                "accepted_at": entry.get("accepted_at"),
                "selection_status": entry.get("selection_status") or "pending",
                "selected_at": entry.get("selected_at"),
                "rejected_at": entry.get("rejected_at"),
            }

            entry_user_id = entry_copy.get("user_id")
            if not entry_user_id:
                continue

            if entry_user_id == selected_user_id:
                entry_copy["selection_status"] = "selected"
                entry_copy["selected_at"] = selected_at
                entry_copy["rejected_at"] = None
            elif entry_copy.get("selection_status") == "pending":
                entry_copy["selection_status"] = "rejected"
                entry_copy["rejected_at"] = selected_at
                rejected_user_ids.append(entry_user_id)

            updated_join_requests.append(entry_copy)

        match_ref.update({
            "status": "accepted",
            "accepted_by": selected_user_id,
            "accepted_at": selected_at,
            "selected_user_id": selected_user_id,
            "selected_at": selected_at,
            "interested_user_ids": [
                entry.get("user_id") for entry in updated_join_requests if entry.get("user_id")
            ],
            "rejected_user_ids": rejected_user_ids,
            "join_requests": updated_join_requests,
            "is_exhausted": False,
            "updated_at": selected_at,
        })

        requester_name = _get_user_display_name(db, user_id)
        send_match_selected_notification(selected_user_id, requester_name, request_id)

        for rejected_user_id in rejected_user_ids:
            send_match_not_selected_notification(rejected_user_id, requester_name, request_id)

        updated_doc = match_ref.get()
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
            partner_profile_doc = db.collection("profiles").document(partner_id).get()
            partner_profile_data = partner_profile_doc.to_dict() if partner_profile_doc.exists else {}
            if partner_doc.exists:
                partner_data = partner_doc.to_dict()
                partner_info = {
                    "partner_uid": partner_id,
                    "partner_name": _get_user_display_name(db, partner_id),
                    "partner_email": partner_data.get("email"),
                    "partner_profile_image": partner_profile_data.get("profile_image_url") or partner_data.get("profile_image_url")
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
