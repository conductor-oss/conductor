------------------------------ MODULE MC_ForkEnd ----------------------------
(* fork (TERMINATE as COMPLETED | TERMINATE as FAILED; a | b); z. One op.   *)
(* The branches start together, so both TERMINATEs run and the workflow    *)
(* ends as one of them says; a never runs.                                  *)
EXTENDS Common

MCNodes ==
       ("f" :> Fork(<< <<"t1">>, <<"t2", "a">>, <<"b">> >>, {1, 2, 3}))
    @@ ("t1" :> TerminateNode("COMPLETED"))
    @@ ("t2" :> TerminateNode("FAILED"))
    @@ ("a"  :> Task(0, FALSE))
    @@ ("b"  :> Task(0, FALSE))
    @@ ("z"  :> Task(0, FALSE))

MCDefs == "main" :> Def(<<"f", "z">>, NONE, NONE, FALSE)

\* Reachable: both TERMINATEs ran. Used as a witness by check.py.
BothRan == /\ Has(1, <<"t1", 0, 0>>) /\ inst[1].act[<<"t1", 0, 0>>].status = "COMPLETED"
           /\ Has(1, <<"t2", 0, 0>>) /\ inst[1].act[<<"t2", 0, 0>>].status = "COMPLETED"
=============================================================================
