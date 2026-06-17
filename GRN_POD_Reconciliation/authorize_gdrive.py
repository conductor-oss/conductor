import os
from pathlib import Path

from dotenv import load_dotenv
from google_auth_oauthlib.flow import InstalledAppFlow

from worker import DRIVE_SCOPES, required_env, resolve_path


ROOT = Path(__file__).resolve().parent


def main() -> None:
    load_dotenv(ROOT / ".env")
    token_path = resolve_path(os.getenv("GOOGLE_TOKEN_FILE"), "token.json")
    token_path.parent.mkdir(parents=True, exist_ok=True)

    client_secret_path = required_env("GOOGLE_CLIENT_SECRET_FILE")
    redirect_port = int(os.getenv("GOOGLE_OAUTH_REDIRECT_PORT", "8089"))

    print(f"Starting one-time Google Drive OAuth authorization on port {redirect_port}.")
    print(f"Token will be saved to {token_path}.")
    flow = InstalledAppFlow.from_client_secrets_file(client_secret_path, DRIVE_SCOPES)
    credentials = flow.run_local_server(
        port=redirect_port,
        open_browser=True,
        access_type="offline",
        include_granted_scopes="true",
        prompt="consent",
    )
    token_path.write_text(credentials.to_json(), encoding="utf-8")
    print(f"Saved Google Drive OAuth token to {token_path}.")


if __name__ == "__main__":
    main()
