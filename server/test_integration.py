#!/usr/bin/env python3
"""
InfoPush 集成测试（边界用例 + 错误处理）
用法: python test_integration.py [服务器地址]
依赖: pip install httpx
"""

import asyncio
import sys
import time
import httpx

BASE_URL = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "http://localhost:8000"
TS = int(time.time())

PASS = 0
FAIL = 0


def check(name: str, cond: bool, detail: str = ""):
    global PASS, FAIL
    if cond:
        PASS += 1
        print(f"  [PASS] {name}")
    else:
        FAIL += 1
        print(f"  [FAIL] {name}" + (f" — {detail}" if detail else ""))


async def run():
    async with httpx.AsyncClient(base_url=BASE_URL, timeout=10) as c:

        # ── 注册 / 登录 ──────────────────────────────────────────────
        print("\n[1] 注册与认证")

        # 正常注册
        r = await c.post("/api/auth/register", json={"username": f"u{TS}", "password": "pass123456"})
        check("正常注册 201", r.status_code == 201)
        token = r.json().get("access_token", "")
        push_token = r.json().get("push_token", "")
        auth = {"Authorization": f"Bearer {token}"}

        # 重复注册
        r = await c.post("/api/auth/register", json={"username": f"u{TS}", "password": "pass123456"})
        check("重复注册 400", r.status_code == 400)

        # 用户名过短
        r = await c.post("/api/auth/register", json={"username": "ab", "password": "pass123456"})
        check("用户名过短 422", r.status_code == 422)

        # 密码过短
        r = await c.post("/api/auth/register", json={"username": f"u{TS}x", "password": "12345"})
        check("密码过短 422", r.status_code == 422)

        # 登录正确
        r = await c.post("/api/auth/login", json={"username": f"u{TS}", "password": "pass123456"})
        check("正确登录 200", r.status_code == 200 and "access_token" in r.json())

        # 密码错误
        r = await c.post("/api/auth/login", json={"username": f"u{TS}", "password": "wrongpass"})
        check("密码错误 401", r.status_code == 401)

        # 无 token 访问受保护接口
        r = await c.get("/api/auth/me")
        check("未认证 401", r.status_code == 401)

        # 正常获取用户信息
        r = await c.get("/api/auth/me", headers=auth)
        check("获取用户信息 200", r.status_code == 200 and r.json()["username"] == f"u{TS}")

        # ── 推送 ─────────────────────────────────────────────────────
        print("\n[2] 推送接口")

        # 正常 POST 推送
        r = await c.post(f"/push/{push_token}", json={"title": "标题", "content": "内容"})
        check("POST 推送 200", r.status_code == 200 and r.json()["code"] == 200)
        msg_id = r.json().get("message_id")

        # 正常 GET 推送
        r = await c.get(f"/push/{push_token}", params={"title": "GET测试", "content": "内容"})
        check("GET 推送 200", r.status_code == 200)

        # GET 推送缺少 content
        r = await c.get(f"/push/{push_token}", params={"title": "缺content"})
        check("GET 推送缺 content 400", r.status_code == 400)

        # 无效 push_token
        r = await c.post("/push/invalid_token_xyz", json={"title": "t", "content": "c"})
        check("无效 push_token 404", r.status_code == 404)

        # markdown 类型
        r = await c.post(f"/push/{push_token}", json={
            "title": "Markdown", "content": "## 标题\n`code`", "content_type": "markdown"
        })
        check("markdown 推送 200", r.status_code == 200)

        # content 超长（65537 字符）
        r = await c.post(f"/push/{push_token}", json={"title": "t", "content": "x" * 65537})
        check("content 超长 422", r.status_code == 422)

        # 无效 content_type
        r = await c.post(f"/push/{push_token}", json={"title": "t", "content": "c", "content_type": "invalid"})
        check("无效 content_type 422", r.status_code == 422)

        # ── 消息列表 ─────────────────────────────────────────────────
        print("\n[3] 消息列表与已读")

        r = await c.get("/api/messages", headers=auth)
        check("消息列表 200", r.status_code == 200)
        data = r.json()
        check("返回字段完整", all(k in data for k in ["total", "page", "page_size", "messages"]))
        check("消息数量正确", data["total"] >= 2)

        # 分页
        r = await c.get("/api/messages", headers=auth, params={"page": 1, "page_size": 1})
        check("分页 page_size=1", r.status_code == 200 and len(r.json()["messages"]) == 1)

        # page_size 超限
        r = await c.get("/api/messages", headers=auth, params={"page_size": 101})
        check("page_size 超限 422", r.status_code == 422)

        # 标记已读
        if msg_id:
            r = await c.put(f"/api/messages/{msg_id}/read", headers=auth)
            check("标记已读 200", r.status_code == 200)

            # 重复标记已读（应幂等）
            r = await c.put(f"/api/messages/{msg_id}/read", headers=auth)
            check("重复标记已读幂等", r.status_code == 200)

        # 不存在的消息 ID
        r = await c.put("/api/messages/00000000-0000-0000-0000-000000000000/read", headers=auth)
        check("不存在消息 404", r.status_code == 404)

        # ── 跨用户隔离 ───────────────────────────────────────────────
        print("\n[4] 用户数据隔离")

        r2 = await c.post("/api/auth/register", json={"username": f"u{TS}b", "password": "pass123456"})
        auth2 = {"Authorization": f"Bearer {r2.json()['access_token']}"}

        # 用户2 的消息列表应为空
        r = await c.get("/api/messages", headers=auth2)
        check("用户隔离（列表为空）", r.json()["total"] == 0)

        # 用户2 不能标记用户1 的消息
        if msg_id:
            r = await c.put(f"/api/messages/{msg_id}/read", headers=auth2)
            check("跨用户标记已读 404", r.status_code == 404)

        # ── 重置推送 token ────────────────────────────────────────────
        print("\n[5] 重置推送 token")

        r = await c.post("/api/auth/reset-token", headers=auth)
        check("重置 token 200", r.status_code == 200)
        new_push_token = r.json().get("push_token", "")
        check("token 已更换", new_push_token != push_token and len(new_push_token) > 0)

        # 旧 token 应失效
        r = await c.post(f"/push/{push_token}", json={"title": "t", "content": "c"})
        check("旧 push_token 失效 404", r.status_code == 404)

        # 新 token 可用
        r = await c.post(f"/push/{new_push_token}", json={"title": "t", "content": "c"})
        check("新 push_token 可用", r.status_code == 200)

    # ── 汇总 ─────────────────────────────────────────────────────────
    total = PASS + FAIL
    print(f"\n{'='*40}")
    print(f"结果: {PASS}/{total} 通过" + (f"，{FAIL} 失败" if FAIL else ""))
    print('='*40)
    sys.exit(0 if FAIL == 0 else 1)


if __name__ == "__main__":
    asyncio.run(run())
