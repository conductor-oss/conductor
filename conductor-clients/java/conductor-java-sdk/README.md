# Conductor Java SDK v4

[Conductor](https://www.conductor-oss.org/) is the leading open-source orchestration platform allowing developers to build highly scalable distributed applications.

This Gradle project includes multiple modules that enable developers to manage, create, test, and run workflows and execute workers.

## Projects

The Conductor Java SDK v4 is organized into several key modules to help developers build and manage workflows and workers with ease

- **[conductor-client](conductor-client/README.md)**: This module provides the core client library to interact with Conductor through its HTTP API, enabling the creation, execution, and management of workflows, while also providing a framework for building workers.

- **[conductor-client-metrics](conductor-client-metrics/README.md)**: (Incubating) Provides metrics and monitoring capabilities for Conductor clients. 

- **[conductor-client-spring](conductor-client-spring/README.md)**: Provides Spring framework configurations, simplifying the use of Conductor client in Spring-based applications.

- **[examples](examples/README.md)**: Samples demonstrating how to use the Conductor Java SDK to create and manage workflows, tasks, and workers. These examples provide practical starting points for developers integrating Conductor into their applications.

- **[sdk](sdk/README.md)**: The SDK module allows developers to create, test and execute workflows using code.

- **orkes-client**: Extends the Conductor client by adding authentication needed for Orkes Conductor, and includes additional clients for interacting with endpoints specific to the Orkes-hosted Conductor platform.

- **orkes-spring**: Provides Spring framework configurations, simplifying the use of Orkes Conductor client in Spring-based applications.


## Roadmap

For insights into the Conductor project's future plans and upcoming features, check out the roadmap here: [Conductor OSS Roadmap](https://github.com/orgs/conductor-oss/projects/3).

## Feedback

We are building this based on feedback from our users and community. 

We encourage everyone to share their thoughts and feedback! You can create new GitHub issues or comment on existing ones. You can also join our [Slack community](https://join.slack.com/t/orkes-conductor/shared_invite/zt-2vdbx239s-Eacdyqya9giNLHfrCavfaA) to connect with us.

Thank you! ♥

