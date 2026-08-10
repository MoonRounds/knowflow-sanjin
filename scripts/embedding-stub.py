#!/usr/bin/env python3
"""本地 Embedding stub：返回固定 1024 维向量，供前端闭环本地验证使用。

用法：python3 scripts/embedding-stub.py [PORT]
默认端口 18080。仅本地验证用途，不进入 CI。
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if not self.path.endswith("/embeddings"):
            self._send(404, {"error": "not found"})
            return
        length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(length) or b"{}")
        inputs = body.get("input", [])
        if isinstance(inputs, str):
            inputs = [inputs]
        data = [
            {
                "object": "embedding",
                "index": i,
                "embedding": [0.1] * 1024,
            }
            for i in range(len(inputs))
        ]
        self._send(200, {"object": "list", "data": data, "model": "text-embedding-v4"})

    def _send(self, status, payload):
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18080
    HTTPServer(("127.0.0.1", port), Handler).serve_forever()
