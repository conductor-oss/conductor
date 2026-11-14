---- MODULE MC_NNested ----
(* async(L1: async(L2: b), c), join(L1): the join waits for L2 too. *)
EXTENDS NativeProtocol

Call(i, ok, fail) == [op |-> "call", in |-> i, ok |-> ok, fail |-> fail]
Sw(next) == [op |-> "switch", in |-> NONE, next |-> next]
Async(ls, next) == [op |-> "async", in |-> NONE, lanes |-> ls, next |-> next]
Join(ls, ok, fail) == [op |-> "join", in |-> NONE, lanes |-> ls, ok |-> ok, fail |-> fail]
Ret == [op |-> "return"]
Raise == [op |-> "raise"]

P == ("f" :> Async({"L1"}, "j")) @@ ("j" :> Join({"L1"}, "r", "x"))
  @@ ("g" :> Async({"L2"}, "c")) @@ ("c" :> Call(NONE, "e", "e")) @@ ("b" :> Call(NONE, "e2", "e2"))
  @@ ("r" :> Ret) @@ ("x" :> Raise) @@ ("e" :> Ret) @@ ("e2" :> Ret)
MCPrograms == 1 :> P
MCRoots == ("main" :> "f") @@ ("L1" :> "g") @@ ("L2" :> "b")
MCSpawns == ("main" :> {"L1"}) @@ ("L1" :> {"L2"}) @@ ("L2" :> {})
====
