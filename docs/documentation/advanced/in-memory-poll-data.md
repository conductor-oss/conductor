# In-memory Poll Data Storage

Conductor stores records of the last time it was polled for data by the task workers and uses this to calculate which domains to assign to tasks. When using Redis as a data store this is fine as updates are cheap, however when using PostgreSQL as the datestore this can result in a lot of updates to the same records, causing resource contention and locking.

If you are not using domains it is also possible to store this data in-memory on each Conductor instance. Whilst this has the downside that when querying the API for this data you are only getting the data for the server serving your request, it has the benefit of not causing any more database writes. In practice, if you have many workers polling for tasks, this data changes hundreds of times per second, so only having a slice of that data has little impact.

Setting the following property will use the in-memory implementation:

```
conductor.polldata.type=memory
```

**WARNING**: If you are using domains for your task workers then you should avoid using this module as it can negatively impact the domain allocation for workers.
