package com.netflix.conductor.tests.integration;

import com.netflix.conductor.tests.utils.MySQLTestRunner;
import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;

@RunWith(MySQLTestRunner.class)
public class MySQLWorkflowServiceTest extends BaseWorkflowServiceTest {
    public MySQLWorkflowServiceTest() {
        super(LoggerFactory.getLogger(MySQLWorkflowServiceTest.class));
    }
}
