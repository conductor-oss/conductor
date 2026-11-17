----------------------------- MODULE MC_TermReady ---------------------------
(* fork (x then TERMINATE as FAILED | a then b | c) joining all; z. When the  *)
(* TERMINATE ends the workflow b may be ready but not started; a retry or    *)
(* rerun reopens it with the TERMINATE on record. One operator action.      *)
EXTENDS Common

MCNodes ==
       ("f" :> Fork(<< <<"x", "t">>, <<"a", "b">>, <<"c">> >>, {1, 2, 3}))
    @@ ("x" :> Task(0, FALSE))
    @@ ("t" :> TerminateNode("FAILED"))
    @@ ("a" :> Task(0, FALSE))
    @@ ("b" :> Task(0, FALSE))
    @@ ("c" :> Task(0, FALSE))
    @@ ("z" :> Task(0, FALSE))

MCDefs == ("main" :> Def(<<"f", "z">>, NONE, NONE, FALSE))
=============================================================================
