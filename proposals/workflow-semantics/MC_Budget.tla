------------------------------ MODULE MC_Budget -----------------------------
(* a task with a retry and a total budget under TIME_OUT_WF; a task.        *)
EXTENDS Common

MCNodes ==
       ("a" :> TimedTask(1, FALSE, "TIME_OUT_WF", TRUE))
    @@ ("z" :> Task(0, FALSE))

MCDefs == "main" :> Def(<<"a", "z">>, NONE, NONE, FALSE)
=============================================================================
