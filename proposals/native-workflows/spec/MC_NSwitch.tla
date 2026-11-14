---- MODULE MC_NSwitch ----
(* a, switch(b; the untaken case would call z), c, d: code continues after the case
   over several continuations; z is a preview and never runs. *)
EXTENDS NativeProtocol

Call(i, ok, fail) == [op |-> "call", in |-> i, ok |-> ok, fail |-> fail]
Sw(next) == [op |-> "switch", in |-> NONE, next |-> next]
Async(ls, next) == [op |-> "async", in |-> NONE, lanes |-> ls, next |-> next]
Join(ls, ok, fail) == [op |-> "join", in |-> NONE, lanes |-> ls, ok |-> ok, fail |-> fail]
Ret == [op |-> "return"]
Raise == [op |-> "raise"]

P == ("a" :> Call(NONE, "s", "s")) @@ ("s" :> ([untaken |-> <<"z_if_other">>] @@ Sw("b"))) @@ ("b" :> Call("s", "c", "c"))
  @@ ("c" :> Call(NONE, "d", "d")) @@ ("d" :> Call(NONE, "r", "r")) @@ ("r" :> Ret)
MCPrograms == 1 :> P
MCRoots == "main" :> "a"
MCSpawns == "main" :> {}
====
