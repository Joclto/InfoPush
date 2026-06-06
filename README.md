<div align="center">

# InfoPush

自建消息推送服务，通过 HTTP 接口推送，WebSocket 实时送达多端客户端。

![FastAPI](https://img.shields.io/badge/FastAPI-0.115-009688?logo=fastapi)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin)
![Tauri](https://img.shields.io/badge/Tauri-v2-blue?logo=tauri)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql)
![License](https://img.shields.io/badge/License-MIT-green)

</div>

---

## 项目结构

```
InfoPush/
├── server/          # 后端服务（FastAPI + PostgreSQL）
├── app/             # Android 客户端（Kotlin + Jetpack Compose）
└── desktop/         # Windows 桌面客户端（Tauri v2 + Rust）
```

## 功能特性

- 用户注册 / 登录，JWT 鉴权
- 每个用户拥有唯一 **Push Token**，支持重置
- 支持 POST / GET 两种方式推送消息
- 消息类型：`text` / `markdown` / `html`
- WebSocket 实时推送，含心跳保活
- 消息持久化存储，支持分页查询与已读标记
- Docker Compose 一键部署（FastAPI + PostgreSQL + Nginx）

## 快速开始

### 后端部署（Docker Compose）

```bash
cd server

# 复制环境变量配置
cp .env.example .env
# 编辑 .env，至少修改 SECRET_KEY

# 启动服务
docker compose up -d
```

服务默认监听 `http://localhost:8000`，API 文档见 `http://localhost:8000/docs`。

### 本地开发（不使用 Docker）

```bash
cd server

# 安装依赖
python -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt

# 配置环境变量
cp .env.example .env

# 启动服务
python -m app.main
```

### Android 客户端

使用 Android Studio 打开 `app/` 目录，修改服务器地址后编译安装即可。

### Windows 桌面客户端

```bash
cd desktop

# 安装前端依赖
pnpm install

# 开发模式
pnpm tauri dev

# 构建安装包
pnpm tauri build
```

需要预先安装：[Rust](https://rustup.rs/)、Node.js 20+、pnpm、Visual Studio Build Tools（C++ 桌面开发工作负载）。

桌面客户端功能：
- 登录 / 注册，支持服务器地址记忆
- 消息列表实时更新，已读 / 未读区分
- 消息详情（text / markdown / html 渲染，XSS 防护）
- 标记已读 / 全部已读
- 试一试：三种模板（文本 / Markdown / 链接）自定义推送
- 系统托盘常驻，关闭最小化到托盘
- Windows Toast 通知
- 自动登录，退出登录
- Push Token 一键复制

## 推送 API

### 注册 / 登录

```http
POST /api/auth/register
Content-Type: application/json

{"username": "alice", "password": "123456"}
```

登录成功返回 `access_token` 和 `push_token`，`push_token` 用于推送消息。

### 发送推送（POST）

```http
POST /push/{push_token}
Content-Type: application/json

{
  "title": "标题",
  "content": "消息内容",
  "content_type": "text",
  "url": "https://example.com"
}
```

### 发送推送（GET，适合 curl / 浏览器）

```
GET /push/{push_token}?title=标题&content=消息内容
```

### 推送响应示例

```json
{
  "code": 200,
  "msg": "推送成功",
  "message_id": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "online_devices": 1
}
```

> 若设备离线，消息仍会持久化存储，客户端上线后可主动拉取。

## 消息 API

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/messages` | 分页获取消息列表 |
| `GET` | `/api/messages/{id}` | 获取单条消息 |
| `PUT` | `/api/messages/{id}/read` | 标记已读 |
| `PUT` | `/api/messages/read-all` | 全部标记已读 |

所有消息接口需在 Header 中携带 `Authorization: Bearer {access_token}`。

## WebSocket

客户端通过 WebSocket 订阅实时消息：

```
ws://your-server/ws/{push_token}
```

服务端每 30 秒发送一次 `ping`，客户端需回复 `pong`，90 秒内无响应则断开连接。

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DATABASE_URL` | PostgreSQL 连接串 | — |
| `SECRET_KEY` | JWT 签名密钥（生产环境必须修改） | — |
| `ACCESS_TOKEN_EXPIRE_MINUTES` | Token 有效期（分钟） | `10080`（7天） |
| `HOST` | 监听地址 | `0.0.0.0` |
| `PORT` | 监听端口 | `8000` |

## 技术栈

**后端**

- Python 3.12 / FastAPI / Uvicorn
- SQLAlchemy 2.0（async）+ PostgreSQL
- JWT（python-jose）+ bcrypt（passlib）
- WebSocket（内置 FastAPI）
- Docker / Docker Compose / Nginx

**Android**

- Kotlin / Jetpack Compose
- Room 数据库
- OkHttp / Retrofit
- WebSocket 长连接

**Windows 桌面**

- Tauri v2（Rust + WebView2）
- Rust（reqwest / tokio-tungstenite / serde）
- TypeScript / HTML / CSS
- marked + DOMPurify

## License

MIT
