# GreenPaw 🐾🌿

> **An agent that tends, not just chats.**

Agent 驱动的宠物与植物护理操作系统：AI 不只是回答问题，而是能**理解对象、读取历史、调用工具、执行任务、留下记录并持续跟进**。

上传一张叶片照片，它会诊断病害并存入历史；说"明早 8 点提醒我喂药"，它会先弹出确认卡、你确认后才真正创建提醒，并经 WebPush 到点推送、完成后自动写入护理记录、生成下期重复提醒。

## 核心特性

- **统一 Agent Runtime** —— 4 条历史 AI 链路（护理问答 / 工具对话 / 图像识别 / 病害诊断）收敛为单一编排入口 `POST /api/agent/messages`，意图路由全权交给 LLM，废除关键词路由
- **Tool Broker 工具中间件** —— 25 个 `@Tool` 工具经反射注册、JSON Schema 自动生成（对 LLM 隐藏鉴权上下文参数），统一收口限流（滑动窗口 30 次/分/用户）、审计落库与参数脱敏
- **写操作确认卡** —— 9 类写操作（建提醒、存诊断、写护理记录等）不直接执行，转为 10 分钟有效期的确认卡；状态机 + 原子 claim 防并发双击双执行，杜绝 LLM 误写数据库
- **SSE 流式对话** —— 工具循环以 StreamListener 贯通 thinking / tools / replying 阶段事件与打字机增量；SSE 解析器逐行重组分片 tool_calls，流式与非流式产出同构响应，同步端点保留为回滚路径
- **轻量 RAG 记忆** —— MySQL（业务）+ SQLite（向量）双库；内存索引 + 余弦相似度 Top-5；检索按 用户/会话/来源 三级归属过滤，杜绝跨用户记忆泄漏；系统提示词动态注入护理档案、近期护理事件与检索记忆
- **多模态与生态** —— 图像理解 / 病害诊断 / 图像生成编辑（DeepSeek-V4 + 千问图像），集成高德地图（附近宠物医院 + 导航）、百度搜索、心知天气、WebPush 推送

## 架构

```
浏览器 (agent.html 工作台: 会话历史 / 工具轨迹 / 确认卡 / SSE 打字机)
   │
   ▼
AgentRuntimeController ── POST /api/agent/messages[/stream]
   ▼
AgentRuntimeService ── 身份 / 护理对象 / Artifact / 记忆检索 → 动态 system prompt
   ▼
ToolCallingService ── 工具循环（≤5 轮，并发执行，副作用不重放）
   ▼
ToolBroker ── 读写分级 · 确认卡 · 限流 · 审计 · 脱敏
   ├── 读工具: getWeather / webSearch / analyzeImage / diagnoseDisease /
   │          queryPetCare / queryPlantSafety / searchNearbyService ...
   └── 写工具: createCareReminder / saveDiagnosis / saveCareRecord ...
                （一律先出确认卡，用户确认后经 broker 执行并落 CareEvent）
```

存储：`agent_conversation` / `agent_message` / `agent_tool_trace` / `artifact` / `care_subject` / `care_event` / `action_confirmation`（MySQL）+ 向量（SQLite）。

## 技术栈

Java 21 · Spring Boot 3.5 · Spring AI · DeepSeek-V4（OpenAI 兼容）· 通义千问图像 · MySQL + SQLite · JPA · OkHttp（SSE）· WebPush（VAPID）· 原生 JS

## 快速开始

```bash
# 1. 准备 MySQL
mysql -uroot -e "CREATE DATABASE IF NOT EXISTS greenpaw"

# 2. 配置密钥（gitignore 的本地文件，或用环境变量）
cat > application-local.properties <<'EOF'
DASHSCOPE_API_KEY=sk-...
DASHSCOPE_EMBEDDING_API_KEY=sk-...
AMAP_API_KEY=...
SENIVERSE_API_KEY=...
BAIDU_SEARCH_API_KEY=...
WEBPUSH_VAPID_PRIVATE_KEY=...
MYSQL_PASSWORD=...
EOF

# 3. 启动
./mvnw spring-boot:run
```

打开 http://localhost:8080/agent 注册后即可对话（测试方案见 `docs/TESTING.md`）。

## 测试

16 个测试类 / 53 个用例，覆盖：LLM 工具路由黄金用例（7 条）、上下文注入、图片分析路由、病害诊断、并发确认原子性、滑动窗口限流、重试副作用安全、SSE 分片聚合、向量归属过滤与孤儿清理。

```bash
./mvnw test
```

## 文档

- [`docs/AGENT_RUNTIME.md`](docs/AGENT_RUNTIME.md) —— 运行时重构计划与工具策略
- [`docs/REFACTOR_NEXT.md`](docs/REFACTOR_NEXT.md) —— 后续路线图
- [`docs/TESTING.md`](docs/TESTING.md) —— 手工验收测试方案
