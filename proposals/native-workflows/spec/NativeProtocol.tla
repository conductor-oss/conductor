---------------------------- MODULE NativeProtocol ----------------------------
(***************************************************************************)
(* How a native workflow executes, and whether crashes can break it.       *)
(*                                                                         *)
(* Workflow.tla says what a workflow means. This module models how code    *)
(* running on distributed workers delivers that: the server holds a        *)
(* definition that grows, and continuation markers; a worker leases a      *)
(* marker, replays the code from the top against what the server recorded, *)
(* and submits a fragment of new tasks.                                    *)
(*                                                                         *)
(* The code is a tree of nodes: what a lane does next given what it has    *)
(* observed. Workers may crash, leases may expire, and queue messages may  *)
(* be lost or delivered twice, and a worker may report that it could not   *)
(* run a continuation. Signals, updates and variables are left for later;  *)
(* so are task retries, which Workflow.tla covers.                         *)
(***************************************************************************)
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANTS
    Programs,   \* build -> node id -> node, see below
    Roots,      \* lane -> first node (the same node ids in every build)
    Spawns,     \* lane -> lanes it may start, directly
    MaxFaults,  \* crashes, lost messages, early lease expiries, duplicates
    MaxDeploys, \* code changes (a change to build 2 and back)
    MaxRetries  \* retries of a failed workflow by an operator

(***************************************************************************)
(* A node is one of                                                        *)
(*   [op |-> "call", in, ok, fail]    call a task; ok / fail: the next     *)
(*                                    node for each outcome                *)
(*   [op |-> "switch", in, next, untaken]  enter the case the code picked;  *)
(*                                    untaken: previews of the other cases *)
(*   [op |-> "async", in, lanes, next]    start lanes and go on            *)
(*   [op |-> "join", in, lanes, ok, fail]   wait for lanes                 *)
(*   [op |-> "return"]  [op |-> "raise", cause, skips]                     *)
(* in is the switch whose case holds the node, or NONE. A node's id is     *)
(* its reference name, as the same code gives the same names. A raise's    *)
(* cause is the call whose failure the code did not catch, or NONE for an  *)
(* exception the code threw itself; skips are the calls the code would     *)
(* have made next had it not raised. Both may be left out.                 *)
(***************************************************************************)
NONE == "none"
BOOT == "boot"
Lanes == DOMAIN Roots
Prog(v) == Programs[v]
AllRefs == UNION {DOMAIN Prog(v) : v \in DOMAIN Programs}
Owners == Lanes \cup {n \in AllRefs : \E v \in DOMAIN Programs : n \in DOMAIN Prog(v) /\ Prog(v)[n].op = "switch"}
Terminal == {"ok", "fail"}

RECURSIVE Closure(_)
Closure(l) == {l} \cup UNION {Closure(c) : c \in Spawns[l]}

VARIABLES
    def,       \* ref -> [kind, lane, owner, lanes]: the definition so far
    seqs,      \* owner -> the refs in it, in order
    st,        \* ref -> "none", "sched", "ok", "fail"
    done,      \* ref -> TRUE once the decider has evaluated it
    mark,      \* ref -> continuation marker on the task the code paused on
    slot,      \* the continuation that last leased and has not finished, or NONE
    expired,   \* whether its lease ran out, so that another continuation may take the slot
    queue,     \* markers with a message in the queue
    inflight,  \* results of leased continuations, on their way to the server
    wf,        \* "RUNNING", "COMPLETED", "FAILED"
    build,     \* the code workers currently run
    faults,    \* faults used
    deploys,   \* code changes used
    failedTask, \* the task a FAILED workflow names as where it failed, or NONE
    retries,   \* retries used
    applied    \* ghost: ref -> [count, epoch] of its applied continuation

vars == <<def, seqs, st, done, mark, slot, expired, queue, inflight, wf, build, faults, deploys, failedTask, retries, applied>>

-----------------------------------------------------------------------------
(* Replay: a pure function of the code and what the server had recorded. *)

OwnerOf(nd, l) == IF "in" \in DOMAIN nd /\ nd.in # NONE THEN nd.in ELSE l

Entry(n, kind, l, owner, lanes, pause) ==
    [ref |-> n, kind |-> kind, lane |-> l, owner |-> owner, lanes |-> lanes, pause |-> pause]

Result(res, entries, seen) == [res |-> res, entries |-> entries, seen |-> seen, raised |-> NONE]

Cause(nd) == IF "cause" \in DOMAIN nd THEN nd.cause ELSE NONE
Skips(nd) == IF "skips" \in DOMAIN nd THEN nd.skips ELSE <<>>

\* An exception the main lane's code threw is marked where it threw: an entry with the raise's
\* own reference, which the server creates failed and never schedules, then previews of what
\* the code would have called next. An uncaught task failure marks nothing: its task failed.
Untaken(nd) == IF "untaken" \in DOMAIN nd THEN nd.untaken ELSE <<>>

\* What the cases a switch did not take would call: drawn as previews, owned by no list the
\* decider walks, so never scheduled.
Previews(nd, l) == [j \in 1..Len(Untaken(nd)) |-> Entry(Untaken(nd)[j], "preview", l, "untaken", {}, FALSE)]

Thrown(n, nd, l) ==
    IF l = "main" /\ Cause(nd) = NONE
    THEN <<Entry(n, "mark", l, OwnerOf(nd, l), {}, FALSE)>>
         \o [j \in 1..Len(Skips(nd)) |-> Entry(Skips(nd)[j], "preview", l, OwnerOf(nd, l), {}, FALSE)]
    ELSE <<>>

RECURSIVE Walk(_, _, _, _), WalkAll(_, _, _)

\* Runs lane l from node n in build v against snapshot s, until it pauses or ends.
Walk(v, l, n, s) ==
    LET nd == Prog(v)[n] IN
    CASE nd.op = "return" -> Result("done", <<>>, {})
      [] nd.op = "raise"  -> [Result("err", IF n \in s.refs THEN <<>> ELSE Thrown(n, nd, l),
                                     IF n \in s.refs THEN {n} ELSE {}) EXCEPT !.raised = n]
      [] nd.op = "call" ->
            IF n \notin s.refs
            THEN Result("paused", <<Entry(n, "task", l, OwnerOf(nd, l), {}, TRUE)>>, {})
            ELSE IF s.st[n] \in Terminal
                 THEN LET r == Walk(v, l, IF s.st[n] = "ok" THEN nd.ok ELSE nd.fail, s)
                      IN [r EXCEPT !.seen = @ \cup {n}]
                 ELSE Result("paused", <<>>, {n})
      [] nd.op = "switch" ->
            LET r == Walk(v, l, nd.next, s) IN
            IF n \in s.refs
            THEN [r EXCEPT !.seen = @ \cup {n}]
            ELSE [r EXCEPT !.entries = <<Entry(n, "switch", l, l, {}, FALSE)>> \o Previews(nd, l) \o @]
      [] nd.op = "async" ->
            LET r == Walk(v, l, nd.next, s) IN
            IF n \in s.refs
            THEN [r EXCEPT !.seen = @ \cup {n}]
            ELSE [r EXCEPT !.entries = <<Entry(n, "fork", l, OwnerOf(nd, l), nd.lanes, FALSE)>>
                                       \o WalkAll(v, nd.lanes, s) \o @]
      [] nd.op = "join" ->
            IF n \notin s.refs
            THEN Result("paused", <<Entry(n, "join", l, OwnerOf(nd, l), nd.lanes, TRUE)>>, {})
            ELSE IF s.st[n] = "ok"
                 THEN LET quiet == /\ nd.lanes \cap s.errs = {}
                                   /\ \A x \in nd.lanes : Walk(v, x, Roots[x], s).res # "err"
                          r == Walk(v, l, IF quiet THEN nd.ok ELSE nd.fail, s)
                      IN [r EXCEPT !.seen = @ \cup {n}]
                 ELSE Result("paused", <<>>, {n})

\* The first entries of lanes being started.
WalkAll(v, ls, s) ==
    IF ls = {} THEN <<>>
    ELSE LET l == CHOOSE x \in ls : TRUE
         IN Walk(v, l, Roots[l], s).entries \o WalkAll(v, ls \ {l}, s)

Snapshot == [refs |-> DOMAIN def, st |-> st,
             errs |-> {mark[k].lane : k \in {x \in DOMAIN mark : mark[x].err}}]

LaneRefs(l) == {r \in DOMAIN def : def[r].lane = l /\ def[r].kind \notin {"boot", "preview"}}

\* The code no longer passes through everything recorded. Replay covers every
\* lane, so one lane that cannot be replayed holds the whole execution.
Diverged(v) == \E l \in Lanes : ~(LaneRefs(l) \subseteq Walk(v, l, Roots[l], Snapshot).seen)

\* What a worker reports: replay of the marker's lane, or a divergence.
Replay(k, v, e) ==
    LET l == mark[k].lane
        r == Walk(v, l, Roots[l], Snapshot)
    IN [key |-> k, epoch |-> e, build |-> v, res |-> IF Diverged(v) THEN "diverged" ELSE r.res,
        entries |-> r.entries, raised |-> r.raised, basis |-> DOMAIN applied]

-----------------------------------------------------------------------------
(* The definition *)

\* A fragment goes after the task the code paused on; code that left a switch
\* case goes after the switch.
AnchorOf(k, e) == IF e.owner = def[k].owner THEN k
                  ELSE IF def[k].owner \in Lanes THEN k ELSE def[k].owner

PosOf(s, x) == CHOOSE j \in 1..Len(s) : s[j] = x
InsertAfter(s, a, rs) == SubSeq(s, 1, PosOf(s, a)) \o rs \o SubSeq(s, PosOf(s, a) + 1, Len(s))

RefsOwnedBy(es, o) ==
    LET mine == SelectSeq(es, LAMBDA e : e.owner = o)
    IN [j \in 1..Len(mine) |-> mine[j].ref]

Placed(es, anchor) ==
    [o \in Owners |->
        IF RefsOwnedBy(es, o) = <<>> THEN seqs[o]
        ELSE IF anchor # NONE /\ o = def[anchor].owner
             THEN InsertAfter(seqs[o], anchor, RefsOwnedBy(es, o))
             ELSE seqs[o] \o RefsOwnedBy(es, o)]

\* The task after t, leaving a switch case at its end.
RECURSIVE NextOf(_)
NextOf(t) ==
    LET o == def[t].owner
        s == seqs[o]
        j == PosOf(s, t)
    IN IF j < Len(s) THEN s[j + 1]
       ELSE IF o \in Lanes THEN NONE ELSE NextOf(o)

StartStatus(r) == IF def[r].kind \in {"switch", "fork"} THEN "ok" ELSE "sched"

\* What evaluating t starts.
StartedBy(t) ==
    LET fromNext == IF NextOf(t) # NONE THEN {NextOf(t)} ELSE {}
        caseHead == IF def[t].kind = "switch" /\ seqs[t] # <<>> THEN {seqs[t][1]} ELSE {}
        heads    == IF def[t].kind = "fork"
                    THEN {seqs[l][1] : l \in {x \in def[t].lanes : seqs[x] # <<>>}} ELSE {}
    IN IF caseHead # {} THEN caseHead ELSE fromNext \cup heads

-----------------------------------------------------------------------------
(* Markers and lanes *)

NewMark(l) == [lane |-> l, state |-> "WAITING", epoch |-> 0, finished |-> FALSE, err |-> FALSE, held |-> 0]

LaneMarks(l) == {k \in DOMAIN mark : mark[k].lane = l}

\* A lane has settled: it returned or raised, and all its tasks ended.
Settled(l) ==
    \/ LaneRefs(l) = {}
    \/ /\ \E k \in LaneMarks(l) : mark[k].state = "APPLIED" /\ mark[k].finished
       /\ \A k \in LaneMarks(l) : mark[k].state = "APPLIED"
       /\ \A r \in LaneRefs(l) : st[r] \in Terminal

Waits(j) == UNION {Closure(l) : l \in def[j].lanes}

-----------------------------------------------------------------------------
(* The server *)

Init ==
    /\ def = BOOT :> [kind |-> "boot", lane |-> "main", owner |-> "main", lanes |-> {}]
    /\ seqs = [o \in Owners |-> IF o = "main" THEN <<BOOT>> ELSE <<>>]
    /\ st = BOOT :> "ok"
    /\ done = BOOT :> TRUE
    /\ mark = BOOT :> [NewMark("main") EXCEPT !.state = "RUNNABLE"]
    /\ slot = NONE
    /\ expired = FALSE
    /\ queue = {BOOT}
    /\ inflight = {}
    /\ wf = "RUNNING"
    /\ build = 1
    /\ faults = 0
    /\ deploys = 0
    /\ failedTask = NONE
    /\ retries = 0
    /\ applied = [k \in {} |-> 0]

Running == wf = "RUNNING"

\* A task that ended is evaluated once: what follows it starts, and the
\* continuation of the code that paused on it becomes runnable.
Evaluate(t) ==
    /\ Running
    /\ t \in DOMAIN def /\ st[t] \in Terminal /\ ~done[t]
    /\ done' = [done EXCEPT ![t] = TRUE]
    /\ LET starts == {r \in StartedBy(t) : st[r] = "none"}
       IN st' = [r \in DOMAIN st |-> IF r \in starts THEN StartStatus(r) ELSE st[r]]
    /\ IF t \in DOMAIN mark /\ mark[t].state = "WAITING"
       THEN /\ mark' = [mark EXCEPT ![t].state = "RUNNABLE"]
            /\ queue' = queue \cup {t}
       ELSE UNCHANGED <<mark, queue, failedTask, retries>>
    /\ UNCHANGED <<def, seqs, slot, expired, inflight, wf, build, faults, deploys, applied, failedTask, retries>>

\* A JOIN completes when the lanes it waits for, and all they started, settled.
JoinSettles(j) ==
    /\ Running
    /\ j \in DOMAIN def /\ def[j].kind = "join" /\ st[j] = "sched"
    /\ \A l \in Waits(j) : Settled(l)
    /\ st' = [st EXCEPT ![j] = "ok"]
    /\ UNCHANGED <<def, seqs, done, mark, slot, expired, queue, inflight, wf, build, faults, deploys, applied, failedTask, retries>>

Leasable(k) ==
    \/ mark[k].state = "RUNNABLE"
    \/ mark[k].state = "LEASED" /\ (slot # k \/ expired)

\* Decides push the message of every runnable or expired marker again.
Repair(k) ==
    /\ Running
    /\ k \in DOMAIN mark /\ k \notin queue
    /\ Leasable(k)
    /\ queue' = queue \cup {k}
    /\ UNCHANGED <<def, seqs, st, done, mark, slot, expired, inflight, wf, build, faults, deploys, applied, failedTask, retries>>

\* A worker takes a message, and replay runs on what is recorded now. The epoch is the
\* offer's: a runnable marker is offered at a new epoch; a lease that ran out is either
\* delivered again under the same offer and epoch (its queue redelivers it: two workers may
\* then run it, and the first result applies) or offered again at a new epoch (its queue is no
\* longer polled). A held marker waits until the build changes.
Lease(k) ==
    /\ Running
    /\ k \in queue /\ k \in DOMAIN mark
    /\ Leasable(k)
    /\ slot = NONE \/ (expired /\ slot = k)   \* a lease that ran out is taken again only by its own continuation
    /\ mark[k].held # build
    /\ \E bump \in IF mark[k].state = "LEASED" THEN {FALSE, TRUE} ELSE {TRUE} :
          LET e == IF bump THEN mark[k].epoch + 1 ELSE mark[k].epoch
          IN /\ inflight' = inflight \cup {Replay(k, build, e)}
             /\ mark' = [mark EXCEPT ![k].state = "LEASED", ![k].epoch = e]
    /\ slot' = k
    /\ expired' = FALSE
    /\ UNCHANGED <<def, seqs, st, done, queue, wf, build, faults, deploys, applied, failedTask, retries>>

\* Applying a result: fenced on the lease, the epoch and the slot, so a result
\* computed before another continuation ran does not count.
Fresh(r) == mark[r.key].state = "LEASED" /\ mark[r.key].epoch = r.epoch /\ slot = r.key

Apply(r) ==
    LET k  == r.key
        es == r.entries
        newRefs == {es[j].ref : j \in 1..Len(es)}
        entry(x) == CHOOSE e \in {es[j] : j \in 1..Len(es)} : e.ref = x
        pauses == {x \in newRefs : entry(x).pause}
        anchor == IF Len(es) = 0 THEN NONE ELSE AnchorOf(k, es[1])
        m == [mark[k] EXCEPT !.state = "APPLIED", !.finished = r.res \in {"done", "err"}, !.err = r.res = "err", !.held = 0]
    IN /\ def' = [x \in DOMAIN def \cup newRefs |->
                    IF x \in newRefs
                    THEN [kind |-> entry(x).kind, lane |-> entry(x).lane, owner |-> entry(x).owner, lanes |-> entry(x).lanes]
                    ELSE def[x]]
       /\ seqs' = Placed(es, anchor)
       /\ st' = [x \in DOMAIN st \cup newRefs |->
                   IF x \in newRefs THEN (IF entry(x).kind = "mark" THEN "fail" ELSE "none") ELSE st[x]]
       /\ done' = [x \in DOMAIN done \cup newRefs |->
                     IF x \in newRefs THEN entry(x).kind \in {"mark", "preview"}
                     ELSE IF x = k THEN FALSE ELSE done[x]]
       /\ mark' = [x \in DOMAIN mark \cup pauses |->
                     IF x = k THEN m
                     ELSE IF x \in pauses THEN NewMark(entry(x).lane) ELSE mark[x]]
       /\ applied' = (k :> [count |-> IF k \in DOMAIN applied THEN applied[k].count + 1 ELSE 1,
                            epoch |-> r.epoch, stale |-> r.basis # DOMAIN applied]) @@ applied
       /\ queue' = queue \ {k}
       /\ wf' = IF mark[k].lane = "main" /\ r.res = "err" THEN "FAILED" ELSE wf
       /\ UNCHANGED retries
       /\ failedTask' = IF mark[k].lane = "main" /\ r.res = "err"
                        THEN IF Cause(Prog(r.build)[r.raised]) # NONE THEN Cause(Prog(r.build)[r.raised]) ELSE r.raised
                        ELSE failedTask

\* A result arrives. A stale one changes nothing. It may be delivered again.
Deliver(r) ==
    /\ Running
    /\ r \in inflight
    /\ \/ inflight' = inflight \ {r} /\ UNCHANGED faults
       \/ faults < MaxFaults /\ faults' = faults + 1 /\ UNCHANGED inflight
    /\ UNCHANGED <<build, deploys>>
    /\ IF ~Fresh(r)
       THEN UNCHANGED <<def, seqs, st, done, mark, slot, expired, queue, wf, applied, failedTask, retries>>
       ELSE /\ slot' = NONE
            /\ expired' = FALSE
            /\ IF r.res = "diverged"
               THEN /\ mark' = [mark EXCEPT ![r.key].state = "RUNNABLE", ![r.key].held = r.build]
                    /\ UNCHANGED <<def, seqs, st, done, queue, wf, applied, failedTask, retries>>
               ELSE IF r.res = "error"
               THEN /\ mark' = [mark EXCEPT ![r.key].state = "RUNNABLE"]   \* deferred: offered again
                    /\ UNCHANGED <<def, seqs, st, done, queue, wf, applied, failedTask, retries>>
               ELSE Apply(r)

\* The workflow completes when the code returned and nothing is left.
Complete ==
    /\ Running
    /\ \E k \in LaneMarks("main") : mark[k].finished /\ ~mark[k].err
    /\ \A r \in DOMAIN st : def[r].kind = "preview" \/ st[r] \in Terminal
    /\ \A r \in DOMAIN done : done[r]
    /\ \A k \in DOMAIN mark : mark[k].state = "APPLIED"
    /\ wf' = "COMPLETED"
    /\ UNCHANGED <<def, seqs, st, done, mark, slot, expired, queue, inflight, build, faults, deploys, applied, failedTask, retries>>

-----------------------------------------------------------------------------
(* The world *)

\* A task ends. (Retries are in Workflow.tla; here a task ends once.)
Finish(t) ==
    /\ Running
    /\ t \in DOMAIN def /\ def[t].kind = "task" /\ st[t] = "sched"
    /\ \E r \in Terminal : st' = [st EXCEPT ![t] = r]
    /\ UNCHANGED <<def, seqs, done, mark, slot, expired, queue, inflight, wf, build, faults, deploys, applied, failedTask, retries>>

\* A worker crashes: its result never arrives. The lease then runs out.
Crash(r) ==
    /\ Running /\ r \in inflight /\ faults < MaxFaults
    /\ inflight' = inflight \ {r}
    /\ faults' = faults + 1
    /\ UNCHANGED <<def, seqs, st, done, mark, slot, expired, queue, wf, build, deploys, applied, failedTask, retries>>

\* A worker reports that it could not run the continuation (an exception, a variable
\* conflict, messages waiting for the handler lane) instead of its result.
Falter(r) ==
    /\ Running /\ r \in inflight /\ r.res \notin {"error", "diverged"} /\ faults < MaxFaults
    /\ inflight' = (inflight \ {r}) \cup {[r EXCEPT !.res = "error", !.entries = <<>>]}
    /\ faults' = faults + 1
    /\ UNCHANGED <<def, seqs, st, done, mark, slot, expired, queue, wf, build, deploys, applied, failedTask, retries>>

\* A queue message is lost.
Lose(k) ==
    /\ Running /\ k \in queue /\ faults < MaxFaults
    /\ queue' = queue \ {k}
    /\ faults' = faults + 1
    /\ UNCHANGED <<def, seqs, st, done, mark, slot, expired, inflight, wf, build, deploys, applied, failedTask, retries>>

\* A lease runs out. A lease with no result left to arrive always runs out;
\* expiring one whose worker is merely slow is a fault.
LiveResult(k) == \E r \in inflight : r.key = k /\ Fresh(r)

ExpireDead(k) ==
    /\ Running /\ slot = k /\ ~expired /\ ~LiveResult(k)
    /\ expired' = TRUE
    /\ UNCHANGED <<slot, def, seqs, st, done, mark, queue, inflight, wf, build, faults, deploys, applied, failedTask, retries>>

ExpireSlow(k) ==
    /\ Running /\ slot = k /\ ~expired /\ LiveResult(k) /\ faults < MaxFaults
    /\ expired' = TRUE
    /\ faults' = faults + 1
    /\ UNCHANGED <<slot, def, seqs, st, done, mark, queue, inflight, wf, build, deploys, applied, failedTask, retries>>

\* A deploy changes the code; a rollback restores it.
Deploy ==
    /\ wf # "COMPLETED" /\ build = 1 /\ deploys < MaxDeploys
    /\ build' = 2
    /\ deploys' = deploys + 1
    /\ UNCHANGED <<def, seqs, st, done, mark, slot, expired, queue, inflight, wf, faults, applied, failedTask, retries>>

Rollback ==
    /\ Running /\ build = 2
    /\ build' = 1
    /\ UNCHANGED <<def, seqs, st, done, mark, slot, expired, queue, inflight, wf, faults, deploys, applied, failedTask, retries>>

\* An operator retries a workflow that failed where its code threw: the mark and its previews
\* go, and the main lane's last continuation runs the code again, on whatever build runs now.
\* (Retrying from a failed task reruns that task; that path predates native workflows.)
Retry ==
    /\ wf = "FAILED" /\ retries < MaxRetries
    /\ failedTask \in DOMAIN def /\ def[failedTask].kind = "mark"
    /\ LET gone == {r \in DOMAIN def : def[r].kind \in {"mark", "preview"}}
           k == CHOOSE x \in DOMAIN mark : mark[x].lane = "main" /\ mark[x].err
       IN /\ def' = [r \in DOMAIN def \ gone |-> def[r]]
          /\ seqs' = [o \in Owners |-> SelectSeq(seqs[o], LAMBDA x : x \notin gone)]
          /\ st' = [r \in DOMAIN st \ gone |-> st[r]]
          /\ done' = [r \in DOMAIN done \ gone |-> done[r]]
          /\ mark' = [mark EXCEPT ![k].state = "RUNNABLE", ![k].finished = FALSE, ![k].err = FALSE]
          /\ queue' = queue \cup {k}
          /\ applied' = [x \in DOMAIN applied \ {k} |-> applied[x]]
    /\ wf' = "RUNNING"
    /\ failedTask' = NONE
    /\ retries' = retries + 1
    /\ UNCHANGED <<slot, expired, inflight, build, faults, deploys>>

-----------------------------------------------------------------------------
Server ==
    \/ \E t \in DOMAIN def : Evaluate(t) \/ JoinSettles(t)
    \/ \E k \in DOMAIN mark : Repair(k) \/ Lease(k) \/ ExpireDead(k)
    \/ \E r \in inflight : Deliver(r)
    \/ Complete
    \/ Rollback

World == \E t \in DOMAIN def : Finish(t)

Faults ==
    \/ \E r \in inflight : Crash(r) \/ Falter(r)
    \/ \E k \in queue : Lose(k)
    \/ \E k \in DOMAIN mark : ExpireSlow(k)
    \/ Deploy
    \/ Retry

Next == Server \/ World \/ Faults

Spec == Init /\ [][Next]_vars /\ WF_vars(Server) /\ WF_vars(World)

-----------------------------------------------------------------------------
(* What it promises *)

\* A continuation is applied at most once, however many workers ran it.
AppliedOnce == \A k \in DOMAIN applied : applied[k].count <= 1

\* Only the current offer's result can apply: a worker from an earlier offer changes nothing.
AppliedAtLatestLease == \A k \in DOMAIN applied : applied[k].epoch = mark[k].epoch

\* Replaying the same marker on the same code gives the same decisions.
ReplayIsStable ==
    \A r1, r2 \in inflight :
        (r1.key = r2.key /\ r1.build = r2.build /\ r1.res # "error" /\ r2.res # "error")
            => (r1.res = r2.res /\ r1.entries = r2.entries)

(***************************************************************************)
(* An independent walk over the code, used only by the properties: which   *)
(* nodes a lane reaches given the results the server holds now.            *)
(***************************************************************************)
Durable == {"call", "switch", "async", "join"}

RECURSIVE Follow(_, _)
Follow(v, n) ==
    IF n \notin DOMAIN Prog(v) THEN {}
    ELSE LET nd == Prog(v)[n] IN
         CASE nd.op \in {"return", "raise"} -> {n}
           [] nd.op \in {"switch", "async"} -> {n} \cup Follow(v, nd.next)
           [] nd.op = "call" ->
                 IF n \in DOMAIN st /\ st[n] = "ok" THEN {n} \cup Follow(v, nd.ok)
                 ELSE IF n \in DOMAIN st /\ st[n] = "fail" THEN {n} \cup Follow(v, nd.fail)
                 ELSE {n}
           [] nd.op = "join" ->
                 IF n \in DOMAIN st /\ st[n] = "ok"
                 THEN {n} \cup Follow(v, IF \/ \E k \in DOMAIN mark : mark[k].err /\ mark[k].lane \in nd.lanes
                                            \/ \E x \in nd.lanes : Prog(v)[Roots[x]].op = "raise"
                                         THEN nd.fail ELSE nd.ok)
                 ELSE {n}

\* As if run once: every task of a lane is one the code reaches, on one build,
\* given the results recorded: crashes, duplicates and replays add nothing.
AsIfRunOnce ==
    \E v \in DOMAIN Programs : \A l \in Lanes : LaneRefs(l) \subseteq Follow(v, Roots[l])

\* The outcome is the code's: it completed only if the code returned, and
\* failed only if it raised, whatever crashes and replays happened.
OutcomeMatchesCode ==
    /\ wf = "COMPLETED" => \E v \in DOMAIN Programs :
          \E n \in Follow(v, Roots["main"]) : Prog(v)[n].op = "return"
    /\ wf = "FAILED" => \E v \in DOMAIN Programs :
          \E n \in Follow(v, Roots["main"]) : Prog(v)[n].op = "raise"

\* A join completes only when its lanes and what they started are over.
JoinWaitsForLanes ==
    \A j \in DOMAIN def :
        (def[j].kind = "join" /\ st[j] = "ok") =>
            \A l \in UNION {Closure(x) : x \in def[j].lanes} :
                \A r \in DOMAIN def :
                    def[r].lane = l => (st[r] \in {"ok", "fail"}
                                        /\ (r \in DOMAIN mark => mark[r].state = "APPLIED"))

\* COMPLETED means the code returned and no lane has work left; no mark of an
\* earlier failure is left behind.
CompletedSound ==
    wf = "COMPLETED" =>
        /\ \A r \in DOMAIN def : def[r].kind # "mark"
        /\ \A r \in DOMAIN st : def[r].kind = "preview" \/ st[r] \in {"ok", "fail"}
        /\ \A k \in DOMAIN mark : mark[k].state = "APPLIED"
        /\ \E v \in DOMAIN Programs :
             \A n \in Follow(v, Roots["main"]) :
                 Prog(v)[n].op \in Durable => n \in DOMAIN def

\* A FAILED workflow names where it failed: a failed task the code reached, either the task
\* whose failure it did not catch or the mark where it raised.
FailedNamesItsCause ==
    wf = "FAILED" =>
        /\ failedTask \in DOMAIN def
        /\ st[failedTask] = "fail"
        /\ \E v \in DOMAIN Programs : failedTask \in Follow(v, Roots["main"])

\* Marks and previews are drawn, never run: a mark is failed from the start, and a preview, of
\* a switch case not taken or of what code that threw skipped, never starts.
MarksAndPreviewsNeverRun ==
    \A r \in DOMAIN def :
        /\ def[r].kind = "mark" => st[r] = "fail"
        /\ def[r].kind = "preview" => st[r] = "none"

\* FAILED comes only from the main lane raising.
FailedSound ==
    wf = "FAILED" => \E k \in DOMAIN mark : mark[k].lane = "main" /\ mark[k].err

\* Every entry is placed where it will run: with nothing left to do, the
\* workflow can complete. A task never scheduled would leave it stuck here.
NeverStuck ==
    (/\ wf = "RUNNING"
     /\ \A r \in DOMAIN st : st[r] # "sched"
     /\ \A r \in DOMAIN done : done[r] \/ st[r] = "none"
     /\ \A k \in DOMAIN mark : mark[k].state \in {"APPLIED", "WAITING"})
        => /\ \A r \in DOMAIN st : def[r].kind = "preview" \/ st[r] # "none"
           /\ \E k \in DOMAIN mark : mark[k].lane = "main" /\ mark[k].finished

\* A task runs only after the entry before it in its list ended.
SequenceOrder ==
    \A o \in Owners : \A j \in 2..Len(seqs[o]) :
        st[seqs[o][j]] # "none" => st[seqs[o][j - 1]] \in {"ok", "fail"}

\* One continuation counts at a time: a result is live only while it holds the slot.
OneContinuationAtATime ==
    Cardinality({k \in DOMAIN mark : mark[k].state = "LEASED" /\ LiveResult(k)}) <= 1

\* A result is applied only if nothing else was applied since its worker read
\* the history: continuations take effect one after another.
AppliedOnFreshHistory == \A k \in DOMAIN applied : ~applied[k].stale

\* With a live worker every workflow ends, unless it is held: the code running
\* now cannot replay what it recorded, so it waits for a compatible build. Each
\* time it runs, including after a retry.
Settles == (wf = "RUNNING") ~> (wf # "RUNNING" \/ \E k \in DOMAIN mark : mark[k].held = build)
=============================================================================
