# Phentist security architecture

Phentist is designed as a protected digital-content platform. The Android client is never the authority for ownership or access.

## Required production architecture

- **Authentication:** short-lived access tokens + rotating refresh tokens; MFA for administrators.
- **Authorization:** server-side entitlement checks on every content request. A user may read only items they purchased.
- **Content delivery:** store originals in a private object store. Never expose permanent public URLs. Serve short-lived, single-use signed URLs or encrypted streams after authorization.
- **Payments:** use Google Play Billing for Android digital purchases. The backend must verify purchases with Google Play before granting an entitlement. Never grant access solely because the client reports success.
- **Admin:** separate admin application/API role with least privilege. File upload, price changes and publishing require authenticated admin authorization and audit logs.
- **Transport:** HTTPS only, TLS 1.2+; reject cleartext traffic. Consider certificate pinning for a high-risk deployment.
- **Local secrets:** never ship API keys, service-account keys, storage credentials or admin credentials in the APK. Use Android Keystore for local cryptographic material.
- **App hardening:** `FLAG_SECURE` is enabled to block normal screenshots, screen recording and non-secure display capture.
- **Tamper resistance:** Play Integrity API should be checked server-side for sensitive operations; do not rely on client-side checks alone.
- **Abuse controls:** rate limiting, replay protection, device/session revocation, audit trails and anomaly detection.

## Camera / recording warning

Android can request camera permission and Phentist can monitor its own camera stream. However, an Android phone cannot reliably determine that a *different physical camera* is pointed at its display using the phone's camera alone. Therefore this must not be advertised as guaranteed anti-camera-copy protection. The production viewer should combine `FLAG_SECURE`, watermarking, short-lived sessions, entitlement checks and optional camera-risk heuristics.

## Admin content model

Each item should have:

- immutable ID
- title and description
- price / Google Play product ID
- private storage object ID
- content checksum
- published/revoked status
- created/updated timestamps

Never store the actual protected file in this Git repository.
