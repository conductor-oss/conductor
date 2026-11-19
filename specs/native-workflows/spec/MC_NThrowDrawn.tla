---- MODULE MC_NThrowDrawn ----
(* a, then in one continuation: a switch whose taken case is empty (the untaken one would call
   z), a lane L started (l1), and a throw before b. The fix in build 2 calls b instead. A retry
   removes all that continuation drew: the code draws it again when it runs again. *)
EXTENDS NativeProtocol
Call(i, ok, fail) == [op |-> "call", in |-> i, ok |-> ok, fail |-> fail]
Sw(i, next) == [op |-> "switch", in |-> i, next |-> next]
Async(ls, next) == [op |-> "async", in |-> NONE, lanes |-> ls, next |-> next]
Throw(skips) == [op |-> "raise", cause |-> NONE, skips |-> skips]
Ret == [op |-> "return"]
Common == ("a" :> Call(NONE, "s", "s")) @@ ("s" :> ([untaken |-> <<"z_if_other">>] @@ Sw(NONE, "f")))
   @@ ("l1" :> Call(NONE, "lr", "lr")) @@ ("lr" :> Ret)
P1 == Common @@ ("f" :> Async({"L"}, "x")) @@ ("x" :> Throw(<<"b_if_no_error">>))
P2 == Common @@ ("f" :> Async({"L"}, "b")) @@ ("b" :> Call(NONE, "r", "r")) @@ ("r" :> Ret)
MCPrograms == (1 :> P1) @@ (2 :> P2)
MCRoots == ("main" :> "a") @@ ("L" :> "l1")
MCSpawns == ("main" :> {"L"}) @@ ("L" :> {})
====
