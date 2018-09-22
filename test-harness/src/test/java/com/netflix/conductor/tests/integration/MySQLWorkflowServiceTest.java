
package com.netflix.conductor.tests.integration;

import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.tests.utils.MySQLTestRunner;

@RunWith(MySQLTestRunner.class)
public class MySQLWorkflowServiceTest extends BaseWorkflowServiceTest {
    public MySQLWorkflowServiceTest() {
        super(LoggerFactory.getLogger(MySQLWorkflowServiceTest.class));

    }
}
