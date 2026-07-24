import json
import secrets
from pathlib import Path


class TokenStore:
    def __init__(self, path: Path):
        self._path = Path(path)
        self._tokens: dict[str, str] = {}
        self.reload()

    def reload(self) -> None:
        try:
            self._tokens = json.loads(self._path.read_text())
        except (FileNotFoundError, json.JSONDecodeError):
            self._tokens = {}

    def device_for(self, token: str | None) -> str | None:
        if not token:
            return None
        return self._tokens.get(token)

    @staticmethod
    def generate_token() -> str:
        return secrets.token_urlsafe(16)
