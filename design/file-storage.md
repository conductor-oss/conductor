# File Storage Design

## Status

Implemented by the workflow-scoped File API in Conductor and `FileClient` in the Java SDK.

## Goals

- Keep binary payloads out of workflow JSON.
- Make every file operation carry workflow context.
- Represent files in workflow data as opaque `conductor://file/<id>` strings.
- Transfer bytes directly to storage without proxying them through Conductor.
- Support retryable, repeatable uploads and crash-safe downloads.
- Keep provider protocol details out of worker code.

## Non-goals

- Smart file objects that the task runner discovers and uploads implicitly.
- A public SDK storage-backend extension point.
- Forwarding Conductor credentials to object storage.
- Treating GCS signed PUT uploads as a resumable multipart protocol.

## Components

```text
Worker
  |
  | FileClient + workflow ID + opaque handle
  v
Workflow-scoped File API
  |
  +--> FileStorageService --> FileMetadataDAO
  |
  +--> FileStorage --> signed URL / storage metadata / multipart mutation

FileClient -- raw signed request --> S3, Azure Blob, GCS, or local filesystem
```

The server decides identity, ownership, storage location, URL lifetime, and upload state. `FileClient` orchestrates the workflow API and byte transfers. Package-private transfer adapters implement exactly one provider transfer attempt; they do not own retries or lifecycle state.

## Public contract

The file value passed through a workflow is always a string:

```text
conductor://file/<id>
```

Provider URLs, bucket paths, filenames, and SDK-specific objects are not part of this contract. This keeps workflow definitions portable across storage providers.

Each call also carries a workflow ID. Upload creation and every upload mutation require the exact owner. Metadata and downloads accept a member of the owner's workflow family so parent and child workflows can exchange files.

## Upload lifecycle

```text
create metadata (UPLOADING)
  --> transfer bytes
  --> complete and verify storage object
  --> metadata (UPLOADED)
```

For streams, the client first buffers to a temporary path. This happens before creation so a local disk failure does not leave an orphaned server record, and it makes every retry repeatable. The caller owns the stream and remains responsible for closing it.

The client selects multipart when `size > multipartThreshold` and the adapter supports it:

```text
initiate
  --> get fresh URL + upload exact byte range for part 1
  --> ...
  --> get fresh URL + upload exact byte range for part N
  --> complete ordered token list
```

If any multipart step fails, the client requests a best-effort abort. S3 has an explicit abort operation; Azure uncommitted blocks expire.

## Provider rules

| Provider | Whole-file rule | Multipart rule |
|---|---|---|
| S3 | Signed PUT. | Each part must return a non-blank `ETag`; ordered ETags complete the upload. |
| Azure Blob | Signed PUT with `x-ms-blob-type: BlockBlob`. | Stable Base64 block IDs; part URLs add `comp=block&blockid=...`; ordered IDs are committed. |
| GCS | Signed PUT. | Disabled until a real resumable protocol is implemented. |
| Local | `file:` URL copy. | Not supported. |
| Unknown | Generic HTTP(S) signed PUT/GET when the server supplies such a URL. | Not supported. |

Range bodies use a positioned file channel and must emit exactly the requested length. A short read is a failure rather than a silently truncated part.

## Retry and completion semantics

Adapters make one attempt. `FileClient` retries only transient transport failures, throttling, expired signatures, and server errors. It asks Conductor for a new signed URL before each retry and preserves thread interruption.

Completion can be ambiguous: storage may be committed even if the response is lost. After a completion error, the client reads workflow-scoped metadata. `UPLOADED` means the operation succeeded; otherwise normal retry rules apply.

## Download lifecycle

The client validates the destination before requesting a signed URL, downloads to a unique sibling `.part` file, and atomically replaces the requested path only after success. A failed transfer removes the temporary file and leaves an existing destination unchanged. Filesystems without atomic replacement support fail explicitly.

## Signed URL security boundary

Signed URLs are bearer credentials. They must not appear in exceptions or logs. Signed requests use a separate raw HTTP client with redirects disabled and no Conductor authentication, cookie jar, or application interceptors. The server API client and storage-transfer client therefore have separate trust boundaries.

## Compatibility

Workflow-scoped routes replace unscoped mutation and metadata routes. Smart SDK file types and automatic task-runner uploads are removed. Workers must exchange raw handle strings and invoke `FileClient` explicitly. Because the serialized workflow shape changed from an object to a string, mixed worker versions are not wire-compatible and require a coordinated rollout.
