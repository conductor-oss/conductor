---
description: "Conventions for Conductor contributions — interface-first design, module boundaries, Spotless formatting, tests without mocks, dependency pinning, and PR hygiene."
---

# Contribution Best Practices

These are the conventions the project actually enforces. Following them is the difference between a review about your change and a review about formatting.

## Before you write code

**Discuss anything non-trivial first.** A feature has usually more than one plausible design, and the cheapest place to compare them is a [discussion](https://github.com/conductor-oss/conductor/discussions) rather than a finished pull request. Showing an idea in code is welcome — just know it may be throw-away work.

**Consider whether it belongs in the core.** Not every feature does. Weigh:

- Does it add complexity or confusion for users who do not need it?
- Does it break backward compatibility? This is seldom acceptable.
- Does it add a dependency to a core module? This is rarely acceptable.
- Should it be opt-in? A new queue or persistence backend belongs in a separate, optionally-enabled module.
- Should it be a separate repository altogether? Integrations with other systems often should be, because their lifecycle differs from the server's.

## Code style

- **Run Spotless before committing.** `./gradlew spotlessApply`. CI fails on formatting, and a formatting-only diff buries the real change.
- **Design against interfaces.** Conductor is pluggable by design; new concepts should be introduced as an interface with implementations behind it.
- **Respect module boundaries.** DAO interfaces belong in `core`. Implementations belong in their own persistence module — `postgres-persistence`, `redis-persistence`, and so on. A `core` class must not reach into a specific backend.
- **Follow the surrounding code.** Match the naming, structure, and comment density of the file you are editing rather than importing a different house style.
- **Comment the non-obvious.** Explain the algorithm, the ordering constraint, the reason a check exists. Skip comments that restate the code.
- **No emojis** in code, logs, or comments.

## Testing

- **Avoid mocks.** Use real implementations wherever possible. A test built from mocks tends to assert that the mocks were called, not that the code works.
- **Test behaviour, not structure.** A test that re-implements the logic it is checking passes for the wrong reasons and fails whenever the implementation is refactored.
- **Use Testcontainers** for databases, caches, and other external dependencies.
- **Cover concurrency.** Much of the engine is multi-threaded; single-threaded tests miss its most important failure modes.
- **`./gradlew test` must pass** before you push.

One thing worth internalising: some bugs only appear across a process boundary. A behaviour that works in an in-process test can still be broken in a deployed server, because the test shares a filesystem, a JVM, and a clock with the code under test. If a change touches something that crosses that boundary, prove it with an integration or end-to-end test.

## Dependency pinning

Some dependencies are pinned deliberately and must not be bumped as a matter of routine. `AGENTS.md` in the repo root records the current hard pins and the reason for each, along with what to check before changing one. Read it before adding or upgrading a dependency — an incidental version bump in an unrelated pull request is a common reason for a request for changes.

## Pull requests

- **Target `main`.** It is the stable branch and the only PR target.
- **One logical change per PR.** A focused diff is reviewed in one pass; a PR that fixes a bug, reformats a file, and bumps Gradle gets stuck on the part nobody asked for.
- **Add or update tests** for any code change.
- **Run `./gradlew spotlessApply` and `./gradlew test`** before pushing.
- **Write a descriptive commit message.** Say what changed and why. The why is the part a reader cannot reconstruct from the diff.

Reviews can take time. The project is distributed across time zones, and maintainers have other work — a delay is not disinterest.

## Documentation

Documentation is derived from source, not written from memory. To document an endpoint, open the controller and copy the path from its mapping annotation. To document a CLI flag, open the command and read its flag declarations. To show output, run the thing and paste what it printed.

If you cannot verify an example — no server available, no credentials — mark it with `<!-- TODO: verify against live server -->` and say so in the pull request rather than leaving an unverified example looking verified.

`CLAUDE.md` in the repo root has the per-content-type checklist and the source locations for each kind of page.

## License

Contributions are licensed under Apache 2.0. Every file carries the standard header, which Spotless adds automatically if it is missing. See [Contribution Guide](../contributing.md#license) for the exact text.

## Related pages

- [Contribution Guide](../contributing.md)
- [Repositories](repositories.md)
- [Code of Conduct](code-of-conduct.md)
- [Build from source](../../devguide/running/source.md)
