---- MODULE MC_NThrowSwitch ----
(* a, switch(c; the untaken case would call z), then code that throws before b; the fix in
   build 2 calls b instead. A retry removes the mark and what it skipped, not the switch's
   preview of the case it did not take. *)
EXTENDS NativeProtocol
Call(i, ok, fail) == [op |-> "call", in |-> i, ok |-> ok, fail |-> fail]
Sw(i, next) == [op |-> "switch", in |-> i, next |-> next]
Async(ls, next) == [op |-> "async", in |-> NONE, lanes |-> ls, next |-> next]
Join(ls, ok, fail) == [op |-> "join", in |-> NONE, lanes |-> ls, ok |-> ok, fail |-> fail]
Throw(skips) == [op |-> "raise", cause |-> NONE, skips |-> skips]
Ret == [op |-> "return"]
Raise == [op |-> "raise"]
P1 == ("a" :> Call(NONE, "s", "s")) @@ ("s" :> ([untaken |-> <<"z_if_other">>] @@ Sw(NONE, "c")))
   @@ ("c" :> Call("s", "x", "x")) @@ ("x" :> Throw(<<"b_if_no_error">>))
P2 == ("a" :> Call(NONE, "s", "s")) @@ ("s" :> ([untaken |-> <<"z_if_other">>] @@ Sw(NONE, "c")))
   @@ ("c" :> Call("s", "b", "b")) @@ ("b" :> Call(NONE, "r", "r")) @@ ("r" :> Ret)
MCPrograms == (1 :> P1) @@ (2 :> P2)
MCRoots == "main" :> "a"
MCSpawns == "main" :> {}
====
