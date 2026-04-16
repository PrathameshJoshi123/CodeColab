"""
Schemas package - Pydantic models organized by domain

Structure:
- identity.py    - User and Profile models
- skills.py      - Skill and UserSkill models
- matching.py    - Match request models
- sprints.py     - Sprint session and task models
- chat.py        - Message and conversation models
- reputation.py  - Feedback, badge models
- payments.py    - Subscription and payment models
- activity.py    - Activity logging models
- moderation.py  - Report and moderation models
"""

# Identity Domain
from .identity import (
    UserCreate,
    UserLogin,
    UserLoginResponse,
    User,
    ProfileUpdate,
    Profile,
)


# Auth Domain
from .auth import (
    SignUpRequest,
    LoginRequest,
    TokenResponse,
    SignUpResponse,
    GoogleOAuthRequest,
    GoogleOAuthResponse,
)

# Skills Domain
from .skills import (
    SkillCreate,
    Skill,
    UserSkillUpdate,
    UserSkillResponse,
)

# Matching Domain
from .matching import (
    MatchRequestCreate,
    MatchRequest,
    InstantHelpRequestCreate,
    InstantHelpRequest,
)

# Sprint Domain
from .sprints import (
    ParticipantDetail,
    SprintSessionCreate,
    SprintSessionUpdate,
    SprintSession,
    SprintTaskCreate,
    SprintTaskUpdate,
    SprintTask,
    SprintTodoCreate,
    SprintTodoUpdate,
    SprintTodo,
    ScratchpadCreate,
    ScratchpadUpdate,
    Scratchpad,
)

# Chat Domain
from .chat import (
    MessageCreate,
    Message,
    ConversationCreate,
    Conversation,
    ConversationParticipant,
)

# Reputation Domain




# Activity Domain
from .activity import (
    UserActivityLogCreate,
    UserActivityLog,
)



__all__ = [
    # Identity
    "UserCreate",
    "UserLogin",
    "UserLoginResponse",
    "User",
    "ProfileUpdate",
    "Profile",
    # Auth
    "SignUpRequest",
    "LoginRequest",
    "TokenResponse",
    "SignUpResponse",
    "GoogleOAuthRequest",
    "GoogleOAuthResponse",
    # Skills
    "SkillCreate",
    "Skill",
    "UserSkillUpdate",
    "UserSkillResponse",
    # Matching
    "MatchRequestCreate",
    "MatchRequest",
    "InstantHelpRequestCreate",
    "InstantHelpRequest",
    # Sprints
    "SprintSessionCreate",
    "SprintSessionUpdate",
    "SprintSession",
    "ParticipantDetail",
    "SprintTaskCreate",
    "SprintTaskUpdate",
    "SprintTask",
    "SprintTodoCreate",
    "SprintTodoUpdate",
    "SprintTodo",
    "ScratchpadCreate",
    "ScratchpadUpdate",
    "Scratchpad",
    # Chat
    "MessageCreate",
    "Message",
    "ConversationCreate",
    "Conversation",
    "ConversationParticipant",
    # Activity
    "UserActivityLogCreate",
    "UserActivityLog",
]
