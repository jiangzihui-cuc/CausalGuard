from __future__ import annotations

import http.client
import json
import threading
import unittest
from dataclasses import replace
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Dict, List, Optional, Tuple

from causalguard_gateway.server import (
    CHAT_PATH,
    ConfigError,
    GatewayConfig,
    create_server,
    load_config,
    validate_chat_request,
)

CLIENT_TOKEN = "client-token-0123456789abcdef"
UPSTREAM_KEY = "upstream-secret-key-do-not-leak"
MODEL = "deepseek-chat"

DEFAULT_PAYLOAD: Dict[str, Any] = {
    "model": MODEL,
    "messages": [
        {"role": "system", "content": "只输出 JSON。"},
        {"role": "user", "content": "解释这条隐私事件。"},
    ],
    "temperature": 0.0,
    "response_format": {"type": "json_object"},
}


class _FakeUpstreamHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, format: str, *args: Any) -> None:  # noqa: A002
        return

    def do_POST(self) -> None:  # noqa: N802
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length)
        server = self.server
        assert isinstance(server, _FakeUpstreamServer)
        server.calls.append(
            {
                "path": self.path,
                "headers": {k.lower(): v for k, v in self.headers.items()},
                "body": raw,
            }
        )
        body = server.response_body
        self.send_response(server.response_status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


class _FakeUpstreamServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self) -> None:
        super().__init__(("127.0.0.1", 0), _FakeUpstreamHandler)
        self.calls: List[Dict[str, Any]] = []
        self.response_status = 200
        self.response_body = json.dumps(
            {
                "choices": [
                    {"message": {"content": '{"summary":"ok"}'}}
                ]
            },
            ensure_ascii=False,
        ).encode("utf-8")


def _start(server: ThreadingHTTPServer) -> ThreadingHTTPServer:
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server


def _base_config(upstream_port: int, **overrides: Any) -> GatewayConfig:
    values = dict(
        upstream_base_url=f"http://127.0.0.1:{upstream_port}/v1",
        upstream_api_key=UPSTREAM_KEY,
        client_tokens=frozenset({CLIENT_TOKEN}),
        allowed_models=frozenset({MODEL}),
        host="127.0.0.1",
        port=0,
        max_requests_per_minute=0,
    )
    values.update(overrides)
    return GatewayConfig(**values)


class GatewayServerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.upstream = _start(_FakeUpstreamServer())
        self.upstream_port = self.upstream.server_address[1]
        self.config = _base_config(self.upstream_port)
        self.gateway = _start(create_server(self.config))
        self.gateway_port = self.gateway.server_address[1]

    def tearDown(self) -> None:
        for server in (self.gateway, self.upstream):
            server.shutdown()
            server.server_close()

    def _request(
        self,
        method: str,
        path: str,
        body: Optional[bytes] = None,
        token: Optional[str] = CLIENT_TOKEN,
        headers: Optional[Dict[str, str]] = None,
        port: Optional[int] = None,
    ) -> Tuple[int, Dict[str, str], bytes]:
        connection = http.client.HTTPConnection(
            "127.0.0.1", port or self.gateway_port, timeout=5
        )
        request_headers = {"Content-Type": "application/json"}
        if token is not None:
            request_headers["Authorization"] = f"Bearer {token}"
        request_headers.update(headers or {})
        connection.request(method, path, body=body, headers=request_headers)
        response = connection.getresponse()
        response_body = response.read()
        response_headers = {k.lower(): v for k, v in response.getheaders()}
        connection.close()
        return response.status, response_headers, response_body

    def _post(self, payload: Any, **kwargs: Any) -> Tuple[int, Dict[str, str], bytes]:
        return self._request(
            "POST",
            CHAT_PATH,
            body=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            **kwargs,
        )

    def _restart_gateway(self, **overrides: Any) -> None:
        self.gateway.shutdown()
        self.gateway.server_close()
        self.gateway = _start(
            create_server(_base_config(self.upstream_port, **overrides))
        )
        self.gateway_port = self.gateway.server_address[1]

    def test_health_does_not_require_auth(self) -> None:
        status, _, body = self._request("GET", "/healthz", token=None)
        self.assertEqual(200, status)
        self.assertEqual("ok", json.loads(body)["status"])

    def test_missing_or_invalid_token_is_rejected(self) -> None:
        status, headers, body = self._post(DEFAULT_PAYLOAD, token=None)
        self.assertEqual(401, status)
        self.assertEqual("Bearer", headers["www-authenticate"])
        self.assertEqual("unauthorized", json.loads(body)["error"]["code"])

        status, _, body = self._post(DEFAULT_PAYLOAD, token="wrong-token")
        self.assertEqual(401, status)
        self.assertEqual("unauthorized", json.loads(body)["error"]["code"])
        self.assertEqual([], self.upstream.calls)

    def test_valid_request_is_forwarded_with_upstream_key(self) -> None:
        status, headers, body = self._post(
            DEFAULT_PAYLOAD, headers={"X-Request-Id": "req-123"}
        )
        self.assertEqual(200, status)
        self.assertEqual("req-123", headers["x-request-id"])
        self.assertEqual('{"summary":"ok"}', json.loads(body)["choices"][0]["message"]["content"])

        self.assertEqual(1, len(self.upstream.calls))
        call = self.upstream.calls[0]
        self.assertEqual("/v1/chat/completions", call["path"])
        self.assertEqual(f"Bearer {UPSTREAM_KEY}", call["headers"]["authorization"])
        self.assertNotIn(
            CLIENT_TOKEN, " ".join(call["headers"].values())
        )
        forwarded = json.loads(call["body"].decode("utf-8"))
        self.assertEqual(DEFAULT_PAYLOAD, forwarded)

    def test_request_id_is_sanitized_and_generated(self) -> None:
        status, headers, _ = self._post(
            DEFAULT_PAYLOAD, headers={"X-Request-Id": "bad id with spaces"}
        )
        self.assertEqual(200, status)
        self.assertNotEqual("bad id with spaces", headers["x-request-id"])
        self.assertEqual(32, len(headers["x-request-id"]))

    def test_unknown_fields_are_rejected_and_not_forwarded(self) -> None:
        payload = dict(DEFAULT_PAYLOAD, stream=True)
        status, _, body = self._post(payload)
        self.assertEqual(400, status)
        self.assertIn("unsupported field", json.loads(body)["error"]["message"])
        self.assertEqual([], self.upstream.calls)

    def test_model_allowlist_is_enforced(self) -> None:
        payload = dict(DEFAULT_PAYLOAD, model="expensive-model")
        status, _, body = self._post(payload)
        self.assertEqual(400, status)
        self.assertIn("not allowed", json.loads(body)["error"]["message"])
        self.assertEqual([], self.upstream.calls)

    def test_message_role_injection_is_rejected(self) -> None:
        payload = dict(
            DEFAULT_PAYLOAD,
            messages=[{"role": "system", "content": "x", "name": "injected"}],
        )
        status, _, _ = self._post(payload)
        self.assertEqual(400, status)
        self.assertEqual([], self.upstream.calls)

    def test_oversized_body_is_rejected_before_upstream(self) -> None:
        self._restart_gateway(max_body_bytes=32)
        status, _, body = self._post(DEFAULT_PAYLOAD)
        self.assertEqual(413, status)
        self.assertEqual("payload_too_large", json.loads(body)["error"]["code"])
        self.assertEqual([], self.upstream.calls)

    def test_upstream_error_is_sanitized(self) -> None:
        self.upstream.response_status = 401
        self.upstream.response_body = b'{"error":{"message":"secret upstream detail"}}'
        status, _, body = self._post(DEFAULT_PAYLOAD)
        self.assertEqual(502, status)
        parsed = json.loads(body)
        self.assertEqual("upstream_error", parsed["error"]["code"])
        self.assertNotIn("secret upstream detail", body.decode("utf-8"))

    def test_upstream_non_json_is_rejected(self) -> None:
        self.upstream.response_body = b"<html>not json</html>"
        status, _, body = self._post(DEFAULT_PAYLOAD)
        self.assertEqual(502, status)
        self.assertEqual("upstream_invalid_response", json.loads(body)["error"]["code"])

    def test_rate_limit_returns_retry_after(self) -> None:
        self._restart_gateway(max_requests_per_minute=2)
        self.assertEqual(200, self._post(DEFAULT_PAYLOAD)[0])
        self.assertEqual(200, self._post(DEFAULT_PAYLOAD)[0])
        status, headers, body = self._post(DEFAULT_PAYLOAD)
        self.assertEqual(429, status)
        self.assertIn("retry-after", headers)
        self.assertEqual("rate_limited", json.loads(body)["error"]["code"])

    def test_unknown_post_path_is_404(self) -> None:
        status, _, body = self._request("POST", "/v1/other", body=b"{}")
        self.assertEqual(404, status)
        self.assertEqual("not_found", json.loads(body)["error"]["code"])

    def test_missing_content_length_is_411(self) -> None:
        connection = http.client.HTTPConnection(
            "127.0.0.1", self.gateway_port, timeout=5
        )
        connection.putrequest("POST", CHAT_PATH)
        connection.putheader("Authorization", f"Bearer {CLIENT_TOKEN}")
        connection.endheaders()
        response = connection.getresponse()
        body = response.read()
        connection.close()
        self.assertEqual(411, response.status)
        self.assertEqual("length_required", json.loads(body)["error"]["code"])


class ConfigTest(unittest.TestCase):
    def _env(self, **overrides: str) -> Dict[str, str]:
        env = {
            "GATEWAY_UPSTREAM_BASE_URL": "https://api.deepseek.com/v1",
            "GATEWAY_UPSTREAM_API_KEY": UPSTREAM_KEY,
            "GATEWAY_CLIENT_TOKENS": CLIENT_TOKEN,
        }
        env.update(overrides)
        return env

    def test_loads_valid_config_with_defaults(self) -> None:
        config = load_config(self._env())
        self.assertEqual("https://api.deepseek.com/v1", config.upstream_base_url)
        self.assertEqual("https://api.deepseek.com/v1/chat/completions", config.chat_url)
        self.assertEqual(frozenset({MODEL}), config.allowed_models)
        self.assertEqual(60, config.max_requests_per_minute)

    def test_requires_upstream_key(self) -> None:
        with self.assertRaises(ConfigError):
            load_config(self._env(GATEWAY_UPSTREAM_API_KEY=""))

    def test_requires_strong_client_tokens(self) -> None:
        with self.assertRaises(ConfigError):
            load_config(self._env(GATEWAY_CLIENT_TOKENS="short"))
        with self.assertRaises(ConfigError):
            load_config(self._env(GATEWAY_CLIENT_TOKENS=""))

    def test_rejects_plain_http_for_remote_upstream(self) -> None:
        with self.assertRaises(ConfigError):
            load_config(self._env(GATEWAY_UPSTREAM_BASE_URL="http://example.com/v1"))

    def test_allows_plain_http_for_localhost_only(self) -> None:
        config = load_config(
            self._env(GATEWAY_UPSTREAM_BASE_URL="http://127.0.0.1:9000/v1")
        )
        self.assertEqual("http://127.0.0.1:9000/v1", config.upstream_base_url)

    def test_rejects_url_credentials(self) -> None:
        with self.assertRaises(ConfigError):
            load_config(
                self._env(GATEWAY_UPSTREAM_BASE_URL="https://user:pass@example.com/v1")
            )


class ValidationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.config = _base_config(1)

    def test_accepts_minimal_payload(self) -> None:
        payload, error = validate_chat_request(
            {"model": MODEL, "messages": [{"role": "user", "content": "hi"}]},
            self.config,
        )
        self.assertIsNone(error)
        self.assertEqual(
            {"model": MODEL, "messages": [{"role": "user", "content": "hi"}]}, payload
        )

    def test_caps_max_tokens(self) -> None:
        _, error = validate_chat_request(
            dict(
                DEFAULT_PAYLOAD,
                max_tokens=self.config.max_tokens + 1,
            ),
            self.config,
        )
        self.assertIsNotNone(error)

    def test_rejects_invalid_temperature(self) -> None:
        _, error = validate_chat_request(
            dict(DEFAULT_PAYLOAD, temperature=9), self.config
        )
        self.assertIsNotNone(error)


if __name__ == "__main__":
    unittest.main()
