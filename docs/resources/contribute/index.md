---
description: "Contribute to Conductor — star and fork the repo, find a good first issue, and learn how the project is organised across its repositories."
---

# Contribute to Conductor

Conductor is Apache 2.0 licensed and developed in the open. Server features, SDKs, docs, and the CLI all live in public repositories, and a large share of what ships comes from the community.

<div class="grid cards" markdown>

-   **Star the repo**

    The fastest way to help, and how most people find the project. [Star conductor-oss/conductor](https://github.com/conductor-oss/conductor).

-   **Fork and build**

    Clone, build, and run the server locally before your first change. Start with [Build from source](../../devguide/running/source.md).

-   **Find a good first issue**

    Issues triaged as approachable, with enough context to start. Browse [good first issue](https://github.com/conductor-oss/conductor/labels/good%20first%20issue).

-   **Ask before you build**

    For anything non-trivial, open a [discussion](https://github.com/conductor-oss/conductor/discussions) first. It saves rework.

</div>

## Ways to contribute

Code is the obvious one, and not the only one that matters.

| | Where it goes |
|---|---|
| **Fix a bug** | The repo that owns the code — see [Repositories](repositories.md) |
| **Add a persistence or queue backend** | A new module in the server repo, opt-in by configuration |
| **Improve an SDK** | The language's own repo |
| **Fix or extend the docs** | `docs/` in the server repo |
| **Report a bug** | [Issues](https://github.com/conductor-oss/conductor/issues), with steps to reproduce |
| **Propose a feature** | [Discussions](https://github.com/conductor-oss/conductor/discussions) first, then an issue |
| **Answer a question** | [Discussions](https://github.com/conductor-oss/conductor/discussions) or [Slack](get-help.md) |
| **Report a vulnerability** | Privately — see [Get Help](get-help.md#security-issues) |

Documentation contributions are worth calling out. Docs here are derived from source rather than written from memory, so a doc fix usually means opening the controller or SDK method and correcting the page to match what the code actually does. That makes docs an unusually good first contribution: you learn the codebase while fixing something real.

## Before your first pull request

1. **Build it locally.** [Build from source](../../devguide/running/source.md), then `./gradlew test`.
2. **Discuss anything non-trivial.** A feature discussed first is a feature that gets merged. See [Contribution Guide](../contributing.md).
3. **Read the conventions.** Interface-first design, DAO interfaces in `core`, Spotless formatting, tests without mocks — [Best Practices](best-practices.md).
4. **Target `main`.** It is the stable branch and the only PR target.

## Related pages

- [Repositories](repositories.md)
- [Contribution Guide](../contributing.md)
- [Best Practices](best-practices.md)
- [Code of Conduct](code-of-conduct.md)
- [Get Help](get-help.md)
