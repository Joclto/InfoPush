import uuid
from datetime import datetime

from pydantic import BaseModel, Field

from app.models import ContentType


# ---- Auth ----

class UserRegister(BaseModel):
    username: str = Field(min_length=3, max_length=64)
    password: str = Field(min_length=6, max_length=128)


class UserLogin(BaseModel):
    username: str
    password: str


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    push_token: str


class UserInfo(BaseModel):
    id: uuid.UUID
    username: str
    push_token: str
    created_at: datetime

    model_config = {"from_attributes": True}


# ---- Push ----

class PushRequest(BaseModel):
    title: str = Field(default="无标题", max_length=256)
    content: str = Field(max_length=65536)
    content_type: ContentType = ContentType.TEXT
    url: str | None = Field(default=None, max_length=2048)


class PushResponse(BaseModel):
    code: int = 200
    msg: str = "ok"
    message_id: uuid.UUID | None = None
    online_devices: int = 0


# ---- Message ----

class MessageOut(BaseModel):
    id: uuid.UUID
    title: str
    content: str
    content_type: ContentType
    url: str | None
    is_read: bool
    created_at: datetime

    model_config = {"from_attributes": True}


class MessageListResponse(BaseModel):
    total: int
    page: int
    page_size: int
    messages: list[MessageOut]
