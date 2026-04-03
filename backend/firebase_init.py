import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
import os

# Initialize Firebase
def init_firebase():
    """Initialize Firebase Admin SDK"""
    
    # Check if Firebase is already initialized
    if not firebase_admin._apps:
        # Get the path to service account key
        key_path = os.getenv("FIREBASE_KEY_PATH", "firebase-key.json")
        
        if not os.path.exists(key_path):
            raise FileNotFoundError(
                f"Firebase service account key not found at {key_path}. "
                "Download from Firebase Console > Project Settings > Service Accounts"
            )
        
        # Initialize Firebase with credentials
        cred = credentials.Certificate(key_path)
        firebase_admin.initialize_app(cred)
    
    return firebase_admin

def get_firestore_client():
    """Get Firestore client instance"""
    init_firebase()
    return firestore.client()

# Lazy initialization
db = None

def get_db():
    """Get or initialize Firestore database"""
    global db
    if db is None:
        db = get_firestore_client()
    return db
