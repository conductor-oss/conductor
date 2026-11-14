------------------------------ MODULE MC_Loop -------------------------------
(* do { switch (p | nothing); dynamic fork of q } while (...); y.          *)
(* q is optional; up to two iterations and two copies.                     *)
EXTENDS Common

MCNodes ==
       ("l"   :> Loop(<<"sw2", "df">>))
    @@ ("sw2" :> Switch(<< <<"p">>, << >> >>))
    @@ ("p"   :> Task(0, FALSE))
    @@ ("df"  :> DynFork("q"))
    @@ ("q"   :> Task(0, TRUE))
    @@ ("y"   :> Task(0, FALSE))

MCDefs == "main" :> Def(<<"l", "y">>, NONE, NONE, FALSE)
=============================================================================
