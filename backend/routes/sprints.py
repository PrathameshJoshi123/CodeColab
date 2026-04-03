from fastapi import APIRouter, HTTPException, status, Depends
from firebase_init import get_db
from middleware import get_current_user, security
from schemas import SprintSessionCreate, SprintSessionUpdate, SprintSession
from datetime import datetime
import uuid
from google.cloud.firestore_v1 import FieldFilter

router = APIRouter(prefix="/sprints", tags=["sprints"])

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
        
        session_id = str(uuid.uuid4())
        
        # Add current user as participant if not already included
        participants = list(set(session_data.participants + [user_id]))
        
        document_data = {
            "id": session_id,
            "createdBy": user_id,
            "goal_title": session_data.goal_title,
            "description": session_data.description,
            "repo_link": session_data.repo_link or "",
            "meeting_link": session_data.meeting_link or "",
            "duration_minutes": session_data.duration_minutes,
            "status": "scheduled",
            "start_time": datetime.utcnow(),
            "end_time": None,
            "participants": participants
        }
        
        db.collection("sprintSessions").document(session_id).set(document_data)
        
        return SprintSession(**document_data)
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.get("/{session_id}", response_model=SprintSession)
async def get_sprint_session(session_id: str):
    """Get a sprint session by ID"""
    try:
        db = get_db()
        session_doc = db.collection("sprintSessions").document(session_id).get()
        
        if not session_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Sprint session not found"
            )
        
        return SprintSession(**session_doc.to_dict())
    
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
            sessions.append(SprintSession(**doc.to_dict()))
        
        # Participated sessions (where user is in participants array)
        all_sessions = db.collection("sprintSessions").stream()
        for doc in all_sessions:
            session_data = doc.to_dict()
            if user_id in session_data.get("participants", []) and session_data.get("createdBy") != user_id:
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
        
        # Prepare update
        update_data = {}
        if session_update.status:
            update_data["status"] = session_update.status
        if session_update.end_time:
            update_data["end_time"] = session_update.end_time
        
        if update_data:
            db.collection("sprintSessions").document(session_id).update(update_data)
        
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
        
        if user_id not in participants:
            participants.append(user_id)
            db.collection("sprintSessions").document(session_id).update({
                "participants": participants
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
