from __future__ import annotations

import csv
import os
import re

from automation.config.settings import REPO_ROOT

_PLACEHOLDER = re.compile(r"\{\{(\w+)\}\}")


def _interpolate(value: str) -> str:
    """replaces {{VAR}} with os.environ["VAR"] so secrets never live in data files"""

    def resolve(match: re.Match[str]) -> str:
        name = match.group(1)
        if name not in os.environ:
            raise RuntimeError(
                f"Test data references {{{{{name}}}}} but the env var is not set."
            )
        return os.environ[name]

    return _PLACEHOLDER.sub(resolve, value)


def read_csv(project: str, file: str) -> list[dict[str, str]]:
    """reads shared/test-data/<project>/<file> as a list of row dicts keyed by the header row"""
    path = REPO_ROOT / "shared" / "test-data" / project / file
    with path.open(newline="", encoding="utf-8") as handle:
        return [
            {key.strip(): _interpolate(value.strip()) for key, value in row.items()}
            for row in csv.DictReader(handle)
        ]
