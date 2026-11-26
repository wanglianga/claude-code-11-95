# 渔业许可与卸港预约服务（fishport）

面向渔业管理部门的服务端应用：出海前许可校验、航次状态机、物种额度账、卸港预约双模式调度，
并附**模拟船舶终端、港口地磅、监管回执**。船主、港口调度员、执法人员、卸货员从同一服务确认当前责任与下一步操作。

- 技术栈：Java 25 · Spring Boot 3.5 · Spring Data JPA · PostgreSQL 18 · Flyway · springdoc-openapi（Swagger UI）
- 项目类型：后端（无前端页面），提供 OpenAPI 3.x 文档与 Swagger UI

## 一、快速开始

### 方式 A：Docker Compose 一键启动

```bash
docker compose up --build
# 应用就绪后：
#   Swagger UI  http://localhost:8080/swagger-ui.html
#   OpenAPI     http://localhost:8080/v3/api-docs
```

Compose 会启动 PostgreSQL 18（库/账号/密码均为 `fishport`）并等待健康检查通过后启动应用；
建表与种子数据由 Flyway 在应用启动时自动完成。

### 方式 B：本地开发

前置：JDK 25、Maven 3.9、本地 PostgreSQL（默认连接 `127.0.0.1:5432`，库/用户/密码 `ccdev`，可用环境变量覆盖）。

```bash
# 1. 启动应用（默认连 jdbc:postgresql://127.0.0.1:5432/ccdev）
mvn spring-boot:run
# 或打包后运行
mvn package -DskipTests
java -jar target/fishport-1.0.0.jar

# 2. 自定义数据库连接
DB_URL=jdbc:postgresql://127.0.0.1:5432/ccdev DB_USER=ccdev DB_PASSWORD=ccdev \
  java -jar target/fishport-1.0.0.jar
```

### 运行测试

```bash
# 集成测试连接 127.0.0.1:5432 的 ccdev_test 库（首次需创建：CREATE DATABASE ccdev_test;）
mvn test
```

## 二、测试账号（HTTP Basic）

| 账号 | 密码 | 角色 | 职责 |
| --- | --- | --- | --- |
| `owner` | `owner123` | 船主 OWNER | 提交许可申请、离港/返港、结案、登记争议 |
| `dispatcher` | `dispatcher123` | 港口调度员 DISPATCHER | 预约登记、排队计算、到港打卡、改期通知 |
| `officer` | `officer123` | 执法人员 ENFORCEMENT | 办结争议（其备注才记为执法事实） |
| `unloader` | `unloader123` | 卸货员 UNLOADER | 分物种卸货申报、地磅票据、签收 |
| `service` | `service123` | 客服 SERVICE | 备注（**不能**当成执法事实） |
| `admin` | `admin123` | 管理部门 ADMIN | 全部权限 |

模拟端 `/api/sim/**`（船舶终端/地磅/监管回执）与 Swagger 文档免登录。

## 三、核心业务规则

### 1. 许可校验（`POST /api/permit-applications`，提交即同步校验）

船主提交船号、船长与船员、计划海区、作业时间、渔具、目标物种及申报捕捞上限，**同时**校验：

1. **有效期**：计划作业窗口合法且未过期（许可有效期 = 计划窗口）；
2. **海区**：海区存在且开放；
3. **季节**：计划窗口不得落入该海区该物种禁渔期（支持跨年禁渔区间）；
4. **渔具限制**：渔具须在该海区登记且未被禁用；
5. **物种额度**：按 `海区+物种+年度` 单账核对 `可用 = 总额 − 已保留 − 已使用`，
   **不同海区或不同物种的剩余额度不得相加**（例如 A1 鳕鱼 1 万 kg、A2 鳕鱼 5 千 kg，申请 A1 鳕鱼 1.2 万 kg 必驳回）。

另：**同一船存在未结航次时不得再获第二张出海许可**（数据库部分唯一索引兜底）。
全部通过则发证、建立航次（`APPROVED`）、按物种 **RESERVE 保留**本航次申报上限，并生成监管回执。

### 2. 航次状态机

`APPROVED → DEPARTED → RETURNED → UNLOADING → (REWEIGHING) → CLOSED`

- 离港/返港按航次幂等：**重复到港、重复离港只返回原处理结果**（`duplicate=true`）；
- 地磅票据按 `ticket_no` 幂等：**重传票据返回原票据**；
- 签收按卸货记录幂等：重复签收返回原结果，**不重复扣减额度**；
- 争议中的记录触发复称（`REWEIGHING`），复称新票核定重量后自动办结重量争议。

### 3. 额度账（只追加流水，结余可解释）

- 批复时 `RESERVE` 保留申报上限；
- 签收实称后 `DEDUCT` 按物种扣减、`RELEASE` 释放未捕部分（样例：预留 300kg 鳕鱼实签 280kg → 扣 280、释放 20）；
- 零渔获物种在结案时全额释放；
- 每条流水记录 `保留/已用/可用` 事后值，回放接口逐条重放校验自洽（`ledgerConsistent=true`）。

### 4. 争议阻断结案

- **重量争议 / 混装来源不清 / 许可证过期**（`OPEN` 状态）阻断结案；许可证过期会在结案尝试时自动登记争议（独立事务，不因结案失败回滚）；
- 争议只能由**执法人员**办结；**客服备注不能当成执法事实**（`enforcement_fact` 仅对执法角色为真）；
- 许可过期争议经执法办结（认定渔获合法性）后才放行结案。

### 5. 卸港预约双模式（一条卸货线 + 一台地磅 + 有限冷库，作业间清场 5 分钟）

- **FIXED 固定时段**：按已通知时段分配，船主容易安排船员和冷链；迟到会占住空档（空档不给别人），
  迟到到无法按期完成则**改期队尾**并重新通知；
- **ROLLING 滚动叫号**：按实际到港（未到港按计划到港）先后滚动排序，吞吐更灵活，
  但会**改变已公布的等待顺序并重新通知**（`QUEUE_CHANGED` 通知）；
- 下一作业起点 = `max(可开始时间, 上一作业结束 + 清场 5 分钟)`；逐船校验最晚完成时间并标记预警；
- 预约登记校验**冷库容量**：同时段在港卸货总量不得超过冷库容量。

样例（06:00-08:00，A 原约 06:30-07:10/最晚 07:45/卸货 40 分钟/改 06:25 到港，B 06:20 到港/20 分钟/最晚 07:35）：

| 模式 | A 船 | B 船 |
| --- | --- | --- |
| FIXED | 06:30-07:10（提早到港仍等时段） | 07:15-07:35（A 结束 + 清场 5 分钟） |
| ROLLING | 06:45-07:25（B 结束 + 清场 5 分钟） | 06:20-06:40（先到先卸） |

## 四、API 概览

| 模块 | 端点 |
| --- | --- |
| 许可 | `POST /api/permit-applications`、`GET /api/permit-applications/{no}`、`GET /api/permits/{no}`、`GET /api/permits/{no}/replay` |
| 航次 | `POST /api/voyages/{no}/depart|arrive|close`、`GET /api/voyages/{no}`、`GET /api/voyages/{no}/records|disputes|notes`、`POST /api/voyages/{no}/disputes|notes` |
| 卸货称重 | `POST /api/voyages/{no}/unload-records`、`POST /api/weigh-tickets`、`POST /api/unload-records/{id}/sign` |
| 预约 | `POST /api/appointments`、`POST /api/appointments/compute`、`POST /api/appointments/{no}/checkin|finish`、`GET /api/appointments?portCode&date`、`GET /api/voyages/{id}/notifications` |
| 争议 | `POST /api/disputes/{id}/resolve`、`GET /api/disputes?status=OPEN` |
| 额度账 | `GET /api/quota-accounts`、`GET /api/quota-accounts/{id}/ledger` |
| 工作台 | `GET /api/workbench/tasks`（按登录角色返回责任清单与下一步操作） |
| 模拟端 | `POST /api/sim/vessel-terminal/events`、`POST /api/sim/weighbridge/tickets`、`GET /api/sim/regulator/receipts`、`POST /api/sim/regulator/receipts/{no}/ack` |
| 参考数据 | `GET /api/reference/areas|species|vessels|ports` |

种子数据：海区 A1/A2；物种 COD 鳕鱼 / FLOUNDER 比目鱼 / HAIRTAIL 带鱼；A1 鳕鱼与比目鱼 05-01~08-31 禁渔、
A1 禁用拖网 TRAWL；2026 年度额度账 5 个；船只 V001/V002/V003；港口 PORT1（06:00-20:00，清场 5 分钟，冷库 5000kg）。

## 五、验证方式（本题）

### 1. 自动化集成测试（已执行：`mvn test`，20 个测试全部通过）

| 测试类 | 覆盖点 |
| --- | --- |
| `PermitValidationTest`（7） | 正常批复并保留额度；跨海区额度不合并（A1 申请 1.2 万 kg 驳回）；跨物种不合并；A1 禁用拖网；禁渔期驳回；有效期驳回；未结航次不得第二张许可 |
| `VoyageLifecycleTest`（3） | A 船 300→280、B 船 100→90 全流程；重复到港/重传票据/重复签收幂等；重称差异 282→280 复称结案；零渔获结案全额释放；历史回放流水自洽 |
| `ScheduleTest`（4） | 固定模式 A 06:30-07:10、B 07:15-07:35（清场占用）；滚动模式 B 06:20-06:40、A 06:45-07:25；迟到 07:30 改期队尾并通知；滚动重排改变已公布顺序并重新通知 |
| `DisputeClosureTest`（3） | 混装争议阻断结案、客服备注不算执法事实、执法办结后放行；多物种零渔获释放；许可过期自动登记争议并阻断、办结后结案 |
| `SmokeTest`（3） | 上下文与 OpenAPI 文档；未认证 401；种子额度账 |

### 2. 手工冒烟（已执行：本地 `java -jar` 启动后 curl 全流程）

```bash
# 申请许可（owner）
curl -u owner:owner123 -X POST http://localhost:8080/api/permit-applications \
  -H 'Content-Type: application/json' -d '{
    "vesselNo":"V001","captainName":"张三","crewCount":8,"crewList":"乙,丙",
    "areaCode":"A1","plannedStart":"<明天>T06:00:00","plannedEnd":"<明天>T12:00:00",
    "gearType":"GILLNET","items":[{"speciesCode":"COD","declaredLimitKg":300}]}'

# 模拟船舶终端离港/返港（重复提交验证幂等）
curl -X POST http://localhost:8080/api/sim/vessel-terminal/events \
  -H 'Content-Type: application/json' -d '{"voyageNo":"<航次号>","eventType":"DEPART","eventId":"E1"}'
curl -X POST http://localhost:8080/api/sim/vessel-terminal/events \
  -H 'Content-Type: application/json' -d '{"voyageNo":"<航次号>","eventType":"ARRIVE","eventId":"E2"}'

# 预约与排队（dispatcher）：FIXED 或 ROLLING
curl -u dispatcher:dispatcher123 -X POST http://localhost:8080/api/appointments \
  -H 'Content-Type: application/json' -d '{"voyageNo":"<航次号>","portCode":"PORT1","mode":"FIXED",
    "requestedStart":"<明天>T06:30:00","durationMin":40,"latestFinish":"<明天>T07:45:00","expectedUnloadKg":300}'
curl -u dispatcher:dispatcher123 -X POST http://localhost:8080/api/appointments/compute \
  -H 'Content-Type: application/json' -d '{"portCode":"PORT1","date":"<明天>","mode":"FIXED"}'

# 卸货申报（unloader）→ 模拟地磅票据（重传验证幂等）→ 签收
curl -u unloader:unloader123 -X POST http://localhost:8080/api/voyages/<航次号>/unload-records \
  -H 'Content-Type: application/json' -d '{"speciesCode":"COD","declaredKg":280}'
curl -X POST http://localhost:8080/api/sim/weighbridge/tickets \
  -H 'Content-Type: application/json' -d '{"ticketNo":"T-1","voyageNo":"<航次号>","speciesCode":"COD","grossKg":290,"tareKg":10}'
curl -u unloader:unloader123 -X POST http://localhost:8080/api/unload-records/<记录ID>/sign

# 额度账 / 结案 / 历史回放 / 监管回执确认
curl -u owner:owner123 http://localhost:8080/api/quota-accounts
curl -u owner:owner123 -X POST http://localhost:8080/api/voyages/<航次号>/close
curl -u owner:owner123 http://localhost:8080/api/permits/<许可号>/replay
curl -X POST http://localhost:8080/api/sim/regulator/receipts/<回执号>/ack
```

实际执行结论（2026-11-26，容器内）：许可批复 → 终端离港/返港（重复返港 `duplicate=true`）→
固定模式排定 A 06:30-07:10、B 07:15-07:35 → 地磅净重 280（重传幂等）→ 签收（重复签收幂等）→
A1/COD 已用 280、保留 0（释放 20）→ 结案 → 回放 11 个事件、`ledgerConsistent=true` → 监管回执可确认。

> 说明：本容器无 Docker 守护进程，`docker compose up --build` 未在本环境实际执行；
> Dockerfile 与 Compose 配置已静态核对，应用本体以上述 `mvn test`（20/20 通过）与本地 jar 运行冒烟验证。

## 六、目录结构

```
├── Dockerfile / docker-compose.yml / .dockerignore
├── pom.xml
└── src
    ├── main/java/com/fishport
    │   ├── config/    安全（Basic+角色）、OpenAPI、全局异常
    │   ├── domain/    21 张表实体与枚举（许可/航次/额度账/预约/争议/回执…）
    │   ├── repo/      Spring Data JPA 仓库（额度账悲观锁、航次行锁）
    │   ├── service/   许可校验、航次状态机、额度账、双模式调度、回放、工作台
    │   └── web/       REST 控制器（含 /api/sim 模拟端）
    ├── main/resources
    │   ├── application.yml
    │   └── db/migration/  V1__init.sql（建表）+ V2__seed.sql（种子数据）
    └── test/java/com/fishport/  20 个集成测试（连 ccdev_test 库）
```
