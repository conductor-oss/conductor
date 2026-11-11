----------------------------- MODULE MC_LoopFork ----------------------------
(* do { fork (c; switch (p | nothing) | d) joining only the first branch }  *)
(* while (...); y. The first branch ends in a switch; d runs past the join  *)
(* but not past the iteration. A sub-workflow runs after the loop.          *)
EXTENDS Common

MCNodes ==
       ("l"  :> Loop(<<"f3">>))
    @@ ("f3" :> Fork(<< <<"c", "sw">>, <<"d">> >>, {1}))
    @@ ("c"  :> Task(0, FALSE))
    @@ ("sw" :> Switch(<< <<"p">>, << >> >>))
    @@ ("p"  :> Task(0, TRUE))
    @@ ("d"  :> Task(0, TRUE))
    @@ ("s"  :> Sub("child", 0, TRUE))
    @@ ("v"  :> Task(0, FALSE))

MCDefs == ("main" :> Def(<<"l", "s">>, NONE, NONE, FALSE))
    @@ ("child" :> Def(<<"v">>, NONE, NONE, FALSE))
=============================================================================
