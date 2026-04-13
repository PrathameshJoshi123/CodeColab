"""Firebase Cloud Messaging service for sending notifications"""
import firebase_admin
from firebase_admin import messaging
from firebase_init import get_db
from google.cloud.firestore_v1 import FieldFilter
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

def send_match_accepted_notification(requester_id: str, accepter_name: str, match_id: str):
    """Convenience function to send match acceptance notification"""
    return FCMService.send_match_acceptance_notification(requester_id, accepter_name, match_id)


def send_match_rejected_notification(requester_id: str, rejecter_name: str, match_id: str):
    """Convenience function to send match rejection notification"""
    return FCMService.send_match_rejection_notification(requester_id, rejecter_name, match_id)
