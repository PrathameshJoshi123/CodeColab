"""Payment model - handles payment transactions"""
from datetime import datetime
from sqlalchemy import Column, String, DateTime, Float, Integer, ForeignKey, Index
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class Payment(Base):
    """
    Payment model for managing payment transactions
    Tracks subscription and payment information
    """
    __tablename__ = "payments"

    id = Column(String(255), primary_key=True)
    uid = Column(String(255), ForeignKey("users.uid"), nullable=False)
    transaction_id = Column(String(255), unique=True, nullable=False)
    amount = Column(Float, nullable=False)
    currency = Column(String(10), default="USD")
    payment_status = Column(String(50), default="pending")  # pending, completed, failed, refunded
    payment_method = Column(String(100), nullable=True)  # credit_card, debit_card, paypal, etc.
    description = Column(String(500), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    user = relationship("User")

    __table_args__ = (
        Index("idx_payment_uid", "uid"),
        Index("idx_payment_transaction_id", "transaction_id"),
        Index("idx_payment_status", "payment_status"),
    )
