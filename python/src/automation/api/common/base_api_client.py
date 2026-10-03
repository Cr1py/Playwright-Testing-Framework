from __future__ import annotations

import json
import re
from dataclasses import dataclass
from typing import Any

import allure
from playwright.sync_api import APIRequestContext

from automation.config.settings import ProjectConfig

_SENSITIVE = re.compile(r"password|token|authorization|secret", re.I)


def api_base_url(config: ProjectConfig) -> str:
    """playwright keeps a baseURL's path only if it ends with "/" and request paths have no leading "/"."""
    return config.api.base_url.rstrip("/") + "/"


def _redact(value: Any) -> Any:
    if isinstance(value, list):
        return [_redact(v) for v in value]
    if isinstance(value, dict):
        return {
            k: "[REDACTED]" if _SENSITIVE.search(k) else _redact(v)
            for k, v in value.items()
        }
    return value


@dataclass
class ApiResult:
    status: int
    ok: bool
    body: Any


class BaseApiClient:
    """base class for API clients. Clients send requests and return results; they never assert"""

    def __init__(self, ctx: APIRequestContext) -> None:
        self._ctx = ctx

    def _send(
        self,
        method: str,
        path: str,
        *,
        data: Any = None,
        params: dict[str, Any] | None = None,
    ) -> ApiResult:
        url = path.lstrip("/")
        response = self._ctx.fetch(url, method=method, data=data, params=params)
        text = response.text()
        body = json.loads(text) if text else None
        self._attach_exchange(method, url, params, data, response.status, body)
        return ApiResult(status=response.status, ok=response.ok, body=body)

    @staticmethod
    def _attach_exchange(
        method: str, url: str, params: Any, data: Any, status: int, body: Any
    ) -> None:
        payload = _redact(
            {
                "request": {
                    "method": method,
                    "url": url,
                    "params": params,
                    "data": data,
                },
                "response": {"status": status, "body": body},
            }
        )
        try:
            allure.attach(
                json.dumps(payload, indent=2),
                name=f"{method} /{url} -> {status}",
                attachment_type=allure.attachment_type.JSON,
            )
        except Exception:  # not running inside an Allure-enabled test
            pass
