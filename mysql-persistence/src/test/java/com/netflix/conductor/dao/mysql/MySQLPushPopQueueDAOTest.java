package com.netflix.conductor.dao.mysql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("Duplicates")
public class MySQLPushPopQueueDAOTest extends MySQLBaseDAOTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MySQLPushPopQueueDAOTest.class);

    private MySQLQueueDAO dao;

    @Before
    public void setup() throws Exception {
        dao = new MySQLQueueDAO(objectMapper, dataSource);
        resetAllData();
    }

    @Test
    public void testWith2THreads() throws Exception {
        testPollDataWithParallelThreads(2);
    }

    private void testPollDataWithParallelThreads(final int threadCount)
            throws Exception {

        List<String> expectedList = Collections.synchronizedList(new ArrayList<String>(threadCount));

        Callable<List<String>> task = new Callable<List<String>>() {
            @Override
            public List<String> call() throws Exception {
                String messageID1 = UUID.randomUUID().toString();
                String messageID2 = UUID.randomUUID().toString();
                expectedList.add(messageID1);
                expectedList.add(messageID2);
                dao.push("T1", messageID1, 0);
                dao.push("T1", messageID2, 0);
                Thread.sleep(10);
                return dao.pop("T1", 5, 10);
            }
        };
        List<Callable<List<String>>> tasks = Collections.nCopies(threadCount, task);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Future<List<String>>> futures = executorService.invokeAll(tasks);
        List<String> resultList = new ArrayList<String>(futures.size());
        // Check for exceptions
        for (Future<List<String>> future : futures) {
            // Throws an exception if an exception was thrown by the task.
            List<String> list = future.get();
            resultList.addAll(list);
        }
        // Validate the IDs
        Assert.assertEquals(threadCount, futures.size());

        Collections.sort(expectedList);
        Collections.sort(resultList);
        Assert.assertEquals(expectedList, resultList);
    }

}
