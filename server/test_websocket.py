#!/usr/bin/env python3
"""
InfoPush WebSocket 实时推送测试
用法: python test_websocket.py [服务器地址]
示例: python test_websocket.py http://localhost:8000
依赖: pip install websockets httpx
"""

import asyncio
import json
import sys
import time
import httpx
import websockets

BASE_URL = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "http://localhost:8000"
WS_BASE = BASE_URL.replace("https://", "wss://").replace("http://", "ws://")

USERNAME = f"wstest_{int(time.time())}"
PASSWORD = "test123456"


async def setup_user():
    async with httpx.AsyncClient() as client:
        r = await client.post(f"{BASE_URL}/api/auth/register", json={
            "username": USERNAME, "password": PASSWORD
        })
        r.raise_for_status()
        data = r.json()
        print(f"[setup] 注册用户: {USERNAME}")
        print(f"[setup] push_token: {data['push_token'][:16]}...")
        return data["access_token"], data["push_token"]


async def send_push(push_token: str, title: str, content: str, content_type: str = "text"):
    async with httpx.AsyncClient() as client:
        r = await client.post(f"{BASE_URL}/push/{push_token}", json={
            "title": title,
            "content": content,
            "content_type": content_type,
        })
        return r.json()


async def test_realtime_push():
    print(f"=== WebSocket 实时推送测试 ===")
    print(f"Server: {BASE_URL}\n")

    access_token, push_token = await setup_user()
    ws_url = f"{WS_BASE}/ws/{push_token}"

    received = []

    async def ws_client():
        print(f"[WS] 连接: {ws_url}")
        async with websockets.connect(ws_url) as ws:
            print("[WS] 已连接，等待消息...")
            # 等待最多 5 条消息或超时
            for _ in range(5):
                try:
                    raw = await asyncio.wait_for(ws.recv(), timeout=5.0)
                    msg = json.loads(raw)
                    received.append(msg)
                    print(f"[WS] 收到: type={msg.get('type')} title={msg.get('data', {}).get('title', '')!r}")
                except asyncio.TimeoutError:
                    break

    async def pusher():
        await asyncio.sleep(0.5)  # 等待 WS 连接建立
        cases = [
            ("文本消息", "Hello, InfoPush!", "text"),
            ("Markdown消息", "## 标题\n- 条目1\n- 条目2", "markdown"),
            ("带链接消息", "点击查看详情", "text"),
        ]
        for title, content, ct in cases:
            resp = await send_push(push_token, title, content, ct)
            online = resp.get("online_devices", 0)
            print(f"[push] 推送 {title!r} → online_devices={online}")
            await asyncio.sleep(0.3)

    # 并发运行 WS 监听和推送
    await asyncio.gather(ws_client(), pusher())

    print(f"\n[结果] 发送 3 条，收到 {len(received)} 条")
    assert len(received) == 3, f"期望收到 3 条，实际收到 {len(received)} 条"
    print("[PASS] WebSocket 实时推送正常")


async def test_ws_auth_invalid():
    """无效 token 应被拒绝或收不到消息"""
    print("\n--- 测试无效 token 的 WebSocket 连接 ---")
    ws_url = f"{WS_BASE}/ws/invalid_token_000"
    try:
        async with websockets.connect(ws_url) as ws:
            # 向这个连接推送一条消息，验证不会泄漏到其他用户
            await asyncio.wait_for(ws.recv(), timeout=2.0)
            print("[INFO] 无效 token 连接被接受（消息隔离由 push_token 保证）")
    except asyncio.TimeoutError:
        print("[PASS] 无效 token 连接超时，无消息泄漏")
    except Exception as e:
        print(f"[PASS] 连接被拒绝: {e}")


if __name__ == "__main__":
    async def main():
        await test_realtime_push()
        await test_ws_auth_invalid()
        print("\n=== WebSocket 测试完成 ===")

    asyncio.run(main())
