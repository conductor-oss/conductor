export declare const sampleJavaCode: ({ taskDefName, inputParamKeys, }: {
    taskDefName: string;
    inputParamKeys: string[];
}) => string;
export declare const samplePythonCode: ({ taskDefName, inputParamKeys, }: {
    taskDefName: string;
    inputParamKeys?: string[];
}) => string;
export declare const sampleGolangCode: ({ taskDefName, inputParamKeys, }: {
    taskDefName: string;
    inputParamKeys?: string[];
}) => string;
export declare const sampleCSharpCode: ({ taskDefName, inputParamKeys, }: {
    taskDefName: string;
    inputParamKeys?: string[];
}) => string;
export declare const sampleJavaScriptCode: ({ taskDefName, accessToken, inputParamKeys, }: {
    taskDefName: string;
    accessToken: string;
    inputParamKeys?: string[];
}) => string;
export declare const sampleTypeScriptCode: ({ taskDefName, accessToken, inputParamKeys, }: {
    taskDefName: string;
    accessToken: string;
    inputParamKeys?: string[];
}) => string;
export declare const sampleClojureCode = "(defn create-tasks\n  \"Returns workflow tasks\"\n  []\n  (vector (sdk/simple-task (:get-user-info constants) (:get-user-info constants) {:userId \"${workflow.input.userId}\"})\n          (sdk/switch-task \"emailorsms\" \"${workflow.input.notificationPref}\" {\"email\" [(sdk/simple-task (:send-email constants) (:send-email constants) {\"email\" \"${get_user_info.output.email}\"})]\n                                                                              \"sms\" [(sdk/simple-task (:send-sms constants) (:send-sms constants) {\"phoneNumber\" \"${get_user_info.output.phoneNumber}\"})]} [])))\n\n(defn create-workflow\n  \"Returns a workflow with tasks\"\n  [tasks]\n  (merge (sdk/workflow (:workflow-name constants) tasks) {:inputParameters [\"userId\" \"notificationPref\"]}))\n";
