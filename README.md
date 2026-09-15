# Phentist

Secure Android digital-content reader for Fentoph.

## Product flow

1. User creates an account and signs in.
2. Public catalog shows protected items and prices.
3. User purchases an item through Google Play Billing.
4. Backend verifies the purchase and creates an entitlement for that user.
5. The Android app requests camera permission for protection features.
6. The protected reader uses Android `FLAG_SECURE` so normal screenshots and screen recording are blocked.
7. The backend authorizes every content request; users cannot access another user's library by changing an ID.
8. Admins can publish items, upload files to private storage, set prices, revoke access and inspect audit logs.

## Important security boundary

The APK is an untrusted client. Authentication, purchase verification, entitlement checks and private file delivery must happen on the server. Do not put Firebase service-account keys, storage master keys or admin secrets in the app or repository.

## Camera protection

The app requests camera permission, but no Android API can guarantee detection of a separate physical camera filming the screen. A robust product therefore treats this as a risk signal rather than a security boundary. `FLAG_SECURE` remains enabled and the reader can add per-user dynamic watermarks and session controls.

## Production backend contract

Suggested endpoints:

- `POST /v1/auth/register`
- `POST /v1/auth/login`
- `POST /v1/auth/refresh`
- `GET /v1/catalog`
- `POST /v1/purchases/google/verify`
- `GET /v1/library`
- `POST /v1/content/{id}/session`
- `GET /v1/content/{id}/stream`
- `POST /v1/admin/content`
- `POST /v1/admin/content/{id}/publish`
- `POST /v1/admin/content/{id}/revoke`

Content-session tokens should be short-lived and scoped to one user, one item and one device/session. Never return a permanent storage URL.

## Local setup

Open the repository in Android Studio and sync Gradle. The package is `com.fentoph.phentist`.

Before release, configure:

- Google Play Console products and Billing
- server-side Google purchase verification
- production authentication provider
- private object storage
- database and entitlement tables
- admin role / MFA
- Play Integrity verification
- TLS-only networking
- privacy policy and terms

Protected source files must never be committed to GitHub.
