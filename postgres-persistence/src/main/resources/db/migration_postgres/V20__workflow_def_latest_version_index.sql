-- getWorkflowDefListItems() (GET /api/metadata/workflow/list) selects the latest version of every
-- workflow with `WHERE version = latest_version ORDER BY name`. Without support that predicate is a
-- column = column comparison the existing unique_name_version(name, version) and
-- workflow_def_name_index(name) indexes cannot satisfy, forcing a full scan of every version row
-- plus a sort.
--
-- A partial index keyed on name and restricted to the latest-version rows serves both needs in one
-- index-ordered scan: the WHERE predicate matches the partial condition exactly, and the name key
-- provides the ORDER BY ordering, so only latest rows are visited and no explicit sort is needed.
--
-- Plain (non-CONCURRENT) CREATE INDEX is used deliberately: meta_workflow_def holds workflow
-- definitions (thousands of rows), so the build and the brief lock are negligible, and it keeps the
-- migration runnable inside Flyway's transaction (CREATE INDEX CONCURRENTLY cannot run in one).
CREATE INDEX IF NOT EXISTS meta_workflow_def_latest_name_idx
    ON meta_workflow_def (name)
    WHERE version = latest_version;
