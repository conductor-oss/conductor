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
package org.conductoross.conductor.ai.agentspan;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.conductoross.conductor.ai.agentspan.runtime.spi.SkillPackageStore;
import org.conductoross.conductor.ai.agentspan.runtime.spi.StoredSkillPackage;
import org.conductoross.conductor.common.metadata.agent.SkillDetail;
import org.conductoross.conductor.dao.SkillMetadataDAO;
import org.conductoross.conductor.dao.SkillPackageDAO;
import org.conductoross.conductor.sqlite.dao.SqliteSkillMetadataDAO;
import org.conductoross.conductor.sqlite.dao.SqliteSkillPackageDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.retry.support.RetryTemplate;
import org.sqlite.SQLiteDataSource;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Integration coverage for the AgentSpan host configuration and both persistence adapters. The
 * tests cross the complete adapter boundary into a real SQLite database; no DAO is mocked.
 */
class ConductorAgentSpanConfigurationIntegrationTest {

    @TempDir Path temporaryDirectory;

    private SkillMetadataDAO metadataDAO;
    private SkillPackageDAO packageDAO;

    @BeforeEach
    void createDatabase() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(
                "jdbc:sqlite:"
                        + temporaryDirectory.resolve("agentspan-integration.db").toAbsolutePath());
        createSchema(dataSource);

        ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
        RetryTemplate retryTemplate = RetryTemplate.builder().maxAttempts(1).build();
        metadataDAO = new SqliteSkillMetadataDAO(retryTemplate, objectMapper, dataSource);
        packageDAO = new SqliteSkillPackageDAO(retryTemplate, objectMapper, dataSource);
    }

    @Test
    void enabledConfigurationPersistsMetadataAndPackagesThroughRealAdapters() {
        contextRunner(true)
                .run(
                        context -> {
                            assertThat(context)
                                    .hasSingleBean(
                                            org.conductoross.conductor.ai.agentspan.runtime.spi
                                                    .SkillMetadataDAO.class);
                            assertThat(context).hasSingleBean(SkillPackageStore.class);

                            org.conductoross.conductor.ai.agentspan.runtime.spi.SkillMetadataDAO
                                    metadataStore =
                                            context.getBean(
                                                    org.conductoross.conductor.ai.agentspan.runtime
                                                            .spi.SkillMetadataDAO.class);
                            SkillPackageStore packageStore =
                                    context.getBean(SkillPackageStore.class);

                            SkillDetail versionOne = skill("incident-response", "1.0.0", 100L);
                            SkillDetail versionTwo = skill("incident-response", "2.0.0", 200L);
                            SkillDetail otherSkill = skill("release-notes", "1.0.0", 300L);

                            metadataStore.save(versionOne, true);
                            metadataStore.save(versionTwo, true);
                            metadataStore.save(otherSkill, true);

                            assertThat(metadataStore.find("incident-response", "1.0.0"))
                                    .contains(versionOne);
                            assertThat(metadataStore.find("missing", "1.0.0")).isEmpty();
                            assertThat(metadataStore.latestVersion("incident-response"))
                                    .contains("2.0.0");
                            assertThat(metadataStore.listVersions("incident-response"))
                                    .containsExactlyInAnyOrder(versionOne, versionTwo);
                            assertThat(metadataStore.list(false))
                                    .containsExactlyInAnyOrder(versionTwo, otherSkill);
                            assertThat(metadataStore.list(true))
                                    .containsExactlyInAnyOrder(versionOne, versionTwo, otherSkill);

                            metadataStore.delete("incident-response", "2.0.0");
                            assertThat(metadataStore.latestVersion("incident-response"))
                                    .contains("1.0.0");
                            assertThat(metadataStore.find("incident-response", "2.0.0")).isEmpty();

                            byte[] packageBytes = {0, 1, 2, 42, -1};
                            StoredSkillPackage stored =
                                    packageStore.store(
                                            "incident-response",
                                            "1.0.0",
                                            "sha256-checksum",
                                            packageBytes);

                            assertThat(packageStore.storageType()).isEqualTo("conductor-db");
                            assertThat(stored.handle()).isEqualTo("conductor-db://sha256-checksum");
                            assertThat(stored.storageType()).isEqualTo("conductor-db");
                            assertThat(stored.size()).isEqualTo(packageBytes.length);
                            assertThat(packageStore.exists(stored.handle())).isTrue();
                            assertThat(packageStore.read(stored.handle()))
                                    .containsExactly(packageBytes);

                            packageStore.delete(stored.handle());
                            assertThat(packageStore.exists(stored.handle())).isFalse();
                            assertThatIllegalArgumentException()
                                    .isThrownBy(() -> packageStore.read(stored.handle()))
                                    .withMessageContaining(stored.handle());
                        });
    }

    @Test
    void disabledConfigurationDoesNotPublishAgentSpanAdapters() {
        contextRunner(false)
                .run(
                        context -> {
                            assertThat(context)
                                    .doesNotHaveBean(
                                            org.conductoross.conductor.ai.agentspan.runtime.spi
                                                    .SkillMetadataDAO.class);
                            assertThat(context).doesNotHaveBean(SkillPackageStore.class);
                        });
    }

    @Test
    void invalidPersistedMetadataIsReportedAsAnAdapterFailure() {
        metadataDAO.save("broken", "1", true, "not-json", 1L, 1L);

        contextRunner(true)
                .run(
                        context -> {
                            org.conductoross.conductor.ai.agentspan.runtime.spi.SkillMetadataDAO
                                    metadataStore =
                                            context.getBean(
                                                    org.conductoross.conductor.ai.agentspan.runtime
                                                            .spi.SkillMetadataDAO.class);

                            assertThatIllegalStateException()
                                    .isThrownBy(() -> metadataStore.find("broken", "1"))
                                    .withMessage("Failed to deserialize skill metadata");
                        });
    }

    @Test
    void unserializableMetadataIsReportedAsAnAdapterFailure() {
        SkillDetail invalid = skill("recursive", "1", 1L);
        Map<String, Object> recursiveMetadata = new HashMap<>();
        recursiveMetadata.put("self", recursiveMetadata);
        invalid.setMetadata(recursiveMetadata);

        contextRunner(true)
                .run(
                        context -> {
                            org.conductoross.conductor.ai.agentspan.runtime.spi.SkillMetadataDAO
                                    metadataStore =
                                            context.getBean(
                                                    org.conductoross.conductor.ai.agentspan.runtime
                                                            .spi.SkillMetadataDAO.class);

                            assertThatIllegalStateException()
                                    .isThrownBy(() -> metadataStore.save(invalid, true))
                                    .withMessage("Failed to serialize skill metadata");
                        });
    }

    private ApplicationContextRunner contextRunner(boolean enabled) {
        return new ApplicationContextRunner()
                .withUserConfiguration(ConductorAgentSpanConfiguration.class)
                .withBean(SkillMetadataDAO.class, () -> metadataDAO)
                .withBean(SkillPackageDAO.class, () -> packageDAO)
                .withPropertyValues("conductor.integrations.ai.enabled=" + enabled);
    }

    private static SkillDetail skill(String name, String version, long timestamp) {
        return SkillDetail.builder()
                .name(name)
                .version(version)
                .description("Integration test skill " + name)
                .checksum(name + '-' + version)
                .packageFileHandleId("conductor-db://" + name + '-' + version)
                .storageType("conductor-db")
                .status("ACTIVE")
                .createdAt(timestamp)
                .updatedAt(timestamp)
                .packageSize(5L)
                .fileCount(1)
                .metadata(Map.of("source", "integration-test"))
                .rawConfig(Map.of("allowed-tools", List.of("Read")))
                .build();
    }

    private static void createSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE skill_metadata ("
                            + "name VARCHAR(255) NOT NULL, version VARCHAR(255) NOT NULL, "
                            + "is_latest BOOLEAN NOT NULL DEFAULT 0, detail_json TEXT NOT NULL, "
                            + "created_at BIGINT, updated_at BIGINT, PRIMARY KEY (name, version))");
            statement.execute(
                    "CREATE TABLE skill_package ("
                            + "handle VARCHAR(255) NOT NULL PRIMARY KEY, data TEXT NOT NULL, "
                            + "size_bytes BIGINT, created_at BIGINT)");
        }
    }
}
