"""
CodeCollab FastAPI Backend

Main application entry point with route registration and configuration.
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from config import settings
from firebase_init import init_firebase

# Import route modules
from routes import users, skills, sprints, matches, auth

# Initialize FastAPI app
app = FastAPI(
    title=settings.API_TITLE,
    version=settings.API_VERSION,
    description="Backend API for CodeCollab mobile app",
    debug=settings.DEBUG
)

# ==================== Middleware ====================

# CORS middleware for mobile app communication
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ==================== Initialization ====================

@app.on_event("startup")
async def startup_event():
    """Initialize Firebase on app startup"""
    try:
        init_firebase()
        print("✓ Firebase initialized successfully")
    except Exception as e:
        print(f"✗ Firebase initialization failed: {e}")
        raise

# ==================== Routes ====================

@app.get("/", tags=["health"])
async def root():
    """Health check endpoint"""
    return {
        "status": "ok",
        "service": "CodeCollab API",
        "version": settings.API_VERSION
    }

@app.get("/health", tags=["health"])
async def health_check():
    """Detailed health check"""
    return {
        "status": "healthy",
        "database": "connected",
        "firebase": "initialized"
    }

# Include route routers
app.include_router(auth.router)
app.include_router(users.router)
app.include_router(skills.router)
app.include_router(sprints.router)
app.include_router(matches.router)

# ==================== Error Handlers ====================

@app.exception_handler(Exception)
async def global_exception_handler(request, exc):
    """Global exception handler"""
    return {
        "error": str(exc),
        "detail": "An unexpected error occurred"
    }

# ==================== Entry Point ====================

if __name__ == "__main__":
    import uvicorn
    
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8000,
        reload=settings.DEBUG,
        log_level="info"
    )
