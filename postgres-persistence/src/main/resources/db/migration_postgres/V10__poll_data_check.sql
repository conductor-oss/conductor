CREATE OR REPLACE FUNCTION poll_data_update_check ()
    RETURNS TRIGGER
    AS $$
BEGIN
    IF(NEW.json_data::json ->> 'lastPollTime')::BIGINT < (OLD.json_data::json ->> 'lastPollTime')::BIGINT THEN
        RAISE EXCEPTION 'lastPollTime cannot be set to a lower value';
    END IF;
    RETURN NEW;
END;
$$
LANGUAGE plpgsql;

CREATE TRIGGER poll_data_update_check_trigger BEFORE UPDATE ON poll_data FOR EACH ROW EXECUTE FUNCTION poll_data_update_check ();