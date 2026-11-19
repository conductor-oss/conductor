---- MODULE MC_NHold ----
(* a, b, c; a deploy replaces b with x. A run that recorded b is held until the rollback. *)
EXTENDS NativeProtocol

Call(i, ok, fail) == [op |-> "call", in |-> i, ok |-> ok, fail |-> fail]
Sw(next) == [op |-> "switch", in |-> NONE, next |-> next]
Async(ls, next) == [op |-> "async", in |-> NONE, lanes |-> ls, next |-> next]
Join(ls, ok, fail) == [op |-> "join", in |-> NONE, lanes |-> ls, ok |-> ok, fail |-> fail]
Ret == [op |-> "return"]
Raise == [op |-> "raise"]

P1 == ("a" :> Call(NONE, "b", "b")) @@ ("b" :> Call(NONE, "c", "c")) @@ ("c" :> Call(NONE, "r", "r")) @@ ("r" :> Ret)
P2 == ("a" :> Call(NONE, "z", "z")) @@ ("z" :> Call(NONE, "c", "c")) @@ ("c" :> Call(NONE, "r", "r")) @@ ("r" :> Ret)
MCPrograms == (1 :> P1) @@ (2 :> P2)
MCRoots == "main" :> "a"
MCSpawns == "main" :> {}
====
