"""overrides for pytest playwright so browser settings come from shared/config"""
import pytest

from automation.config.settings import ProjectConfig


@pytest.fixture(scope="session")
def base_url(config: ProjectConfig) -> str:
    return config.frontend.base_url


@pytest.fixture(scope="session")
def browser_context_args(browser_context_args: dict, config: ProjectConfig) -> dict:
    return {**browser_context_args, "viewport": config.viewport.model_dump()}


@pytest.fixture(scope="session")
def browser_type_launch_args(browser_type_launch_args: dict, config: ProjectConfig, pytestconfig: pytest.Config) -> dict:
    headless = config.headless and not pytestconfig.getoption("headed")
    return {**browser_type_launch_args, "headless": headless}


@pytest.fixture
def page(page, config: ProjectConfig):
    page.set_default_timeout(config.timeouts.action)
    page.set_default_navigation_timeout(config.timeouts.navigation)
    return page
