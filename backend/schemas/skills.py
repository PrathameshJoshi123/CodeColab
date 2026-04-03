from pydantic import BaseModel

# ==================== Skill Models ====================

class SkillCreate(BaseModel):
    """Skill creation"""
    name: str
    category: str

class Skill(BaseModel):
    """Skill response"""
    id: str
    name: str
    category: str

class UserSkillUpdate(BaseModel):
    """User skill proficiency update"""
    skillId: str
    proficiency_level: str  # Beginner, Intermediate, Advanced, Expert

class UserSkillResponse(BaseModel):
    """User skill response"""
    skillId: str
    name: str
    category: str
    proficiency_level: str
