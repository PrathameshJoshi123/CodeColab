# CodeCollab FastAPI Backend

FastAPI backend server for CodeCollab mobile application with Firebase integration.

## Quick Start

### 1. Setup Environment

```bash
cd backend

# Create virtual environment
python -m venv venv

# Activate virtual environment
# Windows:
venv\Scripts\activate
# macOS/Linux:
source venv/bin/activate

# Install dependencies
pip install -r requirements.txt
```

### 2. Configure Firebase

1. Copy `firebase-key.json` to the `backend/` folder (download from Firebase Console)
2. Create `.env` file from `.env.example`:
   ```bash
   cp .env.example .env
   ```
3. Update `.env` if needed (default values are usually fine for development)

### 3. Run Server

```bash
python main.py

# Or use uvicorn directly:
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

Server will be available at `http://localhost:8000`

### 4. API Documentation

- **Swagger UI**: `http://localhost:8000/docs`
- **ReDoc**: `http://localhost:8000/redoc`

---

## Project Structure

```
backend/
├── main.py                 # Main FastAPI app
├── config.py              # Configuration settings
├── firebase_init.py       # Firebase initialization
├── auth.py                # Firebase token verification
├── middleware.py          # Authentication middleware
├── models.py              # Pydantic models
├── requirements.txt       # Python dependencies
├── .env.example           # Environment template
├── firebase-key.json      # Firebase credentials (not in git)
└── routes/
    ├── users.py           # User management endpoints
    ├── skills.py          # Skills endpoints
    ├── sprints.py         # Sprint session endpoints
    └── matches.py         # Match request endpoints
```

---

## API Endpoints

### Users

- `POST /users/register` - Register new user
- `GET /users/me` - Get current user (requires auth)
- `GET /users/{user_id}` - Get user by ID
- `GET /users/{user_id}/profile` - Get user profile
- `PUT /users/me/profile` - Update current user's profile (requires auth)

### Skills

- `GET /skills/` - Get all available skills
- `POST /skills/` - Create new skill
- `GET /skills/{user_id}/skills` - Get user's skills
- `POST /skills/me/add` - Add skill to current user (requires auth)
- `PUT /skills/me/{skill_id}` - Update skill proficiency (requires auth)
- `DELETE /skills/me/{skill_id}` - Remove skill (requires auth)

### Sprint Sessions

- `POST /sprints/` - Create sprint session (requires auth)
- `GET /sprints/{session_id}` - Get sprint session
- `GET /sprints/user/my-sessions` - Get user's sessions (requires auth)
- `PUT /sprints/{session_id}` - Update sprint session (requires auth)
- `POST /sprints/{session_id}/join` - Join sprint session (requires auth)

### Match Requests

- `POST /matches/` - Create match request (requires auth)
- `GET /matches/browse` - Browse available match requests (requires auth)
- `GET /matches/user/my-requests` - Get user's match requests (requires auth)
- `PUT /matches/{request_id}/accept` - Accept match request (requires auth)
- `DELETE /matches/{request_id}` - Cancel match request (requires auth)

---

## Authentication

All endpoints marked with "(requires auth)" expect a Firebase ID token in the Authorization header:

```
Authorization: Bearer <FIREBASE_ID_TOKEN>
```

The mobile app obtains the token via:

```kotlin
FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.addOnSuccessListener { result ->
    val token = result.token
    // Use token in API requests
}
```

---

## Environment Variables

| Variable            | Default              | Description                            |
| ------------------- | -------------------- | -------------------------------------- |
| `FIREBASE_KEY_PATH` | `firebase-key.json`  | Path to Firebase service account key   |
| `API_TITLE`         | `CodeCollab API`     | API title for documentation            |
| `API_VERSION`       | `0.1.0`              | API version                            |
| `DEBUG`             | `false`              | Debug mode                             |
| `CORS_ORIGINS`      | `["*"]`              | Allowed CORS origins                   |
| `JWT_SECRET`        | `your-secret-key...` | JWT signing key (change in production) |
| `JWT_ALGORITHM`     | `HS256`              | JWT algorithm                          |

---

## Development Tips

### Enable Debug Mode

```python
# In .env
DEBUG=true
```

### Test Endpoints

```bash
# Health check
curl http://localhost:8000/health

# API docs
curl http://localhost:8000/docs
```

### View Logs

```bash
# With uvicorn
uvicorn main:app --reload --log-level debug
```

---

## Production Deployment

### Google Cloud Run

1. Create `Dockerfile`:

   ```dockerfile
   FROM python:3.11-slim

   WORKDIR /app
   COPY requirements.txt .
   RUN pip install --no-cache-dir -r requirements.txt

   COPY . .

   CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8080"]
   ```

2. Deploy:
   ```bash
   gcloud run deploy codecolab-api \
     --source . \
     --platform managed \
     --region us-central1 \
     --set-env-vars FIREBASE_KEY_PATH=/etc/secrets/firebase-key.json \
     --update-secrets FIREBASE_KEY=/your-secret
   ```

### Environment Variables for Production

- Set `DEBUG=false`
- Update `JWT_SECRET` to a strong random value
- Restrict `CORS_ORIGINS` to your domain
- Use secure secret management (Google Secret Manager, etc.)

---

## Troubleshooting

### Firebase Connection Error

- Verify `firebase-key.json` exists in backend folder
- Check Firebase console for valid project credentials

### 401 Unauthorized

- Ensure Bearer token is included in Authorization header
- Verify token is not expired
- Check token is from the same Firebase project

### CORS Errors

- Update `CORS_ORIGINS` in `.env` to include your domain
- For local development, use `["*"]` or `["http://localhost:*"]`

### Import Errors

- Ensure virtual environment is activated
- Run `pip install -r requirements.txt`

---

## Next Steps

1. ✅ Set up Firebase Firestore (see FIREBASE_SETUP.md)
2. ✅ Configure backend environment
3. Integrate with mobile app
4. Add more endpoints as needed
5. Implement caching with Redis
6. Add comprehensive logging
7. Set up CI/CD pipeline
