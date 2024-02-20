-- Enable workflow status listener in all workflow definitions
UPDATE public.meta_workflow_def
SET json_data = jsonb_set(
    json_data::jsonb, 
    '{workflowStatusListenerEnabled}', 
    'true'::jsonb
)::text;

INSERT INTO
  public.meta_task_def ("name", json_data)
VALUES
(
    'finalize_workflow_execution_task',
    json_build_object(
      'createTime' ,EXTRACT(epoch FROM CURRENT_TIMESTAMP)::bigint * 1000,
      'createdBy' ,'',
      'accessPolicy', jsonb '{}',
      'name' ,'finalize_workflow_execution_task',
      'description' ,'Finalize Workflow Execution Task',
      'retryCount' ,5,
      'timeoutSeconds' ,3600,
      'inputKeys' ,jsonb '[]',
      'outputKeys' ,jsonb '[]',
      'timeoutPolicy' ,'TIME_OUT_WF',
      'retryLogic' ,'EXPONENTIAL_BACKOFF',
      'retryDelaySeconds' ,10,
      'responseTimeoutSeconds' ,600,
      'inputTemplate', jsonb '{}',
      'rateLimitPerFrequency' ,0,
      'rateLimitFrequencyInSeconds' ,1,
      'ownerEmail' ,'admin@sirenapp.io',
      'backoffScaleFactor' ,1
    )
  );
  
INSERT INTO 
   public.meta_workflow_def ("name", "version", latest_version, json_data)
VALUES(
        'finalize_workflow_execution',
         1,
         1, 
         json_build_object(
          'createTime',EXTRACT(epoch FROM CURRENT_TIMESTAMP)::bigint * 1000,
          'accessPolicy',jsonb '{}',
          'name','finalize_workflow_execution',
          'description','Workflow for finalizing workflow execution',
          'version',1,
          'tasks',
          json_build_array(json_build_object(
            'name','finalize_workflow_execution_task',
          'taskReferenceName','finalize_workflow_execution_task_ref',
          'inputParameters',
          json_build_object(
          'status',E'\u0024{workflow.input.status}',
          'externalExecutionId',E'\u0024{workflow.input.workflowId}'
          ),
          'type','SIMPLE',
          'startDelay',0,
          'optional',false,
          'asyncComplete',false,
          'permissive',false
         )),
          'inputParameters',jsonb '[]',
          'outputParameters',jsonb '{}',
          'schemaVersion',2,
          'restartable',true,
          'workflowStatusListenerEnabled',false,
          'ownerEmail','admin@sirenapp.io',
          'timeoutPolicy','ALERT_ONLY',
          'timeoutSeconds',0,
          'variables',jsonb '{}',
          'inputTemplate',jsonb '{}'
         )
       );

INSERT INTO 
   public.meta_event_handler (id, "name", "event", active, json_data)
VALUES(
        1,
       'finalize_workflow_execution_event_handler', 
       'conductor:finalize_workflow_execution_event', 
        true, 
        json_build_object(
          'name','finalize_workflow_execution_event_handler',
          'event','conductor:finalize_workflow_execution_event',
          'actions',
          json_build_array(
          json_build_object('action','start_workflow',
          'start_workflow',
          json_build_object(
          'name','finalize_workflow_execution',
          'input',json_build_object(
          'workflowType',E'\u0024{workflowType}',
          'version',E'\u0024{version}',
          'workflowId',E'\u0024{workflowId}',
          'correlationId',E'\u0024{correlationId}',
          'status',E'\u0024{status}',
          'input',E'\u0024{input}',
          'output',E'\u0024{output}',
          'reasonForIncompletion',E'\u0024{reasonForIncompletion}',
          'executionTime',E'\u0024{executionTime}',
          'event',E'\u0024{event}')
          )
          )
          ),
           'active',true
        )
       );

