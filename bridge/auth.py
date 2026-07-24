from fastapi import Header, HTTPException, status

from config import settings
from tokenstore import TokenStore

_store = TokenStore(settings.auth_file)


def require_device(authorization: str | None = Header(default=None)) -> str:
    token = None
    if authorization and authorization.lower().startswith("bearer "):
        token = authorization[7:].strip()
    device = _store.device_for(token)
    if device is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="invalid or missing bearer token",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return device
