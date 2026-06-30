# 5. Security — "Secure by Default" / "Enterprise Ready"

Security is one of A2A's five design principles. The stance: an A2A agent is just an
**opaque HTTP enterprise application**, so it should reuse the auth, transport security, and
observability machinery enterprises already run.

## 5.1 Identity lives in HTTP headers, not the payload

The defining decision:

> "A2A protocol payloads, such as JSON-RPC messages, don't carry user or client identity
> information directly."

Credentials travel in **standard HTTP headers** (`Authorization: Bearer <token>`, an API-key
header, etc.). This cleanly separates *messaging* from *auth* and means an A2A endpoint can sit
behind a normal API gateway / identity proxy with no protocol-specific handling.

## 5.2 Security schemes (OpenAPI 3.x-aligned)

Declared on the Agent Card via `securitySchemes` (a map of scheme name → scheme) and `security`
(an array of requirement objects, each mapping a scheme name to required scopes). Supported
scheme subtypes:

| `type` | Scheme type | Notes |
|---|---|---|
| `apiKey` | `APIKeySecurityScheme` | key in header/query/cookie |
| `http` | `HTTPAuthSecurityScheme` | includes HTTP `bearer` |
| `oauth2` | `OAuth2SecurityScheme` | OAuth 2.0 flows; per-skill scopes |
| `openIdConnect` | `OpenIdConnectSecurityScheme` | OIDC discovery |
| `mutualTLS` | `MutualTlsSecurityScheme` | mTLS |

"Parity with OpenAPI's authentication schemes at launch" was an explicit launch goal, so these
map 1:1 onto OpenAPI security definitions.

## 5.3 Transport security
- **HTTPS mandated** for production.
- **TLS 1.2+** with strong cipher suites.
- Clients validate server certificates against trusted CAs during the TLS handshake.

## 5.4 AuthN/AuthZ responses & enforcement
- `401 Unauthorized` (with a `WWW-Authenticate` header) for missing/invalid credentials.
- `403 Forbidden` for valid-but-unauthorized.
- **Least privilege**; per-skill authorization via OAuth scopes declared in the Agent Card;
  agents enforce authorization **before** touching backend systems.

## 5.5 In-task / secondary auth — the `auth-required` state

A task can enter the **`auth-required`** state mid-flight when elevated or additional
credentials are needed (e.g. the agent must access a system the initial token didn't cover).
This is a **paused, non-terminal** state: the client obtains new credentials out-of-band and
resumes the task (same `taskId`) without losing prior work. See lifecycle in
[03-data-model.md](03-data-model.md#35-taskstate--the-lifecycle-enum-v03x-lowercase-strings).

## 5.6 Authenticated Extended Card

Gated by `supportsAuthenticatedExtendedCard` (v0.3.x) / `capabilities.extendedAgentCard`
(v1.0). After authenticating, a client calls `agent/getAuthenticatedExtendedCard` to receive a
richer Agent Card (more skills/capabilities than the public card). Lets an agent advertise a
minimal public surface and reveal privileged capabilities only to authorized callers.

## 5.7 Agent Card signing

From v0.3.0, an Agent Card can carry `signatures[]` (JWS — `protected` header + `signature`).
This lets a consumer verify the card's **integrity and authenticity** (that it really came from
the claimed provider and wasn't tampered with) before trusting its endpoints/skills.

## 5.8 Push-notification (webhook) security

Push notifications introduce a callback channel, so both directions need protection:

- **Server side (the agent POSTing the webhook):**
  - **Validate webhook URLs to prevent SSRF** — don't blindly POST to client-supplied URLs.
  - Authenticate **to** the webhook using the `PushNotificationConfig.authentication` info
    (Bearer token, API key, HMAC, or mTLS).
- **Client side (receiving the webhook):**
  - Verify authenticity — e.g. verify a signed **JWT** against the A2A server's **JWKS**
    (asymmetric signing).
  - Validate the echoed `token`, check timestamps, and **prevent replay** via nonces/unique ids.

## 5.9 Enterprise observability
- **OpenTelemetry** + W3C trace-context headers for distributed tracing across agents.
- Structured logging keyed by `taskId` / `contextId` / correlation ids.
- Metrics and audit logging for sensitive events.
- Compatible with API-gateway policy enforcement. Data-privacy compliance (GDPR/CCPA/HIPAA) is
  the implementer's responsibility — A2A provides the hooks, not the compliance.
