#!/usr/bin/env python3
"""Checks NativeProtocol.tla: every model, every mutant, every witness.

Uses the checking engine of ../../workflow-semantics/check.py.

    TLA2TOOLS=/path/to/tla2tools.jar python3 check.py
"""
import os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'workflow-semantics'))
import check as engine  # noqa: E402

MODELS = ['MC_NSeq', 'MC_NAsync', 'MC_NNested', 'MC_NUnjoined', 'MC_NSwitch', 'MC_NHold', 'MC_NRaise', 'MC_NHoldLanes']

# (name, model, text in NativeProtocol.tla, replacement, property expected to fail)
MUTANTS = [
    ('a stale result is applied', 'MC_NSeq',
     'Fresh(r) == mark[r.key].state = "LEASED" /\\ mark[r.key].epoch = r.epoch /\\ slot = r.key', 'Fresh(r) == mark[r.key].state = "LEASED" /\\ slot = r.key',
     'AppliedAtLatestLease'),
    ('a duplicate result is applied again', 'MC_NSeq',
     'Fresh(r) == mark[r.key].state = "LEASED" /\\ mark[r.key].epoch = r.epoch /\\ slot = r.key', 'Fresh(r) == mark[r.key].epoch = r.epoch',
     'AppliedOnce'),
    ('two continuations run at once', 'MC_NAsync',
     ['    /\\ Leasable(k)\n    /\\ slot = NONE \\/ expired\n', '/\\ mark[r.key].epoch = r.epoch /\\ slot = r.key'],
     ['    /\\ Leasable(k)\n', '/\\ mark[r.key].epoch = r.epoch'], 'OneContinuationAtATime'),
    ('a result from a worker that lost the slot counts', 'MC_NAsync',
     'mark[r.key].epoch = r.epoch /\\ slot = r.key', 'mark[r.key].epoch = r.epoch', 'AppliedOnFreshHistory'),
    ('a divergence is not noticed', 'MC_NHold',
     'IF Diverged(v) THEN "diverged" ELSE r.res', 'r.res', 'AsIfRunOnce'),
    ('one lane diverging does not hold the others', 'MC_NHoldLanes',
     'Diverged(v) == \\E l \\in Lanes : ~(LaneRefs(l)', 'Diverged(v) == \\E l \\in {mark[k0].lane : k0 \\in {slot} \\cap DOMAIN mark} : ~(LaneRefs(l)',
     'unreachable:\\E k \\in DOMAIN mark : mark[k].lane = "main" /\\ mark[k].held = 2'),
    ('a join ignores a failed lane', 'MC_NAsync',
     'r == Walk(v, l, IF quiet THEN nd.ok ELSE nd.fail, s)', 'r == Walk(v, l, nd.ok, s)', 'OutcomeMatchesCode'),
    ('a lane raising at once is lost', 'MC_NRaise',
     '/\\ \\A x \\in nd.lanes : Walk(v, x, Roots[x], s).res # "err"\n', '/\\ TRUE\n', 'OutcomeMatchesCode'),
    ('a crash turns a raise into a return', 'MC_NSeq',
     '!.err = r.res = "err", !.held = 0]', '!.err = (r.res = "err" /\\ mark[k].epoch = 1), !.held = 0]', ('OutcomeMatchesCode', 'FailedSound', 'NeverStuck', 'Settles')),
    ('replay takes the wrong branch', 'MC_NSeq',
     'IF s.st[n] = "ok" THEN nd.ok ELSE nd.fail', 'IF s.st[n] = "ok" THEN nd.fail ELSE nd.ok', 'AsIfRunOnce'),
    ('replay is not deterministic', 'MC_NSeq',
     'entries |-> r.entries, basis', 'entries |-> IF mark[k].epoch > 0 THEN <<>> ELSE r.entries, basis', 'ReplayIsStable'),
    ('a join does not wait', 'MC_NAsync',
     '    /\\ \\A l \\in Waits(j) : Settled(l)\n', '', 'JoinWaitsForLanes'),
    ('a join ignores what its lanes started', 'MC_NNested',
     'Waits(j) == UNION {Closure(l) : l \\in def[j].lanes}', 'Waits(j) == def[j].lanes', 'JoinWaitsForLanes'),
    ('a fragment lands behind finished work', 'MC_NSwitch',
     'AnchorOf(k, e) == IF e.owner = def[k].owner THEN k',
     'AnchorOf(k, e) == IF "s" \\in DOMAIN def /\\ e.owner = "main" THEN "s" ELSE IF e.owner = def[k].owner THEN k',
     ('NeverStuck', 'Settles', 'SequenceOrder')),
    ('a workflow completes with a lane still working', 'MC_NUnjoined',
     '    /\\ \\A k \\in DOMAIN mark : mark[k].state = "APPLIED"\n    /\\ wf\' = "COMPLETED"', '    /\\ wf\' = "COMPLETED"', 'CompletedSound'),
    ('a lane nothing joined fails the workflow', 'MC_NUnjoined',
     'wf\' = IF mark[k].lane = "main" /\\ r.res = "err"', 'wf\' = IF r.res = "err"', 'FailedSound'),
    ('a raise in the main lane does not fail the workflow', 'MC_NSeq',
     'wf\' = IF mark[k].lane = "main" /\\ r.res = "err" THEN "FAILED" ELSE wf', 'wf\' = wf', ('NeverStuck', 'Settles')),
    ('lost messages are never pushed again', 'MC_NSeq',
     'Repair(k) ==\n    /\\ Running\n', 'Repair(k) ==\n    /\\ FALSE\n', 'Settles'),
    ('a dead lease never runs out', 'MC_NSeq',
     'ExpireDead(k) ==\n    /\\ Running /\\ slot = k', 'ExpireDead(k) ==\n    /\\ FALSE /\\ slot = k', 'Settles'),
    ('a fork never starts its lanes', 'MC_NAsync',
     'THEN {seqs[l][1] : l \\in {x \\in def[t].lanes : seqs[x] # <<>>}} ELSE {}', 'THEN {} ELSE {}', ('NeverStuck', 'Settles')),
]

# (model, outcome, state predicate that must be reachable)
WITNESSES = [
    ('MC_NSeq', 'native: a workflow completes', 'wf = "COMPLETED"'),
    ('MC_NSeq', 'native: a raise fails the workflow', 'wf = "FAILED"'),
    ('MC_NSeq', 'native: completes after a fault', 'wf = "COMPLETED" /\\ faults > 0'),
    ('MC_NSeq', 'native: a continuation is leased a second time', '\\E k \\in DOMAIN mark : mark[k].epoch >= 2'),
    ('MC_NAsync', 'native: a failed lane raises at its join', 'wf = "FAILED"'),
    ('MC_NUnjoined', 'native: a lane nothing joined fails and the workflow completes',
     'wf = "COMPLETED" /\\ \\E k \\in DOMAIN mark : mark[k].err'),
    ('MC_NNested', 'native: a join waits for a nested lane', '"j" \\in DOMAIN st /\\ st["j"] = "sched" /\\ "b" \\in DOMAIN st /\\ st["b"] = "sched"'),
    ('MC_NSwitch', 'native: code continues after a case over continuations', 'seqs["main"] = <<BOOT, "a", "s", "c", "d">> /\\ wf = "COMPLETED"'),
    ('MC_NAsync', 'native: a lease that ran out on a slow worker is taken over and the run completes',
     'wf = "COMPLETED" /\\ faults > 0 /\\ \\E k \\in DOMAIN mark : mark[k].epoch >= 2'),
    ('MC_NRaise', 'native: a lane raising at once fails the workflow at the join', 'wf = "FAILED"'),
    ('MC_NHoldLanes', 'native: a diverged lane holds the main lane',
     '\\E k \\in DOMAIN mark : mark[k].lane = "main" /\\ mark[k].held = 2'),
    ('MC_NHold', 'native: a diverged run is held', '\\E k \\in DOMAIN mark : mark[k].held = 2'),
    ('MC_NHold', 'native: a run that recorded the new build is held after the rollback',
     'build = 1 /\\ deploys = 1 /\\ \\E k \\in DOMAIN mark : mark[k].held = 1'),
    ('MC_NHold', 'native: a held run completes after the rollback',
     'wf = "COMPLETED" /\\ "b" \\in DOMAIN def /\\ deploys = 1'),
]

engine.HERE, engine.SPEC = HERE, 'NativeProtocol.tla'
engine.MODELS, engine.MUTANTS, engine.WITNESSES = MODELS, MUTANTS, WITNESSES
engine.main()
