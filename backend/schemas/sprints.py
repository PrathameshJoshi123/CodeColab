from pydantic import BaseModel
from datetime import datetime
from typing import List, Optional

# ==================== Participant Detail Model ====================

class ParticipantDetail(BaseModel):
    """Participant with user details"""
    userId: str
    full_name: Optional[str] = None
    email: Optional[str] = None

# ==================== Sprint Session Models ====================

class SprintSessionCreate(BaseModel):
    """Sprint session creation"""
    goal_title: str
    description: str
    repo_link: Optional[str] = None
    meeting_link: str  # Mandatory meeting link
    duration_minutes: int
    participants: List[str]
    match_id: Optional[str] = None  # Track the match ID for this sprint

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
    participantDetails: Optional[List[ParticipantDetail]] = None
    match_id: Optional[str] = None  # Match ID for this sprint

class SprintSessionWithDetails(BaseModel):
    """Sprint session response with enhanced participant details"""
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
    participantDetails: List[ParticipantDetail]
    match_id: Optional[str] = None

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

# ==================== Sprint Todo Models ====================

class SprintTodoCreate(BaseModel):
    """Sprint todo creation"""
    sprint_id: str
    title: str
    description: Optional[str] = None

class SprintTodoUpdate(BaseModel):
    """Sprint todo update"""
    title: Optional[str] = None
    description: Optional[str] = None
    is_completed: Optional[bool] = None

class SprintTodo(BaseModel):
    """Sprint todo response"""
    id: str
    sprint_id: str
    title: str
    description: Optional[str] = None
    is_completed: bool
    created_by: str
    completed_at: Optional[datetime] = None
    created_at: datetime
    updated_at: datetime

# ==================== Scratchpad Models ====================

class ScratchpadCreate(BaseModel):
    """Scratchpad content creation/update"""
    sprint_id: str
    content: str

class ScratchpadUpdate(BaseModel):
    """Scratchpad update"""
    content: str

class Scratchpad(BaseModel):
    """Scratchpad response"""
    id: str
    sprint_id: str
    content: str
    created_by: str
    created_at: datetime
    updated_at: datetime
    modified_by: str
