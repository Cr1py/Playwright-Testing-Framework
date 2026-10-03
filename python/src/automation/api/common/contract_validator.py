from __future__ import annotations

from pathlib import Path
from typing import Any

import yaml
from jsonschema import Draft202012Validator, FormatChecker


class ContractValidator:
    """validates API responses against an OpenAPI 3.1 document"""

    def __init__(self, spec_path: Path) -> None:
        self._spec: dict[str, Any] = yaml.safe_load(
            Path(spec_path).read_text(encoding="utf-8")
        )
        self._cache: dict[str, Draft202012Validator] = {}

    def assert_response(
        self, method: str, path_template: str, status: int, body: Any
    ) -> None:
        """raises AssertionError if the status is not declared for the operation or the body does not
        match the declared JSON schema. Statuses without a body schema only check the status

        ``path_template`` is the path as written in the spec, ex: ``"/users/{id}"``
        """
        operation = (
            self._spec.get("paths", {}).get(path_template, {}).get(method.lower())
        )
        if operation is None:
            raise AssertionError(
                f"Contract has no operation: {method.upper()} {path_template}"
            )

        responses = operation.get("responses", {})
        response = responses.get(str(status)) or responses.get("default")
        if response is None:
            declared = ", ".join(responses)
            raise AssertionError(
                f"{method.upper()} {path_template}: status {status} is not in the contract (declared: {declared})"
            )

        schema = response.get("content", {}).get("application/json", {}).get("schema")
        if schema is None:
            return

        key = f"{method.lower()} {path_template} {status}"
        validator = self._cache.get(key)
        if validator is None:
            # attach components so "#/components/schemas/..." references resolve
            validator = Draft202012Validator(
                {**schema, "components": self._spec.get("components", {})},
                format_checker=FormatChecker(),
            )
            self._cache[key] = validator

        errors = sorted(
            validator.iter_errors(body), key=lambda e: list(e.absolute_path)
        )
        if errors:
            details = "; ".join(
                f"{'/'.join(map(str, e.absolute_path)) or 'body'}: {e.message}"
                for e in errors
            )
            raise AssertionError(
                f"{method.upper()} {path_template} {status} violates the contract: {details}"
            )
