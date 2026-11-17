#!/usr/bin/env python3
"""Checks every model, then checks that each property can fail.

Each mutant breaks one rule of Workflow.tla and names the property that must
catch it; the mutant passes only when TLC reports that property violated. Each
witness names an outcome a model must be able to reach, so that the
properties are not true only because nothing happens.

    TLA2TOOLS=/path/to/tla2tools.jar python3 check.py
"""
import argparse, os, re, shutil, subprocess, sys, tempfile, time
from concurrent.futures import ThreadPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
SPEC = 'Workflow.tla'   # the module mutants change; check.py of another spec sets this
JAVA = os.environ.get('JAVA', 'java')
TOOLS = os.environ['TLA2TOOLS']
MODELS = ['MC_Compose', 'MC_Loop', 'MC_LoopFork', 'MC_Sub', 'MC_Timeout', 'MC_Term', 'MC_ForkEnd', 'MC_Skip', 'MC_Dyn', 'MC_Budget', 'MC_Unjoined', 'MC_Ops', 'MC_LoopOps',
          'MC_TermReady', 'MC_ForkLast', 'MC_DynLast', 'MC_SkipHead']

# (name, model, text in Workflow.tla, replacement, property expected to fail)
MUTANTS = [
    ('join does not wait', 'MC_Compose',
     '[] Kind(n) = "fork"      -> \\A b \\in Nodes[n].joinOn : SeqDone(i, Nodes[n].branches[b], it)',
     '[] Kind(n) = "fork"      -> TRUE', ('JoinWaits', 'SequenceOrder')),
    ('switch runs every case', 'MC_Compose',
     '/\\ inst[i].act[<<o[2], it, 0>>].choice = o[3])', '/\\ TRUE)', 'SwitchExclusive'),
    ('retries ignore the budget', 'MC_Compose',
     'a.status \\in Retriable /\\ a.attempt <= nd.retries /\\ ~a.spent',
     'a.status \\in Retriable /\\ a.attempt <= nd.retries + 1 /\\ ~a.spent', 'RetryBudget'),
    ('never retries', 'MC_Compose',
     'a.status \\in Retriable /\\ a.attempt <= nd.retries /\\ ~a.spent', 'FALSE', 'FailedSound'),
    ('retries a terminal error', 'MC_Compose',
     'IF a.status \\in Retriable /\\ a.attempt',
     'IF a.status \\in Retriable \\cup {"FAILED_WITH_TERMINAL_ERROR"} /\\ a.attempt', 'RetryOnlyWhenAllowed'),
    ('completes with a branch still running', 'MC_Compose',
     '/\\ BodyDone(i) /\\ Quiet(i)\n    /\\ inst\' =', '/\\ BodyDone(i)\n    /\\ inst\' =',
     ('CompletedSound', 'EndedIsQuiet')),
    ('an unjoined branch never starts', 'MC_Compose',
     ['[] o[1] = "branch" -> Open(i, <<o[2], it, 0>>)', 'IF b > Len(Nodes[n].branches) THEN {w}'],
     ['[] o[1] = "branch" -> Open(i, <<o[2], it, 0>>) /\\ o[3] = 1', 'IF b > 1 THEN {w}'],
     ('CompletedSound', 'ScheduledTogether')),

    ('ending does not cancel', 'MC_Compose',
     'Cancelled(a) == IF a.status = "SCHEDULED" THEN [a EXCEPT !.status = "CANCELED"] ELSE a',
     'Cancelled(a) == a', 'EndedIsQuiet'),
    ('optional failure ends the workflow', 'MC_Compose',
     'ELSE inst\' = IF nd.optional', 'ELSE inst\' = IF FALSE', 'FailedSound'),
    ('output from nowhere', 'MC_Compose',
     '![i].out = IF Defs[inst[i].wf].outParams THEN <<"params">> ELSE LastOut(i)]',
     '![i].out = NoKey]', 'OutputSound'),
    ('the server may stop', 'MC_Compose',
     'Spec == Init /\\ [][Next]_vars /\\ WF_vars(Server) /\\ WF_vars(Respond)',
     'Spec == Init /\\ [][Next]_vars /\\ WF_vars(Respond)', 'Settles'),
    ('an empty dynamic fork never finishes', 'MC_Loop',
     '-> \\A j \\in 1..a.choice : Finished', '-> a.choice > 0 /\\ \\A j \\in 1..a.choice : Finished',
     'NeverStuck'),
    ('a loop goes on before its body is done', 'MC_Loop',
     '    /\\ SeqDone(i, Nodes[k[1]].body, inst[i].act[k].iter)\n    /\\ IterSettled(i, k[1], inst[i].act[k].iter)',
     '    /\\ TRUE', 'LoopInOrder'),
    ('an iteration ends with a branch still running', 'MC_LoopFork',
     '    /\\ IterSettled(i, k[1], inst[i].act[k].iter)\n', '', ('LoopInOrder', 'EarlierIterationsSettled')),
    ('sub completes whatever the child did', 'MC_Sub',
     '[] s = "FAILED"     -> "FAILED"', '[] s = "FAILED"     -> "COMPLETED"', 'SubMirrorsChild'),
    ('a terminated child fails its task', 'MC_Sub',
     '[] s = "TERMINATED" -> "CANCELED"', '[] s = "TERMINATED" -> "FAILED"', 'SubMirrorsChild'),
    ('children outlive their parent', 'MC_Sub', 'IN EndAll(f, kids)', 'IN f',
     ('ChildrenFollowParent', 'SubMirrorsChild')),
    ('a workflow timeout skips the failure workflow', 'MC_Timeout',
     'Cause("timeout", NoKey), TRUE)', 'Cause("timeout", NoKey), FALSE)', 'FailureWorkflowSound'),
    ('an operator terminate starts the failure workflow', 'MC_Term',
     'Cause("operator", NoKey), FALSE)', 'Cause("operator", NoKey), TRUE)', 'FailureWorkflowSound'),
    ('a retry runs past its TERMINATE', 'MC_Term',
     'Live(i) == inst[i].status = "RUNNING" /\\ Terminated(i) = {}', 'Live(i) == inst[i].status = "RUNNING"',
     ('CompletedSound', 'NeverStuck')),
    ('a paused workflow retries', 'MC_Ops',
     'React(i, k) ==\n    /\\ Live(i)', 'React(i, k) ==\n    /\\ inst[i].status \\in {"RUNNING", "PAUSED"}',
     'PausedIsFrozen'),
    ('an answer changes a success', 'MC_Ops',
     '/\\ inst[i].act[k].status = "SCHEDULED"\n    /\\ \\E r \\in',
     '/\\ inst[i].act[k].status \\in {"SCHEDULED", "COMPLETED"}\n    /\\ \\E r \\in', 'SuccessIsFinal'),
    ('an operator terminates a failed workflow', 'MC_Ops',
     '/\\ Op /\\ inst[i].status \\in {"RUNNING", "PAUSED"}\n    /\\ inst\' = EndIn(inst, i, "TERMINATED"',
     '/\\ Op /\\ inst[i].status \\in {"RUNNING", "PAUSED", "FAILED"}\n    /\\ inst\' = EndIn(inst, i, "TERMINATED"',
     'EndIsFinal'),
    ('a retry makes many attempts', 'MC_Ops',
     'IF q \\in redo THEN Fresh(w.act[q], w.clock + rank(q))',
     'IF q \\in redo THEN [Fresh(w.act[q], w.clock + rank(q)) EXCEPT !.attempt = 99]', 'ReopenSound'),
    ('a retry keeps the spent budget', 'MC_Ops',
     'IF q \\in redo THEN Fresh(w.act[q], w.clock + rank(q))',
     'IF q \\in redo THEN [Fresh(w.act[q], w.clock + rank(q)) EXCEPT !.spent = w.act[q].spent]', 'ReopenSound'),
    ('a rerun uses up a retry', 'MC_Ops',
     'IF redo(q) THEN Reset(last(q)) ELSE fix(q)', 'IF redo(q) THEN Fresh(last(q), w.clock) ELSE fix(q)',
     'ReopenSound'),
    ('a timeout ignores ALERT_ONLY', 'MC_Ops',
     '/\\ Nodes[k[1]].timeout \\in {"RETRY", "TIME_OUT_WF"}\n    /\\ LET s == [inst EXCEPT',
     '/\\ TRUE\n    /\\ LET s == [inst EXCEPT', 'TimeoutsFollowPolicy'),
    ('TIME_OUT_WF times out only the attempt', 'MC_Timeout',
     'IN inst\' = IF Nodes[k[1]].timeout = "RETRY" THEN s', 'IN inst\' = IF TRUE THEN s', 'TimeoutsFollowPolicy'),
    ('a total budget under ALERT_ONLY stops retries', 'MC_Ops',
     '/\\ Nodes[k[1]].total\n    /\\ Nodes[k[1]].timeout \\in {"RETRY", "TIME_OUT_WF"}',
     '/\\ Nodes[k[1]].total', 'TimeoutsFollowPolicy'),
    ('a spent budget fails an optional task\'s workflow', 'MC_Ops',
     'ELSE inst\' = IF nd.optional', 'ELSE inst\' = IF nd.optional /\\ ~a.spent', 'FailedSound'),
    ('a skipped task runs', 'MC_Ops',
     'ELSE IF s[j] \\in inst[i].skipped', 'ELSE IF FALSE',
     'SkippedNeverRuns'),
    ('a rerun starts from a skipped task', 'MC_Ops',
     '/\\ IsLeaf(k[1]) /\\ inst[i].act[k].status # "SKIPPED"', '/\\ IsLeaf(k[1])', 'SkippedNeverRuns'),
    ('an optional task gives up before its retries', 'MC_Ops',
     'IF a.status \\in Retriable /\\ a.attempt <= nd.retries /\\ ~a.spent',
     'IF a.status \\in Retriable /\\ a.attempt <= nd.retries /\\ ~a.spent /\\ ~nd.optional',
     'OptionalOnlyWhenExhausted'),
    ('a retry does not count against the budget', 'MC_Ops',
     'IF q \\in redo THEN Fresh(w.act[q], w.clock + rank(q))',
     'IF q \\in redo THEN Reset(w.act[q])', 'ReopenSound'),
    ('a skipped TERMINATE ends the workflow', 'MC_Skip',
     'Terminated(i) == {k \\in DOMAIN inst[i].act : Kind(k[1]) = "terminate" /\\ inst[i].act[k].status = "COMPLETED"}',
     'Terminated(i) == {k \\in DOMAIN inst[i].act : Kind(k[1]) = "terminate"}', ('OutputSound', 'FailedSound', 'CompletedSound')),
    ('a switch finishes at once', 'MC_Compose',
     '[] Kind(n) = "switch"    -> SeqDone(i, Nodes[n].cases[a.choice], it)', '[] Kind(n) = "switch"    -> TRUE',
     'SequenceOrder'),
    ('a dynamic fork waits for its first copy only', 'MC_Dyn',
     '-> \\A j \\in 1..a.choice : Finished', '-> \\A j \\in 1..(IF a.choice > 0 THEN 1 ELSE 0) : Finished',
     'SequenceOrder'),
    ('a dynamic fork makes too many copies', 'MC_Dyn',
     'Open(i, d) /\\ k[3] <= inst[i].act[d].choice', 'Open(i, d)', 'CopiesWithinWidth'),
    ('a JOIN waits for every branch', 'MC_Compose',
     '[] Kind(n) = "fork"      -> \\A b \\in Nodes[n].joinOn : SeqDone',
     '[] Kind(n) = "fork"      -> \\A b \\in 1..Len(Nodes[n].branches) : SeqDone',
     'unreachable:Has(1, <<"z", 0, 0>>) /\\ inst[1].act[<<"d", 0, 0>>].status = "SCHEDULED"'),
    ('a timed-out task fails the workflow as FAILED', 'MC_Ops',
     'CASE s = "TIMED_OUT" -> "TIMED_OUT"', 'CASE s = "TIMED_OUT" -> "FAILED"', 'FailedSound'),
    ('a cancelled task fails the workflow as FAILED', 'MC_Sub',
     '[] s = "CANCELED"  -> "TERMINATED"', '[] s = "CANCELED"  -> "FAILED"', 'FailedSound'),
    ('an optional failure shows as COMPLETED', 'MC_Compose',
     'THEN [inst EXCEPT ![i].act[k].status = "COMPLETED_WITH_ERRORS"]', 'THEN [inst EXCEPT ![i].act[k].status = "COMPLETED"]',
     'FailureIsNotSuccess'),
    ('a parent ending fails its children', 'MC_Sub',
     'EndAll(EndIn(s, c, "TERMINATED", Cause("parent", NoKey), FALSE), C \\ {c})',
     'EndAll(EndIn(s, c, "FAILED", Cause("parent", NoKey), FALSE), C \\ {c})', 'ParentEndTerminates'),
    ('a retried sub-workflow reuses its child', 'MC_Sub',
     '!.child = 0, !.seq = seq, !.seqs', '!.seq = seq, !.seqs', 'NewRunNewChild'),
    ('a spent budget leaves the attempt running', 'MC_Ops',
     'IF a.status = "SCHEDULED" \\/ wf THEN "TIMED_OUT" ELSE @', 'IF wf THEN "TIMED_OUT" ELSE @', 'TimeoutsFollowPolicy'),
    ('an attempt times out while paused', 'MC_Ops',
     '\nTimeOut(i, k) ==\n    /\\ inst[i].status = "RUNNING"', '\nTimeOut(i, k) ==\n    /\\ inst[i].status \\in {"RUNNING", "PAUSED"}',
     'PausedIsFrozen'),
    ('a restart keeps its skips', 'MC_Ops',
     '!.act = [k \\in {} |-> NONE], !.skipped = {},', '!.act = [k \\in {} |-> NONE],', 'ReopenSound'),
    ('a retry reopens a COMPLETED workflow', 'MC_ForkEnd',
     'Retry(i) ==\n    /\\ Op /\\ inst[i].parent = NoKey\n    /\\ inst[i].status \\in WfFailed',
     'Retry(i) ==\n    /\\ Op /\\ inst[i].parent = NoKey\n    /\\ inst[i].status \\in WfTerminal', 'ReopenSound'),
    ('a rerun forgets what came before', 'MC_Ops',
     'kept == {q \\in DOMAIN w.act : w.act[q].seq <= cut}', 'kept == {k}', ('ReopenSound', 'Justified', 'SequenceOrder')),
    ('a rerun forgets its own task', 'MC_Ops',
     'kept == {q \\in DOMAIN w.act : w.act[q].seq <= cut}', 'kept == {q \\in DOMAIN w.act : w.act[q].seq < cut}', 'ReopenSound'),
    ('a task after a TERMINATE starts', 'MC_Skip',
     '[] Kind(n) = "terminate" -> FALSE   \\* nothing follows it', '[] Kind(n) = "terminate" -> TRUE', 'SequenceOrder'),
    ('a task finishes after a TERMINATE ran', 'MC_Term',
     'Answer(i, k) ==\n    /\\ Terminated(i) = {}\n', 'Answer(i, k) ==\n', 'NoAnswerAfterTerminate'),
    ('an operator terminate overrides a TERMINATE that ran', 'MC_Term',
     'Terminate(i) ==\n    /\\ Terminated(i) = {}\n', 'Terminate(i) ==\n', 'TerminateDecides'),
    ('an iteration ignores its running tasks', 'MC_LoopFork',
     '(k[1] \\in Under[l] \\ {l} /\\ k[2] = it /\\ IsLeaf(k[1]))\n              => inst[i].act[k].status \\notin {"SCHEDULED"} \\cup Unsuccessful',
     'FALSE => TRUE', 'EarlierIterationsSettled'),
    ('a terminal error counts as retriable', 'MC_Compose',
     'Retriable    == {"FAILED", "TIMED_OUT"}', 'Retriable    == {"FAILED", "TIMED_OUT", "FAILED_WITH_TERMINAL_ERROR"}',
     'RetryOnlyWhenAllowed'),
    ('a fork starts its branches one by one', 'MC_ForkEnd',
     'StartBranches(i, Add(w, k, Act("RUNNING")), n, it, 1)', 'StartBranches(i, Add(w, k, Act("RUNNING")), n, it, 99)',
     'ScheduledTogether'),
    ('an operator terminate ends the workflow FAILED', 'MC_Term',
     'inst\' = EndIn(inst, i, "TERMINATED", Cause("operator", NoKey), FALSE)', 'inst\' = EndIn(inst, i, "FAILED", Cause("operator", NoKey), FALSE)',
     'FailedSound'),
    ('a task is skipped while paused', 'MC_Skip',
     'Skip(i, n) ==\n    /\\ Op /\\ inst[i].status = "RUNNING"\n', 'Skip(i, n) ==\n    /\\ Op /\\ inst[i].status \\in {"RUNNING", "PAUSED"}\n',
     'PausedIsFrozen'),
    ('a total budget under TIME_OUT_WF times out only the task', 'MC_Budget',
     'IN inst\' = IF wf THEN EndIn(s, i, "TIMED_OUT", Cause("task", k), TRUE) ELSE s', 'IN inst\' = s', 'TimeoutsFollowPolicy'),
    ('a workflow completes with a loop waiting', 'MC_Unjoined',
     '          /\\ ~LoopWaiting(i, k)\n', '', ('CompletedSound', 'EndedIsQuiet')),
    ('a completing TERMINATE starts the failure workflow', 'MC_Skip',
     'IF runFailureWf /\\ st \\in WfFailed /\\ fw # NONE', 'IF runFailureWf /\\ fw # NONE', 'FailureWorkflowSound'),
    ('a paused loop decides', 'MC_LoopOps',
     'LoopDecide(i, k) ==\n    /\\ Live(i)', 'LoopDecide(i, k) ==\n    /\\ inst[i].status \\in {"RUNNING", "PAUSED"}',
     'PausedIsFrozen'),
    ('a rerun leaves its loop stopped', 'MC_LoopOps',
     '!.stopped = @ /\\ ~\\E r \\in gone \\cup {x \\in kept : redo(x)} : r[1] \\in Under[q[1]]]',
     '!.stopped = @ /\\ ~\\E r \\in gone : r[1] \\in Under[q[1]]]', ('LoopInOrder', 'EarlierIterationsSettled')),
    ('a reopened workflow starts what was ready before its TERMINATE ends it', 'MC_TermReady',
     '    /\\ ~inst[i].endsAgain   \\* (the server\'s first decide after the reopen ends it, scheduling nothing)\n', '',
     'ReopenedEndsAgain'),
    ('a sub-workflow task reattaches an ended child', 'MC_Sub',
     "/\\ inst' = Append([inst EXCEPT ![i].act[k].child = Len(inst) + 1],\n                      NewInst(Nodes[k[1]].wf, <<i, k>>, 0))",
     "/\\ \\/ inst' = Append([inst EXCEPT ![i].act[k].child = Len(inst) + 1],\n                         NewInst(Nodes[k[1]].wf, <<i, k>>, 0))\n"
     "       \\/ \\E c \\in 1..Len(inst) : /\\ inst[c].parent = <<i, k>> /\\ inst[c].status \\in WfTerminal\n"
     "                             /\\ inst' = [inst EXCEPT ![i].act[k].child = c]",
     'NewRunNewChild'),
    ('an end starts two failure workflows', 'MC_Term',
     'THEN Append(e, NewInst(fw, NoKey, i))', 'THEN Append(Append(e, NewInst(fw, NoKey, i)), NewInst(fw, NoKey, i))',
     'FailureWorkflowSound'),
    ('the output ignores the JOIN', 'MC_ForkLast',
     '            \\cup {<<"join", k>> : k \\in {q \\in DOMAIN inst[i].act : inst[i].act[q].join > 0}}\n', '',
     'OutputSound'),
    ('a switch\'s case is scheduled in a later decision', 'MC_ForkLast',
     'UNION {StartFrom(i, Add(w, k, [Act("RUNNING") EXCEPT !.choice = c]), Nodes[n].cases[c], 1, it) :',
     'UNION {{Add(w, k, [Act("RUNNING") EXCEPT !.choice = c])} :', 'ScheduledTogether'),
    ('a fork\'s JOIN is scheduled before its branches', 'MC_ForkLast',
     '{JoinAt(x, k) : x \\in StartBranches(i, Add(w, k, Act("RUNNING")), n, it, 1)}',
     'StartBranches(i, JoinAt(Add(w, k, Act("RUNNING")), k), n, it, 1)', 'ScheduledTogether'),
    ('a dynamic fork\'s copies are scheduled in later decisions', 'MC_Dyn',
     '{JoinAt(Copies(Add(w, k, [Act("RUNNING") EXCEPT !.choice = x]), Nodes[n].leaf, it, x), k) :',
     '{JoinAt(Add(w, k, [Act("RUNNING") EXCEPT !.choice = x]), k) :', 'ScheduledTogether'),
    ('a decision stops at a skipped task', 'MC_SkipHead',
     'THEN StartFrom(i, Add(w, <<s[j], it, 0>>, Act("SKIPPED")), s, j + 1, it)',
     'THEN {Add(w, <<s[j], it, 0>>, Act("SKIPPED"))}', 'ScheduledTogether'),
    ('an empty dynamic fork schedules no JOIN', 'MC_DynLast',
     '{JoinAt(Copies(Add(w, k, [Act("RUNNING") EXCEPT !.choice = x]), Nodes[n].leaf, it, x), k) :',
     '{IF x = 0 THEN Add(w, k, [Act("RUNNING") EXCEPT !.choice = x]) ELSE JoinAt(Copies(Add(w, k, [Act("RUNNING") EXCEPT !.choice = x]), Nodes[n].leaf, it, x), k) :',
     'ScheduledTogether'),
]

# (model, outcome, state predicate that must be reachable)
WITNESSES = [
    ('MC_Compose', 'the workflow completes', 'inst[1].status = "COMPLETED"'),
    ('MC_Compose', 'the workflow fails', 'inst[1].status = "FAILED"'),
    ('MC_Compose', 'an unjoined branch runs on past the join',
     'Has(1, <<"z", 0, 0>>) /\\ inst[1].act[<<"d", 0, 0>>].status = "SCHEDULED"'),
    ('MC_Compose', 'a retried task completes', 'Has(1, <<"a", 0, 0>>) /\\ inst[1].act[<<"a", 0, 0>>].attempt = 2 /\\ inst[1].act[<<"a", 0, 0>>].status = "COMPLETED"'),
    ('MC_Loop', 'a second iteration makes two copies', 'Has(1, <<"q", 2, 2>>)'),
    ('MC_Loop', 'the loop completes', 'inst[1].status = "COMPLETED"'),
    ('MC_LoopFork', 'a branch runs past the join inside an iteration',
     'Has(1, <<"sw", 1, 0>>) /\\ Finished(1, <<"sw", 1, 0>>) /\\ Has(1, <<"d", 1, 0>>) /\\ inst[1].act[<<"d", 1, 0>>].status = "SCHEDULED"'),
    ('MC_LoopFork', 'two iterations and the sub-workflow complete',
     'Has(1, <<"c", 2, 0>>) /\\ inst[1].status = "COMPLETED"'),
    ('MC_Sub', 'the failure workflow starts', '\\E i \\in 1..Len(inst) : inst[i].wf = "onfail"'),
    ('MC_Sub', 'a sub-workflow is retried in a new child', 'Len(inst) >= 3 /\\ inst[3].wf = "child"'),
    ('MC_Timeout', 'the workflow times out', 'inst[1].status = "TIMED_OUT"'),
    ('MC_Sub', 'a child is terminated with its parent',
     'Len(inst) >= 2 /\\ inst[2].status = "TERMINATED" /\\ inst[2].cause.kind = "parent"'),
    ('MC_Sub', 'the workflow completes after a retried child', 'inst[1].status = "COMPLETED" /\\ inst[1].act[<<"s", 0, 0>>].attempt = 2'),
    ('MC_ForkEnd', 'both TERMINATEs at the heads of a fork run', 'BothRan'),
    ('MC_Budget', 'a total budget under TIME_OUT_WF times out the workflow', 'inst[1].status = "TIMED_OUT"'),
    ('MC_Unjoined', 'a loop in an unjoined branch finishes after the join', 'Has(1, <<"z", 0, 0>>) /\\ inst[1].status = "COMPLETED"'),
    ('MC_Skip', 'a skipped TERMINATE is passed over', '"t" \\in inst[1].skipped /\\ inst[1].status = "COMPLETED" /\\ inst[1].cause.kind = "returned"'),
    ('MC_Term', 'a TERMINATE that starts with a cancelled task ends the workflow',
     'inst[1].status = "FAILED" /\\ inst[1].cause.kind = "terminate" /\\ Has(1, <<"a", 0, 0>>) /\\ inst[1].act[<<"a", 0, 0>>].status = "CANCELED"'),
    ('MC_Term', 'a reopened workflow ends again at its TERMINATE',
     'inst[1].epoch > 0 /\\ inst[1].status = "FAILED" /\\ inst[1].cause.kind = "terminate"'),
    ('MC_Ops', 'a rerun workflow completes', 'inst[1].epoch > 0 /\\ inst[1].status = "COMPLETED"'),
    ('MC_Ops', 'a skipped task is passed over', '"e" \\in inst[1].skipped /\\ inst[1].status = "COMPLETED"'),
    ('MC_Ops', 'a paused workflow resumes and completes', 'ops = 2 /\\ inst[1].status = "COMPLETED" /\\ inst[1].epoch = 0 /\\ inst[1].skipped = {}'),
    ('MC_Ops', 'an optional task completes with errors after its total budget ran out',
     'Has(1, <<"b", 0, 0>>) /\\ inst[1].act[<<"b", 0, 0>>].spent /\\ inst[1].act[<<"b", 0, 0>>].status = "COMPLETED_WITH_ERRORS"'),
    ('MC_Timeout', 'a TIME_OUT_WF task times the workflow out',
     'inst[1].status = "TIMED_OUT" /\\ inst[1].cause.kind = "task"'),
    ('MC_Skip', 'a rerun after a TERMINATE completes the workflow again',
     'inst[1].epoch > 0 /\\ inst[1].status = "COMPLETED" /\\ inst[1].cause.kind = "terminate"'),
    ('MC_LoopOps', 'a rerun inside a loop completes', 'inst[1].epoch > 0 /\\ inst[1].status = "COMPLETED" /\\ Has(1, <<"p", 1, 0>>)'),
    ('MC_TermReady', 'a task ready with a TERMINATE starts with it, and is cancelled',
     'inst[1].epoch = 0 /\\ inst[1].status = "FAILED" /\\ Has(1, <<"b", 0, 0>>) /\\ inst[1].act[<<"b", 0, 0>>].status = "CANCELED"'),
    ('MC_TermReady', 'a retry ends again at its TERMINATE, the ready task never started',
     'inst[1].how = "retry" /\\ inst[1].status = "FAILED" /\\ inst[1].cause.kind = "terminate"'
     ' /\\ Has(1, <<"a", 0, 0>>) /\\ inst[1].act[<<"a", 0, 0>>].status = "COMPLETED" /\\ ~Has(1, <<"b", 0, 0>>)'),
    ('MC_ForkLast', 'the JOIN supplies the output, with a switch\'s case scheduled with the fork',
     'inst[1].status = "COMPLETED" /\\ inst[1].out = <<"join", <<"f", 0, 0>>>> /\\ Has(1, <<"c", 0, 0>>)'),
    ('MC_SkipHead', 'a fork passes over a skipped branch head to the next task; the task after it supplies the output',
     'inst[1].status = "COMPLETED" /\\ Has(1, <<"b", 0, 0>>) /\\ inst[1].act[<<"b", 0, 0>>].status = "SKIPPED"'
     ' /\\ ~("b2" \\in inst[1].skipped) /\\ inst[1].act[<<"b2", 0, 0>>].seq < inst[1].act[<<"f", 0, 0>>].join'
     ' /\\ inst[1].out = <<"c", 0, 0>>'),
    ('MC_SkipHead', 'a fork passes over two skipped tasks, and the JOIN supplies the output',
     'inst[1].status = "COMPLETED" /\\ Has(1, <<"b", 0, 0>>) /\\ inst[1].act[<<"b", 0, 0>>].status = "SKIPPED"'
     ' /\\ Has(1, <<"b2", 0, 0>>) /\\ inst[1].act[<<"b2", 0, 0>>].status = "SKIPPED"'
     ' /\\ inst[1].out = <<"join", <<"f", 0, 0>>>>'),
    ('MC_DynLast', 'an empty dynamic fork\'s JOIN supplies the output',
     'inst[1].status = "COMPLETED" /\\ inst[1].act[<<"d", 0, 0>>].choice = 0 /\\ inst[1].out = <<"join", <<"d", 0, 0>>>>'),
    ('MC_DynLast', 'a dynamic fork\'s JOIN after its copies supplies the output',
     'inst[1].status = "COMPLETED" /\\ inst[1].act[<<"d", 0, 0>>].choice = 2 /\\ inst[1].out = <<"join", <<"d", 0, 0>>>>'),
    ('MC_ForkLast', 'a retried branch task supplies the output', 'inst[1].status = "COMPLETED" /\\ inst[1].out = <<"b", 0, 0>>'),
]

TIMES = []
TEMPORAL = 'violated a temporal property'


def tlc(directory, model, workers=1):
    meta = tempfile.mkdtemp(prefix='tlc-states-')
    started = time.time()
    try:
        flags = ['-XX:+UseParallelGC'] if workers > 1 else ['-XX:+UseSerialGC', '-XX:TieredStopAtLevel=1', '-Xshare:auto']
        return subprocess.run([JAVA, *flags, '-cp', TOOLS, 'tlc2.TLC', '-workers', str(workers),
                               '-metadir', meta, model],
                              cwd=directory, capture_output=True, text=True).stdout
    finally:
        TIMES.append((time.time() - started, model))
        shutil.rmtree(meta, ignore_errors=True)


def verdict(out):
    m = re.search(r'(?:Invariant|Action property|Temporal property) (\w+) (?:is|was) violated', out)
    if m:
        return 'violated ' + m.group(1)
    if 'Temporal properties were violated' in out:
        return TEMPORAL   # some TLC versions do not name the temporal property
    if 'No error has been found' in out:
        return 'ok'
    return 'error: ' + '\n'.join(out.splitlines()[-15:])


def add_witness(d, model, pred):
    m = os.path.join(d, model + '.tla')
    s = open(m).read()
    end = s.rindex('\n====') + 1
    open(m, 'w').write(s[:end] + 'Witness == ~(' + pred + ')\n' + s[end:])
    c = os.path.join(d, model + '.cfg')
    cfg = re.sub(r'PROPERTIES.*\n', '', open(c).read())
    open(c, 'w').write(re.sub(r'INVARIANTS.*\n', 'INVARIANT Witness\n', cfg))


def copy_to(d):
    for f in os.listdir(HERE):
        if f.endswith(('.tla', '.cfg')) and '_TTrace_' not in f:
            shutil.copy(os.path.join(HERE, f), d)


def only_properties(d, model, names):
    """Keeps in the model's config just the properties a mutant must trip, which is much faster."""
    c = os.path.join(d, model + '.cfg')
    cfg = open(c).read()
    for key in ('INVARIANTS', 'PROPERTIES'):
        m = re.search(key + r' (.*)\n', cfg)
        kept = [x for x in m.group(1).split() if x in names]
        cfg = cfg.replace(m.group(0), f'{key} {" ".join(kept)}\n' if kept else '')
    open(c, 'w').write(cfg)


def check_model(model):
    with tempfile.TemporaryDirectory() as d:
        copy_to(d)
        out = tlc(d, model, 3)
    states = re.search(r'([\d,]+) distinct states found, 0 states left', out)
    v = verdict(out)
    return f'{model}: {v} ({states.group(1) if states else "?"} states)', v != 'ok'


def check_mutant(mutant):
    name, model, old, new, expected = mutant
    with tempfile.TemporaryDirectory() as d:
        copy_to(d)
        spec = os.path.join(d, SPEC)
        s = open(spec).read()
        olds, news = (old, new) if isinstance(old, list) else ([old], [new])
        if any(s.count(o) != 1 for o in olds):
            return f'mutant "{name}": text to change not found exactly once', True
        for o, n in zip(olds, news):
            s = s.replace(o, n)
        open(spec, 'w').write(s)
        if isinstance(expected, str) and expected.startswith('unreachable:'):
            add_witness(d, model, expected.split(':', 1)[1])
            v = verdict(tlc(d, model))
            return f'mutant "{name}": {"caught: outcome unreachable" if v == "ok" else "NOT caught (" + v + ")"}', v != 'ok'
        expected = expected if isinstance(expected, tuple) else (expected,)
        only_properties(d, model, expected)
        v = verdict(tlc(d, model))
    # (a mutant's model keeps only the properties it expects to trip, so an unnamed
    # temporal violation is one of them)
    caught = any(v == 'violated ' + e for e in expected) or v == TEMPORAL
    return f'mutant "{name}": {"caught by " + " or ".join(expected) if caught else "NOT caught (" + v + ")"}', not caught


def check_witness(witness):
    model, outcome, pred = witness
    with tempfile.TemporaryDirectory() as d:
        copy_to(d)
        add_witness(d, model, pred)
        v = verdict(tlc(d, model))
    reached = v == 'violated Witness'
    return f'witness "{outcome}": {"reached" if reached else "NOT reached (" + v + ")"}', not reached


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--jobs', type=int, default=os.cpu_count())
    ap.add_argument('--times', action='store_true', help='print the slowest runs')
    a = ap.parse_args()
    failed = 0
    with ThreadPoolExecutor(a.jobs) as pool:
        futures = [pool.submit(check_model, m) for m in MODELS] + \
                  [pool.submit(check_mutant, m) for m in MUTANTS] + \
                  [pool.submit(check_witness, w) for w in WITNESSES]
        for f in futures:
            line, bad = f.result()
            print(line, flush=True)
            failed += bad
    if a.times:
        for secs, label in sorted(TIMES, reverse=True)[:12]:
            print(f'{secs:6.1f}s {label}')
    print('all checks passed' if failed == 0 else f'{failed} checks failed')
    sys.exit(1 if failed else 0)


if __name__ == '__main__':
    main()
