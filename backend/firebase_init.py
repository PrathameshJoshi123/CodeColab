import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
import os
import json
from config import settings

# Initialize Firebase
def init_firebase():
    """Initialize Firebase Admin SDK
    
    Supports two methods:
    1. FIREBASE_KEY_JSON: Complete Firebase JSON as environment variable (Vercel/serverless)
    2. FIREBASE_KEY_PATH: Path to Firebase JSON file (local development)
    """
    
    # Check if Firebase is already initialized
    if not firebase_admin._apps:
        cred = None
        
        # Method 1: Try loading from JSON environment variable (Vercel deployment)
        if settings.FIREBASE_KEY_JSON:
            try:
                firebase_config = json.loads(settings.FIREBASE_KEY_JSON)
                cred = credentials.Certificate(firebase_config)
                print("✓ Firebase initialized from FIREBASE_KEY_JSON environment variable")
            except json.JSONDecodeError as e:
                raise ValueError(f"Invalid JSON in FIREBASE_KEY_JSON: {e}")
        
        # Method 2: Fall back to file path (local development)
        elif settings.FIREBASE_KEY_PATH:
            if not os.path.exists(settings.FIREBASE_KEY_PATH):
                raise FileNotFoundError(
                    f"Firebase service account key not found at {settings.FIREBASE_KEY_PATH}. "
                    "Download from Firebase Console > Project Settings > Service Accounts"
                )
            cred = credentials.Certificate(settings.FIREBASE_KEY_PATH)
            print(f"✓ Firebase initialized from file: {settings.FIREBASE_KEY_PATH}")
        
        if cred is None:
            raise ValueError(
                "Firebase credentials not found. Set either FIREBASE_KEY_JSON or FIREBASE_KEY_PATH"
            )
        
        # Initialize Firebase with credentials
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
