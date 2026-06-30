-- Partial index to optimize paginated queries on latest workflow definition versions.
-- Covers the WHERE (version = latest_version) filter and ORDER BY name,
-- eliminating full table scans for the /metadata/workflow/latest-versions endpoint.
CREATE INDEX CONCURRENTLY IF NOT EXISTS workflow_def_latest_version_index
ON meta_workflow_def (name)
WHERE version = latest_version;
