---- MODULE MC_NRaise ----
(* async(L1 raises before calling anything), join: the raise reaches the join. *)
EXTENDS NativeProtocol

Call(i, ok, fail) == [op |-> "call", in |-> i, ok |-> ok, fail |-> fail]
Sw(next) == [op |-> "switch", in |-> NONE, next |-> next]
Async(ls, next) == [op |-> "async", in |-> NONE, lanes |-> ls, next |-> next]
Join(ls, ok, fail) == [op |-> "join", in |-> NONE, lanes |-> ls, ok |-> ok, fail |-> fail]
Ret == [op |-> "return"]
Raise == [op |-> "raise"]

P == ("f" :> Async({"L1"}, "j")) @@ ("j" :> Join({"L1"}, "r", "x")) @@ ("r" :> Ret) @@ ("x" :> Raise) @@ ("y" :> Raise)
MCPrograms == 1 :> P
MCRoots == ("main" :> "f") @@ ("L1" :> "y")
MCSpawns == ("main" :> {"L1"}) @@ ("L1" :> {})
====
