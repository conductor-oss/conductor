-- Schema registry storage.
--
-- This location has its own version sequence and its own Flyway history table
-- (flyway_schema_history_schema_registry) so the registry's migrations cannot contend with
-- the main Conductor migration numbering.
--
-- Modelled on meta_workflow_def: a name, a version, and the definition as JSON. The table name
-- deliberately differs from the commercial Orkes registry's, so both products can share one
-- database without colliding on a fresh install.

-- created_on and modified_on are for operators reading the table directly. The timestamps
-- callers see come from the JSON payload, which is what the API returns.
CREATE TABLE IF NOT EXISTS meta_schema_def (
    created_on DATETIME DEFAULT CURRENT_TIMESTAMP,
    modified_on DATETIME DEFAULT CURRENT_TIMESTAMP,
    name TEXT NOT NULL,
    version INTEGER NOT NULL,
    json_data TEXT NOT NULL,
    PRIMARY KEY (name, version)
);
