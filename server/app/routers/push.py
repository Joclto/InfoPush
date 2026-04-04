from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models import Message, User
from app.schemas import PushRequest, PushResponse
from app.ws_manager import manager

router = APIRouter(tags=["推送"])


async def _do_push(token: str, data: PushRequest, db: AsyncSession) -> PushResponse:
    # 通过 push_token 找到用户
    result = await db.execute(select(User).where(User.push_token == token))
    user = result.scalar_one_or_none()
    if not user:
        raise HTTPException(status_code=404, detail="无效的推送令牌")

    # 存储消息
    message = Message(
        user_id=user.id,
        title=data.title,
        content=data.content,
        content_type=data.content_type,
        url=data.url,
    )
    db.add(message)
    await db.commit()
    await db.refresh(message)

    # 通过 WebSocket 推送
    ws_payload = {
        "type": "message",
        "data": {
            "id": str(message.id),
            "title": message.title,
            "content": message.content,
            "content_type": message.content_type.value,
            "url": message.url,
            "created_at": message.created_at.isoformat(),
        },
    }
    online = await manager.send_message(token, ws_payload)

    return PushResponse(
        code=200,
        msg="推送成功" if online > 0 else "消息已保存，设备离线",
        message_id=message.id,
        online_devices=online,
    )


@router.post("/push/{token}", response_model=PushResponse)
async def push_message(token: str, data: PushRequest, db: AsyncSession = Depends(get_db)):
    return await _do_push(token, data, db)


@router.get("/push/{token}", response_model=PushResponse)
async def push_message_get(
    token: str,
    title: str = "无标题",
    content: str = "",
    content_type: str = "text",
    url: str | None = None,
    db: AsyncSession = Depends(get_db),
):
    """GET 方式推送，方便 curl / 浏览器直接调用"""
    if not content:
        raise HTTPException(status_code=400, detail="content 不能为空")
    data = PushRequest(title=title, content=content, content_type=content_type, url=url)
    return await _do_push(token, data, db)
