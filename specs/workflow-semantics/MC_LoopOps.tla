------------------------------ MODULE MC_LoopOps ----------------------------
(* do { switch (p | nothing) } while (...); y, with two operator actions.   *)
(* p retries once, so reruns and retries meet loops and switches.           *)
EXTENDS Common

MCNodes ==
       ("l"  :> Loop(<<"sw">>))
    @@ ("sw" :> Switch(<< <<"p">>, << >> >>))
    @@ ("p"  :> Task(1, FALSE))
    @@ ("y"  :> Task(0, FALSE))

MCDefs == "main" :> Def(<<"l", "y">>, NONE, NONE, FALSE)
=============================================================================
