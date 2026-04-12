"""Session Feedback model - handles user feedback after sessions"""
from datetime import datetime
from sqlalchemy import Column, String, Integer, Float, DateTime, ForeignKey, Text, Index
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class SessionFeedback(Base):
    """
    SessionFeedback model for storing feedback given by users after sessions
    Contributes to reputation calculation
    """
    __tablename__ = "session_feedbacks"

    id = Column(String(255), primary_key=True)
    reputation_id = Column(Integer, ForeignKey("reputations.id"), nullable=False)
    reviewer_uid = Column(String(255), ForeignKey("users.uid"), nullable=False)
    communication_rating = Column(Integer, nullable=False)  # 1-5
    skill_rating = Column(Integer, nullable=False)  # 1-5
    reliability_rating = Column(Integer, nullable=False)  # 1-5
    review_text = Column(Text, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)

    # Relationships
    reputation = relationship("Reputation", back_populates="feedbacks")
    reviewer = relationship("User")

    __table_args__ = (
        Index("idx_session_feedback_reputation_id", "reputation_id"),
        Index("idx_session_feedback_reviewer_uid", "reviewer_uid"),
    )
