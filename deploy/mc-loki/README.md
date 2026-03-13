# Conductor Master Controller — loki.local

Runs on port **8888**. Uses local Postgres (`conductor_mc` database).
Dogfoods `conductor-scheduler-postgres-persistence`.

## First-time setup

```bash
# Create DB (one-time)
sudo -u postgres psql <<'SQL'
CREATE DATABASE conductor_mc;
CREATE USER conductor WITH PASSWORD 'conductor';
GRANT ALL PRIVILEGES ON DATABASE conductor_mc TO conductor;
ALTER DATABASE conductor_mc OWNER TO conductor;
SQL

# Deploy
sudo mkdir -p /opt/conductor-mc
sudo cp application.properties /opt/conductor-mc/
sudo cp conductor-mc.service /etc/systemd/system/
# copy built JAR to /opt/conductor-mc/conductor-server-boot.jar

sudo systemctl daemon-reload
sudo systemctl enable conductor-mc
sudo systemctl start conductor-mc
```

## Build the JAR (from scheduler/postgres-persistence branch)

```bash
cd ~/projects/git/conductor-oss/conductor
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./gradlew :conductor-server:bootJar -x test
cp server/build/libs/conductor-server-*-boot.jar /opt/conductor-mc/conductor-server-boot.jar
```

## Check status

```bash
sudo systemctl status conductor-mc
curl http://localhost:8888/actuator/health
```
