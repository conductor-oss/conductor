------------------------------- MODULE Common -------------------------------
(* Shorthands for writing programs in the models. *)
EXTENDS Workflow

Task(retries, optional) ==
    [kind |-> "task", retries |-> retries, optional |-> optional, timeout |-> NONE, total |-> FALSE]
TimedTask(retries, optional, timeout, total) ==
    [kind |-> "task", retries |-> retries, optional |-> optional, timeout |-> timeout, total |-> total]
Sub(wf, retries, optional) == [kind |-> "sub", wf |-> wf, retries |-> retries, optional |-> optional]
Switch(cases) == [kind |-> "switch", cases |-> cases]
Fork(branches, joinOn) == [kind |-> "fork", branches |-> branches, joinOn |-> joinOn]
DynFork(leaf) == [kind |-> "dynfork", leaf |-> leaf]
Loop(body) == [kind |-> "loop", body |-> body]
TerminateNode(status) == [kind |-> "terminate", status |-> status]
Def(body, failureWf, timeout, outParams) ==
    [body |-> body, failureWf |-> failureWf, timeout |-> timeout, outParams |-> outParams]

=============================================================================
