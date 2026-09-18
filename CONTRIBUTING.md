# Contributing
Thanks for your interest in Conductor!
This guide helps to find the most efficient way to contribute, ask questions, and report issues.

Code of conduct
-----

Please review our [Code of Conduct](CODE_OF_CONDUCT.md)

I have a question!
-----

We have a dedicated [Slack](https://join.slack.com/t/orkes-conductor/shared_invite/zt-3dpcskdyd-W895bJDm8psAV7viYG3jFA#/shared-invite/email) channel for asking "how to" questions and to discuss ideas. The channel is a great place to start if you're considering creating a feature request or work on a Pull Request.
*Please do not create issues to ask questions.*

I want to contribute!
------

We welcome Pull Requests and already have many outstanding community contributions!
Creating and reviewing Pull Requests takes time, so this section helps you to set up a smooth Pull Request experience.

### Issue-first policy

**Before writing any code, please file a GitHub issue and discuss your proposed change with the maintainers.**

This applies to all external contributors for bug fixes, features, and improvements of any size. Here's why this matters:

- We may already be working on the same thing, or have decided not to pursue it.
- The best solution often looks different from the first idea — a short discussion saves everyone from throw-away work.
- We need to agree on the approach before implementation begins, not after.

**Pull Requests submitted without a prior issue discussion will be closed at the maintainers' discretion.** Commenting "I'll take this" on an issue is not sufficient — please wait for a maintainer to confirm the approach before opening a PR.

The stable branch is [main](https://github.com/conductor-oss/conductor/tree/main).

Please create pull requests for your contributions against [main](https://github.com/conductor-oss/conductor/tree/main) only.

Also, consider that not every feature is a good fit for Conductor. A few things to consider are:

* Is it increasing complexity for the user, or might it be confusing?
* Does it, in any way, break backward compatibility (this is seldom acceptable)
* Does it require new dependencies (this is rarely acceptable for core modules)
* Should the feature be opt-in or enabled by default. For integration with a new Queuing recipe or persistence module, a separate module which can be optionally enabled is the right choice.
* Should the feature be implemented in the main Conductor repository, or would it be better to set up a separate repository? Especially for integration with other systems, a separate repository is often the right choice because the life-cycle of it will be different.
* Is it part of the Conductor project roadmap?

You can also discuss ideas in the [Slack channel](https://join.slack.com/t/orkes-conductor/shared_invite/zt-3dpcskdyd-W895bJDm8psAV7viYG3jFA#/shared-invite/email) before filing an issue — that's a great place to get informal feedback early.

We'll try to be responsive to issues and Pull Requests. Do keep in mind that because of the inherently distributed nature of open source projects, responses might take some time because of time zones, weekends, and other things we may be working on.

I want to report an issue
-----

If you found a bug, please create an issue at https://github.com/conductor-oss/conductor/issues/new. Include clear instructions on how to reproduce the issue, or even better, include a test case on a branch. Make sure to come up with a descriptive title for the issue because this helps while organizing issues.

I have a great idea for a new feature
----
Many features in Conductor have come from ideas from the community. If you think something is missing or certain use cases could be supported better, let us know! 

You can do so by starting a discussion in the [Slack channel](https://join.slack.com/t/orkes-conductor/shared_invite/zt-3dpcskdyd-W895bJDm8psAV7viYG3jFA#/shared-invite/email). Provide as much relevant context to why and when the feature would be helpful. Providing context is especially important for "Support XYZ" issues since we might not be familiar with what "XYZ" is and why it's useful. If you have an idea of how to implement the feature, include that as well.

Once we have decided on a direction, it's time to summarize the idea by creating a new issue.

## Code Style
We use [spotless](https://github.com/diffplug/spotless) to enforce consistent code style for the project, so make sure to run `gradlew spotlessApply` to fix any violations after code changes.

## License
All files are released with the [Apache 2.0 license](LICENSE).


