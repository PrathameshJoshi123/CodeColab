from fastapi import APIRouter, HTTPException, status, Depends
from firebase_init import get_db
from middleware import get_current_user, security
from schemas import SprintSessionCreate, SprintSessionUpdate, SprintSession, SprintTodoCreate, SprintTodoUpdate, SprintTodo, ParticipantDetail, ScratchpadCreate, ScratchpadUpdate, Scratchpad
from services.fcm_service import send_sprint_confirmation_notification
from datetime import datetime
import uuid
from google.cloud.firestore_v1 import FieldFilter

router = APIRouter(prefix="/sprints", tags=["sprints"])

# ==================== Helper Functions ====================

def _name_from_email(email: str) -> str:
    """Derive a readable fallback label from email local-part."""
    if not email:
        return ""
    return email.split("@", 1)[0].strip()

def _get_participant_details(db, participant_id: str) -> dict:
    """Fetch participant full name and email from users collection"""
    email = ""
    full_name = ""

    try:
        user_doc = db.collection("users").document(participant_id).get()
        if user_doc.exists:
            user_data = user_doc.to_dict() or {}
            email = (user_data.get("email") or "").strip()
            full_name = (user_data.get("full_name") or "").strip()

            if not full_name:
                full_name = (user_data.get("name") or "").strip()
    except Exception:
        pass

    # Profile updates may store latest name only in profiles collection.
    if not full_name:
        try:
            profile_doc = db.collection("profiles").document(participant_id).get()
            if profile_doc.exists:
                profile_data = profile_doc.to_dict() or {}
                full_name = (profile_data.get("full_name") or "").strip()
        except Exception:
            pass

    if not full_name and email:
        full_name = _name_from_email(email)

    if not full_name:
        full_name = participant_id
    
    return {
        "userId": participant_id,
        "full_name": full_name,
        "email": email
    }

def _enrich_sprint_with_participant_details(db, sprint_data: dict) -> dict:
    """Enrich sprint session data with participant details"""
    participants = sprint_data.get("participants", [])
    participant_details = []
    
    for participant_id in participants:
        detail = _get_participant_details(db, participant_id)
        participant_details.append(detail)
    
    sprint_data["participantDetails"] = participant_details
    return sprint_data

def _sync_match_with_sprint_state(db, match_id: str, sprint_id: str, sprint_status: str):
    """Keep linked match state aligned with sprint lifecycle"""
    if not match_id:
        return

    match_ref = db.collection("matchRequests").document(match_id)
    match_doc = match_ref.get()
    if not match_doc.exists:
        return

    now = datetime.utcnow()
    update_data = {
        "linked_sprint_id": sprint_id,
        "sprint_status": sprint_status,
    }

    if sprint_status == "end":
        update_data.update({
            "status": "exhausted",
            "is_exhausted": True,
            "exhausted_at": now,
        })
    elif sprint_status in ["setupped", "started"]:
        update_data.update({
            "status": "accepted",
            "is_exhausted": False,
        })

    match_ref.update(update_data)


# ==================== Progression Helpers ====================

XP_PER_SPRINT_COMPLETION = 100
XP_PER_LEVEL = 300
PROFICIENCY_LEVELS = ["beginner", "intermediate", "advanced", "expert"]


def _normalize_skill_name(skill_name: str) -> str:
    return (skill_name or "").strip().lower()


def _parse_profile_activity_date(value):
    if not value:
        return None

    if isinstance(value, datetime):
        return value.date()

    try:
        return datetime.fromisoformat(str(value)).date()
    except ValueError:
        return None


def _next_proficiency_level(current_level: str) -> str:
    normalized_level = (current_level or "beginner").strip().lower()
    if normalized_level not in PROFICIENCY_LEVELS:
        normalized_level = "beginner"

    current_index = PROFICIENCY_LEVELS.index(normalized_level)
    next_index = min(current_index + 1, len(PROFICIENCY_LEVELS) - 1)
    return PROFICIENCY_LEVELS[next_index]


def _update_user_progress_for_completion(db, user_id: str, completed_at: datetime):
    profile_ref = db.collection("profiles").document(user_id)
    profile_doc = profile_ref.get()
    profile_data = profile_doc.to_dict() if profile_doc.exists else {}

    current_xp_points = int(profile_data.get("xp_points", 0) or 0)
    current_streak_count = int(profile_data.get("streak_count", 0) or 0)

    new_xp_points = current_xp_points + XP_PER_SPRINT_COMPLETION
    new_level = max(1, (new_xp_points // XP_PER_LEVEL) + 1)

    completed_date = completed_at.date()
    last_activity_date = _parse_profile_activity_date(profile_data.get("last_activity_date"))

    if last_activity_date == completed_date:
        new_streak_count = current_streak_count
    elif last_activity_date and (completed_date - last_activity_date).days == 1:
        new_streak_count = current_streak_count + 1
    else:
        new_streak_count = 1

    profile_ref.set({
        "userId": user_id,
        "xp_points": new_xp_points,
        "level": new_level,
        "streak_count": new_streak_count,
        "last_activity_date": completed_date.isoformat(),
        "updated_at": completed_at,
    }, merge=True)


def _find_required_skill_ids(db, required_skill_names: list[str]) -> set[str]:
    normalized_required_names = {
        _normalize_skill_name(skill_name)
        for skill_name in (required_skill_names or [])
        if isinstance(skill_name, str) and skill_name.strip()
    }

    if not normalized_required_names:
        return set()

    required_skill_ids = set()
    for skill_doc in db.collection("skills").stream():
        skill_data = skill_doc.to_dict() or {}
        skill_name = _normalize_skill_name(skill_data.get("name"))
        if skill_name in normalized_required_names:
            required_skill_ids.add(skill_doc.id)

    return required_skill_ids


def _upgrade_user_skills_for_completion(db, user_id: str, required_skill_ids: set[str], completed_at: datetime):
    if not required_skill_ids:
        return

    user_skill_docs = db.collection("userSkills").where(
        filter=FieldFilter("userId", "==", user_id)
    ).stream()

    for user_skill_doc in user_skill_docs:
        user_skill_data = user_skill_doc.to_dict() or {}
        skill_id = user_skill_data.get("skillId")

        if skill_id not in required_skill_ids:
            continue

        current_level = (user_skill_data.get("proficiency_level") or "beginner").lower()
        updated_level = _next_proficiency_level(current_level)

        if updated_level != current_level:
            user_skill_doc.reference.update({
                "proficiency_level": updated_level,
                "updated_at": completed_at,
            })


def _log_completion_activity(db, user_id: str, sprint_id: str, match_id: str, completed_at: datetime):
    activity_id = str(uuid.uuid4())
    db.collection("activityLogs").document(activity_id).set({
        "id": activity_id,
        "user_id": user_id,
        "action_type": "completed_sprint",
        "action_metadata": {
            "sprint_id": sprint_id,
            "match_id": match_id,
            "xp_awarded": XP_PER_SPRINT_COMPLETION,
        },
        "created_at": completed_at,
    })


def _apply_sprint_completion_effects(db, sprint_id: str, session_data: dict, completed_at: datetime):
    participants = [participant for participant in (session_data.get("participants") or []) if participant]
    if not participants:
        return

    match_id = session_data.get("match_id")
    match_data = {}
    if match_id:
        match_doc = db.collection("matchRequests").document(match_id).get()
        if match_doc.exists:
            match_data = match_doc.to_dict() or {}

    required_skill_ids = _find_required_skill_ids(db, match_data.get("required_skills") or [])

    for participant_id in participants:
        _update_user_progress_for_completion(db, participant_id, completed_at)
        _upgrade_user_skills_for_completion(db, participant_id, required_skill_ids, completed_at)
        _log_completion_activity(db, participant_id, sprint_id, match_id, completed_at)


# ==================== Sprint Session Management ====================

@router.post("/", response_model=SprintSession)
async def create_sprint_session(
    session_data: SprintSessionCreate,
    credentials = Depends(security)
):
    """Create a new sprint session"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()

        if not session_data.match_id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Sprint setup requires a linked match"
            )

        match_ref = db.collection("matchRequests").document(session_data.match_id)
        match_doc = match_ref.get()
        if not match_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Linked match not found"
            )

        match_data = match_doc.to_dict()
        requester_id = match_data.get("userId")
        accepter_id = match_data.get("accepted_by")

        if match_data.get("status") != "accepted":
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Sprint can be setup only for accepted matches"
            )

        if requester_id != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Only the match requester can setup the sprint"
            )

        if not accepter_id:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Match has no accepted partner"
            )

        existing_sprints = list(
            db.collection("sprintSessions")
            .where(filter=FieldFilter("match_id", "==", session_data.match_id))
            .stream()
        )
        if existing_sprints:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Sprint already setup for this match"
            )
        
        session_id = str(uuid.uuid4())

        # Sprint is always between requester and accepter for the linked match
        participants = [requester_id, accepter_id]

        now = datetime.utcnow()
        
        document_data = {
            "id": session_id,
            "createdBy": user_id,
            "goal_title": session_data.goal_title,
            "description": session_data.description,
            "repo_link": session_data.repo_link or "",
            "meeting_link": session_data.meeting_link or "",
            "duration_minutes": session_data.duration_minutes,
            "status": "setupped",
            "start_time": now,
            "end_time": None,
            "participants": participants,
            "match_id": session_data.match_id,
            "confirmed_by": None,
            "confirmed_at": None,
            "joined_participants": [],
            "all_participants_joined": False
        }
        
        db.collection("sprintSessions").document(session_id).set(document_data)
        _sync_match_with_sprint_state(db, session_data.match_id, session_id, "setupped")
        
        return SprintSession(**document_data)
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.get("/{session_id}", response_model=SprintSession)
async def get_sprint_session(
    session_id: str,
    credentials = Depends(security)
):
    """Get a sprint session by ID with participant details"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        session_doc = db.collection("sprintSessions").document(session_id).get()
        
        if not session_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Sprint session not found"
            )
        
        session_data = session_doc.to_dict()

        if user_id not in session_data.get("participants", []) and session_data.get("createdBy") != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Not allowed to access this sprint"
            )

        session_data = _enrich_sprint_with_participant_details(db, session_data)
        
        return SprintSession(**session_data)
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.get("/", response_model=list[SprintSession])
async def get_sprint_sessions(credentials = Depends(security)):
    """Get all sprint sessions for current user"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()

        sessions = []
        for doc in db.collection("sprintSessions").stream():
            session_data = doc.to_dict()
            if user_id in session_data.get("participants", []) or session_data.get("createdBy") == user_id:
                session_data = _enrich_sprint_with_participant_details(db, session_data)
                sessions.append(SprintSession(**session_data))

        return sessions

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.get("/user/my-sessions", response_model=list[SprintSession])
async def get_user_sprint_sessions(credentials = Depends(security)):
    """Get all sprint sessions for current user"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        # Query sessions where user is participant or creator
        sessions = []
        
        # Created sessions
        created = db.collection("sprintSessions").where(
            filter=FieldFilter("createdBy", "==", user_id)
        ).stream()
        for doc in created:
            session_data = _enrich_sprint_with_participant_details(db, doc.to_dict())
            sessions.append(SprintSession(**session_data))
        
        # Participated sessions (where user is in participants array)
        all_sessions = db.collection("sprintSessions").stream()
        for doc in all_sessions:
            session_data = doc.to_dict()
            if user_id in session_data.get("participants", []) and session_data.get("createdBy") != user_id:
                session_data = _enrich_sprint_with_participant_details(db, session_data)
                sessions.append(SprintSession(**session_data))
        
        return sessions
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.get("/user/created", response_model=list[SprintSession])
async def get_created_sprint_sessions(credentials = Depends(security)):
    """Get all sprint sessions created by current user"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        sessions = []
        created = db.collection("sprintSessions").where(
            filter=FieldFilter("createdBy", "==", user_id)
        ).stream()
        for doc in created:
            session_data = _enrich_sprint_with_participant_details(db, doc.to_dict())
            sessions.append(SprintSession(**session_data))
        
        return sessions
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.get("/user/invited", response_model=list[SprintSession])
async def get_invited_sprint_sessions(credentials = Depends(security)):
    """Get all sprint sessions where user is invited (in participants but not creator)"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        sessions = []
        all_sessions = db.collection("sprintSessions").stream()
        for doc in all_sessions:
            session_data = doc.to_dict()
            # Include sprints where user is in participants but not the creator
            if user_id in session_data.get("participants", []) and session_data.get("createdBy") != user_id:
                session_data = _enrich_sprint_with_participant_details(db, session_data)
                sessions.append(SprintSession(**session_data))
        
        return sessions
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.put("/{session_id}", response_model=SprintSession)
async def update_sprint_session(
    session_id: str,
    session_update: SprintSessionUpdate,
    credentials = Depends(security)
):
    """Update a sprint session (only by creator)"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        # Check ownership
        session_doc = db.collection("sprintSessions").document(session_id).get()
        if not session_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Sprint session not found"
            )

        session_data = session_doc.to_dict()
        if session_data.get("createdBy") != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Only session creator can update"
            )

        current_status = (session_data.get("status") or "setupped").lower()
        if current_status == "end":
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Ended sprint cannot be updated"
            )
        
        # Prepare update
        update_data = {}
        if session_update.status:
            requested_status = session_update.status.lower()

            if requested_status not in ["setupped", "started", "end"]:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="Invalid sprint status transition"
                )

            if requested_status == "started":
                if not session_data.get("confirmed_at"):
                    raise HTTPException(
                        status_code=status.HTTP_409_CONFLICT,
                        detail="Sprint must be confirmed before start"
                    )
                update_data["status"] = "started"
                update_data["start_time"] = datetime.utcnow()
            elif requested_status == "end":
                update_data["status"] = "end"
                update_data["end_time"] = session_update.end_time or datetime.utcnow()
            else:
                update_data["status"] = "setupped"

        if session_update.end_time and update_data.get("status") == "end":
            update_data["end_time"] = session_update.end_time
        
        if update_data:
            db.collection("sprintSessions").document(session_id).update(update_data)

            if "status" in update_data:
                _sync_match_with_sprint_state(
                    db,
                    session_data.get("match_id"),
                    session_id,
                    update_data["status"]
                )
        
        # Return updated session
        updated_doc = db.collection("sprintSessions").document(session_id).get()
        return SprintSession(**updated_doc.to_dict())
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.post("/{session_id}/join", response_model=SprintSession)
async def join_sprint_session(
    session_id: str,
    credentials = Depends(security)
):
    """Join an existing sprint session"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        session_doc = db.collection("sprintSessions").document(session_id).get()
        if not session_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Sprint session not found"
            )
        
        session_data = session_doc.to_dict()
        participants = session_data.get("participants", [])

        if (session_data.get("status") or "").lower() == "end":
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Sprint has ended and cannot be joined"
            )

        if user_id not in participants and session_data.get("createdBy") != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Only linked match users can join this sprint"
            )

        joined_participants = session_data.get("joined_participants", [])
        if user_id not in joined_participants:
            joined_participants.append(user_id)

        db.collection("sprintSessions").document(session_id).update({
            "joined_participants": joined_participants,
            "all_participants_joined": len(joined_participants) >= len(participants)
        })
        
        updated_doc = db.collection("sprintSessions").document(session_id).get()
        return SprintSession(**updated_doc.to_dict())
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.post("/{session_id}/leave", response_model=SprintSession)
async def leave_sprint_session(
    session_id: str,
    credentials = Depends(security)
):
    """Leave a sprint waiting room before sprint starts"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()

        session_doc = db.collection("sprintSessions").document(session_id).get()
        if not session_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Sprint session not found"
            )

        session_data = session_doc.to_dict()
        participants = session_data.get("participants", [])
        if user_id not in participants and session_data.get("createdBy") != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Only participants can leave this sprint"
            )

        if (session_data.get("status") or "").lower() == "end":
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Sprint already ended"
            )

        joined_participants = [
            participant
            for participant in session_data.get("joined_participants", [])
            if participant != user_id
        ]

        db.collection("sprintSessions").document(session_id).update({
            "joined_participants": joined_participants,
            "all_participants_joined": len(joined_participants) >= len(participants)
        })

        updated_doc = db.collection("sprintSessions").document(session_id).get()
        return SprintSession(**updated_doc.to_dict())

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.post("/{session_id}/complete", response_model=SprintSession)
async def complete_sprint_session(
    session_id: str,
    credentials = Depends(security)
):
    """End sprint and exhaust linked match"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()

        session_ref = db.collection("sprintSessions").document(session_id)
        session_doc = session_ref.get()
        if not session_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Sprint session not found"
            )

        session_data = session_doc.to_dict()
        if session_data.get("createdBy") != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Only sprint creator can end the sprint"
            )

        if (session_data.get("status") or "").lower() == "end":
            return SprintSession(**session_data)

        completed_at = datetime.utcnow()

        session_ref.update({
            "status": "end",
            "end_time": completed_at
        })
        _sync_match_with_sprint_state(db, session_data.get("match_id"), session_id, "end")

        try:
            _apply_sprint_completion_effects(db, session_id, session_data, completed_at)
        except Exception as progress_error:
            print(f"⚠️ Failed to apply sprint completion effects: {progress_error}")

        updated_doc = session_ref.get()
        return SprintSession(**updated_doc.to_dict())

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.get("/{session_id}/participants-status", response_model=dict)
async def get_participants_status(
    session_id: str,
    credentials = Depends(security)
):
    """Check if all participants have joined the sprint session"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        session_doc = db.collection("sprintSessions").document(session_id).get()
        if not session_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Sprint session not found"
            )
        
        session_data = session_doc.to_dict()
        participants = session_data.get("participants", [])
        joined_participants = session_data.get("joined_participants", [])

        if user_id not in participants and session_data.get("createdBy") != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Not a participant in this sprint"
            )

        session_status = (session_data.get("status") or "setupped").lower()
        
        return {
            "session_id": session_id,
            "status": session_status,
            "createdBy": session_data.get("createdBy"),
            "total_expected": len(participants),
            "joined_count": len(joined_participants),
            "all_joined": len(joined_participants) >= len(participants),
            "started_at": session_data.get("start_time"),
            "participants": participants,
            "joined_participants": joined_participants
        }
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

# ==================== Sprint Confirmation & Partner Notification ====================

@router.post("/{session_id}/confirm", response_model=dict)
async def confirm_sprint_session(
    session_id: str,
    credentials = Depends(security)
):
    """Confirm sprint and send notification to partner"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        print(f"\n{'='*60}")
        print(f"✅ Sprint Confirmation: {session_id}")
        print(f"   Confirmer: {user_id}")
        print(f"{'='*60}")
        
        # Get the sprint session
        session_doc = db.collection("sprintSessions").document(session_id).get()
        if not session_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Sprint session not found"
            )
        
        session_data = session_doc.to_dict()
        
        # Only creator can confirm sprint setup and notify partner
        if session_data.get("createdBy") != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Only sprint creator can confirm sprint"
            )

        if (session_data.get("status") or "").lower() == "end":
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Ended sprint cannot be confirmed"
            )
        
        # Confirmation notifies partner but keeps setup lifecycle state
        db.collection("sprintSessions").document(session_id).update({
            "status": "setupped",
            "confirmed_by": user_id,
            "confirmed_at": datetime.utcnow()
        })
        _sync_match_with_sprint_state(db, session_data.get("match_id"), session_id, "setupped")
        
        # Get partner details from match (same logic as /matches/{match_id}/details)
        partner_id = None
        partner_fcm_token = None
        
        match_id = session_data.get("match_id")
        if match_id:
            # Fetch from match request to get accurate partner info
            match_doc = db.collection("matchRequests").document(match_id).get()
            if match_doc.exists:
                match_data = match_doc.to_dict()
                requester_id = match_data.get("userId")
                accepter_id = match_data.get("accepted_by")
                
                # Partner is the accepter (user B)
                if accepter_id and accepter_id != user_id:
                    partner_id = accepter_id
                elif requester_id and requester_id != user_id:
                    partner_id = requester_id
                
                print(f"   Match ID: {match_id}")
                print(f"   Match Status: {match_data.get('status')}")
        else:
            # Fallback: Get partner from participants list if no match ID
            participants = session_data.get("participants", [])
            for participant in participants:
                if participant != user_id:
                    partner_id = participant
                    break
        
        if partner_id:
            # Fetch partner's FCM token
            partner_doc = db.collection("users").document(partner_id).get()
            if partner_doc.exists:
                partner_data = partner_doc.to_dict()
                partner_fcm_token = partner_data.get("fcm_token")
            
            # Get confirmer's name for notification
            confirmer_doc = db.collection("users").document(user_id).get()
            confirmer_name = "Someone"
            if confirmer_doc.exists:
                confirmer_data = confirmer_doc.to_dict()
                confirmer_name = confirmer_data.get("full_name", confirmer_data.get("email", "Someone"))
            
            print(f"   Partner: {partner_id}")
            print(f"   Partner FCM Token: {partner_fcm_token[:30] + '...' if partner_fcm_token else 'NOT FOUND'}")
            print(f"   Confirmer Name: {confirmer_name}")
            print(f"   Sending sprint confirmation notification...")
            
            # Send notification to partner
            notification_result = send_sprint_confirmation_notification(
                partner_id, 
                confirmer_name, 
                session_id,
                session_data.get("goal_title", "Sprint Session")
            )
            
            if notification_result:
                print(f"   ✅ Sprint confirmation notification sent!")
            else:
                print(f"   ❌ Notification failed to send!")
        else:
            print(f"   ⚠️  No partner found in match or participants")
        
        print(f"{'='*60}\n")
        
        # Return updated session
        updated_doc = db.collection("sprintSessions").document(session_id).get()
        return {"success": True, "message": "Sprint setup confirmed and notification sent to partner", "sprint": updated_doc.to_dict()}
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

# ==================== Sprint Todo Management ====================

@router.post("/{session_id}/todos", response_model=SprintTodo)
async def create_sprint_todo(
    session_id: str,
    todo_data: SprintTodoCreate,
    credentials = Depends(security)
):
    """Create a new sprint todo item"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        # Verify sprint exists
        session_doc = db.collection("sprintSessions").document(session_id).get()
        if not session_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Sprint session not found"
            )

        session_data = session_doc.to_dict()
        if user_id not in session_data.get("participants", []) and session_data.get("createdBy") != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Not a participant in this sprint"
            )

        if (session_data.get("status") or "").lower() == "end":
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Cannot add todo to ended sprint"
            )
        
        todo_id = str(uuid.uuid4())
        now = datetime.utcnow()
        
        document_data = {
            "id": todo_id,
            "sprint_id": session_id,
            "title": todo_data.title,
            "description": todo_data.description or "",
            "is_completed": False,
            "created_by": user_id,
            "completed_at": None,
            "created_at": now,
            "updated_at": now
        }
        
        db.collection("sprintTodos").document(todo_id).set(document_data)
        
        return SprintTodo(**document_data)
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.get("/{session_id}/todos", response_model=list[SprintTodo])
async def get_sprint_todos(
    session_id: str,
    credentials = Depends(security)
):
    """Get all todos for a sprint"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()

        session_doc = db.collection("sprintSessions").document(session_id).get()
        if not session_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Sprint session not found"
            )

        session_data = session_doc.to_dict()
        if user_id not in session_data.get("participants", []) and session_data.get("createdBy") != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Not a participant in this sprint"
            )
        
        todos = []
        query = db.collection("sprintTodos").where(
            filter=FieldFilter("sprint_id", "==", session_id)
        )
        
        for doc in query.stream():
            todos.append(SprintTodo(**doc.to_dict()))
        
        return todos
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.put("/{session_id}/todos/{todo_id}", response_model=SprintTodo)
async def update_sprint_todo(
    session_id: str,
    todo_id: str,
    todo_update: SprintTodoUpdate,
    credentials = Depends(security)
):
    """Update a sprint todo item"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        # Get the todo
        todo_doc = db.collection("sprintTodos").document(todo_id).get()
        if not todo_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Todo not found"
            )
        
        todo_data = todo_doc.to_dict()

        session_doc = db.collection("sprintSessions").document(session_id).get()
        if not session_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Sprint session not found"
            )

        session_data = session_doc.to_dict()
        if user_id not in session_data.get("participants", []) and session_data.get("createdBy") != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Not a participant in this sprint"
            )

        if (session_data.get("status") or "").lower() == "end":
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Cannot edit todo in ended sprint"
            )

        if todo_data.get("sprint_id") != session_id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Todo does not belong to this sprint"
            )
        
        # Prepare update
        update_data = {"updated_at": datetime.utcnow()}
        
        if todo_update.title is not None:
            update_data["title"] = todo_update.title
        if todo_update.description is not None:
            update_data["description"] = todo_update.description
        if todo_update.is_completed is not None:
            update_data["is_completed"] = todo_update.is_completed
            if todo_update.is_completed:
                update_data["completed_at"] = datetime.utcnow()
            else:
                update_data["completed_at"] = None
        
        db.collection("sprintTodos").document(todo_id).update(update_data)
        
        # Return updated todo
        updated_doc = db.collection("sprintTodos").document(todo_id).get()
        return SprintTodo(**updated_doc.to_dict())
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.delete("/{session_id}/todos/{todo_id}", response_model=dict)
async def delete_sprint_todo(
    session_id: str,
    todo_id: str,
    credentials = Depends(security)
):
    """Delete a sprint todo item"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        # Get the todo
        todo_doc = db.collection("sprintTodos").document(todo_id).get()
        if not todo_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Todo not found"
            )

        todo_data = todo_doc.to_dict()
        if todo_data.get("sprint_id") != session_id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Todo does not belong to this sprint"
            )

        session_doc = db.collection("sprintSessions").document(session_id).get()
        if not session_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Sprint session not found"
            )

        session_data = session_doc.to_dict()
        if user_id not in session_data.get("participants", []) and session_data.get("createdBy") != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Not a participant in this sprint"
            )

        if (session_data.get("status") or "").lower() == "end":
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Cannot delete todo from ended sprint"
            )
        
        db.collection("sprintTodos").document(todo_id).delete()
        
        return {"success": True, "message": "Todo deleted successfully"}
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

# ==================== Sprint Details & History ====================

@router.get("/{session_id}/details", response_model=dict)
async def get_sprint_details(
    session_id: str,
    credentials = Depends(security)
):
    """Get complete sprint details including todos and participants info"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        # Get sprint session
        session_doc = db.collection("sprintSessions").document(session_id).get()
        if not session_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Sprint session not found"
            )
        
        sprint_data = session_doc.to_dict()

        if user_id not in sprint_data.get("participants", []) and sprint_data.get("createdBy") != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Not a participant in this sprint"
            )

        sprint_data = _enrich_sprint_with_participant_details(db, sprint_data)
        
        # Get all todos
        todos = []
        todos_query = db.collection("sprintTodos").where(
            filter=FieldFilter("sprint_id", "==", session_id)
        )
        for doc in todos_query.stream():
            todos.append(doc.to_dict())
        
        # Get participant details
        participants_info = []
        for participant in sprint_data.get("participantDetails", []):
            participants_info.append({
                "uid": participant.get("userId"),
                "full_name": participant.get("full_name"),
                "email": participant.get("email"),
            })
        
        return {
            **sprint_data,
            "todos": todos,
            "participants_info": participants_info
        }
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

# ==================== Scratchpad Management ====================

@router.post("/{session_id}/scratchpad", response_model=Scratchpad)
async def create_or_update_scratchpad(
    session_id: str,
    scratchpad_data: ScratchpadCreate,
    credentials = Depends(security)
):
    """Create or update sprint scratchpad"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        # Verify sprint exists
        session_doc = db.collection("sprintSessions").document(session_id).get()
        if not session_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Sprint session not found"
            )
        
        session_data = session_doc.to_dict()
        
        # Verify user is participant
        if user_id not in session_data.get("participants", []) and session_data.get("createdBy") != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Not a participant in this sprint"
            )

        if (session_data.get("status") or "").lower() == "end":
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Cannot update scratchpad for ended sprint"
            )
        
        # Check if scratchpad exists for this sprint
        scratchpad_query = db.collection("sprintScratchpads").where(
            filter=FieldFilter("sprint_id", "==", session_id)
        )
        
        scratchpad_docs = list(scratchpad_query.stream())
        
        if scratchpad_docs:
            # Update existing scratchpad
            scratchpad_id = scratchpad_docs[0].id
            db.collection("sprintScratchpads").document(scratchpad_id).update({
                "content": scratchpad_data.content,
                "modified_by": user_id,
                "updated_at": datetime.utcnow()
            })
            
            # Return updated scratchpad
            updated_doc = db.collection("sprintScratchpads").document(scratchpad_id).get()
            return Scratchpad(**updated_doc.to_dict())
        else:
            # Create new scratchpad
            scratchpad_id = str(uuid.uuid4())
            scratchpad_doc = {
                "id": scratchpad_id,
                "sprint_id": session_id,
                "content": scratchpad_data.content,
                "created_by": user_id,
                "modified_by": user_id,
                "created_at": datetime.utcnow(),
                "updated_at": datetime.utcnow()
            }
            
            db.collection("sprintScratchpads").document(scratchpad_id).set(scratchpad_doc)
            return Scratchpad(**scratchpad_doc)
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.get("/{session_id}/scratchpad", response_model=dict)
async def get_scratchpad(
    session_id: str,
    credentials = Depends(security)
):
    """Get sprint scratchpad content"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        # Verify sprint exists
        session_doc = db.collection("sprintSessions").document(session_id).get()
        if not session_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Sprint session not found"
            )
        
        session_data = session_doc.to_dict()
        
        # Verify user is participant
        if user_id not in session_data.get("participants", []) and session_data.get("createdBy") != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Not a participant in this sprint"
            )
        
        # Get scratchpad
        scratchpad_query = db.collection("sprintScratchpads").where(
            filter=FieldFilter("sprint_id", "==", session_id)
        )
        
        scratchpad_docs = list(scratchpad_query.stream())
        
        if scratchpad_docs:
            return {
                "exists": True,
                "data": scratchpad_docs[0].to_dict()
            }
        else:
            return {
                "exists": False,
                "data": None
            }
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )
