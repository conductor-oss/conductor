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
"""

import json
import os

import pytest


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
    for nid in (nodeid, nodeid.split("@", 1)[0]):
        for suf in (suffix, suffix.split("@", 1)[0]):
            if nid == suf or nid.endswith("::" + suf) or nid.endswith(suf):
                return True
    return False


def pytest_collection_modifyitems(config, items):
    known = _load_known_failures()
    if not known:
        return
    matched = 0
    for item in items:
        for suffix, (reason, run) in known.items():
            if _matches(item.nodeid, suffix):
                item.add_marker(pytest.mark.xfail(reason=reason, strict=False, run=run))
                matched += 1
                break
    reporter = config.pluginmanager.get_plugin("terminalreporter")
    if reporter is not None:
        reporter.write_line(
            f"[known-failures] xfail-marked {matched} item(s) from "
            f"{os.environ.get('E2E_KNOWN_FAILURES')}"
        )
