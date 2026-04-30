# Plan: Ship file-storage docs

## Context

The file-storage feature is implemented on `feature/file-storage-wip` (server in `file-storage-conductor`, SDK in `file-storage-java-sdk`) but has **zero user-facing documentation**. The only file-storage prose in `docs/` is the internal pair `docs/file-storage/{spec.md, design.md}` — design notes, not docs site content. None are wired into `mkdocs.yml`. The spec's "Documentation Placement" table is aspirational (paths like `docs/documentation/quickstart/file-storage.md` don't match the real subdirectory layout).

Goal: ship a small, native-feeling set of pages that lets a Conductor user discover the feature, configure a backend, write a worker that consumes/produces files, and call the REST API directly when needed — and wire those pages into `mkdocs.yml`. **Match the patterns existing features already use; don't invent new ones.**

## How docs ship in this repo (what we found)

- **MkDocs Material** site, source at `docs/`, config at `mkdocs.yml`. Plugins: `search`, `macros`. Markdown extensions enable admonitions, mermaid (via `pymdownx.superfences`), language tabs (`pymdownx.tabbed`), snippets, details, code copy.
- **Real content roots** (vs. what the spec assumed):
  - `docs/documentation/api/` — REST endpoint reference (`workflow.md`, `task.md`, etc.) — every feature with REST endpoints gets a standalone page here, wired into `Reference → API` in nav.
  - `docs/documentation/clientsdks/` — per-language SDK pages (`java-sdk.md`, …). **Spec wrongly called this `sdks/`**. Worker code patterns for any feature live here, in the SDK page.
  - `docs/documentation/advanced/` — operator-facing infra/integration pages (`externalpayloadstorage.md`, `redis.md`, …). Each covers feature flag, config tables, and per-backend setup in one page.
  - `docs/documentation/configuration/appconf.md` — system config table.
  - There is **no** `documentation/quickstart/`, `documentation/sdks/`, or `documentation/guides/` directory.
- **Established two-page pattern for a feature with REST + storage backends**: the closest precedent is **External Payload Storage**, which ships exactly two doc artefacts:
  1. `docs/documentation/advanced/externalpayloadstorage.md` — operator setup + per-backend property tables (Amazon S3, Azure Blob, Postgres). No code samples; relies on SDKs for that.
  2. Behavior is mentioned in passing on the Java SDK page; there's no separate REST API page for it (no public REST surface).
  File storage *does* have a public REST surface (`/api/files/*`), so it also needs an API page (matching `api/task.md`, `api/workflow.md`).
- **Frontmatter convention**: every page starts with `---\ndescription: "…"\n---`.
- **Examples live in the SDK repo**: `file-storage-java-sdk/examples/file-storage/media-transcoder/` (Maven, full app). Doc pages link out, like the existing Java SDK page does for other examples.
- **Negative precedent**: `docs/wmq/workflow-message-queue.md` + `…-architecture.md` are **orphan pages — not wired into `mkdocs.yml` nav**. We won't repeat that.

## Pages to ship

Two new pages + one extension to an existing page + nav wiring. **No standalone "guides" page** — that's not how other features ship; usage notes go on the Advanced page (operator-leaning) and on the Java SDK page (worker-code-leaning).

### 1. `docs/documentation/advanced/file-storage.md` *(new — operator/admin)*

Mirror of `externalpayloadstorage.md`. Single most important page. Sections:
- Frontmatter `description:`
- Context: what file storage is, when to use it (vs. external payload storage — "for actual file content; payload storage is for oversized JSON")
- Feature flag: `conductor.file-storage.enabled=false` by default; nothing registers when off
- Backends list: `local` (default), `s3`, `azure-blob`, `gcs`, plus BYOS pointer
- One subsection per backend with property tables generated from real classes:
  - `core/.../FileStorageProperties.java` (`enabled`, `type`, `signedUrlExpiration`)
  - `awss3-storage/.../S3FileStorageProperties.java`
  - `azureblob-storage/.../AzureBlobFileStorageProperties.java`
  - `gcs-storage/.../GcsFileStorageProperties.java`
  - `local-file-storage/.../LocalFileStorageProperties.java`
- Persistence: `file_metadata` table + Flyway migrations (V15 Postgres / V9 MySQL / V3 SQLite) + Redis/Cassandra notes
- Workflow-family-scoped download access: 1 paragraph (caller's `workflowId` must be in the file's family)
- Note: no per-file size cap in this iteration
- Brief usage pointer to the Java SDK page's "File handling" section + the Media Transcoder example

### 2. `docs/documentation/api/files.md` *(new — REST reference)*

Mirror of `docs/documentation/api/task.md` shape. Verified against `rest/.../FileResource.java` per CLAUDE.md.

One section per endpoint, each with `curl` + JSON sample, both verified against the controller:
- `POST /api/files`
- `GET /api/files/{fileId}/upload-url`
- `POST /api/files/{fileId}/upload-complete`
- `GET /api/files/{workflowId}/{fileId}/download-url` *(workflowId is the caller's, family-scoped)*
- `GET /api/files/{fileId}` — metadata
- `POST /api/files/{fileId}/multipart`
- `GET /api/files/{fileId}/multipart/{uploadId}/part/{partNumber}` (S3 only)
- `POST /api/files/{fileId}/multipart/{uploadId}/complete`
- Errors table — actual exception → status mapping (no invented codes)

### 3. Extend `docs/documentation/clientsdks/java-sdk.md` *(modify, not new)*

Add a "## File handling" section after "## Workers", before "## Monitoring Workers". ~80 lines.
- Brief note on Maven coordinate (already present at the top of the page) — `FileHandler` lives in `org.conductoross.conductor.sdk.file`
- Quick reference for the three types a worker actually touches: `FileHandler`, `FileUploader`, `FileUploadOptions`
- Two short code blocks:
  - Annotated worker that consumes a file: `@WorkerTask process(@InputParam("input") FileHandler input) { … input.getInputStream() … }`
  - Annotated worker that produces one: `return FileHandler.fromLocalFile(out)` (auto-uploaded by TaskRunner)
- One paragraph on `task.getFileUploader().upload(Path, FileUploadOptions)` for explicit uploads inside a `Worker` impl
- Add a Media Transcoder row to the existing "## Examples" table

### 4. `mkdocs.yml` nav wiring *(modify)*

Two new entries, in the slots that match how other features are wired:

```yaml
- Reference:
    - API:
        ...
        - documentation/api/files.md                          # new (alphabetical / after task.md)

- Deploy:
    ...
    - Advanced:
        ...
        - documentation/advanced/file-storage.md              # new (insert near externalpayloadstorage.md)
```

Java SDK page is already in nav under `SDKs:` — no nav change for the section we add to it.

## Authoring conventions to follow (per CLAUDE.md)

- **Verify every REST endpoint** against `FileResource.java` before writing curl. Path is the exact `@RequestMapping` value.
- **Verify every config property** against the relevant `*Properties.java` (defaults, types).
- **Verify every Java snippet** against actual classes — `FileHandler`, `FileUploader`, `FileUploadOptions`, `task.getFileUploader()`, `task.getInputFileHandler(...)`.
- Frontmatter `description:` on every page.
- Property tables: `Property | Description | default value` (matches `externalpayloadstorage.md`).
- Code blocks: ` ```java`, ` ```shell` (curl), ` ```json` (samples), ` ```properties` (config). No language tabs unless we're showing the same example in multiple SDKs.
- Admonitions only when called for; don't sprinkle.
- No new mermaid diagrams unless they materially help; the design doc has them.
- Cross-link with relative paths matching `docs/` layout.

## Critical files

| Purpose | Path |
|---|---|
| Source for REST verification | `rest/src/main/java/org/conductoross/conductor/controllers/FileResource.java` |
| Service-level rules (workflowId required, family scope) | `core/src/main/java/org/conductoross/conductor/core/storage/FileStorageServiceImpl.java` |
| Server config | `core/src/main/java/org/conductoross/conductor/core/storage/FileStorageProperties.java` |
| Per-backend config | `*/src/main/java/org/conductoross/conductor/*/config/*FileStorageProperties.java` |
| SDK FileHandler / static helpers | `file-storage-java-sdk/conductor-client/src/main/java/org/conductoross/conductor/sdk/file/FileHandler.java` |
| SDK FileUploader / FileUploadOptions | same dir, `FileUploader.java`, `FileUploadOptions.java` |
| Closest doc analogue | `docs/documentation/advanced/externalpayloadstorage.md` |
| REST page analogue | `docs/documentation/api/task.md` |
| Java SDK page (to extend) | `docs/documentation/clientsdks/java-sdk.md` |
| Media Transcoder example | `file-storage-java-sdk/examples/file-storage/media-transcoder/` |
| Spec / design (reference, not output) | `docs/file-storage/{spec.md,design.md}` |

## Verification

1. **Build the docs locally**: from the repo root, `pip install mkdocs-material mkdocs-macros-plugin && mkdocs serve`, open `http://127.0.0.1:8000`, click through:
   - Reference → API → File API
   - Deploy → Advanced → File Storage
   - SDKs → Java → "File handling" anchor
   Confirm pages render, code copy buttons work, no broken internal links.
2. **Run `mkdocs build --strict`** — fails on broken links, missing nav refs, or markdown errors. Must pass.
3. **Spot-check curl examples** against a running server (`./gradlew bootRun` with `conductor.file-storage.enabled=true conductor.file-storage.type=local`):
   - `curl -X POST http://localhost:8080/api/files -H 'Content-Type: application/json' -d '{"workflowId":"wf-test","fileName":"x.txt","contentType":"text/plain"}'` returns 201 with `fileHandleId`
   - `curl http://localhost:8080/api/files/{fileId}` returns metadata
4. **Sanity-check the Java code blocks** against the actual `FileHandler`/`FileUploader` signatures — must use real method names, not paraphrases.

## Out of scope

- Other-language SDK pages (Python, Go, JS, C#, Ruby, Rust): no SDK implementation for those yet.
- A `quickstart/file-storage.md` page: project doesn't do feature-specific quickstarts.
- Architecture page mirroring `wmq-architecture.md`: `docs/file-storage/{spec.md,design.md}` already serve that role.
- Updating `docs/file-storage/spec.md`'s "Documentation Placement" table — separate cleanup; can be done after these pages land so the table reflects reality.
