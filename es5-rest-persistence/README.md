# ES5 REST persistence

This module provides ES5 REST persistence instead of relying on the Transport protocol.

## Usage

To use this module, set the following values in your conductor configuration:


```
workflow.elasticsearch.version=5-rest
workflow.elasticsearch.url=http://127.0.0.1:9200
```

**NOTE:** You can set multiple hosts by appending hosts via a `,` (comma).