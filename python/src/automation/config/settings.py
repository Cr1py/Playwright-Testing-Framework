"""loads shared/config for the selected PROJECT and ENV (mirrors typescript/src/config/env.ts)"""

from __future__ import annotations

import json
import os
from functools import lru_cache
from pathlib import Path
from typing import Any, Literal

from dotenv import load_dotenv
from pydantic import BaseModel, ConfigDict, Field, field_validator

#: repo root = parent of python/ (shared/ lives there).
REPO_ROOT = Path(__file__).resolve().parents[4]
load_dotenv(REPO_ROOT / ".env")


class _Model(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="forbid")


class Viewport(_Model):
    width: int
    height: int


class Timeouts(_Model):
    action: int
    navigation: int
    expect: int


class Backend(_Model):
    language: Literal["java", "python", "typescript"]
    framework: str | None = None


class Frontend(_Model):
    framework: str
    base_url: str = Field(alias="baseURL")

    @field_validator("base_url")
    @classmethod
    def _http(cls, v: str) -> str:
        if not v.startswith(("http://", "https://")):
            raise ValueError("must start with http:// or https://")
        return v


class ApiTimeouts(_Model):
    request: int


class ApiAuth(_Model):
    type: Literal["bearer", "none"]
    token_endpoint: str | None = Field(default=None, alias="tokenEndpoint")


class Api(_Model):
    base_url: str = Field(alias="baseURL")
    timeouts: ApiTimeouts
    auth: ApiAuth

    @field_validator("base_url")
    @classmethod
    def _http(cls, v: str) -> str:
        if not v.startswith(("http://", "https://")):
            raise ValueError("must start with http:// or https://")
        return v


class Credentials(_Model):
    email: str | None = None
    password: str | None = None


class ProjectConfig(_Model):
    name: str
    browser: Literal["chromium", "firefox", "webkit"]
    headless: bool
    viewport: Viewport
    timeouts: Timeouts
    retries: int = Field(ge=0)
    video: Literal["off", "on", "retain-on-failure", "on-first-retry"]
    screenshot: Literal["off", "on", "only-on-failure"]
    trace: Literal["off", "on", "retain-on-failure", "on-first-retry", "on-all-retries"]
    backend: Backend
    frontend: Frontend
    api: Api
    contract: str
    # Added by the loader
    env: str
    contract_path: Path
    credentials: Credentials


def _deep_merge(target: dict[str, Any], source: dict[str, Any]) -> dict[str, Any]:
    out = dict(target)
    for key, value in source.items():
        current = out.get(key)
        out[key] = (
            _deep_merge(current, value)
            if isinstance(value, dict) and isinstance(current, dict)
            else value
        )
    return out


def _read_json(file: Path) -> dict[str, Any]:
    if not file.exists():
        raise FileNotFoundError(f"Config file not found: {file}")
    return json.loads(file.read_text(encoding="utf-8"))


@lru_cache(maxsize=None)
def load_config(project: str | None = None, env: str | None = None) -> ProjectConfig:
    """base.json -> projects/<PROJECT>/project.json -> projects/<PROJECT>/<ENV>.json -> environment variables"""
    project = project or os.environ.get("PROJECT", "project-a")
    env = env or os.environ.get("ENV", "dev")

    config_dir = REPO_ROOT / "shared" / "config"
    project_dir = config_dir / "projects" / project
    if not project_dir.is_dir():
        known = ", ".join(sorted(p.name for p in (config_dir / "projects").iterdir()))
        raise ValueError(f'Unknown PROJECT "{project}". Known projects: {known}')

    merged = _deep_merge(
        _read_json(config_dir / "base.json"), _read_json(project_dir / "project.json")
    )
    merged = _deep_merge(merged, _read_json(project_dir / f"{env}.json"))

    if os.environ.get("FRONTEND_BASE_URL"):
        merged = _deep_merge(
            merged, {"frontend": {"baseURL": os.environ["FRONTEND_BASE_URL"]}}
        )
    if os.environ.get("API_BASE_URL"):
        merged = _deep_merge(merged, {"api": {"baseURL": os.environ["API_BASE_URL"]}})

    merged["env"] = env
    merged["contract_path"] = (REPO_ROOT / merged["contract"]).resolve()
    merged["credentials"] = {
        "email": os.environ.get("TEST_USER_EMAIL"),
        "password": os.environ.get("TEST_USER_PASSWORD"),
    }
    return ProjectConfig.model_validate(merged)


def require_credentials(config: ProjectConfig) -> tuple[str, str]:
    email, password = config.credentials.email, config.credentials.password
    if not email or not password:
        raise RuntimeError(
            "TEST_USER_EMAIL and TEST_USER_PASSWORD must be set (see .env.example)."
        )
    return email, password
