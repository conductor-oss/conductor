# Guided Tutorial

## High Level Steps
Generally, these are the steps necessary in order to put Conductor to work for your business workflow:

1. Create task worker(s) that poll for scheduled tasks at regular interval
2. Create task definitions for these workers and register them.
3. Create the workflow definition

## Before We Begin
Ensure you have a Conductor instance up and running. This includes both the Server and the UI. We recommend following the [Docker Instructions](../running/docker.md).

## Tools
For the purpose of testing and issuing API calls, the following tools are useful

- Linux cURL command
- [Postman](https://www.postman.com) or similar REST client

## Let's Go
We will begin by defining a simple workflow that utilizes System Tasks. 

[Next](first-workflow.md)

