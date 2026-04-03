from pydantic import BaseModel
from datetime import datetime
from typing import Dict, Any

# ==================== User Activity Log Models ====================

class UserActivityLogCreate(BaseModel):
    """User activity log creation"""
    action_type: str
    action_metadata: Dict[str, Any] = {}
    device_info: str = ""
    ip_address: str = ""
    duration_seconds: int = 0

class UserActivityLog(BaseModel):
    """User activity log response"""
    id: str
    user_id: str
    action_type: str
    action_metadata: Dict[str, Any]
    device_info: str
    ip_address: str
    duration_seconds: int
    created_at: datetime
