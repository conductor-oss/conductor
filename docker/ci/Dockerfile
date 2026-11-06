FROM debian:trixie-slim AS jdk

# Security: require patched JVM and OS trust stores for CVE-2026-114820.
ARG TEMURIN_VERSION=25.0.5.0.0+8-0
ARG CA_CERTIFICATES_MIN_VERSION=20250419+deb13u1

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && dpkg --compare-versions "$(dpkg-query -W -f='${Version}' ca-certificates)" ge "$CA_CERTIFICATES_MIN_VERSION" \
    && install -d -m 0755 /etc/apt/keyrings \
    && curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public -o /etc/apt/keyrings/adoptium.asc \
    && echo "deb [signed-by=/etc/apt/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb trixie main" > /etc/apt/sources.list.d/adoptium.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends "temurin-25-jdk=${TEMURIN_VERSION}" \
    && rm -rf /var/lib/apt/lists/*

FROM jdk AS build

WORKDIR /workspace/conductor
COPY . /workspace/conductor

RUN ./gradlew clean build
