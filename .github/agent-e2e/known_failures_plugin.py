"""Known-failure xfail loader for the agent (conductor-ai) python e2e suite.

The e2e suite is shared: the SAME tests (this repo's python SDK e2e, shipped as the
`conductor-ai-e2e-python-*` bundle from conductor-oss/python-sdk) run against multiple
targets. A test can be a known failure on one target and pass on another, so skip lists are
kept per target and selected via the E2E_KNOWN_FAILURES env var. This file is the loader; the
conductor-oss list lives in known-failures-python.json (empty when the suite is green).

It runs as an external pytest plugin (`-p known_failures_plugin`) so it composes with the
downloaded bundle's own conftest WITHOUT modifying the bundle.

Point E2E_KNOWN_FAILURES at a JSON object mapping a test node-id (or a "<file>::<test>" suffix
of one) to a human-readable reason. Matched tests are marked xfail(strict=False, run=True):
they still RUN, a failure reports as XFAIL (green), and a fix XPASSes — the signal to delete
the entry. Keys that match nothing are harmless no-ops (the test runs and the gate still
catches a real break), so a stale entry can never silently hide a regression. Keys beginning
with "_" (e.g. "_README") are treated as comments and ignored.

That no-op guarantee depends on matching being ANCHORED at node-id component boundaries —
see _matches(). A key is only ever under-inclusive, never over-inclusive: it can fail to
match the test you meant, but it cannot pick up a test you did not mean.
"""

import json
import os
import warnings

import pytest


class KnownFailuresWarning(UserWarning):
    """An entry matched no test, or matched more than one."""


def _load_known_failures():
    """Return {nodeid_suffix: (reason, run)}.

    Each JSON value may be either a plain string (the reason; the test still
    RUNS so a fix XPASSes) or an object {"reason": ..., "run": false} to xfail
    WITHOUT executing the test — use run:false for a deterministic hang that
    would otherwise burn CI time every run (you then un-list it manually when
    fixed, since a non-run xfail can't XPASS). Keys starting with "_" are
    comments and ignored.
    """
    path = os.environ.get("E2E_KNOWN_FAILURES")
    if not path or not os.path.exists(path):
        return {}
    with open(path) as f:
        data = json.load(f)
    out = {}
    for k, v in data.items():
        if k.startswith("_"):
            continue
        if isinstance(v, dict):
            out[k] = (str(v.get("reason", "")), bool(v.get("run", True)))
        else:
            out[k] = (str(v), True)
    return out


def _matches(nodeid, suffix):
    # The suite appends an xdist loadgroup label as "@<group>" to some node-ids
    # (e.g. test_mcp_lifecycle@credentials). Match against both the raw node-id and
    # the label-stripped base so entries can be written either way.
    #
    # Every arm is ANCHORED at a node-id component boundary ("::" or "/"), never a bare
    # endswith. A bare suffix test would let a key like "_completes" match every test whose
    # name happens to end that way, quietly xfail-ing unrelated tests — the exact
    # hide-a-regression failure this file claims to be immune to. The "/" arm is what makes
    # the common "<file>.py::<Class>::<test>" key match a node-id of
    # "e2e/<file>.py::<Class>::<test>"; without it those keys match nothing at all.
    for nid in (nodeid, nodeid.split("@", 1)[0]):
        for suf in (suffix, suffix.split("@", 1)[0]):
            if nid == suf or nid.endswith("::" + suf) or nid.endswith("/" + suf):
                return True
    return False


def _report(config, counts, marked):
    """Surface per-key match counts, and complain about keys that matched 0 or >1 tests.

    Two channels, because neither alone is sufficient:

    * warnings — xdist forwards these from workers to the controller, so they reach the log
      even though terminal writes from a worker do not. This is the channel that matters for
      the actual CI invocation (`run.sh` uses `-n 3`).
    * terminal lines — richer, but only reachable where a terminalreporter exists: a non-xdist
      run (written inline) or the xdist controller (written from pytest_testnodedown below).
    """
    path = os.environ.get("E2E_KNOWN_FAILURES")
    lines = [f"[known-failures] xfail-marked {marked} item(s) from {path}"]
    for suffix, n in sorted(counts.items()):
        if n == 0:
            note = "   <-- MATCHED NOTHING (stale or typo'd key; it is doing nothing)"
        elif n > 1:
            note = "   <-- matched >1 test (over-broad key?)"
        else:
            note = ""
        lines.append(f"[known-failures]   {n}x  {suffix}{note}")

    for suffix, n in sorted(counts.items()):
        if n == 0:
            warnings.warn(
                f"known-failures entry matched no test: {suffix!r}. On a full-suite run this "
                f"means the entry is doing nothing — renamed, mistyped, or the test is gone. "
                f"(Expected, and ignorable, if this run collected a subset via -k / a path.)",
                KnownFailuresWarning,
                stacklevel=1,
            )
        elif n > 1:
            warnings.warn(
                f"known-failures entry matched {n} tests: {suffix!r}. An over-broad key can "
                f"xfail tests you did not intend to list.",
                KnownFailuresWarning,
                stacklevel=1,
            )

    workeroutput = getattr(config, "workeroutput", None)
    if workeroutput is not None:
        # xdist worker: no terminalreporter here. Hand the lines to the controller.
        workeroutput["known_failures_lines"] = lines
        return
    reporter = config.pluginmanager.get_plugin("terminalreporter")
    if reporter is not None:
        for line in lines:
            reporter.write_line(line)


def pytest_collection_modifyitems(config, items):
    known = _load_known_failures()
    if not known:
        return

    # Count every key's matches, not just the first one to hit each item, so an over-broad
    # key is visible in the report even when another key claimed the item first.
    counts = {suffix: 0 for suffix in known}
    marked = 0
    for item in items:
        claim = None
        for suffix, (reason, run) in known.items():
            if _matches(item.nodeid, suffix):
                counts[suffix] += 1
                if claim is None:
                    claim = (reason, run)
        if claim is not None:
            item.add_marker(pytest.mark.xfail(reason=claim[0], strict=False, run=claim[1]))
            marked += 1

    _report(config, counts, marked)


@pytest.hookimpl(optionalhook=True)  # xdist-only hook; absent when running without -n
def pytest_testnodedown(node, error):
    """xdist controller side: print the report a worker handed us, once."""
    lines = (getattr(node, "workeroutput", None) or {}).get("known_failures_lines")
    if not lines:
        return
    config = node.config
    if getattr(config, "_known_failures_reported", False):
        return
    config._known_failures_reported = True
    reporter = config.pluginmanager.get_plugin("terminalreporter")
    if reporter is not None:
        for line in lines:
            reporter.write_line(line)
