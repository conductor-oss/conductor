-- A pop now moves a message's deliver_on to the end of its lease, and a pop takes any message whose
-- deliver_on has passed, popped or not. The listener lets a pop through only when a queue's
-- nextDelivery has passed, so nextDelivery must include popped messages too: then a pop goes
-- through once a lease runs out, or once an unack timeout set on a popped message runs out.
-- depth still counts only the messages not popped.
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
            count(*) FILTER (WHERE popped = FALSE)
        ) AS val
      FROM
        queue_message
      GROUP BY
        queue_name) AS sq)::text);
$$;

-- An unack timeout or a postpone changes deliver_on without changing popped; notify on both.
DROP TRIGGER IF EXISTS queue_update ON queue_message;

CREATE TRIGGER queue_update
  AFTER UPDATE ON queue_message
  FOR EACH ROW
  WHEN (OLD.popped IS DISTINCT FROM NEW.popped OR OLD.deliver_on IS DISTINCT FROM NEW.deliver_on)
  EXECUTE FUNCTION queue_notify_trigger();
