# Message API 模组使用文档

**版本**: 1.0.1

**适配**: Minecraft 1.21.1 Fabric

**作者**: Kuinone

---

## 1. 概述

Message API 是一个 Fabric 服务端模组，它通过内嵌 HTTP 服务器提供 RESTful API，允许外部程序向游戏内玩家发送消息、广播标题、执行指令，以及获取服务器运行状态。

**主要功能**：

- 向全体/指定玩家发送聊天消息（支持颜色代码）
- 向全体/指定玩家发送标题（Title）与副标题
- 在服务器控制台执行任意命令
- 获取当前在线玩家、TPS、内存使用、运行时长等信息

**安全机制**：所有请求均需在 HTTP 头中携带 `X-API-Key` 进行身份验证。

---

## 2. 安装与部署

### 2.1 前置要求

- Minecraft 1.21.1 **Fabric** 服务端（已安装 Fabric Loader）
- Java 17 或更高版本

### 2.2 安装步骤

1. 下载模组 JAR 文件（如 `messageapi-1.0.0.jar`）。
2. 将 JAR 文件放入服务端的 `mods/` 文件夹中。
3. 启动服务端，模组会自动加载。首次运行会在 `config/` 目录下生成配置文件 `apiserver.json`。
4. 根据需要修改配置文件（见第 3 节），然后重启服务端使配置生效。

启动成功后，服务端日志中会显示类似信息：

```
[Message API] HTTP server started on port 7789
```

---

## 3. 配置文件

配置文件路径：`config/messageapi.json`（如果文件不存在，请手动创建）。

```json
{
  "apiKey": "your_secure_api_key_here"
}
```

| 字段     | 类型    | 描述                                                              |
| -------- | ------- | ----------------------------------------------------------------- |
| `apiKey` | String  | **必填**。用于验证请求的 API 密钥，所有请求头中必须包含相同的值。 |
| `port`   | Integer | HTTP 服务监听端口，默认 `7789`。                                  |

修改配置后**必须重启服务端**才能生效。

---

## 4. API 端点

**基础 URL**: `http://<服务器IP>:<port>`  
**请求头要求**：

- `X-API-Key`: 必须与配置文件中的 `apiKey` 一致。
- `Content-Type`: 对于 POST 请求，必须为 `application/json`。

---

### 4.1 GET `/info` — 获取服务器状态

获取当前服务器运行状态，包括在线玩家、TPS、内存、运行时长等。

**请求示例**：

```bash
curl -H "X-API-Key: your_key" http://localhost:7789/info
```

**响应示例**（成功）：

```json
{
  "success": true,
  "online": 3,
  "maxPlayers": 20,
  "playerList": ["Redstix", "Soloer", "ASXZ"],
  "tps": 19.98,
  "mspt": 50.05,
  "uptime": 11145141919,
  "memory": {
    "maxBytes": 1073741824,
    "totalBytes": 536870912,
    "freeBytes": 268435456,
    "usedBytes": 268435456
  },
  "systemLoad": 0.25,
  "cpuUsage": 0.12
}
```

**字段说明**：

| 字段                | 类型    | 描述                                    |
| ------------------- | ------- | --------------------------------------- |
| `success`           | boolean | 始终为 `true`（表示请求成功）           |
| `online`            | int     | 当前在线玩家数                          |
| `maxPlayers`        | int     | 服务器最大玩家数                        |
| `playerList`        | array   | 在线玩家名称列表                        |
| `tps`               | double  | 当前 TPS（每秒 Tick 数，上限 20）       |
| `mspt`              | double  | 平均每 Tick 耗时（毫秒）                |
| `uptime`            | long    | 服务器已运行毫秒数                      |
| `memory.maxBytes`   | long    | JVM 最大可用内存（字节）                |
| `memory.totalBytes` | long    | 当前总内存（字节）                      |
| `memory.freeBytes`  | long    | 空闲内存（字节）                        |
| `memory.usedBytes`  | long    | 已用内存（字节）                        |
| `systemLoad`        | double  | 系统负载平均值（可能为 -1，取决于环境） |
| `cpuUsage`          | double  | 进程 CPU 使用率（可能为 -1）            |

---

### 4.2 POST `/send/all` — 向全体玩家发送消息

**请求体**：

```json
{
  "message": "&aHello everyone!"
}
```

| 参数      | 类型   | 必填 | 描述                                          |
| --------- | ------ | ---- | --------------------------------------------- |
| `message` | String | 是   | 消息内容，支持 `&` 颜色代码（如 `&a` 为绿色） |

**响应示例**：

```json
{
  "success": true,
  "message": "Message sent to all players"
}
```

---

### 4.3 POST `/send/user` — 向指定玩家发送消息

**请求体**：

```json
{
  "message": "&bHello Redstix!",
  "player": "Redstix"
}
```

| 参数      | 类型   | 必填 | 描述                             |
| --------- | ------ | ---- | -------------------------------- |
| `message` | String | 是   | 消息内容，支持颜色代码           |
| `player`  | String | 是   | 目标玩家的精确名称（区分大小写） |

**响应示例**：

```json
{
  "success": true,
  "message": "Message sent to player Redstix"
}
```

若玩家不在线，返回：

```json
{
  "success": false,
  "message": "Player not found"
}
```

---

### 4.4 POST `/command` — 执行服务器命令

在服务器控制台执行任意命令（以服务端权限运行）。

**请求体**：

```json
{
  "command": "give Redstix redstone_repeator 1"
}
```

| 参数      | 类型   | 必填 | 描述                         |
| --------- | ------ | ---- | ---------------------------- |
| `command` | String | 是   | 要执行的命令（不含前导 `/`） |

**响应示例**：

```json
{
  "success": true,
  "message": "Command executed"
}
```

> ⚠️ **注意**：此端点具有控制台权限，请谨慎使用，并确保 API Key 的保密性。

---

## 5. 错误码与响应格式

所有响应均为 JSON 格式，HTTP 状态码含义如下：

| 状态码 | 含义                                |
| ------ | ----------------------------------- |
| 200    | 请求成功（包含业务 `success` 字段） |
| 400    | 请求参数错误（缺少字段或格式错误）  |
| 401    | API Key 无效或缺失                  |
| 404    | 请求路径不存在                      |
| 405    | 使用了不支持的 HTTP 方法            |
| 500    | 服务器内部错误（请查看服务端日志）  |

对于非 200 状态，响应体通常为：

```json
{
  "success": false,
  "message": "错误描述"
}
```

---

## 6. 注意事项

- **颜色代码**：所有消息字段（`message`, `title`, `subtitle`）均支持 Minecraft 颜色代码，使用 `&` 符号替代 `§`。例如 `&c` 表示红色，`&l` 表示粗体，`&r` 重置样式。具体颜色表请参考 Minecraft 格式化代码。
- **玩家名称**：必须精确匹配玩家名称（大小写敏感）。若包含空格或特殊字符，请确保 JSON 中正确转义。
- **服务端重启**：配置文件仅在启动时加载一次，修改后必须重启服务端。

---

## 7. 常见问题

**Q：启动后日志显示 `Failed to start HTTP server`？**  
A：检查端口是否被占用，或配置文件中的 `port` 是否正确。

**Q：请求返回 401 但 API Key 明明正确？**  
A：确认请求头名称为 `X-API-Key`（注意大小写）。同时检查配置文件中的 Key 是否包含多余空格或换行。

---

**祝使用愉快！**
