---- MODULE MC_NSpawnOnly ----
(* async(L1: async(L2: b), return), join(L1): L1 only starts L2 and returns, never pausing;
   the join waits for L1, which has settled, and for L2. *)
EXTENDS NativeProtocol
Call(i, ok, fail) == [op |-> "call", in |-> i, ok |-> ok, fail |-> fail]
Sw(i, next) == [op |-> "switch", in |-> i, next |-> next]
Async(ls, next) == [op |-> "async", in |-> NONE, lanes |-> ls, next |-> next]
Join(ls, ok, fail) == [op |-> "join", in |-> NONE, lanes |-> ls, ok |-> ok, fail |-> fail]
Throw(skips) == [op |-> "raise", cause |-> NONE, skips |-> skips]
Ret == [op |-> "return"]
Raise == [op |-> "raise"]
P == ("f" :> Async({"L1"}, "j")) @@ ("j" :> Join({"L1"}, "r", "x")) @@ ("r" :> Ret) @@ ("x" :> Raise)
  @@ ("g" :> Async({"L2"}, "e")) @@ ("e" :> Ret)
  @@ ("b" :> Call(NONE, "e2", "e2")) @@ ("e2" :> Ret)
MCPrograms == 1 :> P
MCRoots == ("main" :> "f") @@ ("L1" :> "g") @@ ("L2" :> "b")
MCSpawns == ("main" :> {"L1"}) @@ ("L1" :> {"L2"}) @@ ("L2" :> {})
====
