#!/usr/bin/env python3
"""本地 OpenAI-Compatible 模型 stub：供前端闭环 E2E 本地验证使用。

覆盖三类模型能力：
- ChatModel：普通回答 + SSE 流式（/v1/chat/completions，stream=true）
- Utility（Router / Extraction）：根据请求内容返回固定的 structured output
- Embedding：固定 1024 维向量（/v1/embeddings）

Router / Extraction 的返回由请求体内的 user content 决定，规则固定、可重复，
与 eval/phase-09-v1-acceptance/ 中的固定验收数据保持一致。供隔离的本地与 CI E2E 使用。

使用 ThreadingHTTPServer：后端 okhttp 复用 keep-alive 连接时会并发发请求，
单线程 http.server 无法处理（第二个请求会挂起）。

用法：python3 scripts/model-stub.py [PORT]
默认端口 18080。
"""
import json
import re
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def _embedded_json(value: str) -> str:
    """把 JSON 值作为字符串嵌入 chat completion 的 content。"""
    return json.dumps(value, ensure_ascii=False)


class Handler(BaseHTTPRequestHandler):
    # HTTP/1.1 + keep-alive，保证 okhttp 能复用连接并正确读取响应
    protocol_version = "HTTP/1.1"
    _embedding_failure_counts = {}
    _embedding_failure_lock = threading.Lock()

    def do_GET(self):
        # 健康探测：Playwright webServer 用 GET 探测 stub 是否就绪
        if self.path == "/health":
            self._send(200, {"status": "ok"})
            return
        self._send(404, {"error": "not found"})

    def do_POST(self):
        try:
            length = int(self.headers.get("Content-Length", 0))
            body = json.loads(self.rfile.read(length) or b"{}")
            path = self.path

            if path.endswith("/embeddings"):
                self._handle_embeddings(body)
                return
            if path.endswith("/chat/completions"):
                self._handle_chat(body)
                return
            self._send(404, {"error": "not found"})
        except Exception as e:  # noqa: BLE001 - stub 必须对任何解析错误返回 400 而非中断连接
            self._send(400, {"error": f"bad request: {e}"})

    def _handle_embeddings(self, body):
        inputs = body.get("input", [])
        if isinstance(inputs, str):
            inputs = [inputs]
        failure_key = next((value for value in inputs if "Kf-故障-Embedding" in value), None)
        if failure_key is not None:
            with self._embedding_failure_lock:
                attempt = self._embedding_failure_counts.get(failure_key, 0) + 1
                self._embedding_failure_counts[failure_key] = attempt
            # KNOWLEDGE_INDEX 默认 maxRetries=3：前 4 次（首次 + 3 次自动重试）均失败，
            # 第 5 次由 Processing 页手动 Retry 触发并恢复成功。
            if attempt <= 4:
                self._send(503, {"error": "controlled embedding outage"})
                return
        data = [
            {
                "object": "embedding",
                "index": i,
                "embedding": [0.1] * 1024,
            }
            for i in range(len(inputs))
        ]
        self._send(200, {"object": "list", "data": data, "model": "text-embedding-v4"})

    def _handle_chat(self, body):
        if body.get("stream"):
            self._handle_chat_stream(body)
            return
        content = self._decide_content(body)
        self._send(200, self._completion(content))

    def _decide_content(self, body):
        messages = body.get("messages", [])
        user_text = " ".join(
            m.get("content", "")
            for m in messages
            if m.get("role") == "user" and isinstance(m.get("content"), str)
        )
        if self._is_capability_test(messages, user_text):
            sys_text = " ".join(
                m.get("content", "") for m in messages if m.get("role") == "system"
            )
            return self._capability_test_result(user_text, sys_text)
        if self._has_router_schema(messages):
            return self._router_result(user_text, messages)
        if self._has_extraction_schema(messages):
            return self._extraction_result(user_text, messages)
        return self._chat_reply(user_text, messages)

    @staticmethod
    def _is_capability_test(messages, user_text):
        """Utility 能力测试：system 是 schema 格式，user 是「Return a minimal valid JSON…」。"""
        return "minimal valid JSON" in user_text or "Return a minimal" in user_text

    def _capability_test_result(self, user_text, sys_text):
        if "candidates" in sys_text and "knowledgeBaseId" in sys_text:
            return _embedded_json({"candidates": [{"title": "x", "knowledgeBaseId": "1", "content": "x"}]})
        return _embedded_json(
            {
                "needRag": True,
                "knowledgeBaseIds": ["1"],
                "retrievalQuery": "test",
                "routeScores": [{"knowledgeBaseId": "1", "score": 0.5}],
            }
        )

    @staticmethod
    def _all_text(messages):
        return "\n".join(
            m.get("content", "") for m in messages if isinstance(m.get("content"), str)
        )

    @classmethod
    def _has_router_schema(cls, messages):
        text = cls._all_text(messages)
        # RouterService 把整个 prompt（含 JSON Schema 与「知识检索路由」指示）作为 UserMessage 发送。
        # 同时兼容 Utility 能力测试（system 携带 schema）。
        return ("needRag" in text and "knowledgeBaseIds" in text) or (
            "知识检索路由" in text and "needRag" in text
        )

    @classmethod
    def _has_extraction_schema(cls, messages):
        text = cls._all_text(messages)
        return ("candidates" in text and "knowledgeBaseId" in text) or (
            "知识提取" in text and "candidates" in text
        )

    def _parse_catalog(self, messages):
        """从 prompt 解析可路由知识库目录：返回 {name: id} 与 {id: name}。"""
        text = self._all_text(messages)
        name_to_id, id_to_name = {}, {}
        for line in text.splitlines():
            line = line.strip()
            if not line.startswith("- ") or ":" not in line:
                continue
            rest = line[2:]
            id_part, _, name_part = rest.partition(":")
            name_part = name_part.strip()
            id_part = id_part.strip()
            if not id_part or not name_part:
                continue
            # 去掉「— description」描述部分
            name = name_part.split("—")[0].strip()
            name_to_id[name] = id_part
            id_to_name[id_part] = name
        return name_to_id, id_to_name

    @staticmethod
    def _catalog_id(name_to_id, expected_name):
        """兼容 Playwright retry 后缀，同时仍只匹配固定的 Kf- 验收知识库前缀。"""
        exact = name_to_id.get(expected_name)
        if exact is not None:
            return exact
        return next(
            (kb_id for name, kb_id in name_to_id.items() if name.startswith(expected_name + "-r")),
            None,
        )

    def _router_result(self, user_text, messages):
        name_to_id, _ = self._parse_catalog(messages)
        prompt_text = self._all_text(messages)
        match = re.search(r"当前问题：(.*?)\n\n输出严格 JSON", prompt_text, re.DOTALL)
        current_question = match.group(1).strip() if match else user_text
        if "Kf-" not in current_question:
            return '{"needRag":false,"knowledgeBaseIds":[],"retrievalQuery":"","routeScores":[]}'
        if "海豚" in current_question:
            kb_id = self._catalog_id(name_to_id, "Kf-后端工程规范")
            retrieval_query = "Kf-海豚-部署前备份"
            if kb_id is None:
                kb_id = self._catalog_id(name_to_id, "Kf-个人知识管理")
            if kb_id is None:
                return '{"needRag":false,"knowledgeBaseIds":[],"retrievalQuery":"","routeScores":[]}'
            return _embedded_json(
                {
                    "needRag": True,
                    "knowledgeBaseIds": [kb_id],
                    "retrievalQuery": retrieval_query,
                    "routeScores": [{"knowledgeBaseId": kb_id, "score": 0.9}],
                }
            )
        kb_id = self._catalog_id(name_to_id, "Kf-个人知识管理")
        if kb_id is None:
            return '{"needRag":false,"knowledgeBaseIds":[],"retrievalQuery":"","routeScores":[]}'
        return _embedded_json(
            {
                "needRag": True,
                "knowledgeBaseIds": [kb_id],
                "retrievalQuery": "Kf-番茄工作法-45分钟",
                "routeScores": [{"knowledgeBaseId": kb_id, "score": 0.9}],
            }
        )

    def _extraction_result(self, user_text, messages):
        name_to_id, _ = self._parse_catalog(messages)
        if "海豚" in user_text:
            kb_id = self._catalog_id(name_to_id, "Kf-后端工程规范")
            if kb_id is None:
                return '{"candidates":[]}'
            return _embedded_json(
                {
                    "candidates": [
                        {
                            "title": "Kf-海豚-部署备份要点",
                            "summary": "海豚部署前必须备份数据库、配置文件、原始数据。",
                            "knowledgeBaseIds": [kb_id],
                            "tags": ["部署", "海豚"],
                            "content": "海豚部署前必须备份：数据库、配置文件、原始数据。回滚预案：恢复数据库备份并回退配置。",
                            "reason": "可复用的部署与回滚规范",
                        }
                    ]
                }
            )
        return '{"candidates":[]}'

    def _chat_reply(self, user_text, messages):
        current_question = next(
            (
                m.get("content", "")
                for m in reversed(messages)
                if m.get("role") == "user" and isinstance(m.get("content"), str)
            ),
            "",
        )
        if "海豚" in current_question and "备份" in current_question:
            return "根据知识 [S1]，Kf-海豚-部署前必须备份：数据库、配置文件、原始数据。"
        if "回滚" in current_question and "海豚" in user_text:
            return "根据知识 [S1]，Kf-海豚-回滚预案：恢复数据库备份并回退配置。"
        if "回滚" in current_question:
            return "缺少上一轮上下文，无法确定回滚对象。"
        if "番茄" in current_question:
            return "根据知识 [S1]，Kf-番茄工作法-我实践时固定使用 45 分钟工作 + 10 分钟休息。"
        if "部署三步" in current_question or "部署" in current_question:
            return "根据知识 [S1]，Kf-海豚-部署三步：备份数据库、导出配置文件、传输原始数据。"
        if "Kf-" in current_question:
            if "Kf-慢速" in current_question:
                return "Kf-慢速回答-" * 40
            return "根据知识 [S1]，这是我沉淀的个人知识。"
        return "你好！我可以帮你沉淀知识。"

    def _completion(self, content):
        return {
            "id": "chatcmpl-stub",
            "object": "chat.completion",
            "created": 1700000000,
            "model": "stub-model",
            "choices": [
                {
                    "index": 0,
                    "message": {"role": "assistant", "content": content},
                    "finish_reason": "stop",
                }
            ],
            "usage": {"prompt_tokens": 9, "completion_tokens": 12, "total_tokens": 21},
        }

    def _handle_chat_stream(self, body):
        content = self._decide_content(body)
        parts = []
        # 逐字符发送完整内容，保证后端拼接后 completed 事件携带完整回答
        for ch in content:
            parts.append(
                'data: ' + json.dumps({
                    "id": "chatcmpl-stub",
                    "object": "chat.completion.chunk",
                    "model": "stub-model",
                    "choices": [{"index": 0, "delta": {"content": ch}, "finish_reason": None}],
                }) + '\n\n'
            )
        parts.append('data: [DONE]\n\n')
        payload = "".join(parts).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        messages = body.get("messages", [])
        current_question = next(
            (
                m.get("content", "")
                for m in reversed(messages)
                if m.get("role") == "user" and isinstance(m.get("content"), str)
            ),
            "",
        )
        try:
            if "Kf-慢速" in current_question:
                # 逐事件 flush，给浏览器明确的 stop/断连窗口；不使用固定等待做测试断言。
                for part in parts:
                    self.wfile.write(part.encode("utf-8"))
                    self.wfile.flush()
                    time.sleep(0.03)
            else:
                self.wfile.write(payload)
        except (BrokenPipeError, ConnectionResetError, OSError):
            # 受控模拟客户端中断；连接断开本身就是预期故障，不再尝试写错误响应。
            return

    def _send(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18080
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
