package io.orkes.conductor.client.http;

import com.netflix.conductor.client.exception.ConductorClientException;

import java.util.List;
import java.util.Map;

/**
 * This class exists to maintain backward compatibility and facilitate the migration
 * for users of orkes-conductor-client v2 to v3.
 */
@Deprecated
public class ApiException extends ConductorClientException {

    public ApiException() {
    }

    public ApiException(Throwable throwable) {
        super(throwable);
    }

    public ApiException(String message) {
        super(message);
    }
}
