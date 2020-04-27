## Publishing metrics

Conductor uses [spectator](https://github.com/Netflix/spectator) to collect the metrics.

- To enable conductor serve to publish metrics, add this [dependency](http://netflix.github.io/spectator/en/latest/registry/metrics3/) to your build.gradle.
- Conductor Server enables you to load additional modules dynamically, this feature can be controlled using this [configuration](https://github.com/Netflix/conductor/blob/master/server/README.md#additional-modules-optional).
- Create your own AbstractModule that overides configure function and registers the Spectator metrics registry.
- Initialize the Registry and add it to the global registry via ```((CompositeRegistry)Spectator.globalRegistry()).add(...)```.

The following metrics are published by the server. You can use these metrics to configure alerts for your workflows and tasks.

| Name        | Purpose           | Tags  |
| ------------- |:-------------| -----|
| workflow_server_error | Rate at which server side error is happening | methodName|
| workflow_failure | Counter for failing workflows|workflowName, status|
| workflow_start_error | Counter for failing to start a workflow|workflowName|
| workflow_running | Counter for no. of running workflows | workflowName, version|
| workflow_execution | Timer for Workflow completion | workflowName, ownerApp |
| task_queue_wait | Time spent by a task in queue | taskType|
| task_execution | Time taken to execute a task | taskType, includeRetries, status |
| task_poll | Time taken to poll for a task | taskType|
| task_poll_count | Counter for number of times the task is being polled | taskType, domain |
| task_queue_depth | Pending tasks queue depth | taskType, ownerApp |
| task_rate_limited | Current number of tasks being rate limited | taskType |
| task_concurrent_execution_limited | Current number of tasks being limited by concurrent execution limit | taskType |
| task_timeout | Counter for timed out tasks | taskType |
| task_response_timeout | Counter for tasks timedout due to responseTimeout | taskType |
| task_update_conflict | Counter for task update conflicts. Eg: when the workflow is in terminal state | workflowName, taskType, taskStatus, workflowStatus |
| event_queue_messages_processed | Counter for number of messages fetched from an event queue | queueType, queueName |
| observable_queue_error | Counter for number of errors encountered when fetching messages from an event queue | queueType |
| event_queue_messages_handled | Counter for number of messages executed from an event queue | queueType, queueName |
| external_payload_storage_usage | Counter for number of times external payload storage was used | name, operation, payloadType |

[1]: https://github.com/Netflix/spectator

## Collecting metrics

One way of collecting metrics is to push them into the logging framework (log4j).
Log4j supports various appenders that can print metrics into a console/file or even send them to remote metrics collectors over e.g. syslog channel.

Conductor provides optional modules that connect metrics registry with the logging framework.
To enable these modules, configure following additional modules property in config.properties:

    conductor.additional.modules=com.netflix.conductor.contribs.metrics.MetricsRegistryModule,com.netflix.conductor.contribs.metrics.LoggingMetricsModule
    com.netflix.conductor.contribs.metrics.LoggingMetricsModule.reportPeriodSeconds=15
    
This will push all available metrics into log4j every 15 seconds.

By default, the metrics will be handled as a regular log message (just printed to console with default log4j.properties).
In order to change that, you can use following log4j configuration that prints metrics into a dedicated file:

    log4j.rootLogger=INFO,console,file
    
    log4j.appender.console=org.apache.log4j.ConsoleAppender
    log4j.appender.console.layout=org.apache.log4j.PatternLayout
    log4j.appender.console.layout.ConversionPattern=%d{ISO8601} %5p [%t] (%C) - %m%n
    
    log4j.appender.file=org.apache.log4j.RollingFileAppender
    log4j.appender.file.File=/app/logs/conductor.log
    log4j.appender.file.MaxFileSize=10MB
    log4j.appender.file.MaxBackupIndex=10
    log4j.appender.file.layout=org.apache.log4j.PatternLayout
    log4j.appender.file.layout.ConversionPattern=%d{ISO8601} %5p [%t] (%C) - %m%n
    
    # Dedicated file appender for metrics
    log4j.appender.fileMetrics=org.apache.log4j.RollingFileAppender
    log4j.appender.fileMetrics.File=/app/logs/metrics.log
    log4j.appender.fileMetrics.MaxFileSize=10MB
    log4j.appender.fileMetrics.MaxBackupIndex=10
    log4j.appender.fileMetrics.layout=org.apache.log4j.PatternLayout
    log4j.appender.fileMetrics.layout.ConversionPattern=%d{ISO8601} %5p [%t] (%C) - %m%n
    
    log4j.logger.ConductorMetrics=INFO,console,fileMetrics
    log4j.additivity.ConductorMetrics=false

This configuration is bundled with conductor-server in file: log4j-file-appender.properties and can be utilized by setting env var:

    LOG4J_PROP=log4j-file-appender.properties
    
This variable is used by _startup.sh_ script.

### Integration with logstash using a log file

The metrics collected by log4j can be further processed and pushed into a central collector such as ElasticSearch.
One way of achieving this is to use: log4j file appender -> logstash -> ElasticSearch.

Considering the above setup, you can deploy logstash to consume the contents of /app/logs/metrics.log file, process it and send further to elasticsearch.

Following configuration needs to be used in logstash to achieve it:

pipeline.yml:

    - pipeline.id: conductor_metrics
      path.config: "/usr/share/logstash/pipeline/logstash_metrics.conf"
      pipeline.workers: 2

logstash_metrics.conf

    input {
    
     file {
      path => ["/conductor-server-logs/metrics.log"]
      codec => multiline {
          pattern => "^%{TIMESTAMP_ISO8601} "
          negate => true
          what => previous
      }
     }
    }
    
    filter {
        kv {
            field_split => ", "
            include_keys => [ "name", "type", "count", "value" ]
        }
        mutate {
            convert => {
              "count" => "integer"
              "value" => "float"
            }
          }
    }
    
    output {
     elasticsearch {
      hosts => ["elasticsearch:9200"]
     }
    }

Note: In addition to forwarding the metrics into ElasticSearch, logstash will extract following fields from each metric: name, type, count, value and set proper types

### Integration with fluentd using a syslog channel

Another example of metrics collection uses: log4j syslog appender -> fluentd -> prometheus.

In this case, a specific log4j properties file needs to be used so that metrics are pushed into a syslog channel:

    log4j.rootLogger=INFO,console,file
    
    log4j.appender.console=org.apache.log4j.ConsoleAppender
    log4j.appender.console.layout=org.apache.log4j.PatternLayout
    log4j.appender.console.layout.ConversionPattern=%d{ISO8601} %5p [%t] (%C) - %m%n
    
    log4j.appender.file=org.apache.log4j.RollingFileAppender
    log4j.appender.file.File=/app/logs/conductor.log
    log4j.appender.file.MaxFileSize=10MB
    log4j.appender.file.MaxBackupIndex=10
    log4j.appender.file.layout=org.apache.log4j.PatternLayout
    log4j.appender.file.layout.ConversionPattern=%d{ISO8601} %5p [%t] (%C) - %m%n
    
    # Syslog based appender streaming metrics into fluentd
    log4j.appender.server=org.apache.log4j.net.SyslogAppender
    log4j.appender.server.syslogHost=fluentd:5170
    log4j.appender.server.facility=LOCAL1
    log4j.appender.server.layout=org.apache.log4j.PatternLayout
    log4j.appender.server.layout.ConversionPattern=%d{ISO8601} %5p [%t] (%C) - %m%n
    
    log4j.logger.ConductorMetrics=INFO,console,server
    log4j.additivity.ConductorMetrics=false

And on the fluentd side you need following configuration:

    <source>
      @type prometheus
    </source>
    
    <source>
      @type syslog
      port 5170
      bind 0.0.0.0
      tag conductor
        <parse>
         ; only allow TIMER metrics of workflow execution and extract tenant ID
          @type regexp
          expression /^.*type=TIMER, name=workflow_execution.class-WorkflowMonitor.+workflowName-(?<tenant>.*)_(?<workflow>.+), count=(?<count>\d+), min=(?<min>[\d.]+), max=(?<max>[\d.]+), mean=(?<mean>[\d.]+).*$/
          types count:integer,min:float,max:float,mean:float
        </parse>
    </source>
    
    <filter conductor.local1.info>
        @type prometheus
        <metric>
          name conductor_workflow_count
          type gauge
          desc The total number of executed workflows
          key count
          <labels>
            workflow ${workflow}
            tenant ${tenant}
            user ${email}
          </labels>
        </metric>
        <metric>
          name conductor_workflow_max_duration
          type gauge
          desc Max duration in millis for a workflow
          key max
          <labels>
            workflow ${workflow}
            tenant ${tenant}
            user ${email}
          </labels>
        </metric>
        <metric>
          name conductor_workflow_mean_duration
          type gauge
          desc Mean duration in millis for a workflow
          key mean
          <labels>
            workflow ${workflow}
            tenant ${tenant}
            user ${email}
          </labels>
        </metric>
    </filter>
    
    <match **>
      @type stdout
    </match>
    
With above configuration, fluentd will:
- Listen to raw metrics on 0.0.0.0:5170
- Collect only workflow_execution TIMER metrics
- Process the raw metrics and expose 3 prometheus specific metrics
- Expose prometheus metrics on http://fluentd:24231/metrics 