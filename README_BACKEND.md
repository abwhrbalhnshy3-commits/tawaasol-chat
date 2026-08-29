# Backend & Chat UI scaffold

This branch adds a basic backend scaffold (Ktor + PostgreSQL + Exposed) and a simple chat UI scaffold in the Android app.

Quickstart (local)
1. Start infra services:
   - cd infra
   - docker-compose up -d
   This will start Postgres and Redis. The Postgres service runs on localhost:5432 with user/password tawaasol/password and DB tawaasol.

2. Build and run the backend:
   - cd backend
   - ./gradlew run
   The server will start on port 8080 and expose simple endpoints under /api.

3. Open the Android app in Android Studio (mobile/) and run the app. The mobile app contains UI placeholders for chat list and chat messages and a Retrofit interface (ChatApi) to call the backend.

Notes & next steps
- Authentication and authorization are not implemented yet — endpoints are open and intended as scaffolding.
- Real-time messaging (WebSocket) will be added in the next iteration (server push, presence via Redis).
- Message pagination, indexing, attachments (media), and encryption are future work.
