use crate::models;
use futures_util::{SinkExt, StreamExt};
use std::sync::Mutex;
use tauri::Emitter;
use tokio_tungstenite::tungstenite::Message;

pub struct AppState {
    pub base_url: Mutex<String>,
    pub access_token: Mutex<String>,
    pub push_token: Mutex<String>,
    pub ws_abort: Mutex<Option<tokio::task::AbortHandle>>,
}

impl Default for AppState {
    fn default() -> AppState {
        AppState {
            base_url: Mutex::new("http://localhost:8000".to_string()),
            access_token: Mutex::new(String::new()),
            push_token: Mutex::new(String::new()),
            ws_abort: Mutex::new(None),
        }
    }
}

/// 从 HTTP 响应中提取错误信息：优先读取服务端 JSON 中的 detail/msg 字段
async fn extract_error(resp: reqwest::Response) -> String {
    let status = resp.status();
    let body = resp.text().await.unwrap_or_default();
    // 尝试解析 {"detail": "..."} 或 {"msg": "..."}
    if let Ok(val) = serde_json::from_str::<serde_json::Value>(&body) {
        if let Some(msg) = val.get("detail").and_then(|v| v.as_str()) {
            return msg.to_string();
        }
        if let Some(msg) = val.get("msg").and_then(|v| v.as_str()) {
            return msg.to_string();
        }
    }
    format!("请求失败 (HTTP {})", status)
}

// ---- 认证 ----

#[tauri::command]
pub async fn login(
    state: tauri::State<'_, AppState>,
    base_url: String,
    username: String,
    password: String,
) -> Result<String, String> {
    let url = format!("{}/api/auth/login", base_url);
    let body = models::LoginRequest {
        username,
        password,
    };
    let resp = reqwest::Client::new()
        .post(&url)
        .json(&body)
        .send()
        .await
        .map_err(|e| e.to_string())?;

    if !resp.status().is_success() {
        return Err(extract_error(resp).await);
    }

    let token_resp = resp
        .json::<models::TokenResponse>()
        .await
        .map_err(|e| e.to_string())?;

    *state.base_url.lock().unwrap() = base_url;
    *state.access_token.lock().unwrap() = token_resp.access_token;
    *state.push_token.lock().unwrap() = token_resp.push_token;
    Ok("登录成功".to_string())
}

#[tauri::command]
pub async fn register(
    state: tauri::State<'_, AppState>,
    base_url: String,
    username: String,
    password: String,
) -> Result<String, String> {
    let url = format!("{}/api/auth/register", base_url);
    let body = models::RegisterRequest {
        username,
        password,
    };
    let resp = reqwest::Client::new()
        .post(&url)
        .json(&body)
        .send()
        .await
        .map_err(|e| e.to_string())?;

    if !resp.status().is_success() {
        return Err(extract_error(resp).await);
    }

    let token_resp = resp
        .json::<models::TokenResponse>()
        .await
        .map_err(|e| e.to_string())?;

    *state.base_url.lock().unwrap() = base_url;
    *state.access_token.lock().unwrap() = token_resp.access_token;
    *state.push_token.lock().unwrap() = token_resp.push_token;
    Ok("注册成功".to_string())
}

#[tauri::command]
pub async fn get_user_info(
    state: tauri::State<'_, AppState>,
) -> Result<models::UserInfo, String> {
    let base_url = state.base_url.lock().unwrap().clone();
    let token = state.access_token.lock().unwrap().clone();
    let url = format!("{}/api/auth/me", base_url);
    let resp = reqwest::Client::new()
        .get(&url)
        .header("Authorization", format!("Bearer {}", token))
        .send()
        .await
        .map_err(|e| e.to_string())?;

    if !resp.status().is_success() {
        return Err(extract_error(resp).await);
    }

    let user_info = resp
        .json::<models::UserInfo>()
        .await
        .map_err(|e| e.to_string())?;
    Ok(user_info)
}

// ---- 消息 ----

#[tauri::command]
pub async fn get_messages(
    state: tauri::State<'_, AppState>,
    page: i32,
    page_size: i32,
) -> Result<models::MessageListResponse, String> {
    let base_url = state.base_url.lock().unwrap().clone();
    let token = state.access_token.lock().unwrap().clone();
    let url = format!("{}/api/messages?page={page}&page_size={page_size}", base_url);
    let resp = reqwest::Client::new()
        .get(&url)
        .header("Authorization", format!("Bearer {}", token))
        .send()
        .await
        .map_err(|e| e.to_string())?;

    if !resp.status().is_success() {
        return Err(extract_error(resp).await);
    }

    let messages = resp
        .json::<models::MessageListResponse>()
        .await
        .map_err(|e| e.to_string())?;
    Ok(messages)
}

#[tauri::command]
pub async fn mark_read(
    state: tauri::State<'_, AppState>,
    id: String,
) -> Result<String, String> {
    let base_url = state.base_url.lock().unwrap().clone();
    let token = state.access_token.lock().unwrap().clone();
    let url = format!("{}/api/messages/{}/read", base_url, id);
    let resp = reqwest::Client::new()
        .put(&url)
        .header("Authorization", format!("Bearer {}", token))
        .send()
        .await
        .map_err(|e| e.to_string())?;

    if !resp.status().is_success() {
        return Err(extract_error(resp).await);
    }
    Ok("操作成功".to_string())
}

#[tauri::command]
pub async fn mark_all_read(
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let base_url = state.base_url.lock().unwrap().clone();
    let token = state.access_token.lock().unwrap().clone();
    let url = format!("{}/api/messages/read-all", base_url);
    let resp = reqwest::Client::new()
        .put(&url)
        .header("Authorization", format!("Bearer {}", token))
        .send()
        .await
        .map_err(|e| e.to_string())?;

    if !resp.status().is_success() {
        return Err(extract_error(resp).await);
    }
    Ok("操作成功".to_string())
}

// ---- 测试推送 ----

#[tauri::command]
pub async fn test_push(
    state: tauri::State<'_, AppState>,
    title: String,
    content: String,
    content_type: String,
    url: Option<String>,
) -> Result<String, String> {
    let base_url = state.base_url.lock().unwrap().clone();
    let push_token = state.push_token.lock().unwrap().clone();
    let push_url = format!("{}/push/{}", base_url, push_token);

    let body = serde_json::json!({
        "title": title,
        "content": content,
        "content_type": content_type,
        "url": url,
    });

    let resp = reqwest::Client::new()
        .post(&push_url)
        .json(&body)
        .send()
        .await
        .map_err(|e| e.to_string())?;

    if !resp.status().is_success() {
        return Err(extract_error(resp).await);
    }
    Ok("推送成功".to_string())
}

// ---- Token 持久化（由前端 localStorage 调用） ----

#[derive(serde::Serialize)]
pub struct TokenPair {
    pub access_token: String,
    pub push_token: String,
}

#[tauri::command]
pub fn get_tokens(state: tauri::State<'_, AppState>) -> Result<TokenPair, String> {
    let access_token = state.access_token.lock().unwrap().clone();
    let push_token = state.push_token.lock().unwrap().clone();
    Ok(TokenPair { access_token, push_token })
}

#[tauri::command]
pub fn save_tokens(
    state: tauri::State<'_, AppState>,
    base_url: String,
    access_token: String,
    push_token: String,
) -> Result<String, String> {
    *state.base_url.lock().unwrap() = base_url;
    *state.access_token.lock().unwrap() = access_token;
    *state.push_token.lock().unwrap() = push_token;
    Ok("已保存".to_string())
}

// ---- WebSocket ----

#[derive(serde::Deserialize)]
struct WsMessage {
    #[serde(rename = "type")]
    msg_type: String,
    data: Option<WsMessageData>,
}

// 服务端 WebSocket 推送不包含 is_read，新消息默认未读
#[derive(Clone, serde::Serialize, serde::Deserialize)]
struct WsMessageData {
    id: String,
    title: String,
    content: String,
    content_type: models::ContentType,
    url: Option<String>,
    created_at: String,
    #[serde(skip_deserializing, default)]
    is_read: bool,
}

#[tauri::command]
pub async fn start_websocket(
    app: tauri::AppHandle,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let base_url = state.base_url.lock().unwrap().clone();
    let push_token = state.push_token.lock().unwrap().clone();

    if push_token.is_empty() {
        return Err("未登录".to_string());
    }

    let ws_url = format!(
        "{}/ws/{}",
        base_url.replace("http://", "ws://").replace("https://", "wss://"),
        push_token
    );

    // 先取消已有的 WebSocket 连接
    if let Some(handle) = state.ws_abort.lock().unwrap().take() {
        handle.abort();
    }

    let handle = tokio::spawn(async move {
        let mut reconnect_delay = 1000u64;

        loop {
            match tokio_tungstenite::connect_async(&ws_url).await {
                Ok((ws_stream, _)) => {
                    let _ = app.emit("ws-status", "connected");
                    reconnect_delay = 1000;

                    let (mut write, mut read) = ws_stream.split();

                    while let Some(msg_result) = read.next().await {
                        match msg_result {
                            Ok(Message::Text(text)) => {
                                // 先尝试解析为结构化消息
                                if let Ok(ws_msg) = serde_json::from_str::<WsMessage>(&text) {
                                    match ws_msg.msg_type.as_str() {
                                        "ping" => {
                                            let _ = write
                                                .send(Message::Text("{\"type\":\"pong\"}".into()))
                                                .await;
                                        }
                                        "message" => {
                                            if let Some(mut data) = ws_msg.data {
                                                data.is_read = false;
                                                let _ = app.emit("new-message", &data);
                                            }
                                        }
                                        _ => {}
                                    }
                                }
                            }
                            Ok(_) => continue,
                            Err(_) => break,
                        }
                    }
                    let _ = app.emit("ws-status", "disconnected");
                }
                Err(_) => {
                    let _ = app.emit("ws-status", "disconnected");
                }
            }

            tokio::time::sleep(tokio::time::Duration::from_millis(reconnect_delay)).await;
            reconnect_delay = (reconnect_delay * 2).min(60000);
        }
    });

    *state.ws_abort.lock().unwrap() = Some(handle.abort_handle());
    Ok("WebSocket 已启动".to_string())
}

#[tauri::command]
pub fn stop_websocket(state: tauri::State<'_, AppState>) -> Result<String, String> {
    if let Some(handle) = state.ws_abort.lock().unwrap().take() {
        handle.abort();
    }
    *state.access_token.lock().unwrap() = String::new();
    *state.push_token.lock().unwrap() = String::new();
    Ok("已停止".to_string())
}
