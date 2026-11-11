------------------------------- MODULE MC_Ops -------------------------------
(* a; fork (b | e) joining both; c, with two operator actions: pause,       *)
(* resume, terminate, skip, rerun, retry. a retries once and has a total    *)
(* time budget; b is optional, retries once and has a total budget; c has  *)
(* timeouts and a total budget under ALERT_ONLY.                            *)
EXTENDS Common

MCNodes ==
       ("a"  :> TimedTask(1, FALSE, "RETRY", TRUE))
    @@ ("f2" :> Fork(<< <<"b">>, <<"e">> >>, {1, 2}))
    @@ ("b"  :> TimedTask(1, TRUE, "RETRY", TRUE))
    @@ ("e"  :> Task(0, FALSE))
    @@ ("c"  :> TimedTask(0, FALSE, NONE, TRUE))

MCDefs == "main" :> Def(<<"a", "f2", "c">>, NONE, NONE, FALSE)
=============================================================================
