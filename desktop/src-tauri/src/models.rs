use serde::{Deserialize, Serialize};

#[derive(Serialize)]
pub struct LoginRequest {
    pub username: String,
    pub password: String,
}

#[derive(Serialize, Deserialize)]
pub struct TokenResponse {
    pub access_token: String,
    pub token_type: String,
    pub push_token: String,
}

#[derive(Serialize, Deserialize)]
pub struct RegisterRequest {
    pub username: String,
    pub password: String,
}

#[derive(Serialize, Deserialize)]
pub struct UserInfo {
    pub id: String,
    pub username: String,
    pub push_token: String,
    pub created_at: String,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub enum ContentType {
    #[serde(rename = "text")]
    Text,
    #[serde(rename = "markdown")]
    Markdown,
    #[serde(rename = "html")]
    Html,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Message {
    pub id: String,
    pub title: String,
    pub content: String,
    pub content_type: ContentType,
    pub url: Option<String>,
    pub is_read: bool,
    pub created_at: String,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct MessageListResponse {
    pub total: i64,
    pub page: i32,
    pub page_size: i32,
    pub messages: Vec<Message>,
}
