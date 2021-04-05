package com.netflix.conductor.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.execution.TestConfiguration;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.dao.postgres.PostgresExecutionDAO;
import com.netflix.conductor.dao.postgres.PostgresQueueDAO;
import com.netflix.conductor.postgres.PostgresConfiguration;
import com.netflix.conductor.postgres.PostgresDataSourceProvider;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore("This test cannot be automated")
public class PerformanceTest {

    public static final int MSGS = 1000;
    public static final int PRODUCER_BATCH = 10; // make sure MSGS % PRODUCER_BATCH == 0
    public static final int PRODUCERS = 4;
    public static final int WORKERS = 8;
    public static final int OBSERVERS = 4;
    public static final int OBSERVER_DELAY = 5000;
    public static final int UNACK_RUNNERS = 10;
    public static final int UNACK_DELAY = 500;
    public static final int WORKER_BATCH = 10;
    public static final int WORKER_BATCH_TIMEOUT = 500;
    public static final int COMPLETION_MONITOR_DELAY = 1000;

    private PostgresConfiguration configuration;
    private DataSource dataSource;
    private QueueDAO Q;
    private ExecutionDAO E;

    private ExecutorService THREADPOOL = Executors.newFixedThreadPool(PRODUCERS + WORKERS + OBSERVERS + UNACK_RUNNERS);
    private static final Logger logger = LoggerFactory.getLogger(PerformanceTest.class);

    @Before
    public void setUp() {
        TestConfiguration testConfiguration = new TestConfiguration();
        configuration = new TestPostgresConfiguration(testConfiguration,
                "jdbc:postgresql://localhost:54320/conductor?charset=utf8&parseTime=true&interpolateParams=true",
                10, 2);
        PostgresDataSourceProvider dataSource = new PostgresDataSourceProvider(configuration);
        this.dataSource = dataSource.get();
        resetAllData(this.dataSource);
        flywayMigrate(this.dataSource);

        final ObjectMapper objectMapper = new JsonMapperProvider().get();
        Q = new PostgresQueueDAO(objectMapper, this.dataSource);
        E = new PostgresExecutionDAO(objectMapper, this.dataSource);
    }

    @After
    public void tearDown() throws Exception {
        resetAllData(dataSource);
    }

    public static final String QUEUE = "task_queue";

    @Test
    public void testQueueDaoPerformance() throws InterruptedException {
        AtomicBoolean stop = new AtomicBoolean(false);
        Stopwatch start = Stopwatch.createStarted();
        AtomicInteger poppedCoutner = new AtomicInteger(0);
        HashMultiset<String> allPopped = HashMultiset.create();

        // Consumers - workers
        for (int i = 0; i < WORKERS; i++) {
            THREADPOOL.submit(() -> {
                while (!stop.get()) {
                    List<Message> pop = Q.pollMessages(QUEUE, WORKER_BATCH, WORKER_BATCH_TIMEOUT);
                    logger.info("Popped {} messages", pop.size());
                    poppedCoutner.accumulateAndGet(pop.size(), Integer::sum);

                    if (pop.size() == 0) {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        logger.info("Popped {}", pop.stream().map(Message::getId).collect(Collectors.toList()));
                    }

                    pop.forEach(popped -> {
                        synchronized (allPopped) {
                            allPopped.add(popped.getId());
                        }
                        boolean exists = Q.containsMessage(QUEUE, popped.getId());
                        boolean ack = Q.ack(QUEUE, popped.getId());

                        if (ack && exists) {
                            // OK
                        } else {
                            logger.error("Exists & Ack did not succeed for msg: {}", popped);
                        }
                    });
                }
            });
        }

        // Producers
        List<Future<?>> producers = Lists.newArrayList();
        for (int i = 0; i < PRODUCERS; i++) {
            Future<?> producer = THREADPOOL.submit(() -> {
                try {
                    // N messages
                    for (int j = 0; j < MSGS / PRODUCER_BATCH; j++) {
                        List<Message> randomMessages = getRandomMessages(PRODUCER_BATCH);
                        Q.push(QUEUE, randomMessages);
                        logger.info("Pushed {} messages", PRODUCER_BATCH);
                        logger.info("Pushed {}", randomMessages.stream().map(Message::getId).collect(Collectors.toList()));
                    }
                    logger.info("Pushed ALL");
                } catch (Exception e) {
                    logger.error("Something went wrong with producer", e);
                    throw new RuntimeException(e);
                }
            });

            producers.add(producer);
        }

        // Observers
        for (int i = 0; i < OBSERVERS; i++) {
            THREADPOOL.submit(() -> {
                while (!stop.get()) {
                    try {
                        int size = Q.getSize(QUEUE);
                        Q.queuesDetail();
                        logger.info("Size   {} messages", size);
                    } catch (Exception e) {
                        logger.info("Queue size failed, nevermind");
                    }

                    try {
                        Thread.sleep(OBSERVER_DELAY);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        // Consumers - unack processor
        for (int i = 0; i < UNACK_RUNNERS; i++) {
            THREADPOOL.submit(() -> {
                while (!stop.get()) {
                    try {
                        Q.processUnacks(QUEUE);
                    } catch (Exception e) {
                        logger.info("Unack failed, nevermind", e);
                        continue;
                    }
                    logger.info("Unacked");
                    try {
                        Thread.sleep(UNACK_DELAY);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        long elapsed;
        while (true) {
            try {
                Thread.sleep(COMPLETION_MONITOR_DELAY);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            int size = Q.getSize(QUEUE);
            logger.info("MONITOR SIZE : {}", size);

            if (size == 0 && producers.stream().map(Future::isDone).reduce(true, (b1, b2) -> b1 && b2)) {
                elapsed = start.elapsed(TimeUnit.MILLISECONDS);
                stop.set(true);
                break;
            }
        }

        THREADPOOL.awaitTermination(10, TimeUnit.SECONDS);
        THREADPOOL.shutdown();
        logger.info("Finished in {} ms", elapsed);
        logger.info("Throughput {} msgs/second", ((MSGS * PRODUCERS) / (elapsed * 1.0)) * 1000);
        logger.info("Threads finished");
        if (poppedCoutner.get() != MSGS * PRODUCERS) {
            synchronized (allPopped) {
                List<String> duplicates = allPopped.entrySet().stream()
                        .filter(stringEntry -> stringEntry.getCount() > 1)
                        .map(stringEntry -> stringEntry.getElement() + ": " + stringEntry.getCount())
                        .collect(Collectors.toList());

                logger.error("Found duplicate pops: " + duplicates);
            }
            throw new RuntimeException("Popped " + poppedCoutner.get() + " != produced: " + MSGS * PRODUCERS);
        }
    }

    @Test
    public void testExecDaoPerformance() throws InterruptedException {
        AtomicBoolean stop = new AtomicBoolean(false);
        Stopwatch start = Stopwatch.createStarted();
        BlockingDeque<Task> msgQueue = new LinkedBlockingDeque<>(1000);
        HashMultiset<String> allPopped = HashMultiset.create();

        // Consumers - workers
        for (int i = 0; i < WORKERS; i++) {
            THREADPOOL.submit(() -> {
                while (!stop.get()) {
                    List<Task> popped = new ArrayList<>();
                    while (true) {
                        try {
                            Task poll;
                            poll = msgQueue.poll(10, TimeUnit.MILLISECONDS);

                            if (poll == null) {
                                // poll timed out
                                continue;
                            }
                            synchronized (allPopped) {
                                allPopped.add(poll.getTaskId());
                            }
                            popped.add(poll);
                            if (stop.get() || popped.size() == WORKER_BATCH) {
                                break;
                            }
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    logger.info("Popped {} messages", popped.size());
                    logger.info("Popped {}", popped.stream().map(Task::getTaskId).collect(Collectors.toList()));

                    // Polling
                    popped.stream()
                            .peek(task -> {
                                task.setWorkerId("someWorker");
                                task.setPollCount(task.getPollCount() + 1);
                                task.setStartTime(System.currentTimeMillis());
                            })
                            .forEach(task -> {
                                try {
                                    // should always be false
                                    boolean concurrentLimit = E.exceedsInProgressLimit(task);
                                    task.setStartTime(System.currentTimeMillis());
                                    E.updateTask(task);
                                    logger.info("Polled {}", task.getTaskId());
                                } catch (Exception e) {
                                    logger.error("Something went wrong with worker during poll", e);
                                    throw new RuntimeException(e);
                                }
                            });

                    popped.forEach(task -> {
                        try {

                            String wfId = task.getWorkflowInstanceId();
                            Workflow workflow = E.getWorkflow(wfId, true);
                            E.getTask(task.getTaskId());

                            task.setStatus(Task.Status.COMPLETED);
                            task.setWorkerId("someWorker");
                            task.setOutputData(Collections.singletonMap("a", "b"));
                            E.updateTask(task);
                            E.updateWorkflow(workflow);
                            logger.info("Updated {}", task.getTaskId());
                        } catch (Exception e) {
                            logger.error("Something went wrong with worker during update", e);
                            throw new RuntimeException(e);
                        }
                    });

                }
            });
        }

        Multiset<String> pushedTasks = HashMultiset.create();

        // Producers
        List<Future<?>> producers = Lists.newArrayList();
        for (int i = 0; i < PRODUCERS; i++) {
            Future<?> producer = THREADPOOL.submit(() -> {
                // N messages
                for (int j = 0; j < MSGS / PRODUCER_BATCH; j++) {
                    List<Task> randomTasks = getRandomTasks(PRODUCER_BATCH);

                    Workflow wf = getWorkflow(randomTasks);
                    E.createWorkflow(wf);

                    E.createTasks(randomTasks);
                    randomTasks.forEach(t -> {
                        try {
                            boolean offer = false;
                            while (!offer) {
                                offer = msgQueue.offer(t, 10, TimeUnit.MILLISECONDS);
                            }
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    logger.info("Pushed {} messages", PRODUCER_BATCH);
                    List<String> collect = randomTasks.stream().map(Task::getTaskId).collect(Collectors.toList());
                    synchronized (pushedTasks) {
                        pushedTasks.addAll(collect);
                    }
                    logger.info("Pushed {}", collect);
                }
                logger.info("Pushed ALL");
            });

            producers.add(producer);
        }

        // Observers
        for (int i = 0; i < OBSERVERS; i++) {
            THREADPOOL.submit(() -> {
                while (!stop.get()) {
                    try {
                        List<Task> size = E.getPendingTasksForTaskType("taskType");
                        logger.info("Size   {} messages", size.size());
                        logger.info("Size q {} messages", msgQueue.size());
                        synchronized (allPopped) {
                            logger.info("All pp {} messages", allPopped.size());
                        }
                        logger.info("Workflows by correlation id size: {}", E.getWorkflowsByCorrelationId("abcd", "1", true).size());
                        logger.info("Workflows by correlation id size: {}", E.getWorkflowsByCorrelationId("abcd", "2", true).size());
                        logger.info("Workflows running ids: {}", E.getRunningWorkflowIds("abcd", 1));
                        logger.info("Workflows pending count: {}", E.getPendingWorkflowCount("abcd"));
                    } catch (Exception e) {
                        logger.warn("Observer failed ", e);
                    }
                    try {
                        Thread.sleep(OBSERVER_DELAY);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        long elapsed;
        while (true) {
            try {
                Thread.sleep(COMPLETION_MONITOR_DELAY);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            int size;
            try {
                size = E.getPendingTasksForTaskType("taskType").size();
            } catch (Exception e) {
                logger.warn("Monitor failed", e);
                continue;
            }
            logger.info("MONITOR SIZE : {}", size);

            if (size == 0 && producers.stream().map(Future::isDone).reduce(true, (b1, b2) -> b1 && b2)) {
                elapsed = start.elapsed(TimeUnit.MILLISECONDS);
                stop.set(true);
                break;
            }
        }

        THREADPOOL.awaitTermination(10, TimeUnit.SECONDS);
        THREADPOOL.shutdown();
        logger.info("Finished in {} ms", elapsed);
        logger.info("Throughput {} msgs/second", ((MSGS * PRODUCERS) / (elapsed * 1.0)) * 1000);
        logger.info("Threads finished");

        List<String> duplicates = pushedTasks.entrySet().stream()
                .filter(stringEntry -> stringEntry.getCount() > 1)
                .map(stringEntry -> stringEntry.getElement() + ": " + stringEntry.getCount())
                .collect(Collectors.toList());

        logger.error("Found duplicate pushes: " + duplicates);
    }

    private Workflow getWorkflow(List<Task> randomTasks) {
        Workflow wf = new Workflow();
        wf.setWorkflowId(randomTasks.get(0).getWorkflowInstanceId());
        wf.setCorrelationId(wf.getWorkflowId());
        wf.setTasks(randomTasks);
        WorkflowDef workflowDefinition = new WorkflowDef();
        workflowDefinition.setName("abcd");
        wf.setWorkflowDefinition(workflowDefinition);
        wf.setStartTime(System.currentTimeMillis());
        return wf;
    }

    private List<Task> getRandomTasks(int i) {
        String timestamp = Long.toString(System.nanoTime());
        return IntStream.range(0, i).mapToObj(j -> {
            String id = Thread.currentThread().getId() + "_" + timestamp + "_" + j;
            Task task = new Task();
            task.setTaskId(id);
            task.setCorrelationId(Integer.toString(j));
            task.setTaskType("taskType");
            task.setReferenceTaskName("refName" + j);
            task.setWorkflowType("task_wf");
            task.setWorkflowInstanceId(Thread.currentThread().getId() + "_" + timestamp);
            return task;
        }).collect(Collectors.toList());
    }

    private List<Message> getRandomMessages(int i) {
        String timestamp = Long.toString(System.nanoTime());
        return IntStream.range(0, i).mapToObj(j -> {
            String id = Thread.currentThread().getId() + "_" + timestamp + "_" + j;
            return new Message(id, "{ \"a\": \"b\", \"timestamp\": \" " + timestamp + " \"}", "receipt");
        }).collect(Collectors.toList());
    }

    private void flywayMigrate(DataSource dataSource) {

        FluentConfiguration flywayConfiguration = Flyway.configure()
                .table(configuration.getFlywayTable())
                .locations(Paths.get("db","migration_postgres").toString())
                .dataSource(dataSource)
                .placeholderReplacement(false);

        Flyway flyway = flywayConfiguration.load();

        try {
            flyway.migrate();
        } catch (FlywayException e) {
            if (e.getMessage().contains("non-empty")) {
                return;
            }
            throw e;
        }
    }

    public void resetAllData(DataSource dataSource) {
        // TODO
    }

}
