import uuid


def unique_email(prefix: str = "qa") -> str:
    """unique per call, so parallel workers never collide on records"""
    return f"{prefix}+{uuid.uuid4().hex[:8]}@example.com"


def new_user() -> dict[str, str]:
    return {"name": f"QA {uuid.uuid4().hex[:6]}", "email": unique_email()}
