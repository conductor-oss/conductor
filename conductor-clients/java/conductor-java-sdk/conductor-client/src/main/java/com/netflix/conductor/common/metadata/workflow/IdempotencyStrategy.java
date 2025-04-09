/*\n * Copyright 2020 Conductor Authors.\n * <p>\n * Licensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with\n * the License. You may obtain a copy of the License at\n * <p>\n * http://www.apache.org/licenses/LICENSE-2.0\n * <p>\n * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on\n * an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the\n * specific language governing permissions and limitations under the License.\n */
package com.netflix.conductor.common.metadata.workflow;

public enum IdempotencyStrategy {

    FAIL, RETURN_EXISTING, FAIL_ON_RUNNING
}