----------------------------- MODULE MC_Timeout -----------------------------
(* A sub-workflow, then u, which may time the workflow out. The workflow    *)
(* has its own timeout and a failure workflow.                              *)
EXTENDS Common

MCNodes ==
       ("s"  :> Sub("child", 0, FALSE))
    @@ ("u"  :> TimedTask(0, FALSE, "TIME_OUT_WF", FALSE))
    @@ ("cx" :> Task(0, FALSE))
    @@ ("w"  :> Task(0, FALSE))

MCDefs ==
       ("main"   :> Def(<<"s", "u">>, "onfail", "TIME_OUT_WF", TRUE))
    @@ ("child"  :> Def(<<"cx">>, NONE, NONE, FALSE))
    @@ ("onfail" :> Def(<<"w">>, NONE, NONE, FALSE))
=============================================================================
