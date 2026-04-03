from pydantic_settings import BaseSettings
from pydantic import ConfigDict
from typing import Optional

class Settings(BaseSettings):
    """Application settings loaded from environment variables"""
    
    # Firebase
    FIREBASE_KEY_PATH: str = "firebase-key.json"
    FIREBASE_DATABASE_URL: Optional[str] = None
    
    # API
    API_TITLE: str = "CodeCollab API"
    API_VERSION: str = "0.1.0"
    DEBUG: bool = False
    
    # Server Configuration
    HOST: str = "0.0.0.0"
    PORT: int = 8000
    LOG_LEVEL: str = "info"
    
    # CORS
    CORS_ORIGINS: list = ["*"]
    
    # JWT
    JWT_SECRET: str = "your-secret-key-change-in-production"
    JWT_ALGORITHM: str = "HS256"
    
    model_config = ConfigDict(
        env_file=".env",
        case_sensitive=True
    )

settings = Settings()
