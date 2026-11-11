------------------------------ MODULE MC_Sub --------------------------------
(* main: sub(child), retried once; then u. main has a failure workflow.     *)
(* child: switch (v | TERMINATE as FAILED). One operator action.            *)
EXTENDS Common

MCNodes ==
       ("s"   :> Sub("child", 1, FALSE))
    @@ ("u"   :> Task(0, FALSE))
    @@ ("csw" :> Switch(<< <<"v">>, <<"t">> >>))
    @@ ("v"   :> Task(0, TRUE))
    @@ ("t"   :> TerminateNode("FAILED"))
    @@ ("w"   :> Task(0, FALSE))

MCDefs ==
       ("main"   :> Def(<<"s", "u">>, "onfail", NONE, TRUE))
    @@ ("child"  :> Def(<<"csw">>, NONE, NONE, FALSE))
    @@ ("onfail" :> Def(<<"w">>, NONE, NONE, FALSE))
=============================================================================
