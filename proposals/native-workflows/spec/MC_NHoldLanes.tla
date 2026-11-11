---- MODULE MC_NHoldLanes ----
(* async(L1: b, c), p, q, join: a deploy changes L1 only, and the whole run is held. *)
EXTENDS NativeProtocol

Call(i, ok, fail) == [op |-> "call", in |-> i, ok |-> ok, fail |-> fail]
Sw(next) == [op |-> "switch", in |-> NONE, next |-> next]
Async(ls, next) == [op |-> "async", in |-> NONE, lanes |-> ls, next |-> next]
Join(ls, ok, fail) == [op |-> "join", in |-> NONE, lanes |-> ls, ok |-> ok, fail |-> fail]
Ret == [op |-> "return"]
Raise == [op |-> "raise"]

Common == ("f" :> Async({"L1"}, "p")) @@ ("p" :> Call(NONE, "q", "q")) @@ ("q" :> Call(NONE, "j", "j"))
  @@ ("j" :> Join({"L1"}, "r", "r")) @@ ("r" :> Ret) @@ ("c" :> Call(NONE, "e", "e")) @@ ("e" :> Ret)
P1 == Common @@ ("b" :> Call(NONE, "c", "c"))
P2 == Common @@ ("b" :> Call(NONE, "z", "z")) @@ ("z" :> Call(NONE, "e", "e"))
MCPrograms == (1 :> P1) @@ (2 :> P2)
MCRoots == ("main" :> "f") @@ ("L1" :> "b")
MCSpawns == ("main" :> {"L1"}) @@ ("L1" :> {})
====
