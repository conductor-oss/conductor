package com.netflix.conductor.dao.dynomite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.dao.RateLimitingDao;
import com.netflix.conductor.dyno.DynoProxy;
import com.netflix.conductor.metrics.Monitors;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
@Trace
public class RedisRateLimitingDao extends BaseDynoDAO implements RateLimitingDao {

    private static final Logger logger = LoggerFactory.getLogger(RedisRateLimitingDao.class);

    private static final String TASK_RATE_LIMIT_BUCKET = "TASK_RATE_LIMIT_BUCKET";

    @Inject
    protected RedisRateLimitingDao(DynoProxy dynoClient, ObjectMapper objectMapper, Configuration config) {
        super(dynoClient, objectMapper, config);
    }

    /**
     * This method evaluates if the {@link TaskDef} is rate limited or not based on {@link Task#getRateLimitPerFrequency()}
     * and {@link Task#getRateLimitFrequencyInSeconds()} if not checks the {@link Task} is rate limited or not based on {@link Task#getRateLimitPerFrequency()}
     * and {@link Task#getRateLimitFrequencyInSeconds()}
     *
     * The rate limiting is implemented using the Redis constructs of sorted set and TTL of each element in the rate limited bucket.
     * <ul>
     *     <li>All the entries that are in the not in the frequency bucket are cleaned up by leveraging {@link DynoProxy#zremrangeByScore(String, String, String)},
     *     this is done to make the next step of evaluation efficient</li>
     *     <li>A current count(tasks executed within the frequency) is calculated based on the current time and the beginning of the rate limit frequency time(which is current time - {@link Task#getRateLimitFrequencyInSeconds()} in millis),
     *     this is achieved by using {@link DynoProxy#zcount(String, double, double)} </li>
     *     <li>Once the count is calculated then a evaluation is made to determine if it is within the bounds of {@link Task#getRateLimitPerFrequency()}, if so the count is increased and an expiry TTL is added to the entry</li>
     * </ul>
     *
     * @param task: which needs to be evaluated whether it is rateLimited or not
     * @return true: If the {@link Task} is rateLimited
     * 		false: If the {@link Task} is not rateLimited
     */
    @Override
    public boolean exceedsRateLimitPerFrequency(Task task, TaskDef taskDef) {
        //Check if the TaskDefinition is not null then pick the definition values or else pick from the Task
        ImmutablePair<Integer, Integer> rateLimitPair = Optional.ofNullable(taskDef)
                .map(definition -> new ImmutablePair<>(definition.getRateLimitPerFrequency(), definition.getRateLimitFrequencyInSeconds()))
                .orElse(new ImmutablePair<>(task.getRateLimitPerFrequency(), task.getRateLimitFrequencyInSeconds()));

        int rateLimitPerFrequency = rateLimitPair.getLeft();
        int rateLimitFrequencyInSeconds = rateLimitPair.getRight();
        if (rateLimitPerFrequency <= 0 || rateLimitFrequencyInSeconds <=0) {
            logger.debug("Rate limit not applied to the Task: {}  either rateLimitPerFrequency: {} or rateLimitFrequencyInSeconds: {} is 0 or less",
                    task, rateLimitPerFrequency, rateLimitFrequencyInSeconds);
            return false;
        } else {
            logger.debug("Evaluating rate limiting for TaskId: {} with TaskDefinition of: {} with rateLimitPerFrequency: {} and rateLimitFrequencyInSeconds: {}",
                    task.getTaskId(), task.getTaskDefName(),rateLimitPerFrequency, rateLimitFrequencyInSeconds);
            long currentTimeEpochMillis = System.currentTimeMillis();
            long currentTimeEpochMinusRateLimitBucket = currentTimeEpochMillis - (rateLimitFrequencyInSeconds * 1000);
            String key = nsKey(TASK_RATE_LIMIT_BUCKET, task.getTaskDefName());
            dynoClient.zremrangeByScore(key, "-inf", String.valueOf(currentTimeEpochMinusRateLimitBucket));
            int currentBucketCount = Math.toIntExact(
                    dynoClient.zcount(key,
                            currentTimeEpochMinusRateLimitBucket,
                            currentTimeEpochMillis));
            if (currentBucketCount < rateLimitPerFrequency) {
                dynoClient.zadd(key, currentTimeEpochMillis, String.valueOf(currentTimeEpochMillis));
                dynoClient.expire(key, rateLimitFrequencyInSeconds);
                logger.info("TaskId: {} with TaskDefinition of: {} has rateLimitPerFrequency: {} and rateLimitFrequencyInSeconds: {} within the rate limit with current count {}",
                        task.getTaskId(), task.getTaskDefName(), rateLimitPerFrequency, rateLimitFrequencyInSeconds, ++currentBucketCount);
                Monitors.recordTaskRateLimited(task.getTaskDefName(), rateLimitPerFrequency);
                return false;
            } else {
                logger.info("TaskId: {} with TaskDefinition of: {} has rateLimitPerFrequency: {} and rateLimitFrequencyInSeconds: {} is out of bounds of rate limit with current count {}",
                        task.getTaskId(), task.getTaskDefName(), rateLimitPerFrequency, rateLimitFrequencyInSeconds, currentBucketCount);
                return true;
            }
        }
    }
}
