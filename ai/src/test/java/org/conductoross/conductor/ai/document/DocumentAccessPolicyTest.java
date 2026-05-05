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
package org.conductoross.conductor.ai.document;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentAccessPolicyTest {

    private DocumentAccessPolicy policy;
    private Environment env;

    @BeforeEach
    void setUp() {
        env = mock(Environment.class);
        // Default: no file-storage.parentDir set — uses ~/worker-payload/ fallback
        when(env.getProperty("conductor.file-storage.parentDir")).thenReturn(null);
        policy = new DocumentAccessPolicy(env);
        // Simulate @PostConstruct
        policy.resolveEffectiveAllowedDirectories();
    }

    // ========================================================================
    // Blocklist — local filesystem sensitive paths
    // ========================================================================

    @Test
    void shouldBlockEtcPasswd() {
        assertThrows(
                DocumentAccessDeniedException.class, () -> policy.validateAccess("/etc/passwd"));
    }

    @Test
    void shouldBlockEtcShadow() {
        assertThrows(
                DocumentAccessDeniedException.class, () -> policy.validateAccess("/etc/shadow"));
    }

    @Test
    void shouldBlockEtcSshDirectory() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("/etc/ssh/sshd_config"));
    }

    @Test
    void shouldBlockProcSelfEnviron() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("/proc/self/environ"));
    }

    @Test
    void shouldBlockFileUriScheme() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("file:///etc/passwd"));
    }

    @Test
    void shouldBlockKubernetesSecrets() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("/var/run/secrets/kubernetes.io/serviceaccount/token"));
    }

    @Test
    void shouldBlockDockerSecrets() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("/run/secrets/db_password"));
    }

    // ========================================================================
    // Blocklist — sensitive file names
    // ========================================================================

    @Test
    void shouldBlockDotEnvFile() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("/app/config/.env"));
    }

    @Test
    void shouldBlockPrivateKey() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("/home/user/.ssh/id_rsa"));
    }

    @Test
    void shouldBlockCredentialsJson() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("/app/credentials.json"));
    }

    @Test
    void shouldBlockKeystoreJks() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("/opt/app/keystore.jks"));
    }

    // ========================================================================
    // Blocklist — cloud metadata endpoints
    // ========================================================================

    @Test
    void shouldBlockAwsMetadata() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () ->
                        policy.validateAccess(
                                "http://169.254.169.254/latest/meta-data/iam/security-credentials/"));
    }

    @Test
    void shouldBlockAwsEcsMetadata() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("http://169.254.170.2/v2/credentials/guid"));
    }

    @Test
    void shouldBlockGcpMetadata() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("http://metadata.google.internal/computeMetadata/v1/"));
    }

    @Test
    void shouldBlockAlibabaMetadata() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("http://100.100.100.200/latest/meta-data/"));
    }

    @Test
    void shouldBlockAwsDnsAlias() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("http://instance-data.ec2.internal/latest/meta-data/"));
    }

    @Test
    void shouldBlockKubernetesApiServer() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("https://kubernetes.default.svc/api/v1/secrets"));
    }

    // ========================================================================
    // Link-local range detection (SSRF bypass prevention)
    // ========================================================================

    @Test
    void shouldBlockLinkLocalViaResolvedAddress() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("http://169.254.169.253/something"));
    }

    @Test
    void shouldBlockLoopbackAddress() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("http://127.0.0.1/admin"));
    }

    @Test
    void shouldBlockLocalhostViaResolution() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("http://localhost/admin"));
    }

    // ========================================================================
    // Platform-specific paths
    // ========================================================================

    @Test
    void shouldBlockDockerSocket() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("/var/run/docker.sock"));
    }

    @Test
    void shouldBlockKubernetesNodeConfig() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("/etc/kubernetes/admin.conf"));
    }

    @Test
    void shouldBlockWindowsRegistryHive() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("C:/Windows/System32/config/SAM"));
    }

    @Test
    void shouldBlockWindowsUnattendXml() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("C:/Windows/Panther/Unattend.xml"));
    }

    @Test
    void shouldBlockTerraformState() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("/app/infra/terraform.tfstate"));
    }

    @Test
    void shouldBlockVaultToken() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("/app/deploy/.vault-token"));
    }

    @Test
    void shouldBlockGcpApplicationDefaultCredentials() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("/app/config/application_default_credentials.json"));
    }

    @Test
    void shouldBlockMavenSettings() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("/home/user/.m2/settings.xml"));
    }

    @Test
    void shouldBlockEnvStaging() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("/app/.env.staging"));
    }

    @Test
    void shouldBlockWebConfig() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("C:/inetpub/wwwroot/web.config"));
    }

    @Test
    void shouldBlockMacOsKeychain() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("/Library/Keychains/System.keychain"));
    }

    @Test
    void shouldBlockPowerShellHistory() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () ->
                        policy.validateAccess(
                                "C:/Users/admin/AppData/Roaming/PSReadLine/ConsoleHost_history.txt"));
    }

    // ========================================================================
    // Path traversal
    // ========================================================================

    @Test
    void shouldBlockPathTraversal() {
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("/app/data/../../etc/passwd"));
    }

    // ========================================================================
    // Safe paths — should be allowed (paths under ~/worker-payload/ default)
    // ========================================================================

    @Test
    void shouldAllowPathUnderDefaultPayloadDir() {
        String payloadDir = System.getProperty("user.home") + "/worker-payload/";
        assertDoesNotThrow(() -> policy.validateAccess(payloadDir + "wf123/task456/report.pdf"));
    }

    @Test
    void shouldAllowNormalHttpUrl() {
        assertDoesNotThrow(() -> policy.validateAccess("https://cdn.example.com/image.png"));
    }

    @Test
    void shouldAllowFileUriUnderPayloadDir() {
        String payloadDir = System.getProperty("user.home") + "/worker-payload/";
        assertDoesNotThrow(() -> policy.validateAccess("file://" + payloadDir + "output.pdf"));
    }

    // ========================================================================
    // Disabled policy
    // ========================================================================

    @Test
    void shouldAllowEverythingWhenDisabled() {
        policy.setDisabled(true);
        assertDoesNotThrow(() -> policy.validateAccess("/etc/passwd"));
        assertDoesNotThrow(() -> policy.validateAccess("http://169.254.169.254/latest/meta-data/"));
    }

    // ========================================================================
    // Custom blocklist extensions
    // ========================================================================

    @Test
    void shouldBlockCustomPathPrefix() {
        policy.setBlockedPathPrefixes(List.of("/custom/sensitive/"));
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("/custom/sensitive/data.txt"));
    }

    @Test
    void shouldBlockCustomFileName() {
        policy.setBlockedFileNames(List.of("secret.yaml"));
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("/app/config/secret.yaml"));
    }

    @Test
    void shouldBlockCustomHost() {
        policy.setBlockedHosts(List.of("internal.corp.net"));
        assertThrows(
                DocumentAccessDeniedException.class,
                () -> policy.validateAccess("http://internal.corp.net/api/secret"));
    }

    // ========================================================================
    // Allowed directories — derived from file-storage.parentDir
    // ========================================================================

    @Nested
    class AllowedDirectoriesTests {

        @Test
        void shouldAutoIncludeParentDirFromConfig() {
            when(env.getProperty("conductor.file-storage.parentDir"))
                    .thenReturn("/data/conductor/");
            policy = new DocumentAccessPolicy(env);
            policy.resolveEffectiveAllowedDirectories();

            assertDoesNotThrow(() -> policy.validateAccess("/data/conductor/wf/task/report.pdf"));
            assertThrows(
                    DocumentAccessDeniedException.class,
                    () -> policy.validateAccess("/other/path/report.pdf"));
        }

        @Test
        void shouldFallbackToDefaultPayloadDirWhenParentDirNotSet() {
            when(env.getProperty("conductor.file-storage.parentDir")).thenReturn(null);
            policy = new DocumentAccessPolicy(env);
            policy.resolveEffectiveAllowedDirectories();

            String defaultDir = System.getProperty("user.home") + "/worker-payload/";
            assertDoesNotThrow(() -> policy.validateAccess(defaultDir + "wf/task/report.pdf"));
        }

        @Test
        void shouldAllowAdditionalDirectoriesBeyondParentDir() {
            when(env.getProperty("conductor.file-storage.parentDir"))
                    .thenReturn("/data/conductor/");
            policy = new DocumentAccessPolicy(env);
            policy.setAllowedDirectories(List.of("/tmp/imports/", "/data/shared/"));
            policy.resolveEffectiveAllowedDirectories();

            // parentDir is allowed
            assertDoesNotThrow(() -> policy.validateAccess("/data/conductor/output.pdf"));
            // additional dirs are allowed
            assertDoesNotThrow(() -> policy.validateAccess("/tmp/imports/input.csv"));
            assertDoesNotThrow(() -> policy.validateAccess("/data/shared/image.png"));
            // anything else is denied
            assertThrows(
                    DocumentAccessDeniedException.class,
                    () -> policy.validateAccess("/home/user/report.pdf"));
        }

        @Test
        void shouldDenyPathOutsideAllowedDirectories() {
            when(env.getProperty("conductor.file-storage.parentDir"))
                    .thenReturn("/data/conductor/");
            policy = new DocumentAccessPolicy(env);
            policy.resolveEffectiveAllowedDirectories();

            assertThrows(
                    DocumentAccessDeniedException.class,
                    () -> policy.validateAccess("/app/documents/report.pdf"));
        }

        @Test
        void shouldDenyFileUriOutsideAllowedDirectories() {
            when(env.getProperty("conductor.file-storage.parentDir"))
                    .thenReturn("/data/conductor/");
            policy = new DocumentAccessPolicy(env);
            policy.resolveEffectiveAllowedDirectories();

            assertThrows(
                    DocumentAccessDeniedException.class,
                    () -> policy.validateAccess("file:///home/user/secret.txt"));
        }

        @Test
        void shouldNotApplyAllowedDirectoriesToHttpUrls() {
            when(env.getProperty("conductor.file-storage.parentDir"))
                    .thenReturn("/data/conductor/");
            policy = new DocumentAccessPolicy(env);
            policy.resolveEffectiveAllowedDirectories();

            assertDoesNotThrow(() -> policy.validateAccess("https://cdn.example.com/image.png"));
        }

        @Test
        void shouldStillBlockSensitiveFilesEvenInsideAllowedDir() {
            when(env.getProperty("conductor.file-storage.parentDir")).thenReturn("/app/");
            policy = new DocumentAccessPolicy(env);
            policy.resolveEffectiveAllowedDirectories();

            assertThrows(
                    DocumentAccessDeniedException.class,
                    () -> policy.validateAccess("/app/config/.env"));
        }

        @Test
        void shouldHandleParentDirWithoutTrailingSlash() {
            when(env.getProperty("conductor.file-storage.parentDir")).thenReturn("/data/conductor");
            policy = new DocumentAccessPolicy(env);
            policy.resolveEffectiveAllowedDirectories();

            assertDoesNotThrow(() -> policy.validateAccess("/data/conductor/docs/report.pdf"));
        }

        @Test
        void shouldIncludeEffectiveDirectoriesInDenialMessage() {
            when(env.getProperty("conductor.file-storage.parentDir"))
                    .thenReturn("/data/conductor/");
            policy = new DocumentAccessPolicy(env);
            policy.resolveEffectiveAllowedDirectories();

            DocumentAccessDeniedException ex =
                    assertThrows(
                            DocumentAccessDeniedException.class,
                            () -> policy.validateAccess("/unauthorized/path/file.txt"));
            assertTrue(ex.getMessage().contains("/data/conductor/"));
        }
    }
}
