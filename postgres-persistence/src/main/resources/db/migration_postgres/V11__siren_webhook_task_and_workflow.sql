INSERT INTO
  public.meta_task_def ("name", json_data)
VALUES
(
    'send_webhook_task',
     json_build_object(
      'createTime' ,EXTRACT(epoch FROM CURRENT_TIMESTAMP)::bigint * 1000,
      'createdBy' ,'',
      'accessPolicy', jsonb '{}',
      'name' ,'send_webhook_task',
      'description' ,'Send Webhook Task',
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

INSERT INTO public.meta_workflow_def
( "name", "version", latest_version, json_data)
VALUES(
       'webhook_workflow', 
        1, 
        1, 
        json_build_object(
           'createTime',EXTRACT(epoch FROM CURRENT_TIMESTAMP)::bigint * 1000,
           'accessPolicy', jsonb '{}',
           'name','webhook_workflow',
           'description','Workflow for sending webhook',
           'version',1,
           'tasks',
           json_build_array(
           json_build_object(
            'name','send_webhook_task',
            'taskReferenceName','send_webhook_task_ref',
            'inputParameters',
            json_build_object(
              'notificationAuditId',E'\u0024{workflow.input.notificationAuditId}'
            ),
            'type','SIMPLE',
            'startDelay',0,
            'optional',true,
            'asyncComplete',false,
            'permissive',false
            )),
            'inputParameters', jsonb '[]',
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

