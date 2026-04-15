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

def _get_participant_details(db, participant_id: str) -> dict:
    """Fetch participant full name and email from users collection"""
    try:
        user_doc = db.collection("users").document(participant_id).get()
        if user_doc.exists:
            user_data = user_doc.to_dict()
            return {
                "userId": participant_id,
                "full_name": user_data.get("full_name", participant_id),
                "email": user_data.get("email", "")
            }
    except Exception:
        pass
    
    return {
        "userId": participant_id,
        "full_name": participant_id,
        "email": ""
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
            "participants": participants,
            "match_id": session_data.match_id
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
    """Get a sprint session by ID with participant details"""
    try:
        db = get_db()
        session_doc = db.collection("sprintSessions").document(session_id).get()
        
        if not session_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Sprint session not found"
            )
        
        session_data = session_doc.to_dict()
        session_data = _enrich_sprint_with_participant_details(db, session_data)
        
        return SprintSession(**session_data)
    
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
            sessions.append(SprintSession(**doc.to_dict()))
        
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
            
            # Update participants and track joined participants
            joined_participants = session_data.get("joined_participants", [])
            if user_id not in joined_participants:
                joined_participants.append(user_id)
            
            db.collection("sprintSessions").document(session_id).update({
                "participants": participants,
                "joined_participants": joined_participants,
                "all_participants_joined": len(joined_participants) >= len(session_data.get("participants", [])),
                "started_at": datetime.utcnow() if len(joined_participants) >= len(session_data.get("participants", [])) else None
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
        
        return {
            "session_id": session_id,
            "total_expected": len(participants),
            "joined_count": len(joined_participants),
            "all_joined": len(joined_participants) >= len(participants),
            "started_at": session_data.get("started_at"),
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
        
        # Check if user is creator or participant
        if user_id not in session_data.get("participants", []) and session_data.get("createdBy") != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Only participants can confirm sprint"
            )
        
        # Update sprint status to confirmed
        db.collection("sprintSessions").document(session_id).update({
            "status": "confirmed",
            "confirmed_by": user_id,
            "confirmed_at": datetime.utcnow()
        })
        
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
        return {"success": True, "message": "Sprint confirmed and notification sent to partner", "sprint": updated_doc.to_dict()}
    
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
        
        # Get all todos
        todos = []
        todos_query = db.collection("sprintTodos").where(
            filter=FieldFilter("sprint_id", "==", session_id)
        )
        for doc in todos_query.stream():
            todos.append(doc.to_dict())
        
        # Get participant details
        participants_info = []
        for participant_id in sprint_data.get("participants", []):
            user_doc = db.collection("users").document(participant_id).get()
            if user_doc.exists:
                user_data = user_doc.to_dict()
                participants_info.append({
                    "uid": participant_id,
                    "full_name": user_data.get("full_name"),
                    "email": user_data.get("email"),
                    "profile_image_url": user_data.get("profile_image_url")
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
