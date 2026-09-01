Pull Request type
----
- [ ] Bugfix
- [ ] Feature
- [ ] Refactoring (no functional changes, no api changes)
- [ ] Build related changes
- [ ] WHOSUSING.md
- [ ] Other (please describe):

**NOTE**: Please remember to run `./gradlew spotlessApply` to fix any format violations.

Changes in this PR
----

_Describe the new behavior from this PR, and why it's needed_
Issue #

Alternatives considered
----

_Describe alternative implementation you have considered_

Enterprise UI Playwright Tests
----
PRs that touch `ui-next/` trigger the enterprise UI Playwright suite in
[orkes-io/conductor-ui](https://github.com/orkes-io/conductor-ui/actions).
Pass/fail is posted as a comment on this PR (not a commit status) and
does not gate merge. Fork PRs skip dispatch (no secret). Tests run against
conductor-ui `main` by default. To use a different conductor-ui branch,
add this line anywhere in the PR description:

```
conductor-ui-branch: my-feature-branch
```
