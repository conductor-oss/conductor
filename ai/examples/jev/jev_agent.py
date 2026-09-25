"""Run or compile a Jev agent definition through Conductor's agent API."""

import argparse
import json
import os
from pathlib import Path
from urllib.request import Request, urlopen


def create_request(state: str) -> dict:
    if not isinstance(state, str) or not state.strip():
        raise ValueError("request must contain nonempty text state")
    definition = json.loads(Path(__file__).with_name("agent.json").read_text())
    return {"agentConfig": definition, "prompt": state}


def invoke_agent(server_url: str, command: str, state: str) -> dict:
    endpoint = "compile" if command == "plan" else "start"
    request = Request(
        f"{server_url.rstrip('/')}/agent/{endpoint}",
        data=json.dumps(create_request(state)).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urlopen(request, timeout=30) as response:
        return json.load(response)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("run", "plan"))
    parser.add_argument("--server-url", default=os.environ.get(
        "CONDUCTOR_SERVER_URL", "http://localhost:8080/api"))
    parser.add_argument("--request", type=Path, default=Path(__file__).with_name("request.json"))
    args = parser.parse_args()
    state = json.loads(args.request.read_text())["state"]
    print(json.dumps(invoke_agent(args.server_url, args.command, state), indent=2))


if __name__ == "__main__":
    main()
