# ES5 Persistence

This module provides ES5 persistence when indexing workflows and tasks.

## Usage

This module uses the following configuration options:

* `workflow.elasticsearch.version` - This is the version of ES to use.
You must set this to `5` to use this module.
* `workflow.elasticsearch.transport` - This determines what type of transport to use.
Valid options are `tcp` or `rest`.
Default is `tcp`, if not specified.
* `workflow.elasticsearch.embedded` - This determines whether or not to run an embedded ES server when `db=memory`.
This option is ignored if `db` is set to some other value.
Default is `true`.
* `workflow.elasticsearch.url` - A comma separated list of schema/host/port of the ES nodes to communicate with.
Schema can be ignored when using `tcp` transport; otherwise, you must specify `http` or `https`.
If you are using `db=memory`, this will automatically use the correct host information to connect to it depending on transport.

* `workflow.elasticsearch.index.name` - The name of the workflow and task index.
Defaults to `conductor`
* `workflow.elasticsearch.tasklog.index.name` - The name of the task log index.
Defaults to `task_log`


### REST Transport

If you are using AWS ElasticSearch, you should use the `rest` transport as that's the only version transport that they support.
However, this module currently only works with open IAM, VPC version of ElasticSearch.
Eventually, we should create ES modules that can be loaded in to support authentication and request signing, but this currently does not support that.

### Example Configurations

**In-memory ES with TCP transport**

```
db=memory

workflow.elasticsearch.version=5
```

**In-memory ES with REST transport**

```
db=memory

workflow.elasticsearch.version=5
workflow.elasticsearch.transport=rest
```

**ES with TCP transport**

```
workflow.elasticsearch.version=5
workflow.elasticsearch.url=127.0.0.1:9300
```

**ES with REST transport**

```
workflow.elasticsearch.version=5
workflow.elasticsearch.transport=rest
workflow.elasticsearch.url=http://127.0.0.1:9200
```
