/*
 * Copyright 2021 Netflix, Inc.
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
package com.netflix.conductor.sdk.testing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.sdk.healthcheck.HealthCheckClient;

import com.google.common.util.concurrent.Uninterruptibles;

public class LocalServerRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalServerRunner.class);

    private final HealthCheckClient healthCheck;

    private Process serverProcess;

    private final ScheduledExecutorService healthCheckExecutor =
            Executors.newSingleThreadScheduledExecutor();

    private final CountDownLatch serverProcessLatch = new CountDownLatch(1);

    private final int port;

    private final String conductorVersion;

    private final String serverURL;

    private static Map<Integer, LocalServerRunner> serverInstances = new HashMap<>();

    public LocalServerRunner(int port, String conductorVersion) {
        this.port = port;
        this.conductorVersion = conductorVersion;
        this.serverURL = "http://localhost:" + port + "/";
        healthCheck = new HealthCheckClient(serverURL + "health");
    }

    public String getServerAPIUrl() {
        return this.serverURL + "api/";
    }

    /**
     * Starts the local server. Downloads the latest conductor build from the maven repo If you want
     * to start the server from a specific download location, set `repositoryURL` system property
     * with the link to the actual downloadable server boot jar file.
     *
     * <p><b>System Properties that can be set</b> conductorVersion: when specified, uses this
     * version of conductor to run tests (and downloads from maven repo) repositoryURL: full url
     * where the server boot jar can be downloaded from. This can be a public repo or internal
     * repository, allowing full control over the location and version of the conductor server
     */
    public void startLocalServer() {
        synchronized (serverInstances) {
            if (serverInstances.get(port) != null) {
                throw new IllegalStateException(
                        "Another server has already been started at port " + port);
            }
            serverInstances.put(port, this);
        }

        try {
            String downloadURL =
                    "https://repo1.maven.org/maven2/com/netflix/conductor/conductor-server/"
                            + conductorVersion
                            + "/conductor-server-"
                            + conductorVersion
                            + "-boot.jar";

            String repositoryURL =
                    Optional.ofNullable(System.getProperty("repositoryURL")).orElse(downloadURL);

            LOGGER.info(
                    "Running conductor with version {} from repo url {}",
                    conductorVersion,
                    repositoryURL);

            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
            installAndStartServer(repositoryURL, port);
            healthCheckExecutor.scheduleAtFixedRate(
                    () -> {
                        try {
                            if (serverProcessLatch.getCount() > 0) {
                                boolean isRunning = healthCheck.isServerRunning();
                                if (isRunning) {
                                    serverProcessLatch.countDown();
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.warn(
                                    "Caught an exception while polling for server running status {}",
                                    e.getMessage());
                        }
                    },
                    100,
                    100,
                    TimeUnit.MILLISECONDS);
            Uninterruptibles.awaitUninterruptibly(serverProcessLatch, 1, TimeUnit.MINUTES);

            if (serverProcessLatch.getCount() > 0) {
                throw new RuntimeException("Server not healthy");
            }
            healthCheckExecutor.shutdownNow();

        } catch (IOException e) {
            throw new Error(e);
        }
    }

    public void shutdown() {
        if (serverProcess != null) {
            serverProcess.destroyForcibly();
            serverInstances.remove(port);
        }
    }

    private synchronized void installAndStartServer(String repositoryURL, int localServerPort)
            throws IOException {
        if (serverProcess != null) {
            return;
        }

        String configFile =
                LocalServerRunner.class.getResource("/test-server.properties").getFile();
        String tempDir = System.getProperty("java.io.tmpdir");
        Path serverFile = Paths.get(tempDir, "conductor-server.jar");
        if (!Files.exists(serverFile)) {
            Files.copy(new URL(repositoryURL).openStream(), serverFile);
        }

        String command =
                "java -Dserver.port="
                        + localServerPort
                        + " -DCONDUCTOR_CONFIG_FILE="
                        + configFile
                        + " -jar "
                        + serverFile;
        LOGGER.info("Running command {}", command);

        serverProcess = Runtime.getRuntime().exec(command);
        BufferedReader error =
                new BufferedReader(new InputStreamReader(serverProcess.getErrorStream()));
        BufferedReader op =
                new BufferedReader(new InputStreamReader(serverProcess.getInputStream()));

        // This captures the stream and copies to a visible log for tracking errors asynchronously
        // using a separate thread
        Executors.newSingleThreadScheduledExecutor()
                .execute(
                        () -> {
                            String line = null;
                            while (true) {
                                try {
                                    if ((line = error.readLine()) == null) break;
                                } catch (IOException e) {
                                    LOGGER.error("Exception reading input stream:", e);
                                }
                                // copy to standard error
                                LOGGER.error("Server error stream - {}", line);
                            }
                        });

        // This captures the stream and copies to a visible log for tracking errors asynchronously
        // using a separate thread
        Executors.newSingleThreadScheduledExecutor()
                .execute(
                        () -> {
                            String line = null;
                            while (true) {
                                try {
                                    if ((line = op.readLine()) == null) break;
                                } catch (IOException e) {
                                    LOGGER.error("Exception reading input stream:", e);
                                }
                                // copy to standard out
                                LOGGER.trace("Server input stream - {}", line);
                            }
                        });
    }
}
