import asyncio
import json
import logging
from datetime import datetime, timezone

from fastapi import WebSocket

logger = logging.getLogger(__name__)


class ConnectionManager:
    """管理 WebSocket 连接，支持同一用户多设备"""

    def __init__(self):
        # push_token -> list of WebSocket connections
        self._connections: dict[str, list[WebSocket]] = {}
        self._heartbeat_interval = 30  # seconds

    async def connect(self, token: str, ws: WebSocket):
        await ws.accept()
        if token not in self._connections:
            self._connections[token] = []
        self._connections[token].append(ws)
        logger.info(f"WebSocket connected: token={token[:8]}..., total={len(self._connections[token])}")

    def disconnect(self, token: str, ws: WebSocket):
        if token in self._connections:
            self._connections[token] = [c for c in self._connections[token] if c is not ws]
            if not self._connections[token]:
                del self._connections[token]
        logger.info(f"WebSocket disconnected: token={token[:8]}...")

    def get_online_count(self, token: str) -> int:
        return len(self._connections.get(token, []))

    async def send_message(self, token: str, message: dict) -> int:
        """向指定 token 的所有连接发送消息，返回成功发送的数量"""
        connections = self._connections.get(token, [])
        if not connections:
            return 0

        sent = 0
        dead = []
        data = json.dumps(message, ensure_ascii=False, default=str)

        for ws in connections:
            try:
                await ws.send_text(data)
                sent += 1
            except Exception:
                dead.append(ws)

        # 清理断开的连接
        for ws in dead:
            self.disconnect(token, ws)

        return sent

    async def heartbeat(self, token: str, ws: WebSocket):
        """维持心跳，检测连接是否存活"""
        try:
            while True:
                await asyncio.sleep(self._heartbeat_interval)
                try:
                    await ws.send_json({"type": "ping", "time": datetime.now(timezone.utc).isoformat()})
                except Exception:
                    break
        except asyncio.CancelledError:
            pass


# 全局连接管理器
manager = ConnectionManager()
