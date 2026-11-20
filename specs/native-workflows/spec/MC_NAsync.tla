---- MODULE MC_NAsync ----
(* async(L1: b), c, join(L1). A failed lane raises at the join. One fault. *)
EXTENDS NativeProtocol

Call(i, ok, fail) == [op |-> "call", in |-> i, ok |-> ok, fail |-> fail]
Sw(next) == [op |-> "switch", in |-> NONE, next |-> next]
Async(ls, next) == [op |-> "async", in |-> NONE, lanes |-> ls, next |-> next]
Join(ls, ok, fail) == [op |-> "join", in |-> NONE, lanes |-> ls, ok |-> ok, fail |-> fail]
Ret == [op |-> "return"]
Raise == [op |-> "raise"]

P == ("f" :> Async({"L1"}, "c")) @@ ("c" :> Call(NONE, "j", "j"))
  @@ ("j" :> Join({"L1"}, "r", "x")) @@ ("b" :> Call(NONE, "e", "y"))
  @@ ("r" :> Ret) @@ ("x" :> Raise) @@ ("e" :> Ret) @@ ("y" :> Raise)
MCPrograms == 1 :> P
MCRoots == ("main" :> "f") @@ ("L1" :> "b")
MCSpawns == ("main" :> {"L1"}) @@ ("L1" :> {})
====
