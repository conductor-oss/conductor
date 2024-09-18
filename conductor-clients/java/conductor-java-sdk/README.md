# Conductor Java Client/SDK V4

## Overview

**This project is currently in incubating status**. 

It is under active development and subject to changes as it evolves. While the core features are functional, the project is not yet considered stable, and breaking changes may occur as we refine the architecture and add new functionality.

These changes are largely driven by **"dependency optimization"** and a redesign of the client to introduces filters, events and listeners to allow extensibility through callbacks or Event-Driven Architecture, IoC.

This client has a reduced dependency set. The aim is to minimize Classpath pollution and prevent potential conflicts.

Consider Netflix Eureka, a direct dependency of the OSS v3 client. Some users have reported version conflicts. To prevent these unnecessary conflicts and the hassle of managing exclusions in Gradle or Maven configurations, we’ve decided to remove this direct dependency.

In OSS v3 client it's used by `TaskPollExecutor` before polling to make the following check:

```java
if (eurekaClient != null
        && !eurekaClient.getInstanceRemoteStatus().equals(InstanceStatus.UP)
        && !discoveryOverride) {
    LOGGER.debug("Instance is NOT UP in discovery - will not poll");
    return;
}
```

You will be able to achieve the same with a `PollFilter` (we plan to provide modules with some implementations). It could look something like this:

```java
 var runnerConfigurer = new TaskRunnerConfigurer
        .Builder(taskClient, List.of(new ApprovalWorker()))
        .withThreadCount(10)
        .withPollFilter((String taskType, String domain) -> {
            return eurekaClient.getInstanceRemoteStatus().equals(InstanceStatus.UP);
        })
        .withListener(PollCompleted.class, (e) -> {
            log.info("Poll Completed {}", e);
            var timer = prometheusRegistry.timer("poll_completed", "type", e.getTaskType());
            timer.record(e.getDuration());
        })
        .build();

runnerConfigurer.init();
```

The telemetry part was also removed but you can achieve the same with a MetricsCollector, or Events and Listeners as shown in the example.

### Breaking Changes

While we aim to minimize breaking changes, there are a few areas where such changes are necessary. 

Below are two specific examples of where changes may affect your existing code:

#### (1) Jersey Config

The `WorkflowClient` and other clients will retain the same methods, but constructors with dependencies on Jersey are being removed. For example:

```java
public WorkflowClient(ClientConfig config, ClientHandler handler) {
     this(config, new DefaultConductorClientConfiguration(), handler);
}
```

#### (2) Eureka Client

In the Worker API we've removed the Eureka Client configuration option (from `TaskRunnerConfigurer`).

```java
* @param eurekaClient Eureka client - used to identify if the server is in discovery or
 *     not. When the server goes out of discovery, the polling is terminated. If passed
 *     null, discovery check is not done.
 * @return Builder instance
 */
public Builder withEurekaClient(EurekaClient eurekaClient) {
    this.eurekaClient = eurekaClient;
    return this;
}
```

## TODO

Take a look at this board: https://github.com/orgs/conductor-oss/projects/3

## Feedback

We are building this based on feedback from our users and community. 

We encourage everyone to share their thoughts and feedback! You can create new GitHub issues or comment on existing ones. You can also join our [Slack community](https://orkes-conductor.slack.com/) to connect with us.

Thank you! ♥

