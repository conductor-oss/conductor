------------------------------ MODULE MC_SkipHead ---------------------------
(* a; then, last, a fork of (b, b2, c) and d, joining both. Two operator     *)
(* actions: skipping b, or b and b2, while a runs. The server passes over    *)
(* skipped tasks when it maps the fork, so c is scheduled with the fork and *)
(* the JOIN after it: the output comes from the JOIN.                       *)
EXTENDS Common

MCNodes ==
       ("a"  :> Task(0, FALSE))
    @@ ("f"  :> Fork(<< <<"b", "b2", "c">>, <<"d">> >>, {1, 2}))
    @@ ("b"  :> Task(0, FALSE))
    @@ ("b2" :> Task(0, FALSE))
    @@ ("c"  :> Task(0, FALSE))
    @@ ("d"  :> Task(0, FALSE))

MCDefs == "main" :> Def(<<"a", "f">>, NONE, NONE, FALSE)
=============================================================================
