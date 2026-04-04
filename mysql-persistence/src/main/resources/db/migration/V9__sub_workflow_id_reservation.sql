CREATE TABLE sub_workflow_id_reservation (
  parent_workflow_id varchar(255) NOT NULL,
  parent_workflow_task_id varchar(255) NOT NULL,
  sub_workflow_id varchar(255) NOT NULL,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (parent_workflow_id, parent_workflow_task_id),
  UNIQUE KEY unique_sub_workflow_id_reservation_sub_workflow_id (sub_workflow_id)
);
