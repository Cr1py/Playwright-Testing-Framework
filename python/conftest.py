"""root fixtures and hooks: config, contract, API contexts, Allure labels, project/layer selection"""

from __future__ import annotations

import os
from pathlib import Path

import allure
import pytest
from playwright.sync_api import APIRequestContext, Playwright, expect

from automation.api.common.auth_client import AuthClient
from automation.api.common.base_api_client import api_base_url
from automation.api.common.contract_validator import ContractValidator
from automation.config.settings import ProjectConfig, load_config

LAYERS = ("ui", "api", "e2e")
TESTS_ROOT = Path(__file__).parent / "tests"
_CONFIG = load_config()  # reads PROJECT and ENV


def pytest_configure(config: pytest.Config) -> None:
    expect.set_options(timeout=_CONFIG.timeouts.expect)
    # Retries apply on CI only, like the TypeScript stack.
    if os.environ.get("CI") and not getattr(config.option, "reruns", 0):
        config.option.reruns = _CONFIG.retries


def pytest_ignore_collect(collection_path: Path) -> bool | None:
    """only collect tests/<PROJECT>/ (tests/project_a for PROJECT=project-a)"""
    if collection_path.is_dir() and collection_path.parent == TESTS_ROOT:
        return collection_path.name != _CONFIG.name.replace("-", "_")
    return None


def pytest_collection_modifyitems(items: list[pytest.Item]) -> None:
    """tag each test with its layer from its folder (ui/api/e2e) so `-m api` works"""
    for item in items:
        if item.path.parent.name in LAYERS:
            item.add_marker(getattr(pytest.mark, item.path.parent.name))


@pytest.fixture(scope="session")
def config() -> ProjectConfig:
    return _CONFIG


@pytest.fixture(scope="session")
def contract(config: ProjectConfig) -> ContractValidator:
    return ContractValidator(config.contract_path)


@pytest.fixture(scope="session")
def auth_token(playwright: Playwright, config: ProjectConfig) -> str:
    if config.api.auth.type == "none":
        return ""
    ctx = playwright.request.new_context(base_url=api_base_url(config))
    try:
        return AuthClient(ctx, config).fetch_token()
    finally:
        ctx.dispose()


@pytest.fixture
def anonymous_api(playwright: Playwright, config: ProjectConfig):
    """API context with no credentials, for 401/403 checks"""
    ctx = playwright.request.new_context(
        base_url=api_base_url(config), timeout=config.api.timeouts.request
    )
    yield ctx
    ctx.dispose()


@pytest.fixture
def api(
    playwright: Playwright, config: ProjectConfig, auth_token: str
) -> APIRequestContext:
    """API context authenticated as the configured test account"""
    headers = {"Authorization": f"Bearer {auth_token}"} if auth_token else {}
    ctx = playwright.request.new_context(
        base_url=api_base_url(config),
        timeout=config.api.timeouts.request,
        extra_http_headers=headers,
    )
    yield ctx
    ctx.dispose()


@pytest.fixture(autouse=True)
def _allure_labels(request: pytest.FixtureRequest, config: ProjectConfig) -> None:
    """Tags every result in the combined Allure report with project, backend language, and layer"""
    layer = next(
        (m.name for m in request.node.iter_markers() if m.name in LAYERS), "unknown"
    )
    allure.dynamic.parent_suite("python")
    allure.dynamic.label("project", config.name)
    allure.dynamic.label("backend", config.backend.language)
    allure.dynamic.label("layer", layer)
