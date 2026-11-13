---- MODULE MC_NThrow ----
(* a, then code that throws before b (b is previewed); the fix in build 2 calls b instead.
   An operator retries the failed run, before or after the fix is deployed. *)
EXTENDS NativeProtocol
Call(i, ok, fail) == [op |-> "call", in |-> i, ok |-> ok, fail |-> fail]
Ret == [op |-> "return"]
Throw(skips) == [op |-> "raise", cause |-> NONE, skips |-> skips]
P1 == ("a" :> Call(NONE, "x", "x")) @@ ("x" :> Throw(<<"b_if_no_error">>))
P2 == ("a" :> Call(NONE, "b", "b")) @@ ("b" :> Call(NONE, "r", "r")) @@ ("r" :> Ret)
MCPrograms == (1 :> P1) @@ (2 :> P2)
MCRoots == "main" :> "a"
MCSpawns == "main" :> {}
====
