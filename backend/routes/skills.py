from fastapi import APIRouter, HTTPException, status, Depends
from firebase_init import get_db
from middleware import get_current_user, security
from schemas import UserSkillUpdate, UserSkillResponse, SkillCreate
from datetime import datetime
from google.cloud.firestore_v1 import FieldFilter

router = APIRouter(prefix="/skills", tags=["skills"])

# ==================== Skill Lookup ====================

@router.get("/", response_model=list)
async def get_all_skills():
    """Get all available skills"""
    try:
        db = get_db()
        skills = db.collection("skills").stream()
        
        skill_list = []
        for skill in skills:
            skill_data = skill.to_dict()
            skill_data["id"] = skill.id
            skill_list.append(skill_data)
        
        return skill_list
    
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.post("/", response_model=dict)
async def create_skill(skill: SkillCreate, credentials = Depends(security)):
    """Create a new skill (admin only for now)"""
    try:
        db = get_db()
        
        # Add skill to collection
        doc_ref = db.collection("skills").document()
        doc_ref.set({
            "name": skill.name,
            "category": skill.category
        })
        
        return {"id": doc_ref.id, **skill.model_dump()}
    
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

# ==================== User Skills ====================

@router.get("/{user_id}/skills", response_model=list[UserSkillResponse])
async def get_user_skills(user_id: str):
    """Get all skills for a user"""
    try:
        db = get_db()
        
        # Query userSkills collection
        user_skills = db.collection("userSkills").where(
            filter=FieldFilter("userId", "==", user_id)
        ).stream()
        
        skill_list = []
        for user_skill in user_skills:
            us_data = user_skill.to_dict()
            
            # Get skill details
            skill_doc = db.collection("skills").document(us_data["skillId"]).get()
            skill_data = skill_doc.to_dict()
            
            skill_list.append(UserSkillResponse(
                skillId=us_data["skillId"],
                name=skill_data.get("name", ""),
                category=skill_data.get("category", ""),
                proficiency_level=us_data.get("proficiency_level", "")
            ))
        
        return skill_list
    
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.post("/me/add", response_model=UserSkillResponse)
async def add_user_skill(
    skill_update: UserSkillUpdate,
    credentials = Depends(security)
):
    """Add a skill to current user's profile"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        # Check if skill exists
        skill_doc = db.collection("skills").document(skill_update.skillId).get()
        if not skill_doc.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Skill not found"
            )
        
        skill_data = skill_doc.to_dict()
        
        # Create userSkill entry
        user_skill_id = f"{user_id}_{skill_update.skillId}"
        db.collection("userSkills").document(user_skill_id).set({
            "userId": user_id,
            "skillId": skill_update.skillId,
            "proficiency_level": skill_update.proficiency_level
        })
        
        return UserSkillResponse(
            skillId=skill_update.skillId,
            name=skill_data.get("name", ""),
            category=skill_data.get("category", ""),
            proficiency_level=skill_update.proficiency_level
        )
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.put("/me/{skill_id}", response_model=UserSkillResponse)
async def update_user_skill(
    skill_id: str,
    skill_update: UserSkillUpdate,
    credentials = Depends(security)
):
    """Update a user's skill proficiency"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        user_skill_id = f"{user_id}_{skill_id}"
        db.collection("userSkills").document(user_skill_id).update({
            "proficiency_level": skill_update.proficiency_level
        })
        
        # Get skill data
        skill_doc = db.collection("skills").document(skill_id).get()
        skill_data = skill_doc.to_dict()
        
        return UserSkillResponse(
            skillId=skill_id,
            name=skill_data.get("name", ""),
            category=skill_data.get("category", ""),
            proficiency_level=skill_update.proficiency_level
        )
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

@router.delete("/me/{skill_id}", status_code=status.HTTP_204_NO_CONTENT)
async def remove_user_skill(
    skill_id: str,
    credentials = Depends(security)
):
    """Remove a skill from user's profile"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        user_skill_id = f"{user_id}_{skill_id}"
        db.collection("userSkills").document(user_skill_id).delete()
        
        return None
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )
