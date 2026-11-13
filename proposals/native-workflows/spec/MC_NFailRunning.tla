---- MODULE MC_NFailRunning ----
(* async(L1: b), c, raise: the main lane raises while b may still be running; the failure
   cancels it. *)
EXTENDS NativeProtocol
Call(i, ok, fail) == [op |-> "call", in |-> i, ok |-> ok, fail |-> fail]
Sw(i, next) == [op |-> "switch", in |-> i, next |-> next]
Async(ls, next) == [op |-> "async", in |-> NONE, lanes |-> ls, next |-> next]
Join(ls, ok, fail) == [op |-> "join", in |-> NONE, lanes |-> ls, ok |-> ok, fail |-> fail]
Throw(skips) == [op |-> "raise", cause |-> NONE, skips |-> skips]
Ret == [op |-> "return"]
Raise == [op |-> "raise"]
P == ("f" :> Async({"L1"}, "c")) @@ ("c" :> Call(NONE, "x", "x")) @@ ("x" :> Raise)
  @@ ("b" :> Call(NONE, "e", "e")) @@ ("e" :> Ret)
MCPrograms == 1 :> P
MCRoots == ("main" :> "f") @@ ("L1" :> "b")
MCSpawns == ("main" :> {"L1"}) @@ ("L1" :> {})
====
