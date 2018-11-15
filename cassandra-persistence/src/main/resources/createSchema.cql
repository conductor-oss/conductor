CREATE KEYSPACE IF NOT EXISTS conductor
  WITH replication = { 'class' : 'NetworkTopologyStrategy', 'us-east': '3'};

CREATE TABLE IF NOT EXISTS conductor.workflows (
  workflow_id uuid,
  shard_id int,
  task_id uuid,
  entity text,
  payload text,
  total_tasks int STATIC,
  total_partitions int STATIC,
  PRIMARY KEY((workflow_id, shard_id), entity, task_id)
);

CREATE TABLE IF NOT EXISTS conductor.task_lookup(
  task_id uuid,
  workflow_id uuid,
  PRIMARY KEY (task_id)
);
