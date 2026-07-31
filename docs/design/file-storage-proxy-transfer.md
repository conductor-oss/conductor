# File Storage: the `CONDUCTOR` Storage Type

## Status

Proposed. Extends [File Storage Design](file-storage.md), which is implemented.

Supersedes an earlier draft that added a `transferMode` field and a byte-proxy endpoint the SDK had to be taught about. Issuing a URL that points at Conductor itself achieves the same thing without a protocol change, so that approach is dropped.

## Problem

`StorageType.LOCAL` hands the client a `file:` URI naming a path on the **server's** filesystem:

```java
// local-file-storage/.../LocalFileStorage.java:95
private String resolveUri(String storagePath) {
    return baseDirectory.resolve(storagePath).toAbsolutePath().toUri().toString();
}
```

Only a client sharing that filesystem can honour it; `LocalFileTransferAdapter` rejects any other scheme outright. So the zero-infrastructure default is unusable in the normal deployment — a containerized server with workers elsewhere.

It is not theoretical. It is what makes three `FileStorageE2ETest` cases fail against the `conductor-server` container while passing against a host JVM:

| Server | Client/server filesystem | Result |
|---|---|---|
| Host JVM (`:8080`) | shared | 11/11 pass |
| Container (`:8127`) | separate | 3 fail — `Unable to confirm file upload completion` |

The client writes bytes to its own `/tmp/...`; the server stats the container's `/tmp/...`, finds nothing, and `confirmUpload` throws. Nothing in the API tells the client the URL it was handed is unreachable.

S3, Azure Blob, and GCS are unaffected — a presigned HTTPS URL is reachable from anywhere.

## Design

**Replace `LOCAL` with `CONDUCTOR`, and make Conductor itself the transfer endpoint.** When `conductor.file-storage.type=conductor`, the server hands out an HTTPS URL pointing back at its own content endpoint. The client treats it exactly as it treats an S3 presigned URL: raw `PUT` or `GET`.

```
StorageType: S3, AZURE_BLOB, GCS, CONDUCTOR
```

The client-visible contract becomes uniform across every backend — one HTTPS shape, no special case:

```text
POST /api/files                            -> uploadUrl = https://conductor.example/api/files/content/{wf}/{id}
PUT  <that url>                            -> bytes
POST /api/files/{wf}/{id}/upload-complete  -> UPLOADED
```

### What this removes from the earlier draft

| Dropped | Why it is no longer needed |
|---|---|
| `transferMode: DIRECT \| PROXY` on three DTOs | The URL is an ordinary HTTPS URL. Nothing to signal. |
| `FileStorage.supportsDirectClientTransfer()` | Every backend is now client-reachable. |
| `ProxyFileTransferAdapter` in the SDK | Handled by the existing generic HTTP adapter. |
| The old-SDK compatibility break | Existing SDKs work unchanged — see below. |
| A bind mount in the e2e compose files | The container needs no shared filesystem with the client. |

### Existing SDKs work unchanged

`FileClient.selectAdapter` looks up `storageType` in its adapter map, then falls back on "is this an HTTP URL":

```java
// FileClient.java:564
FileTransferAdapter adapter = adapters.get(storageType.trim().toUpperCase(Locale.ROOT));
if (adapter != null) return adapter;
if (SignedUrlHttpTransfer.isHttpUrl(signedUrl)) return genericHttpAdapter;
```

`CONDUCTOR` is not in the map, so it lands on `GenericHttpFileTransferAdapter`, whose javadoc is *"Conservative fallback for a server storage type newer than this SDK"* and which performs a plain `PUT`/`GET`. The SDK was already designed for a backend it does not recognise.

This is the decisive advantage over the proxy-endpoint approach: **no SDK release is required for correctness.** A dedicated `ConductorFileTransferAdapter` becomes an optional later optimisation (multipart, resumable ranges), not a prerequisite.

### Endpoints

```
PUT /api/files/content/{workflowId}/{fileId}     # request body is the raw bytes
GET /api/files/content/{workflowId}/{fileId}     # response body is the raw bytes
```

`PUT` streams from `HttpServletRequest.getInputStream()`. `GET` returns a `StreamingResponseBody` with `Content-Type` and `Content-Length` from the file's recorded metadata. Neither buffers the payload.

The record is resolved by `fileId` and its `workflowId` must match the path — the persisted `FileModel` remains the source of truth for `storagePath`, which is never taken from the request.

## Production requires a shared filesystem

`CONDUCTOR` stores bytes on the server's filesystem. Signed or not, an HTTPS URL fixes *transfer*, not *storage locality*: behind a load balancer, node A writes the object and node B cannot serve the download.

**This is the operator's responsibility, and it is a documented requirement, not something the server can paper over.** Any multi-node deployment using `type=conductor` must point `conductor.file-storage.conductor.directory` at a filesystem shared by every node — NFS, EFS, or a `ReadWriteMany` PVC. A single-node deployment needs nothing.

Two things follow:

- The server should log a warning at startup when `type=conductor` is configured, naming the directory and stating the requirement. It cannot detect replication itself, so a warning is the honest ceiling.
- `ConductorFileStorage` must commit atomically — write to a unique `.part` sibling, then `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`. This matters more on a shared filesystem than a local one, and it matters regardless because `getStorageFileInfo` treats bare existence as success: without an atomic commit an interrupted upload leaves a truncated file that `confirmUpload` marks `UPLOADED`. That hazard exists today.

Anyone who does not want to run a shared filesystem should use S3, GCS, or Azure Blob. The deployment guide should say so plainly rather than presenting the four backends as equivalent.

## Signed URLs: optional, default off

A signature on the content URL is **not** about keeping the URL secret. OSS Conductor has no authentication, so anyone who can reach `/api/files/{wf}/{id}/download-url` can already obtain the URL and the bytes; a signature adds nothing there. Default it off.

It earns its place for exactly one reason, worth recording so the option is not mistaken for security theatre: **it is how an auth-stripped transfer client gets authorized.** The SDK deliberately transfers signed URLs over a raw HTTP client with no Conductor credentials, cookies, or interceptors — `docs/design/file-storage.md` requires that, because a presigned URL is a bearer credential that must not receive Conductor headers. In a deployment that *does* front `/api/**` with auth, an unsigned content endpoint therefore leaves only bad options: exempt the path and it is fully public, or require auth and the raw client gets a 401. A signature is the third option — it substitutes for the auth header on that one path.

So: implement it, default it off, document it as the switch to flip if Conductor is authenticated or internet-exposed.

### Scheme, when enabled

```
{base}/api/files/content/{workflowId}/{fileId}?op=upload&exp=<unix>&kid=k1&sig=<base64url HMAC-SHA256>
```

Canonical string — newline-joined, fixed field order, version-prefixed so it can evolve:

```text
v1
<op>
<workflowId>
<fileId>
<exp>
<uploadId or empty>
<partNumber or empty>
```

Scheme, host, and port are deliberately **not** signed, so an ingress that rewrites the host does not invalidate the signature.

Verification: look up the key by `kid` (unknown → 403); recompute and compare with `MessageDigest.isEqual` for constant time (mismatch → 403); check `exp > now` (expired → 403); check `op` matches the HTTP method (mismatch → 405 — without this a download URL is also an upload URL).

Keys are a list: sign with the first, verify against all. That is the whole rotation story. When signing is enabled and no key is configured, startup fails — a per-process generated secret would be unverifiable on a sibling node and dead after a restart, which surfaces as intermittent 403s behind a load balancer.

The repository has no HMAC helper today (`core`, `common`, and `rest` contain no `Mac.getInstance` or `MessageDigest.isEqual`), so this is a new, small, self-contained utility.

## Absolute URLs and the base

The URL must be absolute. Default: derive per request via `ServletUriComponentsBuilder`, with `ForwardedHeaderFilter` registered so `X-Forwarded-Proto` and `-Host` are honoured. That filter is **not** registered today — no `ForwardedHeaderFilter` or `forward-headers-strategy` anywhere in `server/`, `rest/`, or `docker/server/config` — so it is part of this work, and getting it wrong hands clients URLs pointing at an internal hostname.

`conductor.file-storage.conductor.base-url` overrides it where the inbound request cannot describe the public origin.

## Properties

| Property | Default | Meaning |
|---|---|---|
| `conductor.file-storage.type` | `conductor` | Was `local`. |
| `conductor.file-storage.signed-url-expiration` | `60s` | Existing property; also the signature TTL when signing is on. |
| `conductor.file-storage.conductor.directory` | `${java.io.tmpdir}/conductor/files-uploaded` | Where bytes land. Was `conductor.file-storage.local.directory`. Must be shared across nodes in a multi-node deployment. |
| `conductor.file-storage.conductor.base-url` | *(derived from the request)* | Public origin override. |
| `conductor.file-storage.conductor.max-size` | `104857600` (100 MiB) | Rejected while streaming → 413. |
| `conductor.file-storage.conductor.signing.enabled` | `false` | See above. |
| `conductor.file-storage.conductor.signing.keys[n].{id,secret}` | *(required when signing is on)* | Verify against all, sign with the first. |

## Security

- **Size limits.** `FileUploadRequest` has no `fileSize` field (`fileName`, `contentType`, `workflowId`, `taskId` only) and `FileClient` sends none, so there is no declared size to check against. `max-size` is enforced while streaming and the transfer aborted once exceeded. `FileStorageException` already maps to `413` in `ApplicationExceptionMapper`, so the status is right for free.
- **Path traversal.** `storagePath` comes from the persisted `FileModel`, never the request, and is server-generated as `conductor/{workflowId}/{uuid}`. The storage implementation should still normalize the resolved path and reject anything escaping the base directory, so a corrupted metadata row cannot become an arbitrary-write primitive.
- **Concurrency.** Two uploads for one `fileId` race to the same `storagePath`; unique `.part` names plus the atomic move make the result one payload or the other, never a mix.
- **Exposure is unchanged from today.** The content endpoint is as reachable as the rest of `/api` — no more, no less. `LOCAL` had *no* authorization on its `file:` path either. Deployments that need real protection should enable signing, or use S3/GCS/Azure where the presigned URL model already applies.
- **When signing is on**, the URL is a bearer credential and inherits the existing rules from `docs/design/file-storage.md`: never logged, never in exception messages. It is replayable until `exp`, exactly as an S3 presigned URL is; single-use would need server-side nonce state and is not proposed.

## No data migration

There is no production usage of file storage today, so `LOCAL` is simply removed rather than aliased. What that touches:

- `StorageType.valueOf(rs.getString("storage_type"))` in the MySQL, Postgres, and SQLite DAOs, plus Cassandra's JSON blob — all read back rows written by the same build, so nothing to convert.
- Seven `docker/server/config/*.properties` files set `conductor.file-storage.type=local`; they change to `conductor`.
- Test fixtures: `StubFileStorage`, `FileStorageServiceImplTest` (three assertions), `LocalFileStorageTest`, and `FileStorageIntegrationTest`'s `type=local` property.
- Module and class rename: `local-file-storage` → `conductor-file-storage`, `LocalFileStorage` → `ConductorFileStorage`, `LocalFileStorageProperties` likewise.

A stale `type=local` in someone's config should fail at startup with a message naming `conductor`, not fall through to a missing-bean error.

## Multipart

`LOCAL` never supported multipart: `supportsMultipart()` is `false` in the SDK's local adapter, and `LocalFileStorage.generatePartUploadUrl` returns the same path for every part, so parts would have overwritten each other.

`CONDUCTOR` can support it properly — the canonical string already reserves `uploadId` and `partNumber` — but it needs a dedicated SDK adapter, because `GenericHttpFileTransferAdapter.supportsMultipart()` is `false`. Until then, `CONDUCTOR` uploads are single-`PUT` and bounded by `max-size`. Not in scope; the signing scheme is designed so it does not have to change when multipart is added.

## Testing

Unit — storage:
- Atomic commit: an aborted stream leaves no object at `storagePath`.
- A `storagePath` escaping the base directory is rejected.
- `getStorageType()` returns `CONDUCTOR`.

Unit — signing (when enabled):
- Round-trip sign/verify; tampering with each signed field fails.
- Expired `exp` → 403; unknown `kid` → 403; `op`/method mismatch → 405.
- Verification succeeds across a rotated key list; signing uses the first key.
- Changing scheme/host/port does not invalidate a signature.

MockMvc — `rest`:
- `PUT` content by the owning workflow → `UPLOADED`; a `workflowId` that does not own the file → 403.
- Unknown `fileId` → 404.
- Over `max-size` → 413, no object left behind.
- `GET` content before upload → 400, matching `getDownloadUrl`'s existing rule.
- With signing on: unsigned request → 403; download signature used for `PUT` → 405.

E2E — the acceptance criterion:
- The three failing cases pass against the **containerized** server with no bind mount, **using the current SDK**.
- All 11 still pass against the host JVM.
- `FileStorageE2ETest`'s class javadoc claims "a bind mount shares the server's storage directory with the host". No compose file in `docker/` mounts that directory; the claim is wrong and gets deleted.

Neither unit nor MockMvc tests can catch this class of bug — both run in-process, where client and server always share a filesystem. Only the container e2e run distinguishes them, which is why it is the acceptance criterion.

## Work breakdown

| # | Change | Module |
|---|---|---|
| 1 | `StorageType`: `LOCAL` → `CONDUCTOR` | model |
| 2 | `conductor.file-storage.conductor.*` properties; startup warning about the shared-filesystem requirement | `core` |
| 3 | `ConductorFileStorage`: HTTPS URLs, streaming read/write, atomic commit, traversal guard | `conductor-file-storage` (renamed) |
| 4 | `GET`/`PUT /api/files/content/{workflowId}/{fileId}` | `rest` |
| 5 | `ForwardedHeaderFilter` + base-URL derivation | `server` |
| 6 | Optional HMAC signing: utility, config, verification filter | `core`, `rest` |
| 7 | `type=local` fails fast with a message naming `conductor`; update seven `docker/server/config` files and the test fixtures | `server`, `docker`, tests |
| 8 | Unit + MockMvc tests | `core`, `rest`, `conductor-file-storage` |
| 9 | Docs: `advanced/file-storage.md`, `api/files.md`, narrow the direct-transfer goal in `design/file-storage.md` | `docs` |
| 10 | *Optional later:* `ConductorFileTransferAdapter` with multipart | `java-sdk` *(separate repo)* |

1–9 are one server PR, and the e2e assertions flip green on that PR alone — no SDK change needed. 6 can be deferred without blocking anything. 10 is a follow-up.

## Compatibility

| Client | Result |
|---|---|
| Current SDK, `CONDUCTOR` backend | Works via `GenericHttpFileTransferAdapter`, co-located or not. |
| Current SDK, S3/Azure/GCS | Unchanged. |
| Existing `type=local` config | Fails at startup with a message naming `conductor`. |

The workflow-visible contract (`conductor://file/<id>`) does not change, so no workflow definition or worker is affected.
