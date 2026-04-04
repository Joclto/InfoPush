from pydantic_settings import BaseSettings


_INSECURE_DEFAULT_KEY = "your-secret-key-change-this-in-production"


class Settings(BaseSettings):
    database_url: str = "postgresql+asyncpg://infopush:infopush123@localhost:5432/infopush"
    secret_key: str = _INSECURE_DEFAULT_KEY
    access_token_expire_minutes: int = 10080  # 7 days
    host: str = "0.0.0.0"
    port: int = 8000

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}

    def __init__(self, **values):
        super().__init__(**values)
        if self.secret_key == _INSECURE_DEFAULT_KEY:
            import warnings
            warnings.warn(
                "SECRET_KEY is using the insecure default value. "
                "Set SECRET_KEY in your .env file before deploying.",
                stacklevel=2,
            )


settings = Settings()
