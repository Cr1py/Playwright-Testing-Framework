from __future__ import annotations

from playwright.sync_api import APIRequestContext

from automation.api.common.base_api_client import ApiResult, BaseApiClient
from automation.config.settings import ProjectConfig, require_credentials


class AuthClient(BaseApiClient):
    def __init__(self, ctx: APIRequestContext, config: ProjectConfig) -> None:
        super().__init__(ctx)
        self._config = config

    def token(self, email: str, password: str) -> ApiResult:
        endpoint = self._config.api.auth.token_endpoint or "/auth/token"
        return self._send("POST", endpoint, data={"email": email, "password": password})

    def fetch_token(self) -> str:
        """token for the configured test account. Raises if the login fails"""
        email, password = require_credentials(self._config)
        res = self.token(email, password)
        if res.status != 200:
            raise RuntimeError(
                f"Could not obtain a token (HTTP {res.status}): {res.body}"
            )
        return res.body["access_token"]
