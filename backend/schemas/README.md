# Schemas - Modular Pydantic Models

This directory contains domain-driven Pydantic models organized by functional domains.

## Structure

```
schemas/
├── __init__.py         - Central exports and imports
├── identity.py         - User and Profile models
├── skills.py           - Skill and UserSkill models
├── matching.py         - Match request and instant help models
├── sprints.py          - Sprint session and task models
├── chat.py             - Message and conversation models
├── reputation.py       - Feedback, badges models
├── payments.py         - Subscription and payment models
├── activity.py         - Activity logging models
└── moderation.py       - Report and moderation models
```

## Domains

### Identity (`identity.py`)

- `User` - User authentication and account data
- `UserCreate` - User registration
- `Profile` - User public profile and gamification
- `ProfileUpdate` - Profile modification

### Skills (`skills.py`)

- `Skill` - Technical skill definition
- `SkillCreate` - Create new skill
- `UserSkill` - User skill proficiency
- `UserSkillUpdate` - Update user skill proficiency
- `UserSkillResponse` - Skill with user proficiency

### Matching (`matching.py`)

- `MatchRequest` - Partner discovery request
- `MatchRequestCreate` - Create match request
- `InstantHelpRequest` - Urgent help request
- `InstantHelpRequestCreate` - Create help request

### Sprints (`sprints.py`)

- `SprintSession` - Coding collaboration session
- `SprintSessionCreate` - Create new session
- `SprintSessionUpdate` - Update session status
- `SprintTask` - Individual task in sprint
- `SprintTaskCreate` - Create task
- `SprintTaskUpdate` - Update task

### Chat (`chat.py`)

- `Message` - Individual message
- `MessageCreate` - Create message
- `Conversation` - Chat thread
- `ConversationCreate` - Create conversation
- `ConversationParticipant` - Participant in conversation

### Reputation (`reputation.py`)

- `SessionFeedback` - Post-session ratings
- `SessionFeedbackCreate` - Submit feedback
- `Badge` - Achievement badge
- `BadgeCreate` - Create badge
- `UserBadge` - Badge award to user
- `UserBadgeAward` - Award badge

### Payments (`payments.py`)

- `Subscription` - User subscription plan
- `SubscriptionCreate` - Create subscription
- `SubscriptionUpdate` - Update subscription
- `Payment` - Financial transaction
- `PaymentCreate` - Record payment
- `PaymentUpdate` - Update payment status

### Activity (`activity.py`)

- `UserActivityLog` - User action tracking
- `UserActivityLogCreate` - Log user activity

### Moderation (`moderation.py`)

- `Report` - Safety report
- `ReportCreate` - Create report
- `ReportUpdate` - Update report status

## Usage

### Import all models

```python
from schemas import User, Profile, SkillCreate, SprintSession, ...
```

### Import from specific domain

```python
from schemas.identity import User, Profile
from schemas.skills import Skill, UserSkillResponse
from schemas.sprints import SprintSession, SprintTask
```

### In route handlers

```python
from fastapi import APIRouter
from schemas import UserCreate, User, ProfileUpdate, Profile

router = APIRouter(prefix="/users", tags=["users"])

@router.post("/register", response_model=User)
async def register_user(user_data: UserCreate):
    # UserCreate enforced by FastAPI
    pass
```

## Best Practices

1. **Organization** - Keep models grouped by domain
2. **Reusability** - Use shared imports from `__init__.py`
3. **Separation** - Create, Read, Update models separate when needed
4. **Type Hints** - Use `Optional[]` and `|` for nullable fields
5. **Documentation** - Add docstrings to model classes
6. **Validation** - Leverage Pydantic's built-in validation

## Adding New Models

1. Create domain file if needed (e.g., `schemas/notifications.py`)
2. Define models with clear names and docstrings
3. Add imports to `__init__.py`
4. Update this README with new domain

Example:

```python
# schemas/newdomain.py
from pydantic import BaseModel

class NewModel(BaseModel):
    """Clear description"""
    field: str
```

Then in `__init__.py`:

```python
from .newdomain import NewModel
__all__ = [..., "NewModel"]
```

## Validation Examples

```python
# Optional fields
class UserUpdate(BaseModel):
    email: str | None = None
    name: str | None = None

# With constraints
from pydantic import Field
class Rating(BaseModel):
    score: int = Field(..., ge=1, le=5)

# Custom validation
from pydantic import field_validator
class Subscription(BaseModel):
    plan_type: str

    @field_validator('plan_type')
    @classmethod
    def validate_plan(cls, v):
        allowed = ['free', 'premium', 'pro']
        if v not in allowed:
            raise ValueError(f'Plan must be one of {allowed}')
        return v
```

## Related Files

- Routes: `routes/` directory
- Database: `firebase_init.py`
- Configuration: `config.py`
