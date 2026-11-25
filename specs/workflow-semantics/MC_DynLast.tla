------------------------------ MODULE MC_DynLast ----------------------------
(* a; then, last, a dynamic fork of up to two copies of a task. The server  *)
(* schedules the copies with the fork and the JOIN after them, also when    *)
(* there are none: the output comes from the JOIN.                          *)
EXTENDS Common

MCNodes ==
       ("a" :> Task(0, FALSE))
    @@ ("d" :> DynFork("q"))
    @@ ("q" :> Task(0, FALSE))

MCDefs == "main" :> Def(<<"a", "d">>, NONE, NONE, FALSE)
=============================================================================
