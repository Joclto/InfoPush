// ===== 页面路由 =====

import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { sendNotification, isPermissionGranted, requestPermission } from "@tauri-apps/plugin-notification";
import { marked } from "marked";
import DOMPurify from "dompurify";

type PageName = "login" | "messages" | "detail" | "settings";

let currentMessageId = "";

function stripAnsi(text: string): string {
    return text.replace(/\x1b\[[0-9;]*[a-zA-Z]/g, "");
}

function formatTime(dateStr: string): string {
    const d = new Date(dateStr);
    return `${d.getFullYear()}-` +
        `${d.getMonth() + 1}`.padStart(2, "0") + "-" +
        `${d.getDate()}`.padStart(2, "0") + " " +
        `${d.getHours()}`.padStart(2, "0") + ":" +
        `${d.getMinutes()}`.padStart(2, "0");
}

function navigate(page: PageName) {
    document.querySelectorAll(".page").forEach((el) => {
        el.classList.remove("active");
    });
    document.getElementById(`page-${page}`)?.classList.add("active");
}

// ===== 消息列表 =====

interface MessageData {
    id: string;
    title: string;
    content: string;
    content_type: string;
    url: string | null;
    is_read: boolean;
    created_at: string;
}

function createMessageElement(msg: MessageData): HTMLDivElement {
    const div = document.createElement("div");
    div.className = "message-item" + (msg.is_read ? "" : " unread");
    div.dataset.id = msg.id;
    div.innerHTML = `
        <div class="message-unread-bar"></div>
        <div class="message-body">
            <div class="message-header">
                <span class="message-title"></span>
                <span class="message-time"></span>
            </div>
            <p class="message-preview"></p>
        </div>
    `;
    div.querySelector(".message-title")!.textContent = msg.title;
    div.querySelector(".message-time")!.textContent = formatTime(msg.created_at);
    div.querySelector(".message-preview")!.textContent = stripAnsi(msg.content).slice(0, 100);
    div.addEventListener("click", () => openDetail(msg));
    return div;
}

function openDetail(msg: MessageData) {
    currentMessageId = msg.id;

    document.getElementById("detail-title")!.textContent = msg.title;
    document.getElementById("detail-subject")!.textContent = msg.title;
    document.getElementById("detail-time")!.textContent = formatTime(msg.created_at);

    const body = document.getElementById("detail-body")!;
    const content = stripAnsi(msg.content);

    switch (msg.content_type) {
        case "markdown":
            body.innerHTML = DOMPurify.sanitize(marked(content) as string);
            break;
        case "html":
            body.innerHTML = DOMPurify.sanitize(content);
            break;
        default:
            body.textContent = content;
    }

    const urlEl = document.getElementById("detail-url") as HTMLAnchorElement;
    if (msg.url) {
        urlEl.href = msg.url;
        urlEl.style.display = "inline-block";
    } else {
        urlEl.style.display = "none";
    }

    // 自动标记已读
    if (!msg.is_read) {
        invoke("mark_read", { id: msg.id });
        const item = document.querySelector(`.message-item[data-id="${msg.id}"]`);
        item?.classList.remove("unread");
    }

    navigate("detail");
}

async function loadMessages() {
    const list = document.getElementById("message-list")!;
    const emptyState = document.getElementById("empty-state")!;

    // 显示加载状态
    list.innerHTML = '<div class="loading-overlay"><div class="spinner"></div></div>';
    list.style.display = "block";
    emptyState.style.display = "none";

    try {
        const resp = await invoke<{
            total: number;
            page: number;
            page_size: number;
            messages: MessageData[];
        }>("get_messages", { page: 1, pageSize: 20 });

        list.innerHTML = "";

        if (resp.messages.length === 0) {
            list.style.display = "none";
            emptyState.style.display = "flex";
            return;
        }

        for (const msg of resp.messages) {
            list.appendChild(createMessageElement(msg));
        }
    } catch (e) {
        list.innerHTML = "";
        console.error("加载消息失败:", e);
    }
}

// ===== 登录 / 注册 =====

async function doAuth(action: "login" | "register") {
    const server = (document.getElementById("input-server") as HTMLInputElement).value;
    const username = (document.getElementById("input-username") as HTMLInputElement).value;
    const password = (document.getElementById("input-password") as HTMLInputElement).value;
    const msgEl = document.getElementById("login-msg")!;
    const btnLogin = document.getElementById("btn-login") as HTMLButtonElement;
    const btnRegister = document.getElementById("btn-register") as HTMLButtonElement;

    if (!username || !password) {
        msgEl.textContent = "请输入用户名和密码";
        msgEl.className = "msg error";
        return;
    }

    // 禁用按钮防重复点击
    btnLogin.disabled = true;
    btnRegister.disabled = true;

    try {
        await invoke(action, { baseUrl: server, username, password });

        // 获取用户信息
        const info = await invoke<{ id: string; username: string; push_token: string }>("get_user_info");

        // 持久化到 localStorage（access_token 从 Rust state 获取）
        const tokens = await invoke<{ access_token: string; push_token: string }>("get_tokens");
        localStorage.setItem("base_url", server);
        localStorage.setItem("username", info.username);
        localStorage.setItem("access_token", tokens.access_token);
        localStorage.setItem("push_token", tokens.push_token);

        // 更新设置页
        updateSettingsPage(info.username, info.push_token, server);

        navigate("messages");
        await loadMessages();
        invoke("start_websocket");

        msgEl.textContent = "";
    } catch (e) {
        msgEl.textContent = e as string;
        msgEl.className = "msg error";
    } finally {
        btnLogin.disabled = false;
        btnRegister.disabled = false;
    }
}

document.querySelector("#btn-login")?.addEventListener("click", () => doAuth("login"));
document.querySelector("#btn-register")?.addEventListener("click", () => doAuth("register"));

document.getElementById("input-password")?.addEventListener("keydown", (e) => {
    if ((e as KeyboardEvent).key === "Enter") doAuth("login");
});

// ===== 消息列表交互 =====

document.querySelector("#btn-refresh")?.addEventListener("click", () => {
    loadMessages();
});

document.querySelector("#btn-mark-all-read")?.addEventListener("click", async () => {
    try {
        await invoke("mark_all_read");
        document.querySelectorAll(".message-item.unread").forEach((el) => {
            el.classList.remove("unread");
        });
    } catch (e) {
        console.error(e);
    }
});

document.querySelector("#btn-settings")?.addEventListener("click", () => {
    navigate("settings");
});

// ===== 消息详情 =====

document.querySelector("#btn-back")?.addEventListener("click", () => {
    navigate("messages");
});

document.querySelector("#btn-mark-read")?.addEventListener("click", async () => {
    if (!currentMessageId) return;
    try {
        await invoke("mark_read", { id: currentMessageId });
        const item = document.querySelector(`.message-item[data-id="${currentMessageId}"]`);
        item?.classList.remove("unread");
    } catch (e) {
        console.error(e);
    }
});

// ===== 设置页 =====

function updateSettingsPage(username: string, pushToken: string, server: string) {
    document.getElementById("settings-username")!.textContent = username;
    document.getElementById("settings-token")!.textContent = pushToken;
    document.getElementById("settings-server")!.textContent = server;
}

document.querySelector("#btn-settings-back")?.addEventListener("click", () => {
    navigate("messages");
});

// 复制 Push Token
document.querySelector("#btn-copy-token")?.addEventListener("click", async () => {
    const token = document.getElementById("settings-token")!.textContent;
    if (token) {
        try {
            await navigator.clipboard.writeText(token);
        } catch {
            // fallback
        }
    }
});

// ===== 试一试 =====
const TEMPLATES: Record<string, { title: string; content: string; contentType: string; url?: string }> = {
    text: {
        title: "测试推送",
        content: "这是一条普通文本消息，来自 InfoPush 试一试功能。",
        contentType: "text",
    },
    markdown: {
        title: "Markdown 测试",
        content: "# Hello InfoPush\n\n**粗体文本** 和 _斜体文本_ 以及 `内联代码`\n\n- 列表项 1\n- 列表项 2\n\n> 这是一条引用",
        contentType: "markdown",
    },
    url: {
        title: "带链接的消息",
        content: "点击通知可以打开指定网址。\n\n**这是 Markdown 格式内容：**\n- 支持列表\n- 支持**粗体**和_斜体_\n- 支持`代码`\n\n> 这是引用文本",
        contentType: "markdown",
        url: "https://github.com",
    },
};
let currentTemplate = "text";

document.querySelectorAll(".chip[data-template]").forEach((chip) => {
    chip.addEventListener("click", () => {
        const tpl = (chip as HTMLElement).dataset.template!;
        currentTemplate = tpl;
        const t = TEMPLATES[tpl];
        (document.getElementById("test-title") as HTMLInputElement).value = t.title;
        (document.getElementById("test-content") as HTMLTextAreaElement).value = t.content;
        (document.getElementById("test-url") as HTMLInputElement).value = t.url || "";
        document.getElementById("test-url-group")!.style.display = tpl === "url" ? "" : "none";
        document.querySelectorAll(".chip[data-template]").forEach((c) => c.classList.remove("active"));
        chip.classList.add("active");
    });
});

document.querySelector("#btn-test-push")?.addEventListener("click", async () => {
    const msgEl = document.getElementById("test-push-msg")!;
    const title = (document.getElementById("test-title") as HTMLInputElement).value.trim();
    const content = (document.getElementById("test-content") as HTMLTextAreaElement).value.trim();
    const contentType = TEMPLATES[currentTemplate].contentType;
    const url = currentTemplate === "url" ? (document.getElementById("test-url") as HTMLInputElement).value.trim() : undefined;
    if (!title && !content) {
        msgEl.textContent = "标题和内容不能同时为空";
        msgEl.className = "msg error";
        return;
    }
    try {
        await invoke("test_push", { title, content, contentType, url: url || null });
        msgEl.textContent = "发送成功，请查看消息列表";
        msgEl.className = "msg success";
    } catch (e) {
        msgEl.textContent = e as string;
        msgEl.className = "msg error";
    }
    setTimeout(() => { msgEl.textContent = ""; }, 3000);
});

document.querySelector("#btn-logout")?.addEventListener("click", async () => {
    try {
        await invoke("stop_websocket");
    } catch (e) {
        console.error(e);
    }
    localStorage.removeItem("base_url");
    localStorage.removeItem("username");
    localStorage.removeItem("access_token");
    localStorage.removeItem("push_token");
    navigate("login");
});

// ===== WebSocket 事件监听 =====

listen<string>("ws-status", (event) => {
    const dot = document.getElementById("ws-dot");
    const text = document.getElementById("ws-text");
    if (!dot || !text) return;

    dot.className = "status-dot";
    switch (event.payload) {
        case "connected":
            dot.classList.add("connected");
            text.textContent = "已连接";
            break;
        case "disconnected":
            dot.classList.add("disconnected");
            text.textContent = "已断开";
            break;
    }
});

listen<MessageData>("new-message", async (event) => {
    const msg = event.payload;
    const list = document.getElementById("message-list");
    if (!list) return;

    list.style.display = "block";
    document.getElementById("empty-state")!.style.display = "none";

    const div = createMessageElement(msg);
    list.insertBefore(div, list.firstChild);

    // Windows Toast 通知
    try {
        let permitted = await isPermissionGranted();
        if (!permitted) {
            const permission = await requestPermission();
            permitted = permission === "granted";
        }
        if (permitted) {
            sendNotification({
                title: msg.title,
                body: stripAnsi(msg.content).slice(0, 100),
            });
        }
    } catch (e) {
        console.error("通知发送失败:", e);
    }
});

// ===== 自动登录 ===== 

// 回填上次服务器地址
const savedServerUrl = localStorage.getItem("base_url");
if (savedServerUrl) {
    (document.getElementById("input-server") as HTMLInputElement).value = savedServerUrl;
}

async function tryAutoLogin() {
    const baseUrl = localStorage.getItem("base_url");
    const accessToken = localStorage.getItem("access_token");
    const pushToken = localStorage.getItem("push_token");

    if (!baseUrl || !accessToken || !pushToken) return;

    try {
        // 恢复 Rust 端状态
        await invoke("save_tokens", {
            baseUrl,
            accessToken,
            pushToken,
        });

        // 启动 WebSocket
        invoke("start_websocket");

        // 验证 token 是否有效
        const info = await invoke<{ id: string; username: string; push_token: string }>("get_user_info");
        updateSettingsPage(info.username, info.push_token, baseUrl);
        localStorage.setItem("push_token", info.push_token);

        navigate("messages");
        await loadMessages();
    } catch (e) {
        // 自动登录失败，停止 WebSocket 并清除缓存
        console.error("自动登录失败:", e);
        try { await invoke("stop_websocket"); } catch {}
        localStorage.removeItem("base_url");
        localStorage.removeItem("username");
        localStorage.removeItem("access_token");
        localStorage.removeItem("push_token");
    }
}

// 应用启动时尝试自动登录
tryAutoLogin();
