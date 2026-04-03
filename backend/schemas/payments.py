from pydantic import BaseModel
from datetime import datetime
from typing import Optional

# ==================== Subscription Models ====================

class SubscriptionCreate(BaseModel):
    """Subscription creation"""
    plan_type: str  # Free, Premium, Pro, Enterprise
    user_id: str

class Subscription(BaseModel):
    """Subscription response"""
    id: str
    user_id: str
    plan_type: str
    start_date: datetime
    end_date: Optional[datetime] = None
    status: str  # Active, Expired, Cancelled, Paused

class SubscriptionUpdate(BaseModel):
    """Subscription update"""
    plan_type: Optional[str] = None
    status: Optional[str] = None
    end_date: Optional[datetime] = None

# ==================== Payment Models ====================

class PaymentCreate(BaseModel):
    """Payment creation"""
    subscription_id: str
    amount: float
    currency: str

class Payment(BaseModel):
    """Payment response"""
    id: str
    subscription_id: str
    amount: float
    currency: str
    transaction_id: str
    status: str  # Pending, Completed, Failed, Refunded
    created_at: datetime

class PaymentUpdate(BaseModel):
    """Payment status update"""
    status: str
    transaction_id: Optional[str] = None
