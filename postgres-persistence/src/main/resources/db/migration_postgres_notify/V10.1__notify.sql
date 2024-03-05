-- This function notifies on 'conductor_queue_state' with a JSON string containing
-- queue metadata that looks like:
-- {
--   "queue_name_1": {
--     "nextDelivery": 1234567890123,
--     "depth": 10
--   },
--   "queue_name_2": {
--     "nextDelivery": 1234567890456,
--     "depth": 5
--   },
--   "__now__": 1234567890999
-- }
--
CREATE OR REPLACE FUNCTION queue_notify() RETURNS void
LANGUAGE SQL
AS $$
  SELECT pg_notify('conductor_queue_state', (
    SELECT
      COALESCE(jsonb_object_agg(KEY, val), '{}'::jsonb) ||
      jsonb_build_object('__now__', (extract('epoch' from CURRENT_TIMESTAMP)*1000)::bigint)
    FROM (
      SELECT
        queue_name AS KEY,
        jsonb_build_object(
            'nextDelivery',
            (extract('epoch' from min(deliver_on))*1000)::bigint,
            'depth',
            count(*)
        ) AS val
      FROM
        queue_message
      WHERE
        popped = FALSE
      GROUP BY
        queue_name) AS sq)::text);
$$;


CREATE FUNCTION queue_notify_trigger()
  RETURNS TRIGGER 
  LANGUAGE PLPGSQL
AS $$
BEGIN
  PERFORM queue_notify();
  RETURN NULL;
END;
$$;

CREATE TRIGGER queue_update
  AFTER UPDATE ON queue_message
  FOR EACH ROW
  WHEN (OLD.popped IS DISTINCT FROM NEW.popped)
  EXECUTE FUNCTION queue_notify_trigger();

CREATE TRIGGER queue_insert_delete
  AFTER INSERT OR DELETE ON queue_message
  FOR EACH ROW
  EXECUTE FUNCTION queue_notify_trigger();
