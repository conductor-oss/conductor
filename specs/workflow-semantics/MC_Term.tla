------------------------------- MODULE MC_Term ------------------------------
(* fork (a | TERMINATE as FAILED) joining both; z. One operator action, so   *)
(* a retry or rerun can reopen a workflow its TERMINATE ended.              *)
EXTENDS Common

MCNodes ==
       ("f" :> Fork(<< <<"a">>, <<"t">> >>, {1, 2}))
    @@ ("a" :> Task(1, FALSE))
    @@ ("t" :> TerminateNode("FAILED"))
    @@ ("z" :> Task(0, FALSE))
    @@ ("w" :> Task(0, FALSE))

MCDefs == ("main" :> Def(<<"f", "z">>, "onfail", NONE, FALSE))
    @@ ("onfail" :> Def(<<"w">>, NONE, NONE, FALSE))
=============================================================================
