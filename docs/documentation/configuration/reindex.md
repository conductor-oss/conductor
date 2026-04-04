# ES 索引重建（Reindex）

当 Elasticsearch 数据丢失但主数据库（Postgres / MySQL）完好时，可通过此接口将所有 workflow 和 task 重新写入 ES 索引。

## 适用场景

| 索引后端 | 是否有意义 |
|---|---|
| `elasticsearch` / `opensearch2` / `opensearch3` | ✅ 最常见场景，ES 是独立服务，容易丢数据 |
| `postgres` | ⚠️ 意义不大，索引和数据同库，通常一起丢或一起在 |
| 已禁用（`indexing.enabled=false`）| ❌ 空操作 |

---

## 接口

### 启动重建

```
POST /api/admin/reindex
```

**立即返回**，任务在后台单线程执行。

响应示例：
```json
{
  "state": "STARTED",
  "message": "Reindex job started. Use GET /api/admin/reindex/status to track progress."
}
```

若已有任务正在执行：
```json
{
  "state": "ALREADY_RUNNING",
  "message": "A reindex job is already in progress"
}
```

---

### 查询进度

```
GET /api/admin/reindex/status
```

响应示例：
```json
{
  "state":     "RUNNING",
  "processed": 350,
  "errors":    0,
  "total":     1240,
  "message":   "Indexing 350 / 1240"
}
```

**`state` 取值说明：**

| state | 含义 |
|---|---|
| `IDLE` | 服务启动后从未执行过 reindex |
| `RUNNING` | 正在后台执行 |
| `COMPLETED` | 全部完成 |
| `FAILED` | 发生未捕获异常，任务中止 |
| `ALREADY_RUNNING` | 仅出现在 POST 响应中，表示重复提交被忽略 |

完成后响应示例：
```json
{
  "state":     "COMPLETED",
  "processed": 1240,
  "errors":    2,
  "total":     1240,
  "message":   "Completed. processed=1240, errors=2"
}
```

---

## 操作流程

```bash
# 1. 启动重建
curl -X POST http://localhost:8081/api/admin/reindex

# 2. 轮询进度（每隔几秒查一次）
watch -n 5 'curl -s http://localhost:8081/api/admin/reindex/status | jq .'

# 3. 验证 ES 索引数据量
curl http://localhost:9200/conductor_workflow/_count
curl http://localhost:9200/conductor_task/_count

# 4. 验证搜索接口是否恢复
curl "http://localhost:8081/api/workflow/search?query=*"
```

---

## 注意事项

- **幂等**：ES 以 `workflow_id` 作为文档 ID，重复执行会覆盖，不会产生重复数据。
- **可重试**：`COMPLETED` / `FAILED` 状态后再次 `POST /reindex` 会自动重置并重新开始。
- **不影响正在运行的 workflow**：reindex 只是读 DB 写 ES，不修改任何业务数据。
- **errors 不为 0**：检查 conductor-server 日志，搜索 `Failed to reindex workflow` 定位具体原因。

---

## 实现说明

- 后台使用单线程 `ExecutorService`（daemon 线程），不阻塞 HTTP 请求。
- 进度计数使用 `AtomicInteger`，状态使用 `AtomicReference<ReindexState>`，线程安全。
- 每批次 100 条，每批完成后输出一次 INFO 日志。
- 涉及文件：
  - `core/.../service/AdminService.java` — 接口定义
  - `core/.../service/AdminServiceImpl.java` — 异步实现
  - `rest/.../controllers/AdminResource.java` — HTTP 端点
  - `postgres-persistence/.../dao/PostgresExecutionDAO.java` — `getAllWorkflowIds()` 分页查询
