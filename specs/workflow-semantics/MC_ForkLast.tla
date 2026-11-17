------------------------------ MODULE MC_ForkLast ---------------------------
(* a; then, last, a fork of b (retried once) and a switch (c | nothing),     *)
(* joining both. The server schedules the switch's chosen case with the     *)
(* fork and the JOIN after it, so the output comes from the JOIN, or from   *)
(* b's second attempt, scheduled after the JOIN.                            *)
EXTENDS Common

MCNodes ==
       ("a"  :> Task(0, FALSE))
    @@ ("f"  :> Fork(<< <<"b">>, <<"sw">> >>, {1, 2}))
    @@ ("b"  :> Task(1, FALSE))
    @@ ("sw" :> Switch(<< <<"c">>, << >> >>))
    @@ ("c"  :> Task(0, FALSE))

MCDefs == "main" :> Def(<<"a", "f">>, NONE, NONE, FALSE)
=============================================================================
