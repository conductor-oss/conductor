# Metrics
Conductor publishes detailed metrics for the server. 
For the list of metrics published by server, see:

https://netflix.github.io/conductor/metrics/server/

Conductor supports plugging in metrics collectors, the following are currently supported by this module:
1. Datadog
2. Prometheus
3. Logging (dumps all the metrics to Slf4J logger)

## Published Artifacts

Group: `com.netflix.conductor`

| Published Artifact | Description |
| ----------- | ----------- | 
| conductor-metrics | Metrics configuration  |

**Note**: If you are using `condutor-contribs` as a dependency, the metrics module is already included, you do not need to include it separately.

#### Configuration
* Logging Metrics

    `conductor.metrics-logger.enabled=true`
* Prometheus

    `conductor.metrics-prometheus.enabled=true`
* Datadog

    `conductor.metrics-datadog.enabled=true`