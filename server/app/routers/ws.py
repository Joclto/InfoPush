import asyncio
import logging
import time

from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from sqlalchemy import select

from app.database import async_session
from app.models import User
from app.ws_manager import manager

logger = logging.getLogger(__name__)
router = APIRouter(tags=["WebSocket"])

PONG_TIMEOUT = 90  # seconds — must receive pong within this window after a ping


@router.websocket("/ws/{token}")
async def websocket_endpoint(ws: WebSocket, token: str):
    # 验证 token
    async with async_session() as db:
        result = await db.execute(select(User).where(User.push_token == token))
        user = result.scalar_one_or_none()
        if not user:
            await ws.close(code=4001, reason="无效的推送令牌")
            return

    await manager.connect(token, ws)
    last_pong = time.monotonic()
    heartbeat_task = asyncio.create_task(manager.heartbeat(token, ws))

    async def pong_watchdog():
        while True:
            await asyncio.sleep(10)
            if time.monotonic() - last_pong > PONG_TIMEOUT:
                logger.warning(f"WebSocket pong timeout: token={token[:8]}...")
                await ws.close(code=1001, reason="pong timeout")
                break

    watchdog_task = asyncio.create_task(pong_watchdog())

    try:
        while True:
            data = await ws.receive_text()
            if data == "pong" or data == '{"type":"pong"}':
                last_pong = time.monotonic()
    except WebSocketDisconnect:
        pass
    except Exception as e:
        logger.warning(f"WebSocket error: {e}")
    finally:
        heartbeat_task.cancel()
        watchdog_task.cancel()
        manager.disconnect(token, ws)
