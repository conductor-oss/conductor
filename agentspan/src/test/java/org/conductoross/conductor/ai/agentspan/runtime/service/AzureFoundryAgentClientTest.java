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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.agent.ConductorAgentCancelRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentRespondRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartResponse;
import org.conductoross.conductor.ai.agent.ConductorAgentState;
import org.conductoross.conductor.ai.agent.ConductorAgentStatusResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.azure.identity.CredentialUnavailableException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives the client against a real HTTP server standing in for the Foundry API, with the Entra ID
 * token endpoint short-circuited by an interceptor so token fetches and secret-store reads can both
 * be counted.
 *
 * <p>The property under test is that the client keeps nothing: every call rebuilds where the run
 * lives from the task input plus the thread id, so a second client instance sharing no state can
 * serve a poll, a respond, or a cancel for a run the first one started. On top of that, the token
 * provider is cached per credential, because rebuilding one per call threw away the token cache
 * inside it.
 */
class AzureFoundryAgentClientTest {

    private static final String CREDENTIAL_REF = "AZURE_FOUNDRY_CRED";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private MockWebServer foundry;
    private Map<String, String> credentials;
    private MutableClock clock;
    private AzureFoundryAgentClient client;

    /** Status of the newest run on the thread; tests mutate it to drive the state machine. */
    private final AtomicReference<String> runStatus = new AtomicReference<>("in_progress");

    /** Id of the newest run on the thread — respond() starting a new run bumps this. */
    private final AtomicReference<String> latestRunId = new AtomicReference<>("run-1");

    private final AtomicBoolean rejectAuth = new AtomicBoolean(false);

    /**
     * How many tool calls a requires_action run reports. Models fan out when tools are independent.
     */
    private final AtomicInteger toolCallCount = new AtomicInteger(1);

    /** Assistants with code interpreter put an image part ahead of the text part. */
    private final AtomicBoolean assistantReturnsImageFirst = new AtomicBoolean(false);

    private final List<String> requestLog = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        foundry = new MockWebServer();
        foundry.setDispatcher(new FoundryDispatcher());
        foundry.start();

        // Conductor substitutes ${workflow.secrets.*} before the task runs, so a client is handed
        // values and never touches a secret store.
        credentials = Map.of("apiKey", "azure-api-key");

        clock = new MutableClock();
        client = newClient(clock);
    }

    @AfterEach
    void tearDown() throws Exception {
        foundry.shutdown();
    }

    private AzureFoundryAgentClient newClient(Clock clientClock) {
        OkHttpClient httpClient =
                new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build();
        return new AzureFoundryAgentClient(httpClient, clientClock);
    }

    // --- statelessness ---------------------------------------------------------------------

    @Test
    void executionIdIsTheAzureThreadId() {
        assertThat(start().getExecutionId()).isEqualTo("thread-1");
    }

    @Test
    void aSecondClientInstanceCanPollARunItDidNotStart() {
        String executionId = start().getExecutionId();
        runStatus.set("completed");

        // Stands in for a status callback routed to a different replica: a brand-new client with an
        // empty heap, holding nothing about this run.
        AzureFoundryAgentClient otherReplica = newClient(new MutableClock());
        ConductorAgentStatusResponse response =
                otherReplica.getAgentStatus(executionId, statusRequest());

        assertThat(response.getStatus()).isEqualTo(ConductorAgentState.COMPLETED);
        assertThat(response.getOutput()).isEqualTo(Map.of("result", "the answer"));
    }

    @Test
    void aSecondClientInstanceCanRespondAndCancel() {
        String executionId = start().getExecutionId();
        AzureFoundryAgentClient otherReplica = newClient(new MutableClock());

        runStatus.set("requires_action");
        otherReplica.respond(
                ConductorAgentRespondRequest.builder()
                        .executionId(executionId)
                        .body(Map.of("result", "tool output"))
                        .credentials(credentials)
                        .rawConfig(rawConfig())
                        .build());
        assertThat(requestLog).anyMatch(r -> r.contains("/submit_tool_outputs"));

        otherReplica.cancelAgent(
                ConductorAgentCancelRequest.builder()
                        .executionId(executionId)
                        .reason("cancelled by parent")
                        .credentials(credentials)
                        .rawConfig(rawConfig())
                        .build());
        assertThat(requestLog).anyMatch(r -> r.contains("/cancel"));
    }

    @Test
    void terminalRunStaysPollableIndefinitely() {
        String executionId = start().getExecutionId();
        runStatus.set("completed");

        ConductorAgentStatusResponse first = client.getAgentStatus(executionId, statusRequest());
        clock.advance(Duration.ofDays(3));
        ConductorAgentStatusResponse later = client.getAgentStatus(executionId, statusRequest());

        // Azure is the retention: nothing on our side expires, so a poll that repeats — after a
        // failed task update, or days later — gets the same terminal answer.
        assertThat(first.getStatus()).isEqualTo(ConductorAgentState.COMPLETED);
        assertThat(later.getStatus()).isEqualTo(ConductorAgentState.COMPLETED);
        assertThat(later.getOutput()).isEqualTo(first.getOutput());
        assertThat(later.isComplete()).isTrue();
    }

    @Test
    void aStatusPollCostsOneCallToLocateAndReadTheRun() {
        String executionId = start().getExecutionId();
        requestLog.clear();

        client.getAgentStatus(executionId, statusRequest());

        // Listing the newest run returns its full status, so resolving which run is current is not
        // an extra round trip over having remembered its id.
        assertThat(requestLog).hasSize(1);
        assertThat(requestLog.get(0)).contains("order=desc");
    }

    @Test
    void respondStartsANewRunReachableUnderTheSameExecutionId() {
        String executionId = start().getExecutionId();
        runStatus.set("completed");

        client.respond(
                ConductorAgentRespondRequest.builder()
                        .executionId(executionId)
                        .body(Map.of("result", "and now a follow-up question"))
                        .credentials(credentials)
                        .rawConfig(rawConfig())
                        .build());

        // A new run on the same thread. The caller's executionId is untouched, and the next poll
        // resolves the newer run rather than the finished one.
        assertThat(latestRunId.get()).isEqualTo("run-2");
        runStatus.set("in_progress");
        assertThat(client.getAgentStatus(executionId, statusRequest()).getStatus())
                .isEqualTo(ConductorAgentState.RUNNING);
    }

    @Test
    void aResumedPromptIsSentAsTextNotAJavaMapToString() {
        String executionId = start().getExecutionId();
        runStatus.set("completed");
        requestLog.clear();

        client.respond(
                ConductorAgentRespondRequest.builder()
                        .executionId(executionId)
                        .body(Map.of("result", "and now a follow-up question"))
                        .credentials(credentials)
                        .rawConfig(rawConfig())
                        .build());

        assertThat(bodyOf("/messages")).contains("and now a follow-up question");
        assertThat(bodyOf("/messages")).doesNotContain("result=");
    }

    @Test
    void aMissingCredentialRefFallsBackToTheHostsOwnIdentity() {
        ConductorAgentRequest request = new ConductorAgentRequest();
        request.setRawConfig(rawConfig());

        // Deliberate: a deployment running on managed identity configures no credential at all, so
        // the default Azure credential chain is a supported mode rather than a bad request. It has
        // nothing to find in a test JVM, which is what surfaces here.
        assertThatThrownBy(() -> client.getAgentStatus("thread-1", request))
                .isInstanceOf(CredentialUnavailableException.class);
    }

    @Test
    void aThreadWithNoRunsIsReportedClearly() {
        String executionId = start().getExecutionId();
        latestRunId.set(null);

        assertThatThrownBy(() -> client.getAgentStatus(executionId, statusRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no runs");
    }

    @Test
    void textIsFoundEvenWhenTheAssistantReturnsAnImagePartFirst() {
        String executionId = start().getExecutionId();
        runStatus.set("completed");
        assistantReturnsImageFirst.set(true);

        ConductorAgentStatusResponse response = client.getAgentStatus(executionId, statusRequest());

        // Taking content[0] made the result empty for exactly the assistants most likely to
        // produce a chart.
        assertThat(response.getOutput()).isEqualTo(Map.of("result", "the answer"));
    }

    // --- parallel tool calls ------------------------------------------------------------------

    @Test
    void everyToolCallTheAgentAsksForIsReported() {
        String executionId = start().getExecutionId();
        runStatus.set("requires_action");
        toolCallCount.set(2);

        ConductorAgentStatusResponse response = client.getAgentStatus(executionId, statusRequest());

        // Both, in the order the model asked. Reporting only the first left the workflow unable to
        // run the second — it never learned it had been asked.
        assertThat(response.getPendingTools()).hasSize(2);
        assertThat(response.getPendingTools())
                .extracting(tool -> tool.get("tool_name"))
                .containsExactly("tool_1", "tool_2");
        assertThat(response.getPendingTools())
                .extracting(tool -> tool.get("tool_call_id"))
                .containsExactly("call-1", "call-2");
        // pendingTool stays populated with the first, for callers that handle one tool per turn.
        assertThat(response.getPendingTool()).containsEntry("tool_call_id", "call-1");
        assertThat(response.getPendingToolName()).isEqualTo("tool_1");
    }

    @Test
    void eachToolCallGetsItsOwnResult() {
        String executionId = start().getExecutionId();
        runStatus.set("requires_action");
        toolCallCount.set(2);
        requestLog.clear();

        client.respond(
                ConductorAgentRespondRequest.builder()
                        .executionId(executionId)
                        .toolResults(
                                Map.of(
                                        "call-1", Map.of("revenue", "4.2M"),
                                        "call-2", Map.of("headcount", 37)))
                        .credentials(credentials)
                        .rawConfig(rawConfig())
                        .build());

        String submitted = bodyOf("/submit_tool_outputs");
        assertThat(submitted).contains("call-1", "call-2", "4.2M", "37");
        // The bug this replaces: one result replayed against every call, so call-2 was told the
        // revenue figure and the model reasoned from it.
        assertThat(submitted.indexOf("4.2M")).isEqualTo(submitted.lastIndexOf("4.2M"));
    }

    @Test
    void aSingleResultForAMultiToolTurnIsRejected() {
        String executionId = start().getExecutionId();
        runStatus.set("requires_action");
        toolCallCount.set(2);

        // Rather than padding: the provider would accept a padded reply and the model would answer
        // from a result no tool produced.
        assertThatThrownBy(
                        () ->
                                client.respond(
                                        ConductorAgentRespondRequest.builder()
                                                .executionId(executionId)
                                                .body(Map.of("result", "only one answer"))
                                                .credentials(credentials)
                                                .rawConfig(rawConfig())
                                                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("waiting on 2 tool calls");
    }

    @Test
    void aSingleToolTurnStillAnswersFromASimpleBody() {
        String executionId = start().getExecutionId();
        runStatus.set("requires_action");
        toolCallCount.set(1);
        requestLog.clear();

        client.respond(
                ConductorAgentRespondRequest.builder()
                        .executionId(executionId)
                        .body(Map.of("result", "the only answer"))
                        .credentials(credentials)
                        .rawConfig(rawConfig())
                        .build());

        assertThat(bodyOf("/submit_tool_outputs")).contains("call-1", "the only answer");
    }

    @Test
    void anIncompleteKeyedReplyNamesWhatIsMissing() {
        String executionId = start().getExecutionId();
        runStatus.set("requires_action");
        toolCallCount.set(2);

        assertThatThrownBy(
                        () ->
                                client.respond(
                                        ConductorAgentRespondRequest.builder()
                                                .executionId(executionId)
                                                .toolResults(Map.of("call-1", Map.of("ok", true)))
                                                .credentials(credentials)
                                                .rawConfig(rawConfig())
                                                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tool_2")
                .hasMessageContaining("call-2");
    }

    // --- token provider cache --------------------------------------------------------------

    @Test
    void tokenProviderIsReusedAcrossPolls() {
        String executionId = start().getExecutionId();
        int resolutionsAfterStart = client.authResolutions();

        for (int i = 0; i < 5; i++) {
            client.getAgentStatus(executionId, statusRequest());
        }

        // Five polls, one credential built. Before the cache, every poll built it again — and for
        // a service principal, exchanged a token too.
        assertThat(client.authResolutions()).isEqualTo(resolutionsAfterStart);
    }

    @Test
    void cachedProviderIsRebuiltAfterItsTtlLapses() {
        String executionId = start().getExecutionId();
        client.getAgentStatus(executionId, statusRequest());
        int resolutionsWhileCached = client.authResolutions();

        clock.advance(Duration.ofMinutes(11));
        client.getAgentStatus(executionId, statusRequest());

        // The TTL is the backstop that picks up a rotated credential even when Azure never rejects
        // what we hold.
        assertThat(client.authResolutions()).isGreaterThan(resolutionsWhileCached);
    }

    @Test
    void twoEndpointsOnOneScopeShareATokenProvider() {
        // A token is scoped to an Azure resource, not to a URL, so the same credential reaching two
        // endpoints that resolve to the same scope needs one provider, not two.
        Map<String, Object> otherEndpoint = new HashMap<>(rawConfig());
        otherEndpoint.put("endpoint", foundry.url("/other").toString());

        client.startAgent(
                ConductorAgentStartRequest.builder()
                        .prompt("what is the answer?")
                        .credentials(credentialsFor(CREDENTIAL_REF))
                        .rawConfig(rawConfig())
                        .build());
        int afterFirst = client.authResolutions();

        client.startAgent(
                ConductorAgentStartRequest.builder()
                        .prompt("what is the answer?")
                        .credentials(credentialsFor(CREDENTIAL_REF))
                        .rawConfig(otherEndpoint)
                        .build());

        assertThat(client.authResolutions()).isEqualTo(afterFirst);
    }

    @Test
    void aDifferentScopeGetsItsOwnTokenProvider() {
        // The other half of the same rule: an explicit scope override is a different resource, so
        // it must not be served the token cached for the default one.
        Map<String, Object> otherScope = new HashMap<>(rawConfig());
        otherScope.put("scope", "https://ml.azure.com/.default");

        client.startAgent(
                ConductorAgentStartRequest.builder()
                        .prompt("what is the answer?")
                        .credentials(credentialsFor(CREDENTIAL_REF))
                        .rawConfig(rawConfig())
                        .build());
        int afterFirst = client.authResolutions();

        client.startAgent(
                ConductorAgentStartRequest.builder()
                        .prompt("what is the answer?")
                        .credentials(credentialsFor(CREDENTIAL_REF))
                        .rawConfig(otherScope)
                        .build());

        assertThat(client.authResolutions()).isGreaterThan(afterFirst);
    }

    @Test
    void authIsCachedPerCredential() {
        String first = start(CREDENTIAL_REF).getExecutionId();
        String second = start("OTHER_CRED").getExecutionId();
        int resolutionsBefore = client.authResolutions();
        client.getAgentStatus(first, statusRequest(CREDENTIAL_REF));
        client.getAgentStatus(second, statusRequest("OTHER_CRED"));

        // One resolution per credential at start, then nothing: both polls hit the cache. The two
        // never share an entry, which the distinct api-key headers below prove.
        assertThat(resolutionsBefore).isEqualTo(2);
        assertThat(client.authResolutions()).isEqualTo(resolutionsBefore);
        assertThat(requestLog).anyMatch(r -> r.contains("azure-api-key"));
        assertThat(requestLog).anyMatch(r -> r.contains("other-api-key"));
    }

    @Test
    void rejectedAuthEvictsCachedProvider() {
        String executionId = start().getExecutionId();
        int resolutionsBefore = client.authResolutions();

        rejectAuth.set(true);
        assertThatThrownBy(() -> client.getAgentStatus(executionId, statusRequest()))
                .hasMessageContaining("was rejected: HTTP 401");

        rejectAuth.set(false);
        client.getAgentStatus(executionId, statusRequest());

        // A rotated credential is picked up on the very next poll rather than waiting out the TTL —
        // the delegate's failure budget is shorter than the TTL.
        assertThat(client.authResolutions()).isGreaterThan(resolutionsBefore);
    }

    @Test
    void concurrentPollsShareTheCachedProvider() throws Exception {
        String executionId = start().getExecutionId();
        int threads = 8;
        int pollsPerThread = 5;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startLine = new CountDownLatch(1);
        List<Throwable> failures = new ArrayList<>();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(
                        pool.submit(
                                () -> {
                                    try {
                                        startLine.await();
                                        for (int i = 0; i < pollsPerThread; i++) {
                                            client.getAgentStatus(executionId, statusRequest());
                                        }
                                    } catch (Throwable e) {
                                        synchronized (failures) {
                                            failures.add(e);
                                        }
                                    }
                                }));
            }
            startLine.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(failures).isEmpty();
        // The cache races benignly: a few threads may each resolve auth, but nowhere near one
        // resolution per poll.
        assertThat(client.authResolutions()).isLessThan(threads * pollsPerThread);
    }

    // --- helpers ---------------------------------------------------------------------------

    private ConductorAgentStartResponse start() {
        return start(CREDENTIAL_REF);
    }

    /** A distinct credential per name, so a test can tell two apart on the wire. */
    private static Map<String, String> credentialsFor(String credentialRef) {
        return CREDENTIAL_REF.equals(credentialRef)
                ? Map.of("apiKey", "azure-api-key")
                : Map.of("apiKey", "other-api-key");
    }

    private ConductorAgentStartResponse start(String credentialRef) {
        return client.startAgent(
                ConductorAgentStartRequest.builder()
                        .prompt("what is the answer?")
                        .credentials(credentialsFor(credentialRef))
                        .rawConfig(rawConfig())
                        .build());
    }

    private Map<String, Object> rawConfig() {
        return Map.of(
                "endpoint",
                foundry.url("").toString().replaceAll("/$", ""),
                "assistantId",
                "asst-1");
    }

    private ConductorAgentRequest statusRequest() {
        return statusRequest(CREDENTIAL_REF);
    }

    private ConductorAgentRequest statusRequest(String credentialRef) {
        ConductorAgentRequest request = new ConductorAgentRequest();
        request.setCredentials(credentialsFor(credentialRef));
        request.setRawConfig(rawConfig());
        return request;
    }

    private String bodyOf(String pathFragment) {
        synchronized (requestLog) {
            return requestLog.stream()
                    .filter(r -> r.contains(pathFragment))
                    .reduce((a, b) -> b)
                    .orElseThrow(
                            () ->
                                    new AssertionError(
                                            "no request matching "
                                                    + pathFragment
                                                    + " in "
                                                    + requestLog));
        }
    }

    /** Routes by path so a test can poll as many times as it likes without pre-enqueuing. */
    private final class FoundryDispatcher extends Dispatcher {

        @Override
        public MockResponse dispatch(RecordedRequest request) {
            String path = request.getPath() == null ? "" : request.getPath();
            String body = request.getBody().readUtf8();
            synchronized (requestLog) {
                // The credential header goes in too, so a test can tell which credential was used.
                requestLog.add(
                        request.getMethod()
                                + " "
                                + path
                                + " auth="
                                + StringUtils.defaultString(request.getHeader("api-key"))
                                + StringUtils.defaultString(request.getHeader("Authorization"))
                                + " "
                                + body);
            }
            if (rejectAuth.get()) {
                return new MockResponse().setResponseCode(401).setBody("{\"error\":\"expired\"}");
            }
            if (path.contains("/threads?")) {
                return json("{\"id\":\"thread-1\"}");
            }
            if (path.contains("/cancel")) {
                return json("{\"id\":\"" + latestRunId.get() + "\",\"status\":\"cancelled\"}");
            }
            if (path.contains("/submit_tool_outputs")) {
                return json("{\"id\":\"" + latestRunId.get() + "\",\"status\":\"queued\"}");
            }
            if (path.contains("/runs")) {
                if ("POST".equals(request.getMethod())) {
                    // A new run supersedes the previous one on this thread.
                    latestRunId.set(latestRunId.get() == null ? "run-1" : "run-2");
                    return json("{\"id\":\"" + latestRunId.get() + "\",\"status\":\"queued\"}");
                }
                if (latestRunId.get() == null) {
                    return json("{\"data\":[]}");
                }
                StringBuilder calls = new StringBuilder();
                for (int i = 1; i <= toolCallCount.get(); i++) {
                    if (i > 1) {
                        calls.append(',');
                    }
                    calls.append("{\"id\":\"call-")
                            .append(i)
                            .append("\",\"function\":{\"name\":\"tool_")
                            .append(i)
                            .append("\",\"arguments\":\"{\\\"n\\\":")
                            .append(i)
                            .append("}\"}}");
                }
                return json(
                        "{\"data\":[{\"id\":\""
                                + latestRunId.get()
                                + "\",\"status\":\""
                                + runStatus.get()
                                + "\",\"required_action\":{\"submit_tool_outputs\":{\"tool_calls\":"
                                + "["
                                + calls
                                + "]}}}]}");
            }
            if (path.contains("/messages")) {
                if (assistantReturnsImageFirst.get()) {
                    return json(
                            """
                            {"data":[{"role":"assistant","content":[
                               {"type":"image_file","image_file":{"file_id":"file-1"}},
                               {"type":"text","text":{"value":"the answer"}}]}]}""");
                }
                return "POST".equals(request.getMethod())
                        ? json("{\"id\":\"msg-1\"}")
                        : json(
                                """
                                {"data":[{"role":"assistant","content":[{"type":"text","text":{"value":"the answer"}}]},
                                         {"role":"user","content":[{"type":"text","text":{"value":"what is the answer?"}}]}]}""");
            }
            return new MockResponse().setResponseCode(404).setBody("{}");
        }

        private MockResponse json(String body) {
            return new MockResponse()
                    .setResponseCode(200)
                    .setBody(body)
                    .addHeader("Content-Type", "application/json");
        }
    }
}
