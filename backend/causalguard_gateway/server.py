"""CausalGuard 最小模型网关（Python 标准库实现，无第三方依赖）。

目标（docs/22 决策 D3）：
- 上游模型密钥只存在于服务端环境变量，不进入 APK、不进入构建配置；
- App 只持有可轮换的 gateway client token；
- 只放行受约束的 Chat Completions 字段，拒绝流式、未知字段和未授权模型；
- 不记录请求/响应正文与鉴权头，只记录 request id、路径、状态码和耗时。

这不是通用代理：没有账号体系、没有多租户、没有持久化。生产部署必须置于
TLS 反向代理之后，并配合防火墙、日志采集与密钥轮换。
"""

from __future__ import annotations

import hashlib
import hmac
import json
import logging
import math
import os
import signal
import threading
import time
import uuid
from collections import defaultdict, deque
from dataclasses import dataclass
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Deque, Dict, Mapping, Optional, Tuple
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import HTTPRedirectHandler, Request, build_opener

LOGGER = logging.getLogger("causalguard.gateway")

CHAT_PATH = "/v1/chat/completions"
HEALTH_PATH = "/healthz"

DEFAULT_MAX_BODY_BYTES = 64 * 1024
DEFAULT_MAX_UPSTREAM_BYTES = 1024 * 1024
DEFAULT_MAX_REQUESTS_PER_MINUTE = 60
DEFAULT_MAX_TOKENS = 2048
DEFAULT_MAX_MESSAGES = 40
DEFAULT_MAX_MESSAGE_CHARS = 30_000
DEFAULT_MIN_CLIENT_TOKEN_CHARS = 16

ALLOWED_REQUEST_FIELDS = {
    "model",
    "messages",
    "temperature",
    "max_tokens",
    "top_p",
    "response_format",
}
ALLOWED_MESSAGE_ROLES = {"system", "user", "assistant"}
LOCAL_HOSTS = {"127.0.0.1", "localhost", "::1"}


class ConfigError(ValueError):
    """Raised when required gateway configuration is missing or unsafe."""


@dataclass(frozen=True)
class GatewayConfig:
    upstream_base_url: str
    upstream_api_key: str
    client_tokens: frozenset[str]
    allowed_models: frozenset[str]
    host: str = "127.0.0.1"
    port: int = 8080
    upstream_timeout_seconds: float = 30.0
    max_body_bytes: int = DEFAULT_MAX_BODY_BYTES
    max_upstream_bytes: int = DEFAULT_MAX_UPSTREAM_BYTES
    max_requests_per_minute: int = DEFAULT_MAX_REQUESTS_PER_MINUTE
    max_tokens: int = DEFAULT_MAX_TOKENS
    max_messages: int = DEFAULT_MAX_MESSAGES
    max_message_chars: int = DEFAULT_MAX_MESSAGE_CHARS
    log_level: str = "INFO"

    @property
    def chat_url(self) -> str:
        return self.upstream_base_url.rstrip("/") + "/chat/completions"


def _env_int(env: Mapping[str, str], name: str, default: int, minimum: int = 0) -> int:
    raw = env.get(name, "").strip()
    if not raw:
        return default
    try:
        value = int(raw)
    except ValueError as exc:
        raise ConfigError(f"{name} must be an integer") from exc
    if value < minimum:
        raise ConfigError(f"{name} must be >= {minimum}")
    return value


def _env_float(env: Mapping[str, str], name: str, default: float, minimum: float = 0.0) -> float:
    raw = env.get(name, "").strip()
    if not raw:
        return default
    try:
        value = float(raw)
    except ValueError as exc:
        raise ConfigError(f"{name} must be a number") from exc
    if value <= minimum:
        raise ConfigError(f"{name} must be > {minimum}")
    return value


def _split_csv(raw: str) -> frozenset[str]:
    return frozenset(item.strip() for item in raw.split(",") if item.strip())


def _validate_upstream_url(raw: str) -> str:
    value = raw.strip().rstrip("/")
    parsed = urlparse(value)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise ConfigError("GATEWAY_UPSTREAM_BASE_URL must be an absolute http(s) URL")
    if parsed.username or parsed.password:
        raise ConfigError("GATEWAY_UPSTREAM_BASE_URL must not contain credentials")
    if parsed.query or parsed.fragment:
        raise ConfigError("GATEWAY_UPSTREAM_BASE_URL must not contain query or fragment")
    if parsed.scheme == "http" and parsed.hostname not in LOCAL_HOSTS:
        raise ConfigError(
            "plain http upstream is only allowed for localhost; use https for remote providers"
        )
    return value


def load_config(env: Optional[Mapping[str, str]] = None) -> GatewayConfig:
    """Load and validate configuration from environment variables."""

    source: Mapping[str, str] = os.environ if env is None else env

    upstream_base_url = _validate_upstream_url(
        source.get("GATEWAY_UPSTREAM_BASE_URL", "")
    )
    upstream_api_key = source.get("GATEWAY_UPSTREAM_API_KEY", "").strip()
    if not upstream_api_key:
        raise ConfigError("GATEWAY_UPSTREAM_API_KEY is required")

    tokens = _split_csv(source.get("GATEWAY_CLIENT_TOKENS", ""))
    if not tokens:
        raise ConfigError("GATEWAY_CLIENT_TOKENS must contain at least one token")
    too_short = [token for token in tokens if len(token) < DEFAULT_MIN_CLIENT_TOKEN_CHARS]
    if too_short:
        raise ConfigError(
            f"each GATEWAY_CLIENT_TOKENS entry must be at least "
            f"{DEFAULT_MIN_CLIENT_TOKEN_CHARS} characters"
        )

    models = _split_csv(source.get("GATEWAY_ALLOWED_MODELS", "deepseek-chat"))
    if not models:
        raise ConfigError("GATEWAY_ALLOWED_MODELS must contain at least one model")

    host = source.get("GATEWAY_HOST", "127.0.0.1").strip() or "127.0.0.1"
    port = _env_int(source, "GATEWAY_PORT", 8080, minimum=1)
    if port > 65535:
        raise ConfigError("GATEWAY_PORT must be <= 65535")

    log_level = source.get("GATEWAY_LOG_LEVEL", "INFO").strip().upper() or "INFO"
    if log_level not in {"DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"}:
        raise ConfigError("GATEWAY_LOG_LEVEL must be a standard logging level")

    return GatewayConfig(
        upstream_base_url=upstream_base_url,
        upstream_api_key=upstream_api_key,
        client_tokens=tokens,
        allowed_models=models,
        host=host,
        port=port,
        upstream_timeout_seconds=_env_float(
            source, "GATEWAY_UPSTREAM_TIMEOUT_SECONDS", 30.0
        ),
        max_body_bytes=_env_int(
            source, "GATEWAY_MAX_BODY_BYTES", DEFAULT_MAX_BODY_BYTES, minimum=1
        ),
        max_upstream_bytes=_env_int(
            source,
            "GATEWAY_MAX_UPSTREAM_BYTES",
            DEFAULT_MAX_UPSTREAM_BYTES,
            minimum=1,
        ),
        max_requests_per_minute=_env_int(
            source,
            "GATEWAY_MAX_REQUESTS_PER_MINUTE",
            DEFAULT_MAX_REQUESTS_PER_MINUTE,
            minimum=0,
        ),
        max_tokens=_env_int(source, "GATEWAY_MAX_TOKENS", DEFAULT_MAX_TOKENS, minimum=1),
        max_messages=_env_int(
            source, "GATEWAY_MAX_MESSAGES", DEFAULT_MAX_MESSAGES, minimum=1
        ),
        max_message_chars=_env_int(
            source,
            "GATEWAY_MAX_MESSAGE_CHARS",
            DEFAULT_MAX_MESSAGE_CHARS,
            minimum=1,
        ),
        log_level=log_level,
    )


class SlidingWindowRateLimiter:
    """In-memory per-token request limiter.

    The limit is intentionally simple: it protects a single gateway process from
    accidental request storms. It is not a substitute for edge rate limiting.
    """

    def __init__(self, limit: int, window_seconds: float = 60.0) -> None:
        self._limit = limit
        self._window = window_seconds
        self._events: Dict[str, Deque[float]] = defaultdict(deque)
        self._lock = threading.Lock()

    def allow(self, key: str, now: Optional[float] = None) -> Tuple[bool, int]:
        if self._limit <= 0:
            return True, 0
        current = time.monotonic() if now is None else now
        with self._lock:
            events = self._events[key]
            cutoff = current - self._window
            while events and events[0] <= cutoff:
                events.popleft()
            if len(events) >= self._limit:
                retry_after = max(1, int(math.ceil(self._window - (current - events[0]))))
                return False, retry_after
            events.append(current)
            if len(self._events) > 10_000:
                for stale_key in [k for k, v in self._events.items() if not v]:
                    del self._events[stale_key]
            return True, 0


class _NoRedirectHandler(HTTPRedirectHandler):
    """Never follow upstream redirects: a redirected request would carry the key."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: D401
        return None


_UPSTREAM_OPENER = build_opener(_NoRedirectHandler)


def _token_key(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def _bearer_token(header_value: Optional[str]) -> Optional[str]:
    if not header_value:
        return None
    scheme, separator, token = header_value.partition(" ")
    if not separator or scheme.lower() != "bearer":
        return None
    token = token.strip()
    return token or None


def _is_authorized(token: Optional[str], allowed: frozenset[str]) -> bool:
    if token is None:
        return False
    return any(hmac.compare_digest(token, candidate) for candidate in allowed)


def _request_id(header_value: Optional[str]) -> str:
    if header_value:
        candidate = header_value.strip()
        if 1 <= len(candidate) <= 64 and all(
            ch.isalnum() or ch in "._-" for ch in candidate
        ):
            return candidate
    return uuid.uuid4().hex


def _error_body(code: str, message: str, request_id: str) -> bytes:
    return json.dumps(
        {"error": {"code": code, "message": message}, "request_id": request_id},
        ensure_ascii=False,
        separators=(",", ":"),
    ).encode("utf-8")


def validate_chat_request(
    body: Mapping[str, Any], config: GatewayConfig
) -> Tuple[Optional[Dict[str, Any]], Optional[str]]:
    """Validate and rebuild the upstream payload from an allowlist.

    Unknown fields are rejected rather than silently forwarded, so a compromised
    client cannot smuggle provider-specific options (for example ``tools`` or
    ``stream``) through the gateway.
    """

    unknown = set(body) - ALLOWED_REQUEST_FIELDS
    if unknown:
        return None, f"unsupported field(s): {', '.join(sorted(unknown))}"

    model = body.get("model")
    if not isinstance(model, str) or not model.strip():
        return None, "model must be a non-empty string"
    if model not in config.allowed_models:
        return None, "model is not allowed by this gateway"

    messages = body.get("messages")
    if not isinstance(messages, list) or not messages:
        return None, "messages must be a non-empty array"
    if len(messages) > config.max_messages:
        return None, f"too many messages (max {config.max_messages})"

    total_chars = 0
    rebuilt_messages = []
    for index, message in enumerate(messages):
        if not isinstance(message, dict):
            return None, f"messages[{index}] must be an object"
        extra = set(message) - {"role", "content"}
        if extra:
            return None, f"messages[{index}] has unsupported field(s)"
        role = message.get("role")
        content = message.get("content")
        if role not in ALLOWED_MESSAGE_ROLES:
            return None, f"messages[{index}].role is invalid"
        if not isinstance(content, str) or not content:
            return None, f"messages[{index}].content must be a non-empty string"
        total_chars += len(content)
        if total_chars > config.max_message_chars:
            return None, f"message content exceeds {config.max_message_chars} characters"
        rebuilt_messages.append({"role": role, "content": content})

    payload: Dict[str, Any] = {"model": model, "messages": rebuilt_messages}

    if "temperature" in body:
        temperature = body["temperature"]
        if not isinstance(temperature, (int, float)) or isinstance(temperature, bool):
            return None, "temperature must be a number"
        if not 0.0 <= float(temperature) <= 2.0:
            return None, "temperature must be between 0 and 2"
        payload["temperature"] = float(temperature)

    if "max_tokens" in body:
        max_tokens = body["max_tokens"]
        if not isinstance(max_tokens, int) or isinstance(max_tokens, bool):
            return None, "max_tokens must be an integer"
        if max_tokens < 1 or max_tokens > config.max_tokens:
            return None, f"max_tokens must be between 1 and {config.max_tokens}"
        payload["max_tokens"] = max_tokens

    if "top_p" in body:
        top_p = body["top_p"]
        if not isinstance(top_p, (int, float)) or isinstance(top_p, bool):
            return None, "top_p must be a number"
        if not 0.0 < float(top_p) <= 1.0:
            return None, "top_p must be in (0, 1]"
        payload["top_p"] = float(top_p)

    if "response_format" in body:
        response_format = body["response_format"]
        if not isinstance(response_format, dict):
            return None, "response_format must be an object"
        extra = set(response_format) - {"type"}
        if extra:
            return None, "response_format has unsupported field(s)"
        if response_format.get("type") not in {"json_object", "text"}:
            return None, "response_format.type must be json_object or text"
        payload["response_format"] = {"type": response_format["type"]}

    return payload, None


class Gateway:
    """Turns validated requests into upstream calls and sanitized responses."""

    def __init__(self, config: GatewayConfig) -> None:
        self.config = config
        self.rate_limiter = SlidingWindowRateLimiter(config.max_requests_per_minute)

    def authorize(self, header_value: Optional[str]) -> Optional[str]:
        token = _bearer_token(header_value)
        if not _is_authorized(token, self.config.client_tokens):
            return None
        return token

    def forward(self, payload: Dict[str, Any], request_id: str) -> Tuple[int, bytes]:
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode(
            "utf-8"
        )
        request = Request(
            self.config.chat_url,
            data=body,
            method="POST",
            headers={
                "Authorization": f"Bearer {self.config.upstream_api_key}",
                "Content-Type": "application/json",
                "Accept": "application/json",
                "User-Agent": "CausalGuard-Gateway/0.1",
                "X-Request-Id": request_id,
            },
        )
        try:
            with _UPSTREAM_OPENER.open(
                request, timeout=self.config.upstream_timeout_seconds
            ) as response:
                raw = response.read(self.config.max_upstream_bytes + 1)
                if len(raw) > self.config.max_upstream_bytes:
                    LOGGER.warning(
                        "upstream response too large request_id=%s", request_id
                    )
                    return HTTPStatus.BAD_GATEWAY, _error_body(
                        "upstream_response_too_large",
                        "upstream response exceeded the gateway limit",
                        request_id,
                    )
                try:
                    parsed = json.loads(raw.decode("utf-8"))
                except (UnicodeDecodeError, json.JSONDecodeError):
                    LOGGER.warning(
                        "upstream returned non-json request_id=%s", request_id
                    )
                    return HTTPStatus.BAD_GATEWAY, _error_body(
                        "upstream_invalid_response",
                        "upstream returned an invalid response",
                        request_id,
                    )
                return response.status, json.dumps(
                    parsed, ensure_ascii=False, separators=(",", ":")
                ).encode("utf-8")
        except HTTPError as error:
            LOGGER.warning(
                "upstream http error request_id=%s status=%s",
                request_id,
                error.code,
            )
            return HTTPStatus.BAD_GATEWAY, _error_body(
                "upstream_error",
                f"upstream returned HTTP {error.code}",
                request_id,
            )
        except URLError as error:
            reason = getattr(error, "reason", error)
            timed_out = "timed out" in str(reason).lower()
            LOGGER.warning(
                "upstream unavailable request_id=%s timeout=%s", request_id, timed_out
            )
            if timed_out:
                return HTTPStatus.GATEWAY_TIMEOUT, _error_body(
                    "upstream_timeout", "upstream request timed out", request_id
                )
            return HTTPStatus.BAD_GATEWAY, _error_body(
                "upstream_unavailable", "upstream is unavailable", request_id
            )
        except TimeoutError:
            LOGGER.warning("upstream timeout request_id=%s", request_id)
            return HTTPStatus.GATEWAY_TIMEOUT, _error_body(
                "upstream_timeout", "upstream request timed out", request_id
            )


class GatewayHTTPServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, config: GatewayConfig) -> None:
        self.gateway = Gateway(config)
        super().__init__((config.host, config.port), GatewayRequestHandler)


class GatewayRequestHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "CausalGuardGateway/0.1"
    sys_version = ""

    def version_string(self) -> str:
        return self.server_version

    @property
    def gateway(self) -> Gateway:
        server = self.server
        assert isinstance(server, GatewayHTTPServer)
        return server.gateway

    def log_message(self, format: str, *args: Any) -> None:  # noqa: A002
        # Deliberately disable BaseHTTPRequestHandler's unstructured access log.
        return

    def _send_json(
        self,
        status: int,
        body: bytes,
        headers: Optional[Dict[str, str]] = None,
        request_id: Optional[str] = None,
    ) -> None:
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        if request_id:
            self.send_header("X-Request-Id", request_id)
        for name, value in (headers or {}).items():
            self.send_header(name, value)
        self.end_headers()
        self.wfile.write(body)

    def _send_error(
        self,
        status: int,
        code: str,
        message: str,
        request_id: str,
        headers: Optional[Dict[str, str]] = None,
    ) -> None:
        self._send_json(
            status,
            _error_body(code, message, request_id),
            headers,
            request_id=request_id,
        )

    def do_GET(self) -> None:  # noqa: N802
        request_id = _request_id(self.headers.get("X-Request-Id"))
        if self.path.split("?", 1)[0] == HEALTH_PATH:
            body = json.dumps(
                {"status": "ok", "service": "causalguard-gateway"},
                separators=(",", ":"),
            ).encode("utf-8")
            self._send_json(HTTPStatus.OK, body, request_id=request_id)
            return
        self._send_error(
            HTTPStatus.NOT_FOUND, "not_found", "endpoint not found", request_id
        )

    def do_POST(self) -> None:  # noqa: N802
        started = time.monotonic()
        request_id = _request_id(self.headers.get("X-Request-Id"))
        path = self.path.split("?", 1)[0]
        if path != CHAT_PATH:
            self._send_error(
                HTTPStatus.NOT_FOUND, "not_found", "endpoint not found", request_id
            )
            self._log_access("POST", path, HTTPStatus.NOT_FOUND, started, request_id)
            return

        client_token = self.gateway.authorize(self.headers.get("Authorization"))
        if client_token is None:
            self._send_error(
                HTTPStatus.UNAUTHORIZED,
                "unauthorized",
                "missing or invalid gateway token",
                request_id,
                headers={"WWW-Authenticate": "Bearer"},
            )
            self._log_access("POST", path, HTTPStatus.UNAUTHORIZED, started, request_id)
            return

        allowed, retry_after = self.gateway.rate_limiter.allow(_token_key(client_token))
        if not allowed:
            self._send_error(
                HTTPStatus.TOO_MANY_REQUESTS,
                "rate_limited",
                "too many requests",
                request_id,
                headers={"Retry-After": str(retry_after)},
            )
            self._log_access(
                "POST", path, HTTPStatus.TOO_MANY_REQUESTS, started, request_id
            )
            return

        length_header = self.headers.get("Content-Length")
        if length_header is None:
            self._send_error(
                HTTPStatus.LENGTH_REQUIRED,
                "length_required",
                "Content-Length is required",
                request_id,
            )
            self._log_access(
                "POST", path, HTTPStatus.LENGTH_REQUIRED, started, request_id
            )
            return
        try:
            length = int(length_header)
        except ValueError:
            length = -1
        if length < 0:
            self._send_error(
                HTTPStatus.BAD_REQUEST,
                "invalid_content_length",
                "Content-Length is invalid",
                request_id,
            )
            self._log_access("POST", path, HTTPStatus.BAD_REQUEST, started, request_id)
            return
        if length > self.gateway.config.max_body_bytes:
            self.close_connection = True
            self._send_error(
                HTTPStatus.REQUEST_ENTITY_TOO_LARGE,
                "payload_too_large",
                "request body is too large",
                request_id,
            )
            self._log_access(
                "POST", path, HTTPStatus.REQUEST_ENTITY_TOO_LARGE, started, request_id
            )
            return

        raw = self.rfile.read(length)
        if len(raw) != length:
            self.close_connection = True
            self._send_error(
                HTTPStatus.BAD_REQUEST,
                "incomplete_body",
                "request body was truncated",
                request_id,
            )
            self._log_access("POST", path, HTTPStatus.BAD_REQUEST, started, request_id)
            return

        try:
            body = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            self._send_error(
                HTTPStatus.BAD_REQUEST,
                "invalid_json",
                "request body must be valid UTF-8 JSON",
                request_id,
            )
            self._log_access("POST", path, HTTPStatus.BAD_REQUEST, started, request_id)
            return
        if not isinstance(body, dict):
            self._send_error(
                HTTPStatus.BAD_REQUEST,
                "invalid_request",
                "request body must be a JSON object",
                request_id,
            )
            self._log_access("POST", path, HTTPStatus.BAD_REQUEST, started, request_id)
            return

        payload, validation_error = validate_chat_request(
            body, self.gateway.config
        )
        if validation_error is not None:
            self._send_error(
                HTTPStatus.BAD_REQUEST,
                "invalid_request",
                validation_error,
                request_id,
            )
            self._log_access("POST", path, HTTPStatus.BAD_REQUEST, started, request_id)
            return
        assert payload is not None

        status, response_body = self.gateway.forward(payload, request_id)
        self._send_json(status, response_body, request_id=request_id)
        self._log_access("POST", path, status, started, request_id)

    def _log_access(
        self, method: str, path: str, status: int, started: float, request_id: str
    ) -> None:
        duration_ms = int((time.monotonic() - started) * 1000)
        LOGGER.info(
            "request method=%s path=%s status=%s duration_ms=%s request_id=%s",
            method,
            path,
            int(status),
            duration_ms,
            request_id,
        )


def create_server(config: GatewayConfig) -> GatewayHTTPServer:
    return GatewayHTTPServer(config)


def main() -> None:
    config = load_config()
    logging.basicConfig(
        level=getattr(logging, config.log_level),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    server = create_server(config)
    host, port = server.server_address[:2]
    LOGGER.info(
        "gateway listening host=%s port=%s upstream_host=%s models=%s",
        host,
        port,
        urlparse(config.upstream_base_url).hostname,
        ",".join(sorted(config.allowed_models)),
    )

    def _stop(signum: int, _frame: Any) -> None:
        LOGGER.info("gateway stopping signal=%s", signum)
        threading.Thread(target=server.shutdown, daemon=True).start()

    signal.signal(signal.SIGTERM, _stop)
    signal.signal(signal.SIGINT, _stop)
    try:
        server.serve_forever(poll_interval=0.5)
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
