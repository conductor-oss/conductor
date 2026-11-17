------------------------------- MODULE MC_Dyn -------------------------------
(* a dynamic fork of up to two copies of a task retried once; z. One        *)
(* operator action. What follows the fork waits for every copy.             *)
EXTENDS Common

MCNodes ==
       ("d" :> DynFork("q"))
    @@ ("q" :> Task(1, FALSE))
    @@ ("z" :> Task(0, FALSE))

MCDefs == "main" :> Def(<<"d", "z">>, NONE, NONE, FALSE)
=============================================================================
