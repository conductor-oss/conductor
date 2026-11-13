---- MODULE MC_NLanePreview ----
(* async(L1: switch(b; the untaken case would call z)), join(L1): the joined lane draws a
   preview, which never runs; the join still completes. *)
EXTENDS NativeProtocol
Call(i, ok, fail) == [op |-> "call", in |-> i, ok |-> ok, fail |-> fail]
Sw(i, next) == [op |-> "switch", in |-> i, next |-> next]
Async(ls, next) == [op |-> "async", in |-> NONE, lanes |-> ls, next |-> next]
Join(ls, ok, fail) == [op |-> "join", in |-> NONE, lanes |-> ls, ok |-> ok, fail |-> fail]
Throw(skips) == [op |-> "raise", cause |-> NONE, skips |-> skips]
Ret == [op |-> "return"]
Raise == [op |-> "raise"]
P == ("f" :> Async({"L1"}, "j")) @@ ("j" :> Join({"L1"}, "r", "x")) @@ ("r" :> Ret) @@ ("x" :> Raise)
  @@ ("s" :> ([untaken |-> <<"z_if_other">>] @@ Sw(NONE, "b"))) @@ ("b" :> Call("s", "e", "e")) @@ ("e" :> Ret)
MCPrograms == 1 :> P
MCRoots == ("main" :> "f") @@ ("L1" :> "s")
MCSpawns == ("main" :> {"L1"}) @@ ("L1" :> {})
====
