/*
 * Copyright 2025 Conductor Authors.
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
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.Set;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/** Offline smoke check for the JVM and trust store shipped in both server images. */
public class RuntimeTrustStoreCheck {

    private static final Runtime.Version MIN_JAVA_VERSION = Runtime.Version.parse("25.0.5+8");

    // Fingerprints, not aliases: OS and JVM vendors use different names for the same roots.
    private static final Set<String> AFFECTED_ROOTS =
            Set.of(
                    "86A1ECBA089C4A8D3BBE2734C612BA341D813E043CF9E8A862CD5C57A36BBE6B",
                    "D7A7A0FB5D7E2731D771E9484EBCDEF71D5F0C3E0A2948782BC83EE0EA699EF4");

    public static void main(String[] args) throws GeneralSecurityException {
        Runtime.Version version = Runtime.version();
        if (version.feature() != MIN_JAVA_VERSION.feature()
                || version.compareToIgnoreOptional(MIN_JAVA_VERSION) < 0) {
            throw new IllegalStateException(
                    "Expected Java "
                            + MIN_JAVA_VERSION
                            + " or newer in the same LTS release, got "
                            + version);
        }

        TrustManagerFactory factory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        int trustedRoots = 0;
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager x509) {
                for (X509Certificate certificate : x509.getAcceptedIssuers()) {
                    String fingerprint =
                            HexFormat.of()
                                    .withUpperCase()
                                    .formatHex(digest.digest(certificate.getEncoded()));
                    if (AFFECTED_ROOTS.contains(fingerprint)) {
                        throw new IllegalStateException(
                                "JVM still trusts an affected root: "
                                        + certificate.getSubjectX500Principal());
                    }
                    trustedRoots++;
                }
            }
        }
        if (trustedRoots == 0) {
            throw new IllegalStateException("JVM default trust store is empty");
        }
        System.out.printf(
                "Java %s: %d trusted roots, no affected eMudhra/AAA roots%n",
                Runtime.version(), trustedRoots);
    }
}
