------------------------------- MODULE MC_Skip ------------------------------
(* a; TERMINATE as COMPLETED; z. Two operator actions, so a   *)
(* TERMINATE can be skipped, and a workflow it completed rerun or retried.  *)
EXTENDS Common

MCNodes ==
       ("a" :> Task(0, FALSE))
    @@ ("t" :> TerminateNode("COMPLETED"))
    @@ ("z" :> Task(0, FALSE))
    @@ ("w" :> Task(0, FALSE))

MCDefs == ("main" :> Def(<<"a", "t", "z">>, "onfail", NONE, FALSE))
    @@ ("onfail" :> Def(<<"w">>, NONE, NONE, FALSE))
=============================================================================
