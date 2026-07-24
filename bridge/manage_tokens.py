#!/usr/bin/env python3
"""
Usage:
  python manage_tokens.py add <device-name>
  python manage_tokens.py list
  python manage_tokens.py revoke <device-name>
"""
import json
import sys
from pathlib import Path

from config import settings
from tokenstore import TokenStore


def _load(path: Path) -> dict[str, str]:
    try:
        return json.loads(path.read_text())
    except (FileNotFoundError, json.JSONDecodeError):
        return {}


def _save(path: Path, data: dict[str, str]) -> None:
    path.write_text(json.dumps(data, indent=2))
    path.chmod(0o600)


def main(argv: list[str]) -> int:
    path = settings.auth_file
    if len(argv) < 1:
        print(__doc__)
        return 2
    cmd = argv[0]
    data = _load(path)

    if cmd == "add" and len(argv) == 2:
        name = argv[1]
        token = TokenStore.generate_token()
        data[token] = name
        _save(path, data)
        print(f"Device '{name}' provisioned. Bearer token:\n\n  {token}\n")
        return 0

    if cmd == "list":
        for tok, name in data.items():
            print(f"{name}\t{tok[:8]}…")
        return 0

    if cmd == "revoke" and len(argv) == 2:
        name = argv[1]
        removed = [t for t, n in data.items() if n == name]
        for t in removed:
            del data[t]
        _save(path, data)
        print(f"Revoked {len(removed)} token(s) for '{name}'.")
        return 0

    print(__doc__)
    return 2


if __name__ == "__main__":
    main(sys.argv[1:])
