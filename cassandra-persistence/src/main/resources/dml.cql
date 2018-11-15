INSERT INTO conductor.workflows (workflow_id,shard_id,task_id,entity,payload,total_tasks,total_partitions) VALUES (?,?,?,'workflow',?,?,?);
INSERT INTO conductor.workflows (workflow_id,shard_id,task_id,entity,payload) VALUES (?,?,?,'task',?);

SELECT total_tasks,total_partitions FROM conductor.workflows WHERE workflow_id=? AND shard_id=1;
SELECT payload FROM conductor.workflows WHERE workflow_id=? AND shard_id=? AND entity='task' AND task_id=?;
SELECT payload FROM conductor.workflows WHERE workflow_id=? AND shard_id=1 AND entity='workflow';
SELECT * FROM conductor.workflows WHERE workflow_id=? AND shard_id=?;
SELECT workflow_id FROM conductor.task_lookup WHERE task_id=?;

UPDATE conductor.workflows SET payload=? WHERE workflow_id=? AND shard_id=1 AND entity='workflow' AND task_id='';
UPDATE conductor.workflows SET total_tasks=? WHERE workflow_id=? AND shard_id=?;
UPDATE conductor.workflows SET total_partitions=?,total_tasks=? WHERE workflow_id=? AND shard_id=1;
UPDATE conductor.task_lookup SET workflow_id=? WHERE task_id=?;

DELETE FROM conductor.workflows WHERE workflow_id=? AND shard_id=?;
DELETE FROM conductor.workflows WHERE workflow_id=? AND shard_id=? AND entity='task' AND task_id=?;
DELETE FROM conductor.task_lookup WHERE task_id=?;
