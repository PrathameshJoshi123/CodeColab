"""Firebase Cloud Messaging service for sending notifications"""
import firebase_admin
from firebase_admin import messaging
from firebase_init import get_db
from google.cloud.firestore_v1 import FieldFilter
from datetime import datetime
import logging

logger = logging.getLogger(__name__)


class FCMService:
    """
    Service for handling Firebase Cloud Messaging notifications.
    Supports sending notifications to users via their FCM tokens.
    """

    @staticmethod
    def send_match_acceptance_notification(requester_id: str, accepter_name: str, match_id: str):
        """
        Send notification when a match request is accepted
        
        Args:
            requester_id: UID of user who created the match request
            accepter_name: Full name of user who accepted the request
            match_id: ID of the match request
        """
        try:
            # Get requester's FCM token
            db = get_db()
            user_doc = db.collection("users").document(requester_id).get()
            
            if not user_doc.exists:
                logger.warning(f"User {requester_id} not found in database")
                return False
            
            # Get FCM token from user document
            user_data = user_doc.to_dict()
            fcm_token = user_data.get("fcm_token")
            
            if not fcm_token:
                logger.warning(f"FCM token not found for user {requester_id}. Tokens are required to send notifications.")
                logger.debug(f"User data keys: {user_data.keys()}")
                return False
            
            logger.info(f"Sending acceptance notification to user {requester_id} with token {fcm_token[:20]}...")
            
            # Prepare notification message
            message = messaging.Message(
                notification=messaging.Notification(
                    title="Match Accepted! 🎉",
                    body=f"{accepter_name} accepted your collaboration request"
                ),
                data={
                    "match_id": match_id,
                    "type": "MATCH_ACCEPTED",
                    "accepter_name": accepter_name
                },
                token=fcm_token
            )
            
            # Send the message
            response = messaging.send(message)
            logger.info(f"✓ Notification sent successfully. Message ID: {response}")
            
            # Log notification in Firestore (optional)
            FCMService._log_notification(
                requester_id,
                "MATCH_ACCEPTED",
                f"{accepter_name} accepted your collaboration request",
                match_id
            )
            
            return True
            
        except Exception as e:
            logger.error(f"✗ Failed to send notification: {str(e)}", exc_info=True)
            return False

    @staticmethod
    def send_match_rejection_notification(requester_id: str, rejecter_name: str, match_id: str):
        """
        Send notification when a match request is rejected
        
        Args:
            requester_id: UID of user who created the match request
            rejecter_name: Full name of user who rejected the request
            match_id: ID of the match request
        """
        try:
            db = get_db()
            user_doc = db.collection("users").document(requester_id).get()
            
            if not user_doc.exists:
                logger.warning(f"User {requester_id} not found")
                return False
            
            user_data = user_doc.to_dict()
            fcm_token = user_data.get("fcm_token")
            
            if not fcm_token:
                logger.warning(f"FCM token not found for user {requester_id}")
                return False
            
            message = messaging.Message(
                notification=messaging.Notification(
                    title="Match Request Declined",
                    body=f"{rejecter_name} declined your collaboration request"
                ),
                data={
                    "match_id": match_id,
                    "type": "MATCH_REJECTED",
                    "rejecter_name": rejecter_name
                },
                token=fcm_token
            )
            
            response = messaging.send(message)
            logger.info(f"✓ Rejection notification sent: {response}")
            
            FCMService._log_notification(
                requester_id,
                "MATCH_REJECTED",
                f"{rejecter_name} declined your collaboration request",
                match_id
            )
            
            return True
            
        except Exception as e:
            logger.error(f"✗ Failed to send rejection notification: {str(e)}", exc_info=True)
            return False

    @staticmethod
    def send_match_created_notification(match_id: str, match_data: dict, requester_name: str):
        """
        Send notifications to all users with required skills when a match is created
        
        Args:
            match_id: ID of the created match request
            match_data: Dictionary containing match details (required_skills, session_type, message)
            requester_name: Full name of user who created the match request
        """
        try:
            db = get_db()
            required_skills = match_data.get("required_skills", [])
            session_type = match_data.get("session_type", "")
            message = match_data.get("message", "")
            requester_id = match_data.get("userId", "")
            
            if not required_skills:
                logger.info(f"No required skills specified for match {match_id}")
                return True
            
            # Collect all unique user IDs with required skills
            users_to_notify = set()
            
            for skill_id in required_skills:
                # Query userSkills collection for users with this skill
                user_skills_query = db.collection("userSkills").where(
                    filter=FieldFilter("skillId", "==", skill_id)
                ).stream()
                
                for user_skill_doc in user_skills_query:
                    user_skill_data = user_skill_doc.to_dict()
                    user_id = user_skill_data.get("userId")
                    
                    # Don't notify the requester
                    if user_id and user_id != requester_id:
                        users_to_notify.add(user_id)
            
            logger.info(f"Found {len(users_to_notify)} users with required skills for match {match_id}")
            
            # Send notifications to all matched users
            notification_count = 0
            for user_id in users_to_notify:
                try:
                    # Get user's FCM token
                    user_doc = db.collection("users").document(user_id).get()
                    
                    if not user_doc.exists:
                        logger.warning(f"User {user_id} not found in database")
                        continue
                    
                    user_data = user_doc.to_dict()
                    fcm_token = user_data.get("fcm_token")
                    
                    if not fcm_token:
                        logger.debug(f"FCM token not found for user {user_id}")
                        continue
                    
                    # Prepare notification message
                    notification_body = f"{requester_name} created a {session_type} collaboration request"
                    
                    message_obj = messaging.Message(
                        notification=messaging.Notification(
                            title=f"New {session_type} Collaboration Request 🤝",
                            body=notification_body
                        ),
                        data={
                            "match_id": match_id,
                            "type": "MATCH_CREATED",
                            "requester_name": requester_name,
                            "session_type": session_type,
                            "message_preview": message[:100]  # Send preview of message
                        },
                        token=fcm_token
                    )
                    
                    # Send the message
                    response = messaging.send(message_obj)
                    logger.info(f"✓ Match created notification sent to user {user_id}. Message ID: {response}")
                    notification_count += 1
                    
                except Exception as e:
                    logger.error(f"✗ Failed to send notification to user {user_id}: {str(e)}")
                    continue
            
            # Log notification in Firestore
            FCMService._log_notification(
                requester_id,
                "MATCH_CREATED",
                f"Match created with {len(users_to_notify)} potential matches",
                match_id
            )
            
            logger.info(f"✓ Successfully sent {notification_count} match created notifications for match {match_id}")
            return True
            
        except Exception as e:
            logger.error(f"✗ Failed to send match created notifications: {str(e)}", exc_info=True)
            return False

    @staticmethod
    def _log_notification(user_id: str, notification_type: str, title: str, reference_id: str):
        """
        Log notification in Firestore for history/tracking
        
        Args:
            user_id: UID of recipient
            notification_type: Type of notification
            title: Notification title
            reference_id: Reference ID (match_id, sprint_id, etc)
        """
        try:
            from datetime import datetime
            db = get_db()
            
            notification_data = {
                "user_id": user_id,
                "type": notification_type,
                "title": title,
                "reference_id": reference_id,
                "read": False,
                "created_at": datetime.utcnow()
            }
            
            db.collection("notifications").add(notification_data)
            
        except Exception as e:
            logger.error(f"Failed to log notification: {str(e)}")


# ==================== Convenience Functions ====================

def send_match_created_notification(match_id: str, match_data: dict, requester_name: str):
    """Convenience function to send match created notifications to users with required skills"""
    return FCMService.send_match_created_notification(match_id, match_data, requester_name)


def send_match_accepted_notification(requester_id: str, accepter_name: str, match_id: str):
    """Convenience function to send match acceptance notification"""
    return FCMService.send_match_acceptance_notification(requester_id, accepter_name, match_id)


def send_match_rejected_notification(requester_id: str, rejecter_name: str, match_id: str):
    """Convenience function to send match rejection notification"""
    return FCMService.send_match_rejection_notification(requester_id, rejecter_name, match_id)


def send_sprint_confirmation_notification(partner_id: str, confirmer_name: str, sprint_id: str, goal_title: str):
    """
    Send notification when sprint is confirmed by partner
    
    Args:
        partner_id: UID of user who will receive notification
        confirmer_name: Full name of user who confirmed the sprint
        sprint_id: ID of the sprint session
        goal_title: Title/goal of the sprint
    """
    try:
        # Get partner's FCM token
        db = get_db()
        user_doc = db.collection("users").document(partner_id).get()
        
        if not user_doc.exists:
            logger.warning(f"Partner {partner_id} not found in database")
            return False
        
        # Get FCM token from user document
        user_data = user_doc.to_dict()
        fcm_token = user_data.get("fcm_token")
        
        if not fcm_token:
            logger.warning(f"FCM token not found for partner {partner_id}")
            return False
        
        logger.info(f"Sending sprint confirmation notification to partner {partner_id}...")
        
        # Prepare notification message
        message = messaging.Message(
            notification=messaging.Notification(
                title="Sprint Confirmed! ✅",
                body=f"{confirmer_name} confirmed sprint: {goal_title}"
            ),
            data={
                "sprint_id": sprint_id,
                "type": "SPRINT_CONFIRMED",
                "confirmer_name": confirmer_name,
                "goal_title": goal_title
            },
            token=fcm_token
        )
        
        # Send the message
        response = messaging.send(message)
        logger.info(f"✓ Sprint confirmation notification sent: {response}")
        
        # Log notification in Firestore
        FCMService._log_notification(
            partner_id,
            "SPRINT_CONFIRMED",
            f"Sprint confirmed: {goal_title}",
            sprint_id
        )
        
        return True
        
    except Exception as e:
        logger.error(f"✗ Failed to send sprint confirmation notification: {str(e)}", exc_info=True)
        return False


def send_chat_message_notification(partner_id: str, sender_name: str, message_content: str, sprint_id: str):
    """
    Send real-time data message notification for new chat messages
    Uses FCM data message (not notification) for instant in-app updates
    
    Args:
        partner_id: UID of user who will receive notification
        sender_name: Full name of user sending the message
        message_content: Content of the chat message
        sprint_id: ID of the sprint session
    """
    try:
        # Get partner's FCM token
        db = get_db()
        user_doc = db.collection("users").document(partner_id).get()
        
        if not user_doc.exists:
            logger.warning(f"Partner {partner_id} not found in database")
            return False
        
        # Get FCM token from user document
        user_data = user_doc.to_dict()
        fcm_token = user_data.get("fcm_token")
        
        if not fcm_token:
            logger.warning(f"FCM token not found for partner {partner_id}")
            return False
        
        logger.info(f"Sending chat message to partner {partner_id}...")
        
        # Prepare data message (no notification UI, just data for app to handle)
        message = messaging.Message(
            data={
                "sprint_id": sprint_id,
                "type": "CHAT_MESSAGE",
                "sender_name": sender_name,
                "content": message_content[:200],  # Truncate to fit in FCM
                "timestamp": str(datetime.utcnow())
            },
            token=fcm_token
        )
        
        # Send the message
        response = messaging.send(message)
        logger.info(f"✓ Chat message sent via FCM: {response}")
        
        return True
        
    except Exception as e:
        logger.error(f"✗ Failed to send chat message: {str(e)}", exc_info=True)
        return False
