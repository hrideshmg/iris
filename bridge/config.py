from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="IRIS_", env_file=".env", extra="ignore")

    auth_file: Path = Path(__file__).parent / "auth.json"
    hermes_home: Path = Path("/home/user/.hermes")       # override via IRIS_HERMES_HOME in .env
    hermes_src: Path = Path("/home/user/.hermes/hermes-agent")  # override via IRIS_HERMES_SRC in .env
    hermes_cli: str = "hermes"
    host: str = "127.0.0.1"
    port: int = 8807
    agent_timeout_s: int = 120


settings = Settings()
