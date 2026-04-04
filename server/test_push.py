import httpx

SERVER = "http://192.168.1.41:8000"
TOKEN = "d0PSWH8b7jxgcO-zzeP-009ZbQuLzCJ_Kdy3FR3n-Xw"

# POST 方式推送（支持 markdown）
def push(title, content, content_type="text", url=None):
    resp = httpx.post(f"{SERVER}/push/{TOKEN}", json={
        "title": title,
        "content": content,
        "content_type": content_type,
        "url": url,
    })
    print(resp.status_code, resp.json())

# 测试1：普通文本
push("测试通知", "这是一条测试消息")

# 测试2：带链接
push("带链接的通知", "点击查看详情", url="https://example.com")

# 测试3：Markdown
push("Markdown 测试", "## 标题\n\n- 列表项1\n- 列表项2\n\n**加粗文字**", content_type="markdown")

# 测试4：关联应用打开（BUFF Deep Link）
push(
    "BUFF 商品",
    "点击查看商品详情（测试关联应用打开）",
    url="https://buff.163.com/goods/956602?from=market#tab=selling&max_paintwear=0.0743&page_num=1",
)
