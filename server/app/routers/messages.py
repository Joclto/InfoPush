from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth import get_current_user
from app.database import get_db
from app.models import Message, User
from app.schemas import MessageListResponse, MessageOut

router = APIRouter(prefix="/api/messages", tags=["消息"])


@router.get("", response_model=MessageListResponse)
async def get_messages(
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    # 总数
    count_result = await db.execute(
        select(func.count()).select_from(Message).where(Message.user_id == user.id)
    )
    total = count_result.scalar()

    # 分页查询
    result = await db.execute(
        select(Message)
        .where(Message.user_id == user.id)
        .order_by(Message.created_at.desc())
        .offset((page - 1) * page_size)
        .limit(page_size)
    )
    messages = result.scalars().all()

    return MessageListResponse(
        total=total,
        page=page,
        page_size=page_size,
        messages=[MessageOut.model_validate(m) for m in messages],
    )


@router.put("/read-all")
async def mark_all_read(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    from sqlalchemy import update
    await db.execute(
        update(Message)
        .where(Message.user_id == user.id, Message.is_read == False)
        .values(is_read=True)
    )
    await db.commit()
    return {"msg": "ok"}


@router.put("/{message_id}/read")
async def mark_read(
    message_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(
        select(Message).where(Message.id == message_id, Message.user_id == user.id)
    )
    message = result.scalar_one_or_none()
    if not message:
        raise HTTPException(status_code=404, detail="消息不存在")

    message.is_read = True
    await db.commit()
    return {"msg": "ok"}
