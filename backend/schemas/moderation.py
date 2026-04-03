from pydantic import BaseModel
from datetime import datetime
from typing import Optional

# ==================== Report Models ====================

class ReportCreate(BaseModel):
    """Report creation"""
    reported_user_id: Optional[str] = None
    reported_content_id: Optional[str] = None
    reason: str  # Harassment, Inappropriate, Spam, Abuse, Other
    description: str

class Report(BaseModel):
    """Report response"""
    id: str
    reporter_id: str
    reported_user_id: Optional[str] = None
    reported_content_id: Optional[str] = None
    reason: str
    description: str
    status: str  # Open, UnderReview, Resolved, Dismissed
    created_at: datetime

class ReportUpdate(BaseModel):
    """Report status update"""
    status: str
    resolution_notes: Optional[str] = None
