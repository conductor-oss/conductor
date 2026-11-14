----------------------------- MODULE MC_Unjoined ----------------------------
(* fork (a | do { b } while (...)) joining only a; z. The loop is in a      *)
(* branch nothing waits for, so the workflow must still wait for it.        *)
EXTENDS Common

MCNodes ==
       ("f" :> Fork(<< <<"a">>, <<"l">> >>, {1}))
    @@ ("a" :> Task(0, FALSE))
    @@ ("l" :> Loop(<<"b">>))
    @@ ("b" :> Task(0, TRUE))
    @@ ("z" :> Task(0, FALSE))

MCDefs == "main" :> Def(<<"f", "z">>, NONE, NONE, FALSE)
=============================================================================
