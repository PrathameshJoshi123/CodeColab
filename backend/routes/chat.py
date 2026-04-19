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
    offset: int = 0,
    credentials = Depends(security)
):
    """Get messages from a sprint session chat - PAGINATED & OPTIMIZED"""
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
        
        # Fetch messages ordered by created_at, with pagination
        messages_query = db.collection("chatMessages").where(
            filter=FieldFilter("sprint_id", "==", session_id)
        ).order_by(
            "created_at"
        )
        
        all_messages = []
        for doc in messages_query.stream():
            all_messages.append(doc.to_dict())
        
        # Apply pagination
        return all_messages[offset : offset + limit]
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )


# ==================== Direct Messages (WhatsApp-like) ====================

def _get_conversation_id(user1_id: str, user2_id: str) -> str:
    """Generate consistent conversation ID for two users"""
    users = sorted([user1_id, user2_id])
    return f"{users[0]}_{users[1]}"


@router.post("/dm/create")
async def create_or_get_conversation(
    recipient_data: dict,
    credentials = Depends(security)
):
    """Create or get existing direct message conversation"""
    try:
        current_user_id = await get_current_user(credentials)
        recipient_id = recipient_data.get("user_id")
        
        if not recipient_id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="recipient user_id is required"
            )
        
        if current_user_id == recipient_id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Cannot chat with yourself"
            )
        
        db = get_db()
        
        # Verify recipient exists
        recipient_doc = db.collection("users").document(recipient_id).get()
        if not recipient_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Recipient user not found"
            )
        
        conversation_id = _get_conversation_id(current_user_id, recipient_id)
        conversation_ref = db.collection("conversations").document(conversation_id)
        conversation_doc = conversation_ref.get()
        
        if conversation_doc.exists:
            return conversation_doc.to_dict()
        
        # Create new conversation
        now = datetime.utcnow()
        conversation = {
            "id": conversation_id,
            "participants": [current_user_id, recipient_id],
            "created_at": now,
            "updated_at": now,
            "last_message": None,
            "last_message_sender": None,
            "message_count": 0
        }
        
        conversation_ref.set(conversation)
        return conversation
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )


@router.post("/dm/{conversation_id}/send")
async def send_direct_message(
    conversation_id: str,
    message_data: dict,
    credentials = Depends(security)
):
    """Send a direct message"""
    try:
        current_user_id = await get_current_user(credentials)
        db = get_db()
        
        # Verify conversation exists
        conversation_doc = db.collection("conversations").document(conversation_id).get()
        if not conversation_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Conversation not found"
            )
        
        conversation_data = conversation_doc.to_dict()
        
        # Verify user is participant
        if current_user_id not in conversation_data.get("participants", []):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Not a participant in this conversation"
            )
        
        # Validate and prepare message
        content = (message_data.get("content") or "").strip()
        message_type = (message_data.get("message_type") or "text").strip().lower()
        
        if not content:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Message content cannot be empty"
            )
        
        message_id = str(uuid.uuid4())
        sender_name = _resolve_sender_name(db, current_user_id)
        
        message_document = {
            "id": message_id,
            "conversation_id": conversation_id,
            "sender_id": current_user_id,
            "sender_name": sender_name,
            "content": content,
            "message_type": message_type,
            "created_at": datetime.utcnow()
        }
        
        if message_data.get("media_url"):
            message_document["media_url"] = message_data.get("media_url")
        
        # Store message
        db.collection("directMessages").document(message_id).set(message_document)
        
        # Update conversation metadata
        db.collection("conversations").document(conversation_id).update({
            "updated_at": datetime.utcnow(),
            "last_message": content[:100],
            "last_message_sender": sender_name,
            "message_count": len(db.collection("directMessages").where(
                filter=FieldFilter("conversation_id", "==", conversation_id)
            ).stream())
        })
        
        return message_document
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )


@router.get("/dm/{conversation_id}/messages")
async def get_conversation_history(
    conversation_id: str,
    limit: int = 50,
    credentials = Depends(security)
):
    """Get message history for a conversation"""
    try:
        current_user_id = await get_current_user(credentials)
        db = get_db()
        
        # Verify conversation exists
        conversation_doc = db.collection("conversations").document(conversation_id).get()
        if not conversation_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Conversation not found"
            )
        
        conversation_data = conversation_doc.to_dict()
        
        # Verify user is participant
        if current_user_id not in conversation_data.get("participants", []):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Not a participant in this conversation"
            )
        
        # Fetch messages ordered by created_at
        messages = []
        query = db.collection("directMessages").where(
            filter=FieldFilter("conversation_id", "==", conversation_id)
        ).order_by("created_at", direction="DESCENDING").limit(limit)
        
        for doc in query.stream():
            messages.insert(0, doc.to_dict())  # Insert at beginning to maintain chronological order
        
        return messages
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )


@router.get("/dm/conversations")
async def get_my_conversations(
    credentials = Depends(security)
):
    """Get all conversations for current user"""
    try:
        current_user_id = await get_current_user(credentials)
        db = get_db()
        
        conversations = []
        query = db.collection("conversations").where(
            filter=FieldFilter("participants", "array-contains", current_user_id)
        ).order_by("updated_at", direction="DESCENDING")
        
        for doc in query.stream():
            conv_data = doc.to_dict()
            
            # Add partner info
            partner_id = conv_data["participants"][0] if conv_data["participants"][0] != current_user_id else conv_data["participants"][1]
            partner_doc = db.collection("users").document(partner_id).get()
            partner_data = partner_doc.to_dict() if partner_doc.exists else {}
            
            conv_data["partner"] = {
                "userId": partner_id,
                "email": partner_data.get("email", ""),
                "full_name": partner_data.get("full_name", ""),
                "profile_image_url": partner_data.get("profile_image_url", "")
            }
            
            conversations.append(conv_data)
        
        return conversations
    
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
