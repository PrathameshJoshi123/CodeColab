from pydantic import BaseModel
from datetime import datetime
from typing import List, Optional

# ==================== Sprint Session Models ====================

class SprintSessionCreate(BaseModel):
    """Sprint session creation"""
    goal_title: str
    description: str
    repo_link: Optional[str] = None
    meeting_link: Optional[str] = None
    duration_minutes: int
    participants: List[str]

class SprintSessionUpdate(BaseModel):
    """Sprint session update"""
    status: Optional[str] = None
    end_time: Optional[datetime] = None

class SprintSession(BaseModel):
    """Sprint session response"""
    id: str
    createdBy: str
    goal_title: str
    description: str
    repo_link: str
    meeting_link: str
    duration_minutes: int
    status: str
    start_time: datetime
    end_time: Optional[datetime] = None
    participants: List[str]

# ==================== Sprint Task Models ====================

class SprintTaskCreate(BaseModel):
    """Sprint task creation"""
    session_id: str
    title: str

class SprintTaskUpdate(BaseModel):
    """Sprint task update"""
    title: Optional[str] = None
    is_completed: Optional[bool] = None

class SprintTask(BaseModel):
    """Sprint task response"""
    id: str
    session_id: str
    title: str
    is_completed: bool
    completed_at: Optional[datetime] = None
