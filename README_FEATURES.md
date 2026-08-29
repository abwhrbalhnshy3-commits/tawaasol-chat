# Full chat stack plan

This branch implements a first-pass "everything" plan:
- Authentication (OTP request/verify) issuing JWT tokens
- WebSocket-based real-time messaging with Redis pub/sub
- Simple file upload endpoint (saved to server uploads folder; MinIO available in infra)
- Android client additions: login screens, AuthRepository, WebSocketManager, LiveChatScreen
- DataStore updated to persist JWT token

What to run locally
1. Start infra: `cd infra && docker-compose up -d`
   - Postgres on 5432, Redis on 6379, MinIO on 9000
2. Start backend: `cd backend && ./gradlew run` (ensure JAVA_HOME set)
3. Open mobile in Android Studio, run the app on emulator. The emulator uses 10.0.2.2 to reach localhost on the host machine.

Security notes
- OTP is printed to backend logs for demo only. Replace with SMS provider and remove plaintext logging.
- JWT uses HMAC secret from env var JWT_SECRET; set a strong secret in production.
- File uploads are currently saved locally. Use MinIO and signed URLs for production storage.

Next steps I can take automatically if you confirm:
- Harden auth (refresh tokens, rate-limiting OTP), add Twilio integration
- Replace local uploads with MinIO client usage
- Add WebSocket client reconnection/resilience and conversation join/leave UX
- Add CI (build + tests) for backend and Android
