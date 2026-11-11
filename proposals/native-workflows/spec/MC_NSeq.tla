---- MODULE MC_NSeq ----
(* a, then b; if a fails, c and raise. Two faults. *)
EXTENDS NativeProtocol

Call(i, ok, fail) == [op |-> "call", in |-> i, ok |-> ok, fail |-> fail]
Sw(next) == [op |-> "switch", in |-> NONE, next |-> next]
Async(ls, next) == [op |-> "async", in |-> NONE, lanes |-> ls, next |-> next]
Join(ls, ok, fail) == [op |-> "join", in |-> NONE, lanes |-> ls, ok |-> ok, fail |-> fail]
Ret == [op |-> "return"]
Raise == [op |-> "raise"]

P == ("a" :> Call(NONE, "b", "c")) @@ ("b" :> Call(NONE, "r", "x"))
  @@ ("c" :> Call(NONE, "x", "x")) @@ ("r" :> Ret) @@ ("x" :> Raise)
MCPrograms == 1 :> P
MCRoots == "main" :> "a"
MCSpawns == "main" :> {}
====
