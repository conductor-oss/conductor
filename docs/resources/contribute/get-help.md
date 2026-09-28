---
description: "Where to get help with Conductor — Slack, GitHub Discussions, issues, and how to report a security vulnerability."
---

# Get Help

Pick the channel that matches what you need. The wrong one mostly costs you time waiting.

<div class="grid cards" markdown>

-   **Slack**

    Real-time questions, quick unblocking, and talking to other users. [Join the Slack community](https://join.slack.com/t/orkes-conductor/shared_invite/zt-3dpcskdyd-W895bJDm8psAV7viYG3jFA).

-   **GitHub Discussions**

    "How do I…" questions, design proposals, and anything worth finding later. [Open a discussion](https://github.com/conductor-oss/conductor/discussions).

-   **GitHub Issues**

    Reproducible bugs, and features already agreed in a discussion. [File an issue](https://github.com/conductor-oss/conductor/issues).

-   **Community forum**

    Longer-form discussion across the wider Conductor community. [community.orkes.io](https://community.orkes.io/).

</div>

## Which channel?

| You want to | Use |
|---|---|
| Ask how something works | [Discussions](https://github.com/conductor-oss/conductor/discussions) or [Slack](https://join.slack.com/t/orkes-conductor/shared_invite/zt-3dpcskdyd-W895bJDm8psAV7viYG3jFA) |
| Report a bug you can reproduce | [Issues](https://github.com/conductor-oss/conductor/issues) |
| Propose a feature | [Discussions](https://github.com/conductor-oss/conductor/discussions) first, then an issue once there is agreement |
| Get a pull request reviewed | Open the PR; mention it in Slack if it goes quiet |
| Report a vulnerability | Privately — see below |

**Please do not open issues to ask questions.** Questions in the issue tracker crowd out actionable bugs and tend to get answered more slowly than the same question in Discussions.

## Writing a good bug report

The difference between a bug that gets fixed and one that sits is almost always the report:

- **What you did**, precisely enough to repeat — the workflow definition, the API call, the configuration.
- **What happened**, including the actual error and stack trace, not a paraphrase.
- **What you expected** instead.
- **Your setup**: Conductor version, `conductor.db.type`, `conductor.queue.type`, and how you are running it.
- **A failing test on a branch**, if you can manage it. Nothing shortens the round trip more.

Configuration matters more than people expect. Several classes of bug only appear in particular combinations — one database with a different queue backend, or a containerized server with clients on another host — so a report that omits the backends can be impossible to reproduce.

## Security issues

Do not report vulnerabilities in a public issue, discussion, or Slack channel. Follow the private disclosure process in [`SECURITY.md`](https://github.com/conductor-oss/conductor/blob/main/SECURITY.md) so a fix can ship before the details are public.

## Related pages

- [Contribute overview](index.md)
- [Contribution Guide](../contributing.md)
- [Code of Conduct](code-of-conduct.md)
- [FAQ](../../devguide/faq.md)
- [Debugging Workflows](../../devguide/how-tos/Workflows/debugging-workflows.md)
