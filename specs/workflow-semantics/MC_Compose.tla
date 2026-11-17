----------------------------- MODULE MC_Compose -----------------------------
(* a; switch (b | nothing); fork (c | d) joining only c; z.                 *)
(* a is retried once; b is optional; d runs on past the join.              *)
EXTENDS Common

MCNodes ==
       ("a"  :> Task(1, FALSE))
    @@ ("sw" :> Switch(<< <<"b">>, << >> >>))
    @@ ("b"  :> Task(0, TRUE))
    @@ ("f"  :> Fork(<< <<"c">>, <<"d">> >>, {1}))
    @@ ("c"  :> Task(0, FALSE))
    @@ ("d"  :> Task(0, FALSE))
    @@ ("z"  :> Task(0, FALSE))

MCDefs == "main" :> Def(<<"a", "sw", "f", "z">>, NONE, NONE, FALSE)
=============================================================================
