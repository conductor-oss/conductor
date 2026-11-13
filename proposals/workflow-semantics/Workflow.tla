------------------------------- MODULE Workflow -------------------------------
(***************************************************************************)
(* What a Conductor workflow means, independent of how the server runs it. *)
(*                                                                         *)
(* A workflow is a program: a sequence of tasks. A task is either a leaf,  *)
(* run by someone else (a worker, an HTTP call, an event, a person, a      *)
(* wait), or an operator composing other tasks: SWITCH runs the one        *)
(* sequence its value picks, FORK runs sequences in parallel and its JOIN  *)
(* waits for some of them, a dynamic fork runs a number of copies of one   *)
(* task, DO_WHILE repeats a sequence, SUB_WORKFLOW runs another program as *)
(* a task, and TERMINATE ends the program.                                 *)
(*                                                                         *)
(* Data is abstract. The world chooses each leaf's answer, and also the    *)
(* choices that depend on data: the case a switch takes, how many copies a *)
(* dynamic fork makes, and whether a loop goes on. The spec says what      *)
(* follows from those choices. Queues, locks, the sweeper, persistence,    *)
(* caching and timing are not in it.                                       *)
(***************************************************************************)
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANTS
    Nodes,        \* node id (taskReferenceName) -> node, see below
    Defs,         \* workflow name -> [body, failureWf, timeout, outParams]
    Main,         \* the workflow a caller starts
    MaxIter,      \* most iterations a loop may run (bounds the model)
    MaxBranches,  \* most copies a dynamic fork may make (bounds the model)
    MaxOps        \* most operator actions in one behaviour (bounds the model)

(***************************************************************************)
(* Nodes[n] is one of                                                      *)
(*   [kind |-> "task", retries, optional, timeout, total]                  *)
(*       timeout: the timeout policy, which every timeout of the task      *)
(*                follows: "none" (no timeout, or ALERT_ONLY), "RETRY",    *)
(*                or "TIME_OUT_WF"                                         *)
(*       total:   TRUE when a total time budget across attempts is set     *)
(*   [kind |-> "sub", wf, retries, optional]                               *)
(*   [kind |-> "switch", cases]        the last case is the default        *)
(*   [kind |-> "fork", branches, joinOn]  joinOn: branches the JOIN awaits *)
(*   [kind |-> "dynfork", leaf]        leaf: the task each copy runs       *)
(*   [kind |-> "loop", body]                                               *)
(*   [kind |-> "terminate", status]    "COMPLETED", "FAILED", "TERMINATED" *)
(* Defs[w] is [body, failureWf (a name or NONE), timeout ("none" or        *)
(* "TIME_OUT_WF"), outParams (TRUE when outputParameters are set)].        *)
(***************************************************************************)

NONE == "none"

-----------------------------------------------------------------------------
(* The program *)

Kind(n) == Nodes[n].kind
LeafKinds == {"task", "sub"}
IsLeaf(n) == Kind(n) \in LeafKinds
NodesOf(k) == {n \in DOMAIN Nodes : Kind(n) = k}
Templates == {Nodes[d].leaf : d \in NodesOf("dynfork")}
SeqNodes(s) == {s[j] : j \in 1..Len(s)}
Max(S) == CHOOSE x \in S : \A y \in S : y <= x

\* Every sequence in the program has an owner: a workflow body, a switch case,
\* a fork branch or a loop body.
Owners ==
    {<<"root", w>> : w \in DOMAIN Defs}
    \cup (UNION {{<<"case", s, c>> : c \in 1..Len(Nodes[s].cases)} : s \in NodesOf("switch")})
    \cup (UNION {{<<"branch", f, b>> : b \in 1..Len(Nodes[f].branches)} : f \in NodesOf("fork")})
    \cup {<<"body", l>> : l \in NodesOf("loop")}

SeqOf(o) ==
    CASE o[1] = "root"   -> Defs[o[2]].body
      [] o[1] = "case"   -> Nodes[o[2]].cases[o[3]]
      [] o[1] = "branch" -> Nodes[o[2]].branches[o[3]]
      [] o[1] = "body"   -> Nodes[o[2]].body

Placed == {n \in DOMAIN Nodes : \E o \in Owners : n \in SeqNodes(SeqOf(o))}
Owner == [n \in Placed |-> CHOOSE o \in Owners : n \in SeqNodes(SeqOf(o))]
Index == [n \in Placed |-> CHOOSE j \in 1..Len(SeqOf(Owner[n])) : SeqOf(Owner[n])[j] = n]
DynOf == [t \in Templates |-> CHOOSE d \in NodesOf("dynfork") : Nodes[d].leaf = t]

\* The node a node sits in, or NONE at the top of a workflow body.
Up(n) == IF n \in Templates THEN DynOf[n]
         ELSE IF Owner[n][1] = "root" THEN NONE ELSE Owner[n][2]

RECURSIVE Ancestors(_)
Ancestors(n) == IF Up(n) = NONE THEN {} ELSE {Up(n)} \cup Ancestors(Up(n))

RECURSIVE WfOfR(_)
WfOfR(n) == IF Up(n) = NONE THEN Owner[n][2] ELSE WfOfR(Up(n))

AllNodes == Placed \cup Templates
WfOf == [n \in AllNodes |-> WfOfR(n)]
InLoop == [n \in AllNodes |-> \E a \in Ancestors(n) : Kind(a) = "loop"]

RECURSIVE UnderR(_)
UnderR(n) == {n} \cup
    (CASE Kind(n) = "switch"  -> UNION {UNION {UnderR(m) : m \in SeqNodes(c)} : c \in {Nodes[n].cases[i] : i \in 1..Len(Nodes[n].cases)}}
       [] Kind(n) = "fork"    -> UNION {UNION {UnderR(m) : m \in SeqNodes(b)} : b \in {Nodes[n].branches[i] : i \in 1..Len(Nodes[n].branches)}}
       [] Kind(n) = "loop"    -> UNION {UnderR(m) : m \in SeqNodes(Nodes[n].body)}
       [] Kind(n) = "dynfork" -> {Nodes[n].leaf}
       [] OTHER               -> {})
Under == [n \in AllNodes |-> UnderR(n)]

\* The nodes in each case of each switch, at any depth.
CaseNodes == [s \in NodesOf("switch") |->
                 [c \in 1..Len(Nodes[s].cases) |-> UNION {Under[m] : m \in SeqNodes(Nodes[s].cases[c])}]]

\* The node after n in its sequence, or NONE.
Succ == [n \in Placed |-> IF Index[n] < Len(SeqOf(Owner[n])) THEN SeqOf(Owner[n])[Index[n] + 1] ELSE NONE]

(***************************************************************************)
(* An activation is one run of a node: the key <<n, it, j>> names node n   *)
(* in loop iteration it (0 outside loops) and dynamic copy j (0 for nodes  *)
(* that are not copies). Nested loops are not modeled: the server tags a   *)
(* task with a single iteration number (TaskUtils.appendIteration).        *)
(***************************************************************************)
Iters(n) == IF InLoop[n] THEN 1..MaxIter ELSE {0}
AllKeys == [w \in DOMAIN Defs |->
              (UNION {{<<n, it, 0>> : it \in Iters(n)} : n \in {m \in Placed : WfOf[m] = w}})
              \cup (UNION {{<<t, it, j>> : it \in Iters(t), j \in 1..MaxBranches} :
                             t \in {m \in Templates : WfOf[m] = w}})]

ASSUME Main \in DOMAIN Defs
ASSUME \A w \in DOMAIN Defs : Len(Defs[w].body) > 0
ASSUME \A n \in DOMAIN Nodes : n \in Placed \/ n \in Templates
ASSUME \A n \in Placed :
          Cardinality(UNION {{<<o, j>> : j \in {x \in 1..Len(SeqOf(o)) : SeqOf(o)[x] = n}} : o \in Owners}) = 1
ASSUME \A t \in Templates : Cardinality({d \in NodesOf("dynfork") : Nodes[d].leaf = t}) = 1
ASSUME \A n \in Templates : IsLeaf(n) /\ n \notin Placed
ASSUME \A n \in NodesOf("loop") : \A m \in Under[n] \ {n} : Kind(m) # "loop"

-----------------------------------------------------------------------------
(* Statuses, as the server shows them *)

Successful   == {"COMPLETED", "COMPLETED_WITH_ERRORS", "SKIPPED"}
Unsuccessful == {"FAILED", "FAILED_WITH_TERMINAL_ERROR", "TIMED_OUT", "CANCELED"}
Retriable    == {"FAILED", "TIMED_OUT"}
WfTerminal   == {"COMPLETED", "FAILED", "TIMED_OUT", "TERMINATED"}
WfFailed     == {"FAILED", "TIMED_OUT", "TERMINATED"}

\* The workflow status a leaf's unrecovered failure gives.
WfStatusFor(s) == CASE s = "TIMED_OUT" -> "TIMED_OUT"
                    [] s = "CANCELED"  -> "TERMINATED"
                    [] OTHER           -> "FAILED"

\* The status a SUB_WORKFLOW task takes from its ended child.
LeafStatusFor(s) == CASE s = "COMPLETED"  -> "COMPLETED"
                       [] s = "FAILED"     -> "FAILED"
                       [] s = "TIMED_OUT"  -> "TIMED_OUT"
                       [] s = "TERMINATED" -> "CANCELED"

-----------------------------------------------------------------------------
(***************************************************************************)
(* State. inst is the sequence of workflow instances: the one started, the *)
(* children its SUB_WORKFLOW tasks start, and failure workflows. Each      *)
(* instance's clock orders the activations and attempts it creates (the    *)
(* task seq). ops counts operator actions.                                 *)
(***************************************************************************)
VARIABLES inst, ops
vars == <<inst, ops>>

NoKey == << >>
Cause(kind, key) == [kind |-> kind, key |-> key]
NoCause == Cause(NONE, NoKey)

NewInst(w, parent, origin) ==
    [wf |-> w, status |-> "RUNNING",
     parent |-> parent,        \* <<instance, key>> of the SUB_WORKFLOW task, or NoKey
     origin |-> origin,        \* the instance a failure workflow is for, or 0
     act |-> [k \in {} |-> NONE],
     skipped |-> {},           \* nodes an operator skipped before they ran
     cause |-> NoCause,        \* why it ended
     out |-> NoKey,            \* where its output comes from: a key, <<"params">>
     epoch |-> 0,              \* bumped when an operator reopens it
     how |-> NONE,             \* how it was last reopened: "retry", "rerun", "restart"
     endsAgain |-> FALSE,      \* reopened with a TERMINATE on record: it ends again, starting nothing
     clock |-> 1]

\* A new activation; leaves hold the latest attempt. Put sets its seq.
Act(st) == [status |-> st, attempt |-> 1, auto |-> 0, spent |-> FALSE, child |-> 0,
            choice |-> 0, iter |-> 0, stopped |-> FALSE, seq |-> 0,
            join |-> 0]   \* a fork's or dynamic fork's JOIN: where the server scheduled it, after its branches

\* The next attempt of a leaf, created at seq.
Fresh(a, seq) == [a EXCEPT !.status = "SCHEDULED", !.attempt = @ + 1, !.spent = FALSE,
                           !.child = 0, !.seq = seq]

\* The same attempt run again in place, as a rerun does: same seq, same count.
Reset(a) == [a EXCEPT !.status = "SCHEDULED", !.spent = FALSE, !.child = 0]

Has(i, k)  == k \in DOMAIN inst[i].act
Open(i, k) == Has(i, k) /\ inst[i].act[k].status # "SKIPPED"

\* The TERMINATEs that ran in instance i (a skipped one did not).
Terminated(i) == {k \in DOMAIN inst[i].act : Kind(k[1]) = "terminate" /\ inst[i].act[k].status = "COMPLETED"}

\* Running, with no TERMINATE on record. Once a TERMINATE has run, the
\* workflow ends as it says (TerminateEnds), also whenever it is reopened;
\* until then only what started with it may still start, and is cancelled.
Live(i) == inst[i].status = "RUNNING" /\ Terminated(i) = {}

-----------------------------------------------------------------------------
(***************************************************************************)
(* The meaning of each construct: when an activation has finished, so      *)
(* that what follows it may run. A leaf has finished when it succeeded; a  *)
(* switch when its chosen case has; a fork when the branches its JOIN      *)
(* names have; a dynamic fork when every copy has; a loop when it stopped  *)
(* after its last iteration finished. A skipped node has finished.         *)
(***************************************************************************)
RECURSIVE Finished(_, _), SeqDone(_, _, _)

Finished(i, k) ==
    /\ Has(i, k)
    /\ LET n  == k[1]
           it == k[2]
           a  == inst[i].act[k]
       IN IF a.status = "SKIPPED" THEN TRUE
          ELSE CASE IsLeaf(n)              -> a.status \in Successful
                 [] Kind(n) = "switch"    -> SeqDone(i, Nodes[n].cases[a.choice], it)
                 [] Kind(n) = "fork"      -> \A b \in Nodes[n].joinOn : SeqDone(i, Nodes[n].branches[b], it)
                 [] Kind(n) = "dynfork"   -> \A j \in 1..a.choice : Finished(i, <<Nodes[n].leaf, it, j>>)
                 [] Kind(n) = "loop"      -> a.stopped /\ SeqDone(i, Nodes[n].body, a.iter)
                 [] Kind(n) = "terminate" -> FALSE   \* nothing follows it

SeqDone(i, s, it) == IF Len(s) = 0 THEN TRUE ELSE Finished(i, <<s[Len(s)], it, 0>>)

(***************************************************************************)
(* When a node may run: after its predecessor in the sequence finished,    *)
(* or, first in a sequence, when the construct owning the sequence chose   *)
(* it. past = TRUE accepts earlier loop iterations (for the invariant).    *)
(***************************************************************************)
Reached(i, k, past) ==
    LET n  == k[1]
        it == k[2]
    IN IF k[3] # 0
       THEN LET d == <<DynOf[n], it, 0>> IN Open(i, d) /\ k[3] <= inst[i].act[d].choice
       ELSE LET o == Owner[n]
                j == Index[n]
            IN IF j > 1
               THEN Finished(i, <<SeqOf(o)[j - 1], it, 0>>)
               ELSE CASE o[1] = "root"   -> TRUE
                      [] o[1] = "case"   -> (Open(i, <<o[2], it, 0>>)
                                             /\ inst[i].act[<<o[2], it, 0>>].choice = o[3])
                      [] o[1] = "branch" -> Open(i, <<o[2], it, 0>>)
                      [] o[1] = "body"   -> (Open(i, <<o[2], 0, 0>>)
                                             /\ IF past THEN it <= inst[i].act[<<o[2], 0, 0>>].iter
                                                        ELSE it = inst[i].act[<<o[2], 0, 0>>].iter)

Ready(i, k) == k \in AllKeys[inst[i].wf] /\ ~Has(i, k) /\ Reached(i, k, FALSE)

\* Everything iteration it of loop l started has settled: no attempt
\* running or failed and not yet handled, and nothing left to start.
IterSettled(i, l, it) ==
    /\ \A k \in DOMAIN inst[i].act :
          (k[1] \in Under[l] \ {l} /\ k[2] = it /\ IsLeaf(k[1]))
              => inst[i].act[k].status \notin {"SCHEDULED"} \cup Unsuccessful
    /\ \A k \in AllKeys[inst[i].wf] :
          (k[1] \in Under[l] \ {l} /\ k[2] = it) => ~Ready(i, k)

\* A loop whose current iteration finished, waiting for its condition. An
\* iteration is a scope: it finishes when its body did and everything it
\* started has settled, including fork branches no JOIN waits for.
LoopWaiting(i, k) ==
    /\ Kind(k[1]) = "loop" /\ Open(i, k)
    /\ ~inst[i].act[k].stopped
    /\ SeqDone(i, Nodes[k[1]].body, inst[i].act[k].iter)
    /\ IterSettled(i, k[1], inst[i].act[k].iter)

\* Nothing running, nothing left to start, nothing to decide.
Quiet(i) ==
    /\ \A k \in DOMAIN inst[i].act :
          /\ IsLeaf(k[1]) => inst[i].act[k].status \notin {"SCHEDULED"} \cup Unsuccessful
          /\ ~LoopWaiting(i, k)
    /\ \A k \in AllKeys[inst[i].wf] : ~Ready(i, k)

BodyDone(i) == SeqDone(i, Defs[inst[i].wf].body, 0)

\* The places of what the server scheduled, in its order: each activation's,
\* and each fork's JOIN's. A workflow that returns, with no outputParameters,
\* outputs what was scheduled last: a task, or a JOIN (its branches' outputs).
Spots(i) == {<<"act", k>> : k \in DOMAIN inst[i].act}
            \cup {<<"join", k>> : k \in {q \in DOMAIN inst[i].act : inst[i].act[q].join > 0}}
At(i, s) == IF s[1] = "act" THEN inst[i].act[s[2]].seq ELSE inst[i].act[s[2]].join
OutOfSpot(s) == IF s[1] = "act" THEN s[2] ELSE <<"join", s[2]>>
LastOut(i) == OutOfSpot(CHOOSE s \in Spots(i) : \A t \in Spots(i) : At(i, t) <= At(i, s))

\* What a server trace shows of instance i after a decide: its status, what was
\* scheduled in the order it was (each activation's latest attempt, and each
\* JOIN), and each task's status and attempt. A running task shows SCHEDULED.
TraceOrder(i) ==
    LET S == Spots(i) IN
    [j \in 1..Cardinality(S) |-> OutOfSpot(CHOOSE s \in S : Cardinality({t \in S : At(i, t) < At(i, s)}) = j - 1)]
TraceTasks(i) ==
    [k \in {q \in DOMAIN inst[i].act : IsLeaf(q[1]) \/ Kind(q[1]) = "terminate"} |->
        <<inst[i].act[k].status, inst[i].act[k].attempt>>]
TraceView(i) == [status |-> inst[i].status, order |-> TraceOrder(i), tasks |-> TraceTasks(i)]

-----------------------------------------------------------------------------
(***************************************************************************)
(* Ending an instance: every running attempt is cancelled, and a failure   *)
(* workflow, when one is configured and the end was a failure the workflow *)
(* suffered, starts on its own.                                            *)
(***************************************************************************)
Cancelled(a) == IF a.status = "SCHEDULED" THEN [a EXCEPT !.status = "CANCELED"] ELSE a

\* The children instance i's SUB_WORKFLOW tasks are waiting for.
Children(s, i) == {c \in 1..Len(s) : s[c].parent # NoKey /\ s[c].parent[1] = i
                                     /\ s[c].status \in {"RUNNING", "PAUSED"}}

\* Cancelling a SUB_WORKFLOW task terminates its child, and so on down; the
\* children do not start their failure workflows.
RECURSIVE EndIn(_, _, _, _, _), EndAll(_, _)

EndIn(s, i, st, cause, runFailureWf) ==
    LET kids == Children(s, i)
        e  == [s EXCEPT ![i].status = st, ![i].cause = cause,
                        ![i].act = [k \in DOMAIN s[i].act |-> Cancelled(s[i].act[k])]]
        fw == Defs[s[i].wf].failureWf
        f  == IF runFailureWf /\ st \in WfFailed /\ fw # NONE
              THEN Append(e, NewInst(fw, NoKey, i))
              ELSE e
    IN EndAll(f, kids)

EndAll(s, C) ==
    IF C = {} THEN s
    ELSE LET c == CHOOSE x \in C : TRUE
         IN EndAll(EndIn(s, c, "TERMINATED", Cause("parent", NoKey), FALSE), C \ {c})

Put(i, k, a) == inst' = [inst EXCEPT ![i].act = (k :> [a EXCEPT !.seq = inst[i].clock]) @@ @,
                                     ![i].clock = @ + 1]

-----------------------------------------------------------------------------
(***************************************************************************)
(* The world's choices, named so that a test can fix them. By default the  *)
(* world may choose anything: any answer to an attempt, any case of a      *)
(* switch, any width of a dynamic fork, and going on or stopping a loop.   *)
(***************************************************************************)
Answers(i, k) == {"COMPLETED", "FAILED", "FAILED_WITH_TERMINAL_ERROR"}
Cases(i, k)   == 1..Len(Nodes[k[1]].cases)
Widths(i, k)  == 0..MaxBranches
GoesOn(i, k)  == {TRUE, FALSE}
RerunTargets(i) == DOMAIN inst[i].act   \* the activations an operator may rerun from

-----------------------------------------------------------------------------
(* The server *)

\* What the server schedules in one decision when it reaches node n of
\* iteration it (getTasksToBeScheduled): the node and, recursively, a SWITCH's
\* chosen case's first node; each branch's first node of a fork, then its
\* JOIN; a dynamic fork's copies, then its JOIN. A DO_WHILE schedules its body
\* in a later decision. Each gets the next seq in that order, a JOIN its own.
\* A skipped node is SKIPPED, and the decision goes on to the next node of its
\* sequence (passOverSkipped), past any number of them. A TERMINATE runs when none
\* had run before the decision; after one ran, one started in the decision may
\* run too, or be cancelled first. w holds the activations and clock as the
\* decision builds them; the result is every way the decision can end.
RECURSIVE StartNode(_, _, _, _), StartFrom(_, _, _, _, _), StartBranches(_, _, _, _, _)

Add(w, k, a) == [w EXCEPT !.act = (k :> [a EXCEPT !.seq = w.clock]) @@ @, !.clock = @ + 1]
JoinAt(w, k) == [w EXCEPT !.act[k].join = w.clock, !.clock = @ + 1]
Copies(w, leaf, it, width) ==
    [w EXCEPT !.act = [q \in {<<leaf, it, j>> : j \in 1..width} |-> [Act("SCHEDULED") EXCEPT !.seq = w.clock + q[3] - 1]] @@ @,
              !.clock = @ + width]

StartNode(i, w, n, it) ==
    LET k == <<n, it, 0>> IN
         CASE IsLeaf(n)              -> {Add(w, k, Act("SCHEDULED"))}
           [] Kind(n) = "terminate" -> {Add(w, k, Act(st)) :
                                           st \in IF Terminated(i) = {} THEN {"COMPLETED"} ELSE {"COMPLETED", "CANCELED"}}
           [] Kind(n) = "switch"    -> UNION {StartFrom(i, Add(w, k, [Act("RUNNING") EXCEPT !.choice = c]), Nodes[n].cases[c], 1, it) :
                                                 c \in Cases(i, k)}
           [] Kind(n) = "fork"      -> {JoinAt(x, k) : x \in StartBranches(i, Add(w, k, Act("RUNNING")), n, it, 1)}
           [] Kind(n) = "dynfork"   -> {JoinAt(Copies(Add(w, k, [Act("RUNNING") EXCEPT !.choice = x]), Nodes[n].leaf, it, x), k) :
                                           x \in Widths(i, k)}
           [] Kind(n) = "loop"      -> {Add(w, k, [Act("RUNNING") EXCEPT !.iter = 1])}

\* From the node at index j of sequence s: the first one not skipped starts.
StartFrom(i, w, s, j, it) ==
    IF j > Len(s) THEN {w}
    ELSE IF s[j] \in inst[i].skipped
         THEN StartFrom(i, Add(w, <<s[j], it, 0>>, Act("SKIPPED")), s, j + 1, it)
         ELSE StartNode(i, w, s[j], it)

StartBranches(i, w, n, it, b) ==
    IF b > Len(Nodes[n].branches) THEN {w}
    ELSE UNION {StartBranches(i, x, n, it, b + 1) : x \in StartFrom(i, w, Nodes[n].branches[b], 1, it)}

\* Start a node that has been reached, with what the server schedules with it.
\* Data-dependent choices are the world's. (A copy is reached on its own only
\* when a rerun forgot it.)
Activate(i, k) ==
    /\ inst[i].status = "RUNNING"
    /\ ~inst[i].endsAgain   \* (the server's first decide after the reopen ends it, scheduling nothing)
    /\ Ready(i, k)
    /\ UNCHANGED ops
    /\ IF k[3] # 0
       THEN Put(i, k, Act("SCHEDULED"))
       ELSE \E w \in StartFrom(i, [act |-> inst[i].act, clock |-> inst[i].clock], SeqOf(Owner[k[1]]), Index[k[1]], k[2]) :
               inst' = [inst EXCEPT ![i].act = w.act, ![i].clock = w.clock]

\* A SUB_WORKFLOW attempt starts its child.
Launch(i, k) ==
    /\ Live(i)
    /\ Has(i, k) /\ Kind(k[1]) = "sub"
    /\ inst[i].act[k].status = "SCHEDULED" /\ inst[i].act[k].child = 0
    /\ inst' = Append([inst EXCEPT ![i].act[k].child = Len(inst) + 1],
                      NewInst(Nodes[k[1]].wf, <<i, k>>, 0))
    /\ UNCHANGED ops

\* A failed attempt: retry within budget, else an optional task completes
\* with errors, else the workflow ends.
React(i, k) ==
    /\ Live(i)
    /\ Has(i, k) /\ IsLeaf(k[1])
    /\ inst[i].act[k].status \in Unsuccessful
    /\ UNCHANGED ops
    /\ LET a  == inst[i].act[k]
           nd == Nodes[k[1]]
       IN IF a.status \in Retriable /\ a.attempt <= nd.retries /\ ~a.spent
          THEN inst' = [inst EXCEPT ![i].act[k] = [Fresh(a, inst[i].clock) EXCEPT !.auto = @ + 1],
                                    ![i].clock = @ + 1]
          ELSE inst' = IF nd.optional
                       THEN [inst EXCEPT ![i].act[k].status = "COMPLETED_WITH_ERRORS"]
                       ELSE EndIn(inst, i, WfStatusFor(a.status), Cause("task", k), TRUE)

\* A SUB_WORKFLOW task takes the outcome of its child.
ChildDone(i, k) ==
    /\ Terminated(i) = {}
    /\ inst[i].status \in {"RUNNING", "PAUSED"}
    /\ Has(i, k) /\ Kind(k[1]) = "sub"
    /\ inst[i].act[k].status = "SCHEDULED" /\ inst[i].act[k].child # 0
    /\ inst[inst[i].act[k].child].status \in WfTerminal
    /\ inst' = [inst EXCEPT ![i].act[k].status = LeafStatusFor(inst[inst[i].act[k].child].status)]
    /\ UNCHANGED ops

\* A loop whose iteration finished goes on or stops, as its condition says.
LoopDecide(i, k) ==
    /\ Live(i)
    /\ Has(i, k) /\ LoopWaiting(i, k)
    /\ \/ /\ FALSE \in GoesOn(i, k)
          /\ inst' = [inst EXCEPT ![i].act[k].stopped = TRUE]
       \/ /\ TRUE \in GoesOn(i, k)
          /\ inst[i].act[k].iter < MaxIter
          /\ inst' = [inst EXCEPT ![i].act[k].iter = @ + 1]
    /\ UNCHANGED ops

\* The body finished and nothing else is left: the workflow completes.
Complete(i) ==
    /\ Live(i)
    /\ BodyDone(i) /\ Quiet(i)
    /\ inst' = [inst EXCEPT ![i].status = "COMPLETED", ![i].cause = Cause("returned", NoKey),
                            ![i].out = IF Defs[inst[i].wf].outParams THEN <<"params">> ELSE LastOut(i)]
    /\ UNCHANGED ops

\* A TERMINATE that ran ends the workflow as it says: when it runs, and again
\* whenever the workflow is reopened. What else had started is cancelled.
TerminateEnds(i) ==
    /\ inst[i].status = "RUNNING"
    /\ \E k \in Terminated(i) :
          inst' = EndIn([inst EXCEPT ![i].out = k], i, Nodes[k[1]].status, Cause("terminate", k), TRUE)
    /\ UNCHANGED ops

-----------------------------------------------------------------------------
(* The world *)

\* Whoever runs a task answers its attempt, also while the workflow is paused.
Answer(i, k) ==
    /\ Terminated(i) = {}
    /\ inst[i].status \in {"RUNNING", "PAUSED"}
    /\ Has(i, k) /\ Kind(k[1]) = "task"
    /\ inst[i].act[k].status = "SCHEDULED"
    /\ \E r \in Answers(i, k) :
          inst' = [inst EXCEPT ![i].act[k].status = r]
    /\ UNCHANGED ops

\* An attempt runs out of time: timeoutSeconds, pollTimeoutSeconds (no worker
\* took it) or responseTimeoutSeconds (its worker stopped reporting). The
\* policy says what that means: RETRY times the attempt out, TIME_OUT_WF
\* times out the workflow, ALERT_ONLY changes nothing.
TimeOut(i, k) ==
    /\ inst[i].status = "RUNNING"
    /\ Terminated(i) = {}
    /\ Has(i, k) /\ Kind(k[1]) = "task"
    /\ inst[i].act[k].status = "SCHEDULED"
    /\ Nodes[k[1]].timeout \in {"RETRY", "TIME_OUT_WF"}
    /\ LET s == [inst EXCEPT ![i].act[k].status = "TIMED_OUT"]
       IN inst' = IF Nodes[k[1]].timeout = "RETRY" THEN s
                  ELSE EndIn(s, i, "TIMED_OUT", Cause("task", k), TRUE)
    /\ UNCHANGED ops

\* The total budget across attempts runs out. Under RETRY no attempt follows
\* and a running one times out; under TIME_OUT_WF the task and the workflow
\* time out; under ALERT_ONLY nothing changes.
TotalTimeOut(i, k) ==
    /\ inst[i].status = "RUNNING"
    /\ Terminated(i) = {}
    /\ Has(i, k) /\ Kind(k[1]) = "task" /\ Nodes[k[1]].total
    /\ Nodes[k[1]].timeout \in {"RETRY", "TIME_OUT_WF"}
    /\ ~inst[i].act[k].spent
    /\ inst[i].act[k].status \in {"SCHEDULED"} \cup Retriable
    /\ LET a  == inst[i].act[k]
           wf == Nodes[k[1]].timeout = "TIME_OUT_WF"
           s  == [inst EXCEPT ![i].act[k].spent = TRUE,
                              ![i].act[k].status = IF a.status = "SCHEDULED" \/ wf THEN "TIMED_OUT" ELSE @]
       IN inst' = IF wf THEN EndIn(s, i, "TIMED_OUT", Cause("task", k), TRUE) ELSE s
    /\ UNCHANGED ops

\* The workflow runs out of time; this applies while paused too.
WfTimeOut(i) ==
    /\ Terminated(i) = {}
    /\ inst[i].status \in {"RUNNING", "PAUSED"}
    /\ Defs[inst[i].wf].timeout = "TIME_OUT_WF"
    /\ inst' = EndIn(inst, i, "TIMED_OUT", Cause("timeout", NoKey), TRUE)
    /\ UNCHANGED ops

-----------------------------------------------------------------------------
(* Operators *)

Op == ops < MaxOps /\ ops' = ops + 1

Pause(i) ==
    /\ Op /\ inst[i].status = "RUNNING"
    /\ Terminated(i) = {}
    /\ inst' = [inst EXCEPT ![i].status = "PAUSED"]

Resume(i) ==
    /\ Op /\ inst[i].status = "PAUSED"
    /\ inst' = [inst EXCEPT ![i].status = "RUNNING"]

\* An operator's terminate; it does not start the failure workflow.
Terminate(i) ==
    /\ Terminated(i) = {}
    /\ Op /\ inst[i].status \in {"RUNNING", "PAUSED"}
    /\ inst' = EndIn(inst, i, "TERMINATED", Cause("operator", NoKey), FALSE)

\* Skip a node that has not run: it counts as finished when reached.
Skip(i, n) ==
    /\ Op /\ inst[i].status = "RUNNING"
    /\ Terminated(i) = {}
    /\ n \in Placed /\ WfOf[n] = inst[i].wf /\ ~InLoop[n]
    /\ n \notin inst[i].skipped
    /\ \A k \in DOMAIN inst[i].act : k[1] # n
    /\ inst' = [inst EXCEPT ![i].skipped = @ \cup {n}]

\* Rerun an ended workflow from one of its leaf activations: what came after
\* it is forgotten, it runs again, and so does every kept leaf that had not
\* succeeded, each in place (a rerun uses no retry). A loop holding a task
\* that runs again or was forgotten resumes at its last iteration on record.
RerunFrom(w, k) ==
    LET cut  == w.act[k].seq
        kept == {q \in DOMAIN w.act : w.act[q].seq <= cut}
        gone == DOMAIN w.act \ kept
        redo(q) == IsLeaf(q[1]) /\ (q = k \/ w.act[q].status \in Unsuccessful)
        fix(q) ==
            IF Kind(q[1]) # "loop" THEN w.act[q]
            ELSE [w.act[q] EXCEPT
                    !.iter = Max({1} \cup {r[2] : r \in {x \in kept : x[1] \in Under[q[1]] \ {q[1]}}}),
                    !.stopped = @ /\ ~\E r \in gone \cup {x \in kept : redo(x)} : r[1] \in Under[q[1]]]
        \* a JOIN scheduled after the task goes too, and the rerun schedules it again
        late == {q \in kept : w.act[q].join > cut}
        rejoin(q, a) == IF q \in late
                        THEN [a EXCEPT !.join = w.clock + Cardinality({r \in late : w.act[r].join < w.act[q].join})]
                        ELSE a
    IN [w EXCEPT !.status = "RUNNING", !.cause = NoCause, !.out = NoKey, !.epoch = @ + 1, !.how = "rerun",
                 !.act = [q \in kept |-> rejoin(q, IF redo(q) THEN Reset(w.act[q]) ELSE fix(q))],
                 !.clock = @ + Cardinality(late),
                 !.endsAgain = \E q \in kept : Kind(q[1]) = "terminate" /\ w.act[q].status = "COMPLETED"]

\* Rerun from the start.
Restart(w) ==
    [w EXCEPT !.status = "RUNNING", !.cause = NoCause, !.out = NoKey, !.epoch = @ + 1, !.how = "restart",
              !.act = [k \in {} |-> NONE], !.skipped = {}, !.endsAgain = FALSE]

Rerun(i) ==
    /\ Op /\ inst[i].parent = NoKey
    /\ inst[i].status \in WfTerminal
    /\ \/ inst' = [inst EXCEPT ![i] = Restart(@)]
       \/ \E k \in RerunTargets(i) \cap DOMAIN inst[i].act :
             /\ IsLeaf(k[1]) /\ inst[i].act[k].status # "SKIPPED"
             /\ inst' = [inst EXCEPT ![i] = RerunFrom(@, k)]

\* Retry a failed workflow: every leaf that had not succeeded runs again.
Retry(i) ==
    /\ Op /\ inst[i].parent = NoKey
    /\ inst[i].status \in WfFailed
    /\ LET w    == inst[i]
           redo == {q \in DOMAIN w.act : IsLeaf(q[1]) /\ w.act[q].status \in Unsuccessful}
       IN /\ redo # {} \/ w.status = "TIMED_OUT"
          /\ LET rank(q) == Cardinality({r \in redo : w.act[r].seq < w.act[q].seq}) IN
             inst' = [inst EXCEPT ![i] =
                        [w EXCEPT !.status = "RUNNING", !.cause = NoCause, !.out = NoKey, !.epoch = @ + 1, !.how = "retry",
                                  !.clock = @ + Cardinality(redo), !.endsAgain = Terminated(i) # {},
                                  !.act = [q \in DOMAIN w.act |->
                                             IF q \in redo THEN Fresh(w.act[q], w.clock + rank(q))
                                             ELSE w.act[q]]]]

-----------------------------------------------------------------------------
Init ==
    /\ inst = <<NewInst(Main, NoKey, 0)>>
    /\ ops = 0

Server ==
    \E i \in 1..Len(inst) :
        \/ \E k \in AllKeys[inst[i].wf] : Activate(i, k)
        \/ \E k \in DOMAIN inst[i].act :
              Launch(i, k) \/ React(i, k) \/ ChildDone(i, k) \/ LoopDecide(i, k)
        \/ Complete(i)
        \/ TerminateEnds(i)

Respond == \E i \in 1..Len(inst) : \E k \in DOMAIN inst[i].act : Answer(i, k)

World ==
    \/ Respond
    \/ \E i \in 1..Len(inst) :
          \/ \E k \in DOMAIN inst[i].act : TimeOut(i, k) \/ TotalTimeOut(i, k)
          \/ WfTimeOut(i)

Operator ==
    \E i \in 1..Len(inst) :
        \/ Pause(i) \/ Resume(i) \/ Terminate(i) \/ Rerun(i) \/ Retry(i)
        \/ \E n \in Placed : Skip(i, n)

Next == Server \/ World \/ Operator

\* The server keeps going and whoever runs tasks answers; operators need not act.
Spec == Init /\ [][Next]_vars /\ WF_vars(Server) /\ WF_vars(Respond)

-----------------------------------------------------------------------------
(* What the spec promises *)

\* Nothing runs unless the program reached it.
Justified ==
    \A i \in 1..Len(inst) : \A k \in DOMAIN inst[i].act : Reached(i, k, TRUE)

(***************************************************************************)
(* An independent statement of "has finished", used only by SequenceOrder. *)
(* It shares nothing with Finished, SeqDone or Reached, so a mistake in    *)
(* those rules cannot also change the property meant to catch it.          *)
(***************************************************************************)
RECURSIVE Over(_, _), AllOver(_, _, _)

AllOver(i, s, it) == \A j \in 1..Len(s) : Over(i, <<s[j], it, 0>>)

Over(i, k) ==
    IF k \notin DOMAIN inst[i].act THEN FALSE
    ELSE LET a == inst[i].act[k]
             nd == Nodes[k[1]]
         IN IF a.status = "SKIPPED" THEN TRUE
            ELSE CASE nd.kind \in {"task", "sub"} -> a.status \in {"COMPLETED", "COMPLETED_WITH_ERRORS"}
                   [] nd.kind = "switch"  -> AllOver(i, nd.cases[a.choice], k[2])
                   [] nd.kind = "fork"    -> \A b \in nd.joinOn : AllOver(i, nd.branches[b], k[2])
                   [] nd.kind = "dynfork" -> \A c \in 1..a.choice : Over(i, <<nd.leaf, k[2], c>>)
                   [] nd.kind = "loop"    -> a.stopped /\ AllOver(i, nd.body, a.iter)
                   [] nd.kind = "terminate" -> FALSE

\* A node starts only after everything before it in its sequence is over.
SequenceOrder ==
    \A i \in 1..Len(inst) : \A k \in DOMAIN inst[i].act :
        (k[3] = 0) =>
            LET s == SeqOf(Owner[k[1]])
            IN \A j \in 1..Len(s) :
                  (s[j] = k[1]) => \A e \in 1..(j - 1) : Over(i, <<s[e], k[2], 0>>)

\* Once a TERMINATE has run, the workflow ends as it says, unless the parent
\* ended it first.
TerminateDecides ==
    \A i \in 1..Len(inst) :
        (inst[i].status \in WfTerminal
         /\ \E k \in DOMAIN inst[i].act :
               Nodes[k[1]].kind = "terminate" /\ inst[i].act[k].status = "COMPLETED")
            => inst[i].cause.kind \in {"terminate", "parent"}

\* Once a TERMINATE has run, no running attempt finishes: it can only be cancelled.
NoAnswerAfterTerminate ==
    [][\A i \in 1..Len(inst) : \A k \in DOMAIN inst[i].act :
          (/\ IsLeaf(k[1]) /\ inst[i].act[k].status = "SCHEDULED" /\ Has(i, k)'
           /\ \E t \in DOMAIN inst[i].act :
                 Nodes[t[1]].kind = "terminate" /\ inst[i].act[t].status = "COMPLETED")
              => inst'[i].act[k].status \in {"SCHEDULED", "CANCELED"}]_vars

\* Earlier iterations of a loop are over: every task in them succeeded.
EarlierIterationsSettled ==
    \A i \in 1..Len(inst) : \A l \in DOMAIN inst[i].act :
        (Nodes[l[1]].kind = "loop" /\ inst[i].act[l].status # "SKIPPED") =>
            \A k \in DOMAIN inst[i].act :
                (/\ k[1] \in Under[l[1]] \ {l[1]} /\ Nodes[k[1]].kind \in {"task", "sub"}
                 /\ \/ k[2] < inst[i].act[l].iter
                    \/ k[2] = inst[i].act[l].iter /\ inst[i].act[l].stopped)
                    => inst[i].act[k].status \in {"COMPLETED", "COMPLETED_WITH_ERRORS", "SKIPPED"}

\* A fork starts together the first task of each branch that starts with a task
\* What the server schedules in one decision starts together, in its order:
\* a switch's chosen case's first node after the switch; each branch's first
\* node of a fork after the fork and before its JOIN; a dynamic fork's copies
\* after it and before its JOIN. A node after only skipped ones counts as first. (Until a rerun, which forgets some of it; a
\* later attempt of a task is scheduled when it is retried, after the JOIN.)
ScheduledTogether ==
    \A i \in 1..Len(inst) : inst[i].epoch = 0 =>
        \A k \in DOMAIN inst[i].act :
            LET a == inst[i].act[k]
                n == k[1]
                after(q) == q \in DOMAIN inst[i].act /\ inst[i].act[q].seq > a.seq
                first(q) == ~IsLeaf(q[1]) \/ inst[i].act[q].attempt = 1
                \* the nodes of sequence s that only skipped ones come before
                heads(s) == {j \in 1..Len(s) : \A x \in 1..(j - 1) :
                                <<s[x], k[2], 0>> \in DOMAIN inst[i].act /\ inst[i].act[<<s[x], k[2], 0>>].status = "SKIPPED"}
            IN a.status # "SKIPPED" =>
               CASE Kind(n) = "switch" ->
                       \A j \in heads(Nodes[n].cases[a.choice]) : after(<<Nodes[n].cases[a.choice][j], k[2], 0>>)
                 [] Kind(n) = "fork" ->
                       /\ a.join > a.seq
                       /\ \A b \in 1..Len(Nodes[n].branches) : \A j \in heads(Nodes[n].branches[b]) :
                             LET q == <<Nodes[n].branches[b][j], k[2], 0>> IN
                             after(q) /\ (first(q) => inst[i].act[q].seq < a.join)
                 [] Kind(n) = "dynfork" ->
                       /\ a.join > a.seq
                       /\ \A j \in 1..a.choice :
                             LET q == <<Nodes[n].leaf, k[2], j>> IN after(q) /\ (first(q) => inst[i].act[q].seq < a.join)
                 [] OTHER -> TRUE

\* A dynamic fork makes no more copies than its width.
CopiesWithinWidth ==
    \A i \in 1..Len(inst) : \A k \in DOMAIN inst[i].act :
        (k[3] > 0) =>
            \E d \in DOMAIN inst[i].act :
                /\ Nodes[d[1]].kind = "dynfork" /\ Nodes[d[1]].leaf = k[1] /\ d[2] = k[2]
                /\ k[3] <= inst[i].act[d].choice

\* A child its parent ended is TERMINATED.
ParentEndTerminates ==
    \A i \in 1..Len(inst) : inst[i].cause.kind = "parent" => inst[i].status = "TERMINATED"

\* A task's success is the world's answer: after a failure the only way to a
\* successful status is COMPLETED_WITH_ERRORS.
FailureIsNotSuccess ==
    [][\A i \in 1..Len(inst) : \A k \in DOMAIN inst[i].act :
          (IsLeaf(k[1]) /\ Has(i, k)' /\ inst[i].act[k].status \in Unsuccessful
           /\ inst'[i].act[k].status \in Successful)
              => inst'[i].act[k].status = "COMPLETED_WITH_ERRORS"]_vars

\* Every new run of a SUB_WORKFLOW task gets a new child: the run starts with
\* none, and the child it gets is the instance created in that step, for it.
NewRunNewChild ==
    [][\A i \in 1..Len(inst) : \A k \in DOMAIN inst[i].act :
          /\ (Kind(k[1]) = "sub" /\ Has(i, k)' /\ inst[i].act[k].status # "SCHEDULED"
              /\ inst'[i].act[k].status = "SCHEDULED")
                 => inst'[i].act[k].child = 0
          /\ (Kind(k[1]) = "sub" /\ Has(i, k)' /\ inst'[i].act[k].child \notin {0, inst[i].act[k].child})
                 => /\ Len(inst') = Len(inst) + 1
                    /\ inst'[i].act[k].child = Len(inst')
                    /\ inst'[Len(inst')].parent = <<i, k>>]_vars

\* A switch runs only the case it chose.
SwitchExclusive ==
    \A i \in 1..Len(inst) : \A k \in DOMAIN inst[i].act :
        (Kind(k[1]) = "switch" /\ Open(i, k)) =>
            \A c \in (1..Len(Nodes[k[1]].cases)) \ {inst[i].act[k].choice} :
                \A q \in DOMAIN inst[i].act : ~(q[1] \in CaseNodes[k[1]][c] /\ q[2] = k[2])

\* What follows a fork that ran starts only after the branches its JOIN names finished.
JoinWaits ==
    \A i \in 1..Len(inst) : \A k \in DOMAIN inst[i].act :
        (Kind(k[1]) = "fork" /\ Open(i, k) /\ Succ[k[1]] # NONE /\ Has(i, <<Succ[k[1]], k[2], 0>>))
            => \A b \in Nodes[k[1]].joinOn : SeqDone(i, Nodes[k[1]].branches[b], k[2])

\* Iterations run one after another: a loop moves to iteration n only after
\* iteration n - 1 finished and everything in it settled, and stops only
\* after its last iteration did.
LoopInOrder ==
    \A i \in 1..Len(inst) : \A k \in DOMAIN inst[i].act :
        (Kind(k[1]) = "loop" /\ Open(i, k)) =>
            LET a == inst[i].act[k] IN
            /\ a.iter > 1 => (SeqDone(i, Nodes[k[1]].body, a.iter - 1) /\ IterSettled(i, k[1], a.iter - 1))
            /\ a.stopped => (SeqDone(i, Nodes[k[1]].body, a.iter) /\ IterSettled(i, k[1], a.iter))

\* A task is retried automatically at most as often as configured.
RetryBudget ==
    \A i \in 1..Len(inst) : \A k \in DOMAIN inst[i].act :
        IsLeaf(k[1]) => inst[i].act[k].auto <= Nodes[k[1]].retries

\* An automatic retry follows only an attempt that failed or timed out, with
\* retries and time budget left.
RetryOnlyWhenAllowed ==
    [][\A i \in 1..Len(inst) : \A k \in DOMAIN inst[i].act :
          (IsLeaf(k[1]) /\ inst'[i].epoch = inst[i].epoch /\ Has(i, k)'
           /\ inst'[i].act[k].attempt > inst[i].act[k].attempt)
              => /\ inst[i].act[k].status \in {"FAILED", "TIMED_OUT"}
                 /\ inst[i].act[k].attempt <= Nodes[k[1]].retries
                 /\ ~inst[i].act[k].spent]_vars

\* Timeouts follow the task's policy: an attempt times out, or a task's total
\* budget is spent, only under RETRY or TIME_OUT_WF, and under TIME_OUT_WF
\* the workflow times out with it.
TimeoutsFollowPolicy ==
    [][\A i \in 1..Len(inst) : \A k \in DOMAIN inst[i].act :
          LET a  == inst[i].act[k]
              b  == inst'[i].act[k]
              to == Nodes[k[1]].timeout
          IN (Kind(k[1]) = "task" /\ inst'[i].epoch = inst[i].epoch /\ Has(i, k)') =>
                /\ (a.status = "SCHEDULED" /\ b.status = "TIMED_OUT")
                       => (to \in {"RETRY", "TIME_OUT_WF"}
                           /\ (to = "TIME_OUT_WF" => inst'[i].status = "TIMED_OUT"))
                /\ (~a.spent /\ b.spent) => (Nodes[k[1]].total /\ to \in {"RETRY", "TIME_OUT_WF"})
                /\ (a.status = "SCHEDULED" /\ ~a.spent /\ b.spent) => b.status = "TIMED_OUT"]_vars

\* A skipped task never runs.
SkippedNeverRuns ==
    \A i \in 1..Len(inst) : \A k \in DOMAIN inst[i].act :
        (k[3] = 0 /\ k[1] \in inst[i].skipped) => inst[i].act[k].status = "SKIPPED"

\* A task completes with errors only when it is optional and it failed with
\* nothing left: a terminal error, no retries, or no time.
OptionalOnlyWhenExhausted ==
    [][\A i \in 1..Len(inst) : \A k \in DOMAIN inst[i].act :
          (IsLeaf(k[1]) /\ inst'[i].epoch = inst[i].epoch /\ Has(i, k)'
           /\ inst[i].act[k].status # "COMPLETED_WITH_ERRORS"
           /\ inst'[i].act[k].status = "COMPLETED_WITH_ERRORS")
              => /\ Nodes[k[1]].optional
                 /\ inst[i].act[k].status \in Unsuccessful
                 /\ \/ inst[i].act[k].status \notin {"FAILED", "TIMED_OUT"}
                    \/ inst[i].act[k].attempt > Nodes[k[1]].retries
                    \/ inst[i].act[k].spent]_vars

\* Nothing runs in an ended workflow.
EndedIsQuiet ==
    \A i \in 1..Len(inst) : inst[i].status \in WfTerminal =>
        \A k \in DOMAIN inst[i].act : inst[i].act[k].status # "SCHEDULED"

\* Everything an activation started has finished: every branch of a fork,
\* whether or not its JOIN named it.
AllRan(i, k) ==
    CASE IsLeaf(k[1])         -> inst[i].act[k].status \in Successful
      [] Kind(k[1]) = "fork"  -> \A b \in 1..Len(Nodes[k[1]].branches) :
                                    SeqDone(i, Nodes[k[1]].branches[b], k[2])
      [] OTHER                -> Finished(i, k)

\* COMPLETED means everything that started finished and nothing is left, or
\* a TERMINATE that ran said so.
CompletedSound ==
    \A i \in 1..Len(inst) : inst[i].status = "COMPLETED" =>
        \/ /\ inst[i].cause.kind = "returned"
           /\ BodyDone(i) /\ Quiet(i) /\ Terminated(i) = {}
           /\ \A k \in DOMAIN inst[i].act : Open(i, k) => AllRan(i, k)
        \/ /\ inst[i].cause.kind = "terminate" /\ Nodes[inst[i].cause.key[1]].status = "COMPLETED"
           /\ inst[i].act[inst[i].cause.key].status = "COMPLETED"

\* A failed end has a reason: a task that failed with no recovery left, a
\* TERMINATE, a timeout, an operator, or the parent ending.
FailedSound ==
    \A i \in 1..Len(inst) : inst[i].status \in WfFailed =>
        LET c == inst[i].cause IN
        \/ c.kind = "operator" /\ inst[i].status = "TERMINATED"
        \/ c.kind = "parent" /\ inst[i].status = "TERMINATED"
        \/ c.kind = "timeout" /\ inst[i].status = "TIMED_OUT"
        \/ /\ c.kind = "terminate" /\ Nodes[c.key[1]].status = inst[i].status
           /\ inst[i].act[c.key].status = "COMPLETED"
        \/ /\ c.kind = "task"
           /\ LET a  == inst[i].act[c.key]
                  nd == Nodes[c.key[1]]
              IN /\ a.status \in Unsuccessful
                 /\ \/ /\ ~nd.optional
                       /\ \/ a.status = "TIMED_OUT" /\ inst[i].status = "TIMED_OUT"
                          \/ a.status = "CANCELED" /\ inst[i].status = "TERMINATED"
                          \/ a.status \in {"FAILED", "FAILED_WITH_TERMINAL_ERROR"} /\ inst[i].status = "FAILED"
                       /\ a.status \notin {"FAILED", "TIMED_OUT"} \/ a.attempt > nd.retries \/ a.spent
                    \/ /\ Kind(c.key[1]) = "task" /\ nd.timeout = "TIME_OUT_WF"
                       /\ a.status = "TIMED_OUT" /\ inst[i].status = "TIMED_OUT"

\* A workflow's output comes from its TERMINATE, its outputParameters, or the
\* task scheduled last.
OutputSound ==
    \A i \in 1..Len(inst) : inst[i].status = "COMPLETED" =>
        IF inst[i].cause.kind = "terminate" THEN inst[i].out = inst[i].cause.key
        ELSE IF Defs[inst[i].wf].outParams THEN inst[i].out = <<"params">>
        ELSE LET places == {<<k, inst[i].act[k].seq>> : k \in DOMAIN inst[i].act}
                           \cup {<<<<"join", k>>, inst[i].act[k].join>> : k \in {q \in DOMAIN inst[i].act : inst[i].act[q].join > 0}}
             IN \E p \in places : (\A q \in places : q[2] <= p[2]) /\ inst[i].out = p[1]

\* A SUB_WORKFLOW task's outcome is its child's: COMPLETED, FAILED, TIMED_OUT,
\* or CANCELED for TERMINATED; an optional one that failed shows its errors.
\* One cancelled because its own workflow ended has a child that ended too.
SubMirrorsChild ==
    \A i \in 1..Len(inst) : \A k \in DOMAIN inst[i].act :
        (Kind(k[1]) = "sub" /\ inst[i].act[k].child # 0) =>
            LET a  == inst[i].act[k]
                cs == inst[a.child].status
                mirrors == \/ a.status = "COMPLETED" /\ cs = "COMPLETED"
                           \/ a.status = "FAILED" /\ cs = "FAILED"
                           \/ a.status = "TIMED_OUT" /\ cs = "TIMED_OUT"
                           \/ a.status = "CANCELED" /\ cs = "TERMINATED"
            IN /\ (a.status \in {"COMPLETED"} \cup Unsuccessful
                   /\ ~(a.status = "CANCELED" /\ inst[i].status \in WfTerminal))
                     => mirrors
               /\ (a.status = "CANCELED" /\ inst[i].status \in WfTerminal) => cs \in WfTerminal
               /\ a.status = "COMPLETED_WITH_ERRORS" => cs \in WfFailed

\* A child runs only while its parent's SUB_WORKFLOW task waits for it.
ChildrenFollowParent ==
    \A c \in 1..Len(inst) :
        (inst[c].status \in {"RUNNING", "PAUSED"} /\ inst[c].parent # NoKey) =>
            LET p == inst[c].parent[1]
                k == inst[c].parent[2]
            IN Has(p, k) /\ inst[p].act[k].status = "SCHEDULED" /\ inst[p].act[k].child = c

\* A running workflow is never stuck: when nothing is running and nothing is
\* left to start, its body has finished (so it can complete).
NeverStuck ==
    \A i \in 1..Len(inst) : (Live(i) /\ Quiet(i)) => BodyDone(i)

\* A failure workflow starts exactly when a workflow that has one ends with a
\* failure of its own, not when an operator or its parent terminated it; one
\* per such end.
FailureWorkflowSound ==
    [][/\ \A j \in (Len(inst) + 1)..Len(inst') :
            inst'[j].origin # 0 =>
                LET o == inst'[j].origin IN
                /\ inst[o].status \notin WfFailed /\ inst'[o].status \in WfFailed
                /\ inst'[o].cause.kind \notin {"operator", "parent"}
                /\ inst'[j].wf = Defs[inst[o].wf].failureWf
       /\ \A o \in 1..Len(inst) :
            (/\ inst[o].status \notin WfFailed /\ inst'[o].status \in WfFailed
             /\ inst'[o].cause.kind \notin {"operator", "parent"}
             /\ Defs[inst[o].wf].failureWf # NONE)
                => Cardinality({j \in (Len(inst) + 1)..Len(inst') : inst'[j].origin = o}) = 1]_vars

\* A workflow an operator reopened with a TERMINATE on record ends again as
\* that TERMINATE says, and nothing new starts first: the server's first decide
\* after the reopen schedules nothing and ends it.
ReopenedEndsAgain ==
    [][\A i \in 1..Len(inst) :
          (inst[i].status = "RUNNING" /\ inst[i].endsAgain) => DOMAIN inst'[i].act \subseteq DOMAIN inst[i].act]_vars

\* An ended workflow stays ended until an operator reopens it.
EndIsFinal ==
    [][\A i \in 1..Len(inst) :
          (inst[i].status \in WfTerminal /\ inst'[i].epoch = inst[i].epoch)
              => (inst'[i].status = inst[i].status /\ inst'[i].act = inst[i].act)]_vars

\* A task that succeeded keeps its result until an operator reopens the workflow.
SuccessIsFinal ==
    [][\A i \in 1..Len(inst) : \A k \in DOMAIN inst[i].act :
          (IsLeaf(k[1]) /\ inst[i].act[k].status \in Successful /\ inst'[i].epoch = inst[i].epoch)
              => (Has(i, k)' /\ inst'[i].act[k] = inst[i].act[k])]_vars

\* Reopening an ended workflow (rerun or retry) runs again each task that had
\* not succeeded, with at most one new attempt and a fresh time budget, and
\* keeps every task that had succeeded except the one a rerun starts from.
ReopenSound ==
    [][\A i \in 1..Len(inst) : inst'[i].epoch # inst[i].epoch =>
          /\ inst[i].status \in WfTerminal /\ inst'[i].status = "RUNNING"
          /\ DOMAIN inst'[i].act \subseteq DOMAIN inst[i].act
          /\ \A q \in DOMAIN inst'[i].act :
                (IsLeaf(q[1]) /\ inst[i].act[q].status \in Unsuccessful)
                    => /\ inst'[i].act[q].status = "SCHEDULED" /\ ~inst'[i].act[q].spent
                       /\ inst'[i].act[q].attempt =
                             IF inst'[i].how = "retry" THEN inst[i].act[q].attempt + 1 ELSE inst[i].act[q].attempt
          /\ \A q \in DOMAIN inst'[i].act :
                Kind(q[1]) \in {"switch", "dynfork"} => inst'[i].act[q].choice = inst[i].act[q].choice
          /\ inst'[i].how = "retry" => (DOMAIN inst'[i].act = DOMAIN inst[i].act /\ inst[i].status \in WfFailed)
          /\ inst'[i].how = "restart" => (DOMAIN inst'[i].act = {} /\ inst'[i].skipped = {})
          /\ inst'[i].how = "rerun" =>
                \E t \in DOMAIN inst'[i].act :
                   /\ IsLeaf(t[1]) /\ inst'[i].act[t].status = "SCHEDULED"
                   /\ DOMAIN inst'[i].act = {q \in DOMAIN inst[i].act : inst[i].act[q].seq <= inst[i].act[t].seq}
          /\ LET changed == {q \in DOMAIN inst'[i].act :
                               IsLeaf(q[1]) /\ inst[i].act[q].status \in Successful
                               /\ inst'[i].act[q] # inst[i].act[q]}
             IN /\ Cardinality(changed) <= IF inst'[i].how = "rerun" THEN 1 ELSE 0
                /\ \A q \in changed : inst'[i].act[q] = Reset(inst[i].act[q])]_vars

\* While paused nothing new starts and nothing is decided: running attempts
\* may finish, and ending cancels them; nothing else changes.
PausedIsFrozen ==
    [][\A i \in 1..Len(inst) : inst[i].status = "PAUSED" =>
          /\ DOMAIN inst'[i].act = DOMAIN inst[i].act
          /\ inst'[i].skipped = inst[i].skipped
          /\ \A k \in DOMAIN inst[i].act :
                \/ inst'[i].act[k] = inst[i].act[k]
                \/ /\ inst[i].act[k].status = "SCHEDULED"
                   /\ Kind(k[1]) = "task" => inst'[i].act[k].status # "TIMED_OUT"
                   /\ inst'[i].act[k] = [inst[i].act[k] EXCEPT !.status = inst'[i].act[k].status]]_vars

\* Paused, or waiting on a child that is.
RECURSIVE Held(_)
Held(i) ==
    \/ inst[i].status = "PAUSED"
    \/ \E k \in DOMAIN inst[i].act :
          /\ Kind(k[1]) = "sub" /\ inst[i].act[k].status = "SCHEDULED"
          /\ inst[i].act[k].child # 0 /\ Held(inst[i].act[k].child)

\* Every workflow ends, unless it is paused or waits on a paused child.
Settles ==
    <>[](\A i \in 1..Len(inst) : inst[i].status \in WfTerminal \/ Held(i))
=============================================================================
