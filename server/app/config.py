import os
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str
    secret_key: str
    access_token_expire_minutes: int = 10080
    host: str = "0.0.0.0"
    port: int = 8000

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}


def _get_settings() -> Settings:
    database_url = os.environ.get("DATABASE_URL", "")
    secret_key = os.environ.get("SECRET_KEY", "")

    if not database_url or not secret_key:
        raise ValueError(
            "DATABASE_URL and SECRET_KEY are required.\n"
            "Please create a .env file based on .env.example"
        )

    return Settings(database_url=database_url, secret_key=secret_key)


settings = _get_settings()
