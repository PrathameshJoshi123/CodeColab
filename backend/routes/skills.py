from fastapi import APIRouter, HTTPException, status, Depends, Body
from firebase_init import get_db
from middleware import get_current_user, security
from schemas import UserSkillUpdate, UserSkillResponse, SkillCreate
from datetime import datetime
from google.cloud.firestore_v1 import FieldFilter
from pydantic import BaseModel
from typing import List

router = APIRouter(prefix="/skills", tags=["skills"])

# ==================== Request/Response Models ====================

class BulkSkillUpdate(BaseModel):
    """Bulk skills update request"""
    skills: List[dict]  # List of {skillId, proficiency_level}

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
    """
    Create a new skill
    - Any authenticated user can create skills
    - Skill will be available to all users
    - Automatically assigned to creator if assign_to_user=true
    """
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        # Check if skill with same name and category already exists
        existing_skills = db.collection("skills").where(
            filter=FieldFilter("name", "==", skill.name)
        ).where(
            filter=FieldFilter("category", "==", skill.category)
        ).stream()
        
        for existing in existing_skills:
            # Skill already exists, return it
            existing_data = existing.to_dict()
            return {
                "id": existing.id,
                "name": existing_data.get("name"),
                "category": existing_data.get("category"),
                "already_exists": True
            }
        
        # Create new skill
        doc_ref = db.collection("skills").document()
        doc_ref.set({
            "name": skill.name,
            "category": skill.category,
            "created_by": user_id,
            "created_at": datetime.utcnow(),
            "is_available": True
        })
        
        skill_id = doc_ref.id
        
        # Automatically assign this skill to the user who created it
        user_skill_id = f"{user_id}_{skill_id}"
        db.collection("userSkills").document(user_skill_id).set({
            "userId": user_id,
            "skillId": skill_id,
            "proficiency_level": "beginner",
            "created_at": datetime.utcnow(),
            "updated_at": datetime.utcnow()
        })
        
        return {
            "id": skill_id,
            "name": skill.name,
            "category": skill.category,
            "created_by": user_id,
            "auto_assigned": True
        }
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )

# ==================== User Skills ====================

@router.get("/{user_id}/skills", response_model=list[UserSkillResponse])
async def get_user_skills(user_id: str):
    """Get all skills for a user - OPTIMIZED with batch skill lookups"""
    try:
        db = get_db()
        
        # Query userSkills collection
        user_skills = list(db.collection("userSkills").where(
            filter=FieldFilter("userId", "==", user_id)
        ).stream())
        
        # Collect all skill IDs first
        skill_ids = []
        skill_data_map = {}
        
        for user_skill in user_skills:
            us_data = user_skill.to_dict()
            skill_id = us_data.get("skillId")
            if skill_id:
                skill_ids.append(skill_id)
                skill_data_map[skill_id] = us_data
        
        # Batch fetch all skill documents
        skill_details = {}
        for skill_id in set(skill_ids):
            skill_doc = db.collection("skills").document(skill_id).get()
            if skill_doc.exists:
                skill_details[skill_id] = skill_doc.to_dict()
        
        # Build response using cached data
        skill_list = []
        for skill_id, us_data in skill_data_map.items():
            skill_data = skill_details.get(skill_id, {})
            
            skill_list.append(UserSkillResponse(
                skillId=skill_id,
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

@router.put("/me", response_model=List[UserSkillResponse])
async def update_user_skills_bulk(
    bulk_update: BulkSkillUpdate,
    credentials = Depends(security)
):
    """Update multiple skills for current user at once (build profile)"""
    try:
        user_id = await get_current_user(credentials)
        db = get_db()
        
        # First, remove all existing skills for this user
        existing_skills = db.collection("userSkills").where(
            filter=FieldFilter("userId", "==", user_id)
        ).stream()
        
        for skill_doc in existing_skills:
            skill_doc.reference.delete()
        
        # Add new skills
        updated_skills = []
        
        for skill_item in bulk_update.skills:
            skill_id = skill_item.get("skillId")
            proficiency = skill_item.get("proficiency_level", "beginner")
            
            # Verify skill exists
            skill_doc = db.collection("skills").document(skill_id).get()
            if not skill_doc.exists:
                continue
            
            skill_data = skill_doc.to_dict()
            
            # Create userSkill entry
            user_skill_id = f"{user_id}_{skill_id}"
            db.collection("userSkills").document(user_skill_id).set({
                "userId": user_id,
                "skillId": skill_id,
                "proficiency_level": proficiency,
                "created_at": datetime.utcnow(),
                "updated_at": datetime.utcnow()
            })
            
            updated_skills.append(UserSkillResponse(
                skillId=skill_id,
                name=skill_data.get("name", ""),
                category=skill_data.get("category", ""),
                proficiency_level=proficiency
            ))
        
        return updated_skills
    
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
