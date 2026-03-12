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

import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Enforces access restrictions on document locations to prevent reading or writing sensitive files.
 * Blocks well-known sensitive paths on local filesystems and cloud metadata endpoints by default.
 * Configurable via {@code conductor.document-access-policy.*} properties.
 *
 * <p>By default, the allowed directory list is derived from {@code
 * conductor.file-storage.parentDir} so that document workers and the access policy share a single
 * configuration. Additional directories can be added via {@code
 * conductor.document-access-policy.allowed-directories}.
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "conductor.document-access-policy")
@Getter
@Setter
public class DocumentAccessPolicy {

    private final Environment env;

    public DocumentAccessPolicy(Environment env) {
        this.env = env;
    }

    /**
     * Additional allowed directory prefixes for local filesystem access, beyond the directory
     * configured by {@code conductor.file-storage.parentDir} (which is always included
     * automatically). When the effective allowed list is non-empty, <b>only</b> paths that fall
     * under one of those directories are permitted — all others are denied regardless of the
     * blocklist.
     *
     * <p>Example: {@code /tmp/imports/,/data/shared/} adds those trees in addition to the
     * file-storage parentDir. Supports {@code ~/} expansion and environment variable defaults via
     * Spring.
     */
    private List<String> allowedDirectories = List.of();

    /** Additional path prefixes to block (merged with built-in defaults). */
    private List<String> blockedPathPrefixes = List.of();

    /** Additional file name patterns to block (exact match, case-insensitive). */
    private List<String> blockedFileNames = List.of();

    /** Additional hostname/IP patterns to block for HTTP-based loaders. */
    private List<String> blockedHosts = List.of();

    /** Set to true to disable all access checks (not recommended for production). */
    private boolean disabled = false;

    /**
     * Effective allowed directories resolved at startup: the file-storage parentDir (if configured)
     * plus any additional entries from {@link #allowedDirectories}.
     */
    private List<String> effectiveAllowedDirectories;

    @PostConstruct
    void resolveEffectiveAllowedDirectories() {
        List<String> dirs = new ArrayList<>();

        // Always include the file-storage parentDir — this is where workers store output
        String parentDir = env.getProperty("conductor.file-storage.parentDir");
        if (parentDir != null && !parentDir.isBlank()) {
            dirs.add(parentDir);
        } else {
            // Match the default used by AIModelProvider when the property is not set
            dirs.add(System.getProperty("user.home") + "/worker-payload/");
        }

        if (allowedDirectories != null) {
            dirs.addAll(allowedDirectories);
        }

        effectiveAllowedDirectories = List.copyOf(dirs);

        log.info(
                "Document access policy effective allowed directories: {}",
                effectiveAllowedDirectories);
    }

    // --- Built-in blocked path prefixes (local filesystem) ---
    private static final List<String> DEFAULT_BLOCKED_PATH_PREFIXES =
            List.of(
                    // ---- Linux system credentials & auth ----
                    "/etc/passwd",
                    "/etc/shadow",
                    "/etc/gshadow",
                    "/etc/master.passwd",
                    "/etc/sudoers",
                    "/etc/sudoers.d/",
                    "/etc/pam.d/",
                    "/etc/login.defs",
                    "/etc/krb5.keytab",
                    "/etc/security/",
                    // ---- SSH & TLS ----
                    "/etc/ssh/",
                    "/etc/ssl/",
                    "/etc/pki/",
                    // ---- Kernel / process / device ----
                    "/proc/",
                    "/sys/",
                    "/dev/",
                    // ---- Root home ----
                    "/root/",
                    // ---- Logs (may leak tokens, IPs, credentials) ----
                    "/var/log/",
                    // ---- Scheduled tasks ----
                    "/var/spool/cron/",
                    // ---- Container & orchestration secrets ----
                    "/var/run/secrets/", // Kubernetes service-account tokens
                    "/run/secrets/", // Docker secrets mount
                    "/var/run/docker.sock", // Docker socket — full host control
                    "/etc/kubernetes/", // Node-level K8s configs & PKI
                    // ---- macOS system paths ----
                    "/private/etc/", // macOS symlink to /etc
                    "/private/var/db/dslocal/", // macOS local directory service
                    "~/Library/Keychains/", // macOS user keychains
                    "/Library/Keychains/", // macOS system keychain
                    // ---- Windows sensitive paths (forward-slash notation) ----
                    "C:/Windows/System32/config/", // SAM, SYSTEM, SECURITY hives
                    "C:/Windows/repair/", // Backup registry hives
                    "C:/Windows/Panther/", // Unattend.xml with plaintext passwords
                    "C:/Windows/System32/sysprep/", // Sysprep unattend files
                    "C:/inetpub/", // IIS web root
                    // ---- User-home dotfiles — credentials & secrets ----
                    "~/.ssh/",
                    "~/.gnupg/",
                    "~/.aws/",
                    "~/.azure/",
                    "~/.config/gcloud/",
                    "~/.oci/", // Oracle Cloud CLI
                    "~/.kube/",
                    "~/.docker/",
                    "~/.config/gh/", // GitHub CLI tokens
                    "~/.m2/", // Maven settings (server credentials)
                    "~/.gradle/", // Gradle properties (signing keys, repo creds)
                    "~/.cargo/", // Cargo registry credentials
                    "~/.gem/", // RubyGems credentials
                    "~/.terraform.d/", // Terraform credentials
                    "~/.vault-token", // HashiCorp Vault token
                    "~/.npmrc",
                    "~/.yarnrc",
                    "~/.pypirc", // PyPI upload credentials
                    "~/.netrc",
                    "~/.gitconfig",
                    "~/.git-credentials",
                    "~/.bash_history",
                    "~/.zsh_history",
                    "~/.boto", // Legacy AWS/GCS credentials
                    "~/.s3cfg" // s3cmd credentials
                    );

    // --- Built-in blocked file names (case-insensitive, matched against last path component) ---
    private static final List<String> DEFAULT_BLOCKED_FILE_NAMES =
            List.of(
                    // ---- Environment / dotenv files ----
                    ".env",
                    ".env.local",
                    ".env.development",
                    ".env.development.local",
                    ".env.staging",
                    ".env.test",
                    ".env.production",
                    ".env.production.local",
                    ".env.backup",
                    ".env.bak",
                    ".flaskenv",
                    // ---- Web server auth ----
                    ".htpasswd",
                    // ---- Database credentials ----
                    ".pgpass",
                    ".my.cnf",
                    ".mylogin.cnf",
                    // ---- Git credentials ----
                    ".git-credentials",
                    ".gitconfig",
                    // ---- SSH keys & auth ----
                    "id_rsa",
                    "id_ed25519",
                    "id_ecdsa",
                    "id_dsa",
                    "authorized_keys",
                    "known_hosts",
                    // ---- TLS / signing keys & keystores ----
                    "private.pem",
                    "private.key",
                    "server.key",
                    "keystore.jks",
                    "truststore.jks",
                    "cacerts",
                    // ---- Cloud credentials ----
                    "credentials",
                    "credentials.json",
                    "credentials.db",
                    "service-account.json",
                    "application_default_credentials.json", // GCP ADC
                    "accessTokens.json", // Azure CLI tokens
                    "msal_token_cache.json", // Azure MSAL
                    // ---- Infrastructure-as-code secrets ----
                    "terraform.tfstate",
                    "terraform.tfstate.backup",
                    "terraform.tfvars",
                    ".vault-token",
                    // ---- Build tool credentials ----
                    "settings.xml", // Maven
                    "settings-security.xml", // Maven
                    "gradle.properties",
                    ".npmrc",
                    ".pypirc",
                    // ---- Framework configs with secrets ----
                    "master.key", // Rails master key
                    "web.config", // IIS / ASP.NET
                    // ---- Windows system files ----
                    "SAM",
                    "SYSTEM",
                    "SECURITY",
                    "Unattend.xml",
                    "autounattend.xml",
                    "ConsoleHost_history.txt", // PowerShell history
                    // ---- macOS ----
                    "login.keychain-db",
                    // ---- Docker ----
                    ".dockercfg" // Legacy Docker registry auth
                    );

    // --- Built-in blocked hosts (cloud metadata services) ---
    private static final List<String> DEFAULT_BLOCKED_HOSTS =
            List.of(
                    "169.254.169.254", // AWS / GCP / Azure / OCI / DO / Hetzner / OpenStack
                    "169.254.170.2", // AWS ECS container credentials
                    "metadata.google.internal", // GCP metadata
                    "metadata.internal", // GCP alias
                    "100.100.100.200", // Alibaba Cloud metadata
                    "instance-data.ec2.internal", // AWS metadata DNS alias
                    "fd00:ec2::254", // AWS IPv6 metadata
                    "kubernetes.default", // K8s in-cluster API
                    "kubernetes.default.svc",
                    "kubernetes.default.svc.cluster.local");

    /**
     * Validates that the given location is safe to access. Throws {@link
     * DocumentAccessDeniedException} if blocked.
     */
    public void validateAccess(String location) {
        if (disabled) {
            return;
        }

        String normalized = normalizeLocation(location);

        checkBlockedPaths(normalized);
        checkBlockedFileNames(normalized);
        checkBlockedHosts(location);
        checkPathTraversal(normalized);
        checkAllowedDirectories(location, normalized);
    }

    private void checkBlockedPaths(String normalizedPath) {
        for (String prefix : DEFAULT_BLOCKED_PATH_PREFIXES) {
            String expandedPrefix = expandHome(prefix);
            if (normalizedPath.startsWith(expandedPrefix)) {
                throw new DocumentAccessDeniedException(
                        "Access denied: path matches blocked prefix '" + prefix + "'");
            }
        }
        for (String prefix : blockedPathPrefixes) {
            String expandedPrefix = expandHome(prefix);
            if (normalizedPath.startsWith(expandedPrefix)) {
                throw new DocumentAccessDeniedException(
                        "Access denied: path matches blocked prefix '" + prefix + "'");
            }
        }
    }

    private void checkBlockedFileNames(String normalizedPath) {
        String fileName = extractFileName(normalizedPath);
        if (fileName == null || fileName.isEmpty()) {
            return;
        }
        String lowerFileName = fileName.toLowerCase();

        for (String blocked : DEFAULT_BLOCKED_FILE_NAMES) {
            if (lowerFileName.equals(blocked.toLowerCase())) {
                throw new DocumentAccessDeniedException(
                        "Access denied: file name '" + fileName + "' is blocked");
            }
        }
        for (String blocked : blockedFileNames) {
            if (lowerFileName.equals(blocked.toLowerCase())) {
                throw new DocumentAccessDeniedException(
                        "Access denied: file name '" + fileName + "' is blocked");
            }
        }
    }

    private void checkBlockedHosts(String location) {
        String host = extractHost(location);
        if (host == null || host.isEmpty()) {
            return;
        }
        String lowerHost = host.toLowerCase();

        // Check against explicit blocklist
        for (String blocked : DEFAULT_BLOCKED_HOSTS) {
            if (lowerHost.equals(blocked.toLowerCase())) {
                throw new DocumentAccessDeniedException(
                        "Access denied: host '" + host + "' is blocked");
            }
        }
        for (String blocked : blockedHosts) {
            if (lowerHost.equals(blocked.toLowerCase())) {
                throw new DocumentAccessDeniedException(
                        "Access denied: host '" + host + "' is blocked");
            }
        }

        // Resolve hostname to IP and check for link-local / metadata ranges.
        // This catches obfuscated IPs (hex, octal, decimal encoding) and DNS
        // rebinding because InetAddress.getByName normalizes all representations.
        checkResolvedAddress(host);
    }

    /**
     * Resolves the host to an IP address and blocks link-local (169.254.0.0/16) and other dangerous
     * ranges that are commonly used for SSRF against cloud metadata services.
     */
    private void checkResolvedAddress(String host) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLinkLocalAddress()) {
                throw new DocumentAccessDeniedException(
                        "Access denied: link-local address range is blocked (host resolves to "
                                + addr.getHostAddress()
                                + ")");
            }
            if (addr.isLoopbackAddress()) {
                throw new DocumentAccessDeniedException(
                        "Access denied: loopback address is blocked (host resolves to "
                                + addr.getHostAddress()
                                + ")");
            }
        } catch (DocumentAccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            // DNS resolution failure — allow the request to proceed and fail naturally
            log.debug(
                    "Could not resolve host '{}' for access policy check: {}",
                    host,
                    e.getMessage());
        }
    }

    private void checkPathTraversal(String normalizedPath) {
        if (normalizedPath.contains("/../")
                || normalizedPath.endsWith("/..")
                || normalizedPath.startsWith("../")) {
            throw new DocumentAccessDeniedException(
                    "Access denied: path traversal sequences are not allowed");
        }
    }

    /**
     * Only local filesystem paths under the effective allowed directories (file-storage parentDir +
     * any additional configured directories) are permitted. HTTP/HTTPS URLs are not subject to this
     * check.
     */
    private void checkAllowedDirectories(String originalLocation, String normalizedPath) {
        List<String> dirs = effectiveAllowedDirectories;
        if (dirs == null || dirs.isEmpty()) {
            return;
        }
        // Only apply to local filesystem paths, not HTTP URLs
        if (originalLocation.startsWith("http://") || originalLocation.startsWith("https://")) {
            return;
        }

        for (String dir : dirs) {
            String expandedDir = expandHome(dir.endsWith("/") ? dir : dir + "/");
            if (normalizedPath.startsWith(expandedDir) || normalizedPath.equals(expandedDir)) {
                return; // Path is within an allowed directory
            }
        }

        throw new DocumentAccessDeniedException(
                "Access denied: path is not under any allowed directory. "
                        + "Allowed directories: "
                        + dirs);
    }

    private String normalizeLocation(String location) {
        // Strip file:// scheme
        String path = location;
        if (path.startsWith("file://")) {
            path = path.substring(7);
        }

        // For HTTP URLs, extract the path component
        if (path.startsWith("http://") || path.startsWith("https://")) {
            try {
                URI uri = URI.create(path);
                return uri.getPath() != null ? uri.getPath() : "";
            } catch (Exception e) {
                return path;
            }
        }

        // Resolve to absolute path to catch traversal attacks
        try {
            return Path.of(path).normalize().toString();
        } catch (Exception e) {
            return path;
        }
    }

    private String expandHome(String path) {
        if (path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    private String extractFileName(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1);
        }
        return path;
    }

    private String extractHost(String location) {
        try {
            if (location.startsWith("http://") || location.startsWith("https://")) {
                URI uri = URI.create(location);
                return uri.getHost();
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}
