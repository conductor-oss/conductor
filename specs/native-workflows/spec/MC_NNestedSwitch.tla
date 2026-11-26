---- MODULE MC_NNestedSwitch ----
(* a, switch s1 (its case: switch s2 (its case: b), then c), d. Both switches have a case they
   do not take. The inner switch and c are in s1's case; the code continues in the outer case
   after the inner one, and after both. *)
EXTENDS NativeProtocol

Call(i, ok, fail) == [op |-> "call", in |-> i, ok |-> ok, fail |-> fail]
Sw(i, next) == [op |-> "switch", in |-> i, next |-> next]
Ret == [op |-> "return"]

P == ("a" :> Call(NONE, "s1", "s1"))
  @@ ("s1" :> ([untaken |-> <<"y_if_other">>] @@ Sw(NONE, "s2")))
  @@ ("s2" :> ([untaken |-> <<"z_if_other">>] @@ Sw("s1", "b")))
  @@ ("b" :> Call("s2", "c", "c"))
  @@ ("c" :> Call("s1", "d", "d"))
  @@ ("d" :> Call(NONE, "r", "r")) @@ ("r" :> Ret)
MCPrograms == 1 :> P
MCRoots == "main" :> "a"
MCSpawns == "main" :> {}
====
