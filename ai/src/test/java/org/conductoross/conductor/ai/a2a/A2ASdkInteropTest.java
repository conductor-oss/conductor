/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.conductoross.conductor.ai.a2a;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.ai.a2a.model.AgentCard;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import com.netflix.conductor.model.TaskModel;

import okhttp3.OkHttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

/**
 * Interop test against a <b>real, non-Conductor A2A agent</b> — the reference {@code a2a-sdk} echo
 * agent ({@code ai/src/test/resources/a2a/echo_agent.py}) launched as a subprocess. This proves our
 * client speaks the real wire protocol (Agent Card discovery, {@code message/send}, {@code
 * tasks/get} poll-to-completion, and SSE streaming) against the protocol's reference implementation
 * — not a hand-rolled fixture.
 *
 * <p><b>Self-skipping.</b> Requires a Python interpreter with {@code a2a-sdk} + {@code uvicorn}
 * importable. Point {@code A2A_PYTHON} at it (e.g. a uv venv), or have {@code python3}/{@code
 * python} on PATH with the packages installed; otherwise the whole class is skipped:
 *
 * <pre>
 *   uv venv --python 3.12 /tmp/a2a-venv
 *   uv pip install --python /tmp/a2a-venv "a2a-sdk&gt;=0.2,&lt;0.3" uvicorn
 *   A2A_PYTHON=/tmp/a2a-venv/bin/python ./gradlew :conductor-ai:test --tests '*A2ASdkInteropTest'
 * </pre>
 */
class A2ASdkInteropTest {

    private static String python;
    private static AgentProcess taskAgent;

    @BeforeAll
    static void startRealAgent() throws Exception {
        python = findPythonWithA2aSdk();
        assumeTrue(
                python != null,
                "No Python with a2a-sdk + uvicorn found (set A2A_PYTHON); skipping real-agent interop");
        taskAgent = AgentProcess.launch(python, "task");
    }

    @AfterAll
    static void stopRealAgent() {
        if (taskAgent != null) {
            taskAgent.close();
        }
    }

    /** Loopback agent → bypass the SSRF guard with the allow-private-network constructor. */
    private static A2AService service() {
        OkHttpClient client =
                new OkHttpClient.Builder()
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .build();
        return new A2AService(client, true);
    }

    @Test
    void discoversRealAgentCard() {
        AgentCard card = service().getAgentCard(taskAgent.url(), null);
        assertEquals("Echo Agent", card.getName());
        assertNotNull(card.getCapabilities());
        assertTrue(card.getCapabilities().isStreaming(), "echo agent advertises streaming");
        assertEquals("echo", card.getSkills().get(0).getId());
    }

    @Test
    void callAgentTask_drivesRealAgentToCompletion() {
        TaskModel task = callAgentTask(taskAgent.url(), "hello world", false);
        new CallAgentTask(service(), mock(Environment.class)).start(null, task, null);

        int guard = 0;
        CallAgentTask client = new CallAgentTask(service(), mock(Environment.class));
        while (task.getStatus() == TaskModel.Status.IN_PROGRESS && guard++ < 60) {
            client.execute(null, task, null);
        }

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForIncompletion());
        assertEquals("completed", task.getOutputData().get("state"));
        assertTrue(
                String.valueOf(task.getOutputData().get("text")).contains("echo-task: hello world"),
                "agent echoed our text back: " + task.getOutputData().get("text"));
    }

    @Test
    void streamingCall_aggregatesRealSseToCompletion() {
        TaskModel task = callAgentTask(taskAgent.url(), "stream me", true);
        new CallAgentTask(service(), mock(Environment.class)).start(null, task, null);

        // Streaming aggregates to a terminal state in start(); no poll loop needed.
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForIncompletion());
        assertTrue(
                String.valueOf(task.getOutputData().get("text")).contains("echo-task: stream me"),
                "streamed artifact text: " + task.getOutputData().get("text"));
    }

    @Test
    void messageModeAgent_returnsDirectMessage() throws Exception {
        // A second real agent, this one configured to reply with a direct Message (not a Task).
        try (AgentProcess messageAgent = AgentProcess.launch(python, "message")) {
            TaskModel task = callAgentTask(messageAgent.url(), "ping", false);
            new CallAgentTask(service(), mock(Environment.class)).start(null, task, null);

            assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
            assertTrue(
                    String.valueOf(task.getOutputData().get("text")).contains("echo: ping"),
                    "direct message reply: " + task.getOutputData().get("text"));
        }
    }

    // ---- helpers -----------------------------------------------------------------------------

    private TaskModel callAgentTask(String agentUrl, String text, boolean streaming) {
        TaskModel task = new TaskModel();
        task.setTaskId("interop-" + System.nanoTime());
        task.setWorkflowInstanceId("interop-wf");
        task.setReferenceTaskName("callEcho");
        Map<String, Object> input = new HashMap<>();
        input.put("agentUrl", agentUrl);
        input.put("text", text);
        input.put("pollIntervalSeconds", 1);
        if (streaming) {
            input.put("streaming", true);
        }
        task.setInputData(input);
        return task;
    }

    private static String findPythonWithA2aSdk() {
        List<String> candidates = new ArrayList<>();
        String env = System.getenv("A2A_PYTHON");
        if (env != null && !env.isBlank()) {
            candidates.add(env);
        }
        candidates.add("python3");
        candidates.add("python");
        for (String candidate : candidates) {
            try {
                Process p =
                        new ProcessBuilder(candidate, "-c", "import a2a, uvicorn")
                                .redirectErrorStream(true)
                                .start();
                if (p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return candidate;
                }
                p.destroyForcibly();
            } catch (Exception ignored) {
                // try the next candidate
            }
        }
        return null;
    }

    /** A running {@code echo_agent.py} subprocess on a free loopback port. */
    private static final class AgentProcess implements AutoCloseable {
        private final Process process;
        private final int port;
        private final Path logFile;

        private AgentProcess(Process process, int port, Path logFile) {
            this.process = process;
            this.port = port;
            this.logFile = logFile;
        }

        String url() {
            return "http://localhost:" + port;
        }

        static AgentProcess launch(String python, String mode) throws Exception {
            int port = freePort();
            Path agent =
                    Paths.get(
                            System.getProperty("user.dir"), "src/test/resources/a2a/echo_agent.py");
            assertTrue(Files.exists(agent), "echo_agent.py fixture must exist at " + agent);
            Path log = Files.createTempFile("a2a-echo-" + mode + "-", ".log");

            ProcessBuilder pb = new ProcessBuilder(python, agent.toString());
            pb.environment().put("A2A_AGENT_PORT", String.valueOf(port));
            pb.environment().put("AGENT_MODE", mode);
            pb.redirectErrorStream(true);
            pb.redirectOutput(log.toFile());
            Process process = pb.start();

            AgentProcess running = new AgentProcess(process, port, log);
            running.waitUntilReady();
            return running;
        }

        private void waitUntilReady() throws Exception {
            A2AService probe = service();
            String url = url();
            for (int i = 0; i < 60; i++) {
                if (!process.isAlive()) {
                    throw new IllegalStateException(
                            "echo agent exited early (code "
                                    + process.exitValue()
                                    + "):\n"
                                    + readLog());
                }
                try {
                    if (probe.getAgentCard(url, null) != null) {
                        return; // card served → ready
                    }
                } catch (Exception notReadyYet) {
                    // server still starting
                }
                Thread.sleep(500);
            }
            close();
            throw new IllegalStateException("echo agent did not become ready:\n" + readLog());
        }

        private String readLog() {
            try {
                return Files.readString(logFile);
            } catch (IOException e) {
                return "(no log: " + e.getMessage() + ")";
            }
        }

        @Override
        public void close() {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
