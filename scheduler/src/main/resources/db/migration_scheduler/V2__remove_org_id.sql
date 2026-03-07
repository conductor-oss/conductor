-- Remove org_id from scheduler tables.
--
-- The org_id concept is Orkes-specific (multi-tenancy). OSS uses a simpler
-- single-tenant schema. Orkes injects org_id within their DAO implementation.

-- Drop existing primary key constraints (they include org_id)
ALTER TABLE workflow_schedule DROP CONSTRAINT workflow_schedule_pkey;
ALTER TABLE workflow_schedule_execution DROP CONSTRAINT workflow_schedule_execution_pkey;

-- Drop org_id columns
ALTER TABLE workflow_schedule DROP COLUMN org_id;
ALTER TABLE workflow_schedule_execution DROP COLUMN org_id;

-- Recreate primary keys without org_id
ALTER TABLE workflow_schedule ADD PRIMARY KEY (schedule_name);
ALTER TABLE workflow_schedule_execution ADD PRIMARY KEY (execution_id);
