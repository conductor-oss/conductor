package org.conductoross.conductor.id;

import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.utils.IDGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.core.util.UuidUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Function;

@Component
@ConditionalOnProperty(name = "conductor.id.generator", havingValue = "time_based")
@Slf4j
public class TimeBasedUUIDGenerator extends IDGenerator {

    private static Calendar uuidEpoch = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    private static final long epochMillis;

    private static final long TIME_DIVISOR = 10000L;

    static {
        uuidEpoch.clear();
        uuidEpoch.set(1582, 9, 15, 0, 0, 0); //
        epochMillis = uuidEpoch.getTime().getTime();
    }

    public TimeBasedUUIDGenerator() {
        log.info("Using TimeBasedUUIDGenerator to generate Ids");
    }

    public String generate() {
        // TODO: OSS-OrkesRequestContext-removed — orgId prefix is multi-tenant
        // context that does not exist in OSS. Returning the bare UUID.
        UUID uuid = UuidUtil.getTimeBasedUuid();
        return uuid.toString();
    }

    /**
     * used for testing only so far
     * @param time
     * @return
     */
    public String generate(long time) {
        // TODO: OSS-OrkesRequestContext-removed — IDUtils.getTimeBasedUuid(time)
        // is an Orkes-internal helper not ported to OSS. Falling back to random UUID.
        UUID uuid = UUID.randomUUID();
        return uuid.toString();
    }

    public static String getOrgId(String idWithOrgMayBe) {
        // TODO: OSS-OrkesRequestContext-removed — no orgId in OSS, always default.
        return "_";
    }

    public static long getDate(String idWithOrgMayBe) {
        return safelyExecute(id -> {
            UUID uuid = UUID.fromString(id);
            if (uuid.version() != 1) {
                return 0L;
            }
            return (uuid.timestamp() / TIME_DIVISOR) + epochMillis;
        }, idWithOrgMayBe);
    }

    private static <T, R> R safelyExecute(Function<T, R> function, T input) {
        try {
            return function.apply(input);
        } catch (Exception e) {
            switch (e.getClass().getSimpleName()) {
                case "IllegalArgumentException":
                    throw new NonTransientException("Invalid UUID Provided %s".formatted(input));
                case "NullPointerException":
                    throw new NonTransientException("Null UUID Provided %s".formatted(input));
                case "UnsupportedOperationException":
                    throw new NonTransientException("Unsupported UUID Version %s".formatted(input));
                default:
                    throw e;
            }
        }
    }


}
