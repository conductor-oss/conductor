# Summary

Adds a file storage subsystem to Conductor OSS that lets workers pass large binary files between tasks via presigned
URLs, without routing file bytes through the Conductor server.

- **REST API** (`/api/files`) — create file, get upload/download URLs, confirm upload, initiate/complete multipart upload
- **Workflow-scoped access** — download requests are gated by workflow family (self + ancestors + descendants); a
  configurable `defaultWorkflowId` bypass for shared reference files
- **4 storage backends** — S3, Azure Blob, GCS, Local 
  - feature-flagged, disabled by default via `conductor.file-storage.enabled`
- **5 DAO implementations** — Postgres (`V15`), MySQL (`V9`), SQLite (`V3`), Redis, Cassandra
- **File handle IDs** use `conductor://file/<fileId>` scheme
  - conversion between bare `fileId` (URL path params) and `fileHandleId` (JSON bodies) is handled by `FileIdToFileHandleIdConverter`
- **Multipart upload** support: S3 uses per-part presigned URLs; GCS/Azure use a single resumable URL from initiate

# Key design decisions

- File bytes never pass through the Conductor server — all transfers go directly between the SDK client and the storage backend via presigned URLs
- Files are strictly owned by their creator workflow — download access requires the caller's workflow to be in the file's workflow family (self + ancestors + descendants), resolved by `WorkflowFamilyResolver`
- `storagePath` is server-internal and never exposed in any API response

# Test plan

- [ ] `FileStorageServiceImplTest` — unit tests for all service methods incl. access control
- [ ] `WorkflowFamilyResolverTest` — ancestor/descendant resolution
- [ ] `FileModelConverterTest` — DTO ↔ model conversion
- [ ] `FileResourceTest` — controller layer
- [ ] `LocalFileStorageTest` — local backend
- [ ] Integration tests for S3 (LocalStack), Azure Blob (Azurite), GCS (fake-gcs-server)
- [ ] E2E test suite in `e2e/` against MinIO

# References

- File Storage Design Doc: docs/file-storage/design.md