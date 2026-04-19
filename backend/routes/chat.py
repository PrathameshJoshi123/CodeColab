from fastapi import APIRouter, HTTPException, status, Depends
from firebase_init import get_db
from middleware import get_current_user, security
from schemas import MessageCreate
from services.fcm_service import send_chat_message_notification
from datetime import datetime, timezone
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


def _sort_timestamp_value(raw_value):
    """Normalize timestamp-like values for safe sorting."""
    # Handle Firestore Timestamp objects (check for to_rfc3339 method)
    if hasattr(raw_value, 'to_rfc3339') and callable(getattr(raw_value, 'to_rfc3339')):
        return raw_value.timestamp()
    
    if isinstance(raw_value, datetime):
        if raw_value.tzinfo is None:
            return raw_value.replace(tzinfo=timezone.utc).timestamp()
        return raw_value.timestamp()

    if isinstance(raw_value, str):
        try:
            normalized = raw_value.replace("Z", "+00:00")
            parsed = datetime.fromisoformat(normalized)
            if parsed.tzinfo is None:
                parsed = parsed.replace(tzinfo=timezone.utc)
            return parsed.timestamp()
        except ValueError:
            return 0.0

    return 0.0


def _serialize_conversation(conv_data: dict) -> dict:
    """Convert Firestore Timestamp objects to ISO format strings for JSON serialization."""
    if not conv_data:
        return {}
    
    serialized = dict(conv_data)
    
    # Convert timestamps to ISO format strings
    for key in ["created_at", "updated_at"]:
        if key in serialized:
            value = serialized[key]
            # Check for Firestore Timestamp (has to_rfc3339 method)
            if hasattr(value, 'to_rfc3339') and callable(getattr(value, 'to_rfc3339')):
                serialized[key] = value.to_rfc3339()
            elif isinstance(value, datetime):
                if value.tzinfo is None:
                    value = value.replace(tzinfo=timezone.utc)
                serialized[key] = value.isoformat()
    
    return serialized


def _serialize_message(msg_data: dict) -> dict:
    """Convert Firestore Timestamp objects in messages to ISO format strings for JSON serialization."""
    if not msg_data:
        return {}
    
    serialized = dict(msg_data)
    
    # Convert created_at timestamp to ISO format string
    if "created_at" in serialized:
        value = serialized["created_at"]
        # Check for Firestore Timestamp (has to_rfc3339 method)
        if hasattr(value, 'to_rfc3339') and callable(getattr(value, 'to_rfc3339')):
            serialized["created_at"] = value.to_rfc3339()
        elif isinstance(value, datetime):
            if value.tzinfo is None:
                value = value.replace(tzinfo=timezone.utc)
            serialized["created_at"] = value.isoformat()
    
    return serialized

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
        
        return _serialize_message(message_document)
    
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
        
        # Apply pagination and serialize timestamps
        paginated = all_messages[offset : offset + limit]
        return [_serialize_message(msg) for msg in paginated]
    
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
            return _serialize_conversation(conversation_doc.to_dict())
        
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
        return _serialize_conversation(conversation)
    
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
        media_url = (message_data.get("media_url") or "").strip()
        media_name = (message_data.get("media_name") or "").strip()

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
        
        if media_url:
            message_document["media_url"] = media_url
        if media_name:
            message_document["media_name"] = media_name
        
        # Store message
        db.collection("directMessages").document(message_id).set(message_document)

        participants = conversation_data.get("participants", [])
        partner_id = None
        for participant_id in participants:
            if participant_id != current_user_id:
                partner_id = participant_id
                break
        
        # Update conversation metadata
        message_preview = _notification_preview(message_type, content, media_name)
        previous_count = conversation_data.get("message_count", 0)
        if not isinstance(previous_count, int):
            previous_count = 0

        db.collection("conversations").document(conversation_id).update({
            "updated_at": datetime.utcnow(),
            "last_message": message_preview[:100],
            "last_message_sender": sender_name,
            "message_count": previous_count + 1
        })

        if partner_id:
            send_chat_message_notification(
                partner_id,
                sender_name,
                message_preview,
                conversation_id=conversation_id
            )
        
        return _serialize_message(message_document)
    
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
        
        # Fetch messages and sort in memory to avoid index-related query failures.
        safe_limit = max(1, min(limit, 200))
        messages = []
        query = db.collection("directMessages").where(
            filter=FieldFilter("conversation_id", "==", conversation_id)
        )
        
        for doc in query.stream():
            messages.append(doc.to_dict())

        messages.sort(key=lambda msg: _sort_timestamp_value(msg.get("created_at")))
        
        if len(messages) > safe_limit:
            messages = messages[-safe_limit:]
        
        # Serialize timestamps to ISO format for JSON response
        return [_serialize_message(msg) for msg in messages]
    
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
            filter=FieldFilter("participants", "array_contains", current_user_id)
        )
        
        for doc in query.stream():
            try:
                conv_data = doc.to_dict()
                if not conv_data:
                    continue

                participants = conv_data.get("participants", [])
                if not isinstance(participants, list) or len(participants) < 2:
                    continue
                
                # Add partner info
                partner_id = participants[0] if participants[0] != current_user_id else participants[1]
                partner_doc = db.collection("users").document(partner_id).get()
                partner_data = (partner_doc.to_dict() or {}) if partner_doc.exists else {}

                partner_full_name = partner_data.get("full_name", "")
                if not partner_full_name:
                    profile_doc = db.collection("profiles").document(partner_id).get()
                    if profile_doc.exists:
                        profile_data = profile_doc.to_dict() or {}
                        partner_full_name = profile_data.get("full_name", "")
                
                conv_data["partner"] = {
                    "userId": partner_id,
                    "email": partner_data.get("email", ""),
                    "full_name": partner_full_name,
                    "profile_image_url": partner_data.get("profile_image_url", "")
                }
                
                conversations.append(conv_data)
            except Exception as inner_e:
                print(f"Error processing conversation doc: {inner_e}", flush=True)
                continue

        conversations.sort(
            key=lambda conv: _sort_timestamp_value(conv.get("updated_at")),
            reverse=True
        )
        
        # Serialize timestamps to ISO format for JSON response
        return [_serialize_conversation(conv) for conv in conversations]
    
    except HTTPException:
        raise
    except Exception as e:
        import traceback
        print(f"Error in get_my_conversations: {e}", flush=True)
        print(traceback.format_exc(), flush=True)
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
