# Scheduler Nested Modules Restructure Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move all scheduler-* root modules into a nested `scheduler/` directory as Gradle sub-projects (`scheduler/core`, `scheduler/mysql-persistence`, etc.).

**Architecture:** Gradle supports nested multi-project builds via colon-separated paths (e.g. `scheduler:core`). The `scheduler/` directory becomes a container project with no build logic of its own. Sub-project names are set explicitly in `settings.gradle` to produce the same `conductor-scheduler-*` artifact IDs consumers already reference, with one exception: `conductor-scheduler` becomes `conductor-scheduler-core`.

**Tech Stack:** Gradle multi-project builds, Bash file moves

---

## File Map

| Action | Path |
|--------|------|
| Move (rename) | `scheduler/` → `scheduler/core/` |
| Move | `scheduler-mysql-persistence/` → `scheduler/mysql-persistence/` |
| Move | `scheduler-postgres-persistence/` → `scheduler/postgres-persistence/` |
| Move | `scheduler-redis-persistence/` → `scheduler/redis-persistence/` |
| Move | `scheduler-sqlite-persistence/` → `scheduler/sqlite-persistence/` |
| Modify | `settings.gradle` |
| Modify | `scheduler/mysql-persistence/build.gradle` |
| Modify | `scheduler/postgres-persistence/build.gradle` |
| Modify | `scheduler/redis-persistence/build.gradle` |
| Modify | `scheduler/sqlite-persistence/build.gradle` |

No Java source changes needed. `server/build.gradle` does not need changes — `conductor-scheduler-postgres-persistence` name is preserved.

---

## Chunk 1: Move Directories

### Task 1: Rename `scheduler/` → `scheduler/core/`

`scheduler/` already exists, so we need a two-step move.

**Files:**
- Move: `scheduler/` → `scheduler/core/`

- [ ] **Step 1: Move scheduler/ out of the way, recreate as parent, move back in**

```bash
mv scheduler scheduler-core-temp
mkdir scheduler
mv scheduler-core-temp scheduler/core
```

- [ ] **Step 2: Verify structure**

```bash
ls scheduler/core/
# Expected: build.gradle  src/  SCHEDULER_DAO_TEST_PLAN.md
```

---

### Task 2: Move persistence modules into `scheduler/`

**Files:**
- Move: `scheduler-mysql-persistence/` → `scheduler/mysql-persistence/`
- Move: `scheduler-postgres-persistence/` → `scheduler/postgres-persistence/`
- Move: `scheduler-redis-persistence/` → `scheduler/redis-persistence/`
- Move: `scheduler-sqlite-persistence/` → `scheduler/sqlite-persistence/`

- [ ] **Step 1: Move all four persistence directories**

```bash
mv scheduler-mysql-persistence scheduler/mysql-persistence
mv scheduler-postgres-persistence scheduler/postgres-persistence
mv scheduler-redis-persistence scheduler/redis-persistence
mv scheduler-sqlite-persistence scheduler/sqlite-persistence
```

- [ ] **Step 2: Verify structure**

```bash
ls scheduler/
# Expected: core  mysql-persistence  postgres-persistence  redis-persistence  sqlite-persistence
```

---

## Chunk 2: Update Gradle Configuration

### Task 3: Update `settings.gradle`

**Files:**
- Modify: `settings.gradle`

- [ ] **Step 1: Replace the 5 scheduler `include` lines**

Old (lines 69-73):
```gradle
include 'scheduler'
include 'scheduler-postgres-persistence'
include 'scheduler-mysql-persistence'
include 'scheduler-sqlite-persistence'
include 'scheduler-redis-persistence'
```

New:
```gradle
include 'scheduler:core'
include 'scheduler:postgres-persistence'
include 'scheduler:mysql-persistence'
include 'scheduler:sqlite-persistence'
include 'scheduler:redis-persistence'
```

- [ ] **Step 2: Replace the rename logic (last line)**

Old (line 75):
```gradle
rootProject.children.each {it.name="conductor-${it.name}"}
```

New:
```gradle
rootProject.children.findAll { it.name != 'scheduler' }.each { it.name = "conductor-${it.name}" }
rootProject.children.find { it.name == 'scheduler' }?.children?.each { it.name = "conductor-scheduler-${it.name}" }
```

This produces:
- `:scheduler:core` → `conductor-scheduler-core`
- `:scheduler:postgres-persistence` → `conductor-scheduler-postgres-persistence`
- `:scheduler:mysql-persistence` → `conductor-scheduler-mysql-persistence`
- `:scheduler:redis-persistence` → `conductor-scheduler-redis-persistence`
- `:scheduler:sqlite-persistence` → `conductor-scheduler-sqlite-persistence`

---

### Task 4: Update persistence `build.gradle` files

All four persistence modules depend on the old `conductor-scheduler` name (both `implementation` and `testFixtures`). Update to `conductor-scheduler-core`.

**Files:**
- Modify: `scheduler/mysql-persistence/build.gradle`
- Modify: `scheduler/postgres-persistence/build.gradle`
- Modify: `scheduler/redis-persistence/build.gradle`
- Modify: `scheduler/sqlite-persistence/build.gradle`

- [ ] **Step 1: Update each file — replace `conductor-scheduler` with `conductor-scheduler-core`**

In each of the four `build.gradle` files, make these replacements:

```gradle
// Before
implementation project(':conductor-scheduler')
// ...
testImplementation testFixtures(project(':conductor-scheduler'))

// After
implementation project(':conductor-scheduler-core')
// ...
testImplementation testFixtures(project(':conductor-scheduler-core'))
```

Note: `redis-persistence` has no `testFixtures` line — only the `implementation` line needs updating.

---

## Chunk 3: Verify and Commit

### Task 5: Verify the build

- [ ] **Step 1: Run a full Gradle build (compile + test for scheduler modules)**

```bash
./gradlew :conductor-scheduler-core:build \
          :conductor-scheduler-mysql-persistence:build \
          :conductor-scheduler-postgres-persistence:build \
          :conductor-scheduler-redis-persistence:build \
          :conductor-scheduler-sqlite-persistence:build \
          --continue
```

Expected: All tasks succeed (or only integration tests fail if Docker is unavailable — that's acceptable).

- [ ] **Step 2: Verify the root project still resolves**

```bash
./gradlew projects 2>&1 | grep -i scheduler
```

Expected output should show:
```
+--- Project ':scheduler'
     +--- Project ':scheduler:core' (conductor-scheduler-core)
     +--- Project ':scheduler:mysql-persistence' (conductor-scheduler-mysql-persistence)
     +--- Project ':scheduler:postgres-persistence' (conductor-scheduler-postgres-persistence)
     +--- Project ':scheduler:redis-persistence' (conductor-scheduler-redis-persistence)
     +--- Project ':scheduler:sqlite-persistence' (conductor-scheduler-sqlite-persistence)
```

- [ ] **Step 3: Verify server still resolves its scheduler dependency**

```bash
./gradlew :conductor-server:dependencies --configuration compileClasspath 2>&1 | grep scheduler
```

Expected: `conductor-scheduler-postgres-persistence` appears in the tree.

---

### Task 6: Commit

- [ ] **Step 1: Stage and commit**

```bash
git add settings.gradle \
        scheduler/ \
        scheduler/core/ \
        scheduler/mysql-persistence/ \
        scheduler/postgres-persistence/ \
        scheduler/redis-persistence/ \
        scheduler/sqlite-persistence/
git status  # confirm no leftover scheduler-*-persistence at root
git commit -m "refactor: move scheduler modules into scheduler/ nested sub-project layout"
```
