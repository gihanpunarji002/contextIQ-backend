# Authentication setup

Email/password registration and login are implemented. Google OpenID Connect sign-in is implemented but disabled until you supply your own OAuth credentials. The backend/frontend processes are managed by you; no service was started or restarted during this change.

## Apply the changes to your running Docker backend

Your existing image contains the older backend. When ready, run these commands yourself:

```sh
cd /Users/gihanpunarji/Developer/ContextIQ
docker compose up -d --build --no-deps backend
docker compose logs -f backend
```

The build runs backend tests. Flyway applies V2 on application startup, creating `app_users` and adding conversation ownership. Do not remove your PostgreSQL volume and do not edit V1. Existing anonymous chat records remain in the database, with no owner, and are inaccessible through authenticated APIs. Assigning historical records requires a deliberate administrative migration; the first registrant never inherits them.

Your already-running Vite frontend should hot-reload source edits. Open **http://localhost:5173/register**, then register with first name, last name, email and a password of 12–128 characters. You will be signed in and redirected to the dashboard. Use the sign-out icon in the top bar, then test **/login** with email and password. Unauthenticated dashboard/chat URLs redirect to login. While the old backend image is still running, the updated frontend may show an auth API error; rebuild the backend before testing.

## Enable Google sign-in

1. In [Google Cloud Console](https://console.cloud.google.com/), create/select a project and configure Google Auth Platform branding/consent and audience. If the app is in Testing, add the Google accounts you will use as test users.
2. Create an OAuth client of type **Web application**. Add this exact **Authorized redirect URI**:

   ```text
   http://localhost:8080/login/oauth2/code/google
   ```

   This callback belongs to Spring Boot, not Vite. Update the port if you use a different backend port. This server-side authorization-code flow does not require a browser JavaScript OAuth SDK.
3. Add to the existing backend `.env` (do not replace its database entries):

   ```dotenv
   GOOGLE_LOGIN_ENABLED=true
   GOOGLE_CLIENT_ID=your-google-web-client-id
   GOOGLE_CLIENT_SECRET=your-google-client-secret
   FRONTEND_URL=http://localhost:5173
   SESSION_COOKIE_SECURE=false
   ```

   These are placeholders: use your own values. No secret goes into `ContextIQ-frontend` or any `VITE_*` variable. Compose forwards these variables to the backend; a missing credential with Google enabled prevents startup rather than silently misconfiguring login.
4. Rebuild/recreate the backend using the command above and reload the browser. **Continue with Google** becomes enabled.
5. Test in a regular Chrome, Safari, Firefox or Edge window. Google may reject authentication inside embedded/in-app browsers. After consent, Spring validates the OIDC response, creates/reuses the Google account and redirects to `/dashboard`.

Google uses `openid`, `profile` and `email` scopes only. Google names may be absent; those fields are then stored as empty strings. A verified email is required. Returning users are matched by Google's stable subject ID, not mutable email text. A Google identity is **not automatically linked** to a password account with the same email: use the original password login. Account linking is not implemented.

Official setup references: [Google OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect), [Google web-server OAuth](https://developers.google.com/identity/protocols/oauth2/web-server).

## How the session works

- Passwords are normalized only for email lookup; passwords themselves are never trimmed. Emails are case-insensitive. Passwords use salted PBKDF2 through Spring Security's delegating encoder; plaintext passwords are never persisted or returned.
- Spring stores authentication in a server session and sends an HttpOnly, SameSite=Lax session cookie. No authentication token is placed in localStorage.
- Session IDs and CSRF tokens rotate on login/registration. The frontend fetches `/api/v1/auth/csrf` before every mutation and sends `X-CSRF-TOKEN`. CSRF protection includes login, registration and logout.
- Credentialed CORS is restricted to `FRONTEND_URL`. Use `localhost` consistently for frontend and API, not a mix of `localhost` and `127.0.0.1`.
- Sessions expire after 30 minutes of inactivity. Sessions are in memory: backend restarts sign users out, but accounts and conversations persist in PostgreSQL. Multi-instance deployment needs shared session storage.
- Logging out invalidates the server session and clears frontend query data. Google logout ends the ContextIQ session, not the user's Google session.
- Dashboard and chat APIs require authentication. Conversations are filtered by the server-resolved account ID. Other users' conversation IDs return 404, and anonymous legacy records are hidden.

For HTTPS deployments set `SESSION_COOKIE_SECURE=true`. Keep frontend/API on the same site or explicitly redesign cross-site cookie handling. Configure a trusted reverse proxy and correct external OAuth callback URLs before deployment; do not blindly trust client-supplied forwarded headers.

## API

All paths below start with `/api/v1/auth`.

| Method/path | Response |
| --- | --- |
| GET `/csrf` | 200 `{ "token": "...", "headerName": "X-CSRF-TOKEN" }`; public, establishes CSRF session |
| GET `/providers` | 200 `{ "google": false }` or true; public |
| POST `/register` | 201 user DTO and authenticated session |
| POST `/login` | 200 user DTO and authenticated session |
| GET `/me` | 200 user DTO or 401 |
| POST `/logout` | 204; requires CSRF token |

Registration request:

```json
{"firstName":"Ada","lastName":"Lovelace","email":"ada@example.com","password":"a-long-unique-passphrase"}
```

Login request:

```json
{"email":"ada@example.com","password":"a-long-unique-passphrase"}
```

User DTO (never includes password hashes or Google tokens):

```json
{"id":"00000000-0000-0000-0000-000000000001","firstName":"Ada","lastName":"Lovelace","email":"ada@example.com","provider":"LOCAL"}
```

`provider` is LOCAL or GOOGLE. Invalid input returns 400 ProblemDetail, invalid credentials/unauthenticated access 401, missing/invalid CSRF 403, and duplicate registration 409. Login errors deliberately do not distinguish a missing account from a wrong password. Registration names are required and limited to 80 characters; email is required, validated, and limited to 254 characters.

`GET /oauth2/authorization/google` begins the Google redirect flow. `/login/oauth2/code/google` is Spring's callback. Failures return to `/login?error=google` with a generic frontend message, never raw provider error details. Health endpoints remain public; actuator info now requires authentication.

## Verification and remaining scope

Run `./mvnw test` for H2/Flyway + MockMvc tests and `npm run typecheck && npm run build` in the sibling frontend. These do not launch your application services. Tests exercise actual session login/rotation/logout, real CSRF-token exchange, validation, hashing, duplicates, conversation isolation and verified-Google-account provisioning. Google account provisioning tests use mocked OIDC claims; a real Google round trip requires your configured client and consent.

Email verification, password recovery, MFA, account linking, login/registration rate limiting, session revocation across devices, organization roles and tenant membership are not included. Add these controls before exposing the platform publicly. This change introduces account authentication and conversation ownership, not full enterprise tenant isolation.
