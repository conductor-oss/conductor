DROP INDEX IF EXISTS unique_event_execution;

CREATE UNIQUE INDEX unique_event_execution ON event_execution (event_handler_name,event_name,execution_id);