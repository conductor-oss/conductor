/**
 * Copyright 2016 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 *
 */
package com.netflix.conductor.tests.integration;

import com.netflix.conductor.tests.utils.TestRunner;
import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;

/**
 * @author Viren
 */
@RunWith(TestRunner.class)
public class WorkflowServiceTest extends BaseWorkflowServiceTest {
    public WorkflowServiceTest() {
        super(LoggerFactory.getLogger(WorkflowServiceTest.class));
    }
}
