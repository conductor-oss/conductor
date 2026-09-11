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
Every PR automatically triggers the enterprise UI Playwright E2E test suite.
Tests run against conductor-ui `main` by default. To test against a different
conductor-ui branch, add this line anywhere in the PR description:

```
conductor-ui-branch: my-feature-branch
```
