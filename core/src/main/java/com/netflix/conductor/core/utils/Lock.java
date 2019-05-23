package com.netflix.conductor.core.utils;

import java.util.concurrent.TimeUnit;

/**
 * Interface implemented by a distributed lock client.
 *
 * A typical usage:
 *   if (acquireLock(workflowId, 5, TimeUnit.MILLISECONDS)) {
 *      [load and execute workflow....]
 *      ExecutionDAO.updateWorkflow(workflow);  //use optimistic locking
 *   } finally {
 *     releaseLock(workflowId)
 *   }
 *
 */

public interface Lock {

    /**
     * acquires a re-entrant lock on lockId, blocks indefinitely on lockId until it succeeds
     * @param lockId resource to lock on
     * @return
     */
    void acquireLock(String lockId);

    /**
     * acquires a re-entrant lock on lockId, blocks for timeToTry duration before giving up
     * @param lockId resource to lock on
     * @param timeToTry blocks up to timeToTry duration in attempt to acquire the lock
     * @param unit time unit
     * @return
     */
    Boolean acquireLock(String lockId, long timeToTry, TimeUnit unit);

    void releaseLock(String lockId);

    /**
     *
     * @param lockId
     * @return true if current Thread holds the lock for resource lockId
     */
    Boolean hasLock(String lockId);

}
