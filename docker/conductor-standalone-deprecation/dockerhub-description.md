# conductoross/conductor-standalone

> [!WARNING]
> **DEPRECATED — DO NOT USE**
>
> This image has not been updated since **December 2023** (pinned at Conductor **3.15.0**).
> The current, actively maintained image is [`conductoross/conductor`](https://hub.docker.com/r/conductoross/conductor).
>
> For a working one-command local setup, see the [getting-started guide](https://github.com/conductor-oss/getting-started).

---

## Migration

Replace any reference to `conductoross/conductor-standalone` with:

```bash
docker pull conductoross/conductor:latest
```

For a full local setup (UI + server), use the Docker Compose file from the main repo:

```bash
git clone https://github.com/conductor-oss/conductor.git
cd conductor/docker
docker compose up
```

## Tags

| Tag | Description |
|-----|-------------|
| `:deprecated` | Alpine-based image that prints a deprecation notice and exits with code 1. Useful as a drop-in to make CI pipelines self-document the migration. |

All older tags (`:3.15.0`, `:latest` prior to 2026) point at a Conductor 3.15.0 build that is no longer supported.

## Why was this deprecated?

`conductor-standalone` was intentionally deprecated in favor of consolidating on a single image. `conductoross/conductor` now supports SQLite out of the box, providing the same zero-dependency local experience without a separate image to maintain.

## Links

- [conductoross/conductor on Docker Hub](https://hub.docker.com/r/conductoross/conductor)
- [Getting started guide](https://github.com/conductor-oss/getting-started)
- [Conductor OSS on GitHub](https://github.com/conductor-oss/conductor)
