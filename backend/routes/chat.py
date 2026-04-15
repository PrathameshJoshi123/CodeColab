from fastapi import APIRouter, HTTPException, status, Depends
from firebase_init import get_db
from middleware import get_current_user, security
from schemas import MessageCreate
from services.fcm_service import send_chat_message_notification
from datetime import datetime
import uuid
from google.cloud.firestore_v1 import FieldFilter

router = APIRouter(prefix="/chat", tags=["chat"])

ALLOWED_MESSAGE_TYPES = {"text", "image", "video", "code", "link", "file"}


# ==================== Helper Functions ====================

def _resolve_sender_name(db, user_id: str) -> str:
    """Resolve a readable sender name with profile fallback."""
    sender_name = "Unknown"

    sender_doc = db.collection("users").document(user_id).get()
    if sender_doc.exists:
        sender_data = sender_doc.to_dict() or {}
        sender_name = (
            sender_data.get("full_name")
            or sender_data.get("name")
            or sender_data.get("email")
            or sender_name
        )

    if sender_name == "Unknown":
        profile_doc = db.collection("profiles").document(user_id).get()
        if profile_doc.exists:
            profile_data = profile_doc.to_dict() or {}
            sender_name = profile_data.get("full_name") or sender_name

    return sender_name


def _notification_preview(message_type: str, content: str, media_name: str) -> str:
    """Build compact notification preview text per message type."""
    if message_type == "image":
        return f"📷 {media_name}" if media_name else "📷 Image"
    if message_type == "video":
        return f"🎥 {media_name}" if media_name else "🎥 Video"
    if message_type == "file":
        return f"📎 {media_name}" if media_name else "📎 File"
    return content

# ==================== Chat Management ====================

@router.post("/conversations/{session_id}/messages", response_model=dict)
async def send_message(
    session_id: str,
    message_data: MessageCreate,
    credentials = Depends(security)
):
    """Send a message in a sprint session chat"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        # Verify sprint session exists
        session_doc = db.collection("sprintSessions").document(session_id).get()
        if not session_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Sprint session not found"
            )
        
        # Verify user is participant
        session_data = session_doc.to_dict()
        if user_id not in session_data.get("participants", []) and session_data.get("createdBy") != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Not a participant in this sprint"
            )
        
        # Validate message payload
        message_id = str(uuid.uuid4())
        content = (message_data.content or "").strip()
        message_type = (message_data.message_type or "text").strip().lower()
        media_url = (message_data.media_url or "").strip()
        media_name = (message_data.media_name or "").strip()

        if message_type not in ALLOWED_MESSAGE_TYPES:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Unsupported message_type: {message_type}"
            )

        if message_type == "text" and not content:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Message content cannot be empty"
            )

        if message_type in {"code", "link"} and not content:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"content is required for {message_type} messages"
            )

        if message_type in {"image", "video", "file"} and not media_url:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"media_url is required for {message_type} messages"
            )

        # Get sender details
        sender_name = _resolve_sender_name(db, user_id)
        
        message_document = {
            "id": message_id,
            "sprint_id": session_id,
            "sender_id": user_id,
            "sender_name": sender_name,
            "content": content,
            "message_type": message_type,
            "created_at": datetime.utcnow()
        }

        if media_url:
            message_document["media_url"] = media_url
        if media_name:
            message_document["media_name"] = media_name
        
        # Store message in Firestore
        db.collection("chatMessages").document(message_id).set(message_document)
        
        # Get partner from participants and send FCM notification
        participants = session_data.get("participants", [])
        for participant in participants:
            if participant != user_id:
                # Send FCM data message to partner for instant update
                send_chat_message_notification(
                    participant,
                    sender_name,
                    _notification_preview(message_type, content, media_name),
                    session_id
                )
        
        return message_document
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.get("/conversations/{session_id}/messages", response_model=list[dict])
async def get_messages(
    session_id: str,
    limit: int = 50,
    credentials = Depends(security)
):
    """Get messages from a sprint session chat"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        # Verify sprint session exists
        session_doc = db.collection("sprintSessions").document(session_id).get()
        if not session_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Sprint session not found"
            )
        
        # Verify user is participant
        session_data = session_doc.to_dict()
        if user_id not in session_data.get("participants", []) and session_data.get("createdBy") != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Not a participant in this sprint"
            )
        
        # Fetch messages ordered by created_at
        messages_query = db.collection("chatMessages").where(
            filter=FieldFilter("sprint_id", "==", session_id)
        ).order_by(
            "created_at"
        ).limit(limit)
        
        messages = []
        for doc in messages_query.stream():
            messages.append(doc.to_dict())
        
        return messages
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.delete("/messages/{message_id}", response_model=dict)
async def delete_message(
    message_id: str,
    credentials = Depends(security)
):
    """Delete a message (only by sender)"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        # Get the message
        message_doc = db.collection("chatMessages").document(message_id).get()
        if not message_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Message not found"
            )
        
        message_data = message_doc.to_dict()
        
        # Check ownership
        if message_data.get("sender_id") != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Can only delete own messages"
            )
        
        # Delete message
        db.collection("chatMessages").document(message_id).delete()
        
        return {"detail": "Message deleted", "message_id": message_id}
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )
