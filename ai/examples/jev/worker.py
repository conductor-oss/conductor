"""Jev typed decisions through OpenRouter or TypeSafe, exposed as a Conductor worker."""

import argparse
import json
import math
import os
import signal
from pathlib import Path
import time
from typing import Protocol
from urllib.error import HTTPError, URLError
from urllib.request import HTTPRedirectHandler, Request, build_opener


from conductor.client.automator.task_handler import TaskHandler
from conductor.client.configuration.configuration import Configuration
from conductor.client.http.models.task_result_status import TaskResultStatus
from conductor.client.worker.worker_interface import WorkerInterface


class ConnectionError(ValueError):
    """Sanitized error suitable for task output; never contains a response body or key."""


class NoRedirects(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Credentials must stay with the explicitly configured destination.
        return None


def request(url, payload=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = Request(
        url,
        data=None if payload is None else json.dumps(payload, allow_nan=False).encode(),
        headers=headers,
    )
    try:
        with build_opener(NoRedirects()).open(req, timeout=20) as response:
            raw = response.read(2_000_001)
            if len(raw) > 2_000_000:
                raise ConnectionError("response_too_large")
            if not raw:
                return None
            try:
                return json.loads(raw)
            except (ValueError, UnicodeError):
                raise ConnectionError("invalid_json_response") from None
    except HTTPError as error:
        code = error.code
        error.close()
        raise ConnectionError("http_status_" + str(code)) from None
    except (URLError, TimeoutError, OSError):
        raise ConnectionError("transport_failed") from None


def number(value):
    return type(value) in (int, float) and math.isfinite(value)


def validate_input(data):
    if not isinstance(data, dict) or set(data) != {"model", "state", "questions"}:
        raise ValueError("model_state_questions_required")
    if not isinstance(data["model"], str) or not data["model"].strip():
        raise ValueError("model_required")
    if not isinstance(data["state"], str) or not data["state"].strip():
        raise ValueError("nonempty_text_state_required")
    questions = data["questions"]
    if not isinstance(questions, dict) or not questions:
        raise ValueError("questions_required")
    for name, question in questions.items():
        if not isinstance(name, str) or not name.strip() or not isinstance(question, dict):
            raise ValueError("invalid_question")
        if set(question) - {"type", "instructions", "criteria"}:
            raise ValueError("unsupported_question_field")
        if not isinstance(question.get("instructions"), str) or not question["instructions"].strip():
            raise ValueError("instructions_required")
        kind, criteria = question.get("type"), question.get("criteria")
        if kind == "choice":
            if (not isinstance(criteria, dict) or not 2 <= len(criteria) <= 255
                    or not all(isinstance(k, str) and k.strip() and isinstance(v, str)
                               and v.strip() for k, v in criteria.items())):
                raise ValueError("invalid_choice_criteria")
        elif kind == "score":
            if (not isinstance(criteria, list) or not 2 <= len(criteria) <= 10
                    or not all(isinstance(v, str) and v.strip() for v in criteria)):
                raise ValueError("invalid_score_criteria")
        elif kind == "noul":
            if criteria is not None and (not isinstance(criteria, dict)
                    or set(criteria) != {"true", "false"}
                    or not all(isinstance(v, str) and v.strip() for v in criteria.values())):
                raise ValueError("invalid_noul_criteria")
        else:
            raise ValueError("unsupported_question_type")


def validate_response(data, result):
    """Type constraints do not guarantee a correct decision; validate the wire contract too."""
    if (not isinstance(result, dict) or not isinstance(result.get("model"), str)
            or not result["model"] or not isinstance(result.get("answers"), dict)
            or set(result["answers"]) != set(data["questions"])):
        raise ConnectionError("invalid_response")
    if not isinstance(result.get("usage", {}), dict):
        raise ConnectionError("invalid_usage")
    for name, question in data["questions"].items():
        answer = result["answers"][name]
        kind = question["type"]
        if not isinstance(answer, dict) or answer.get("type") != kind:
            raise ConnectionError("invalid_answer_type")
        if kind == "choice" and answer.get("choice") not in question["criteria"]:
            raise ConnectionError("invalid_choice")
        if kind in ("noul", "score"):
            upper = 1 if kind == "noul" else len(question["criteria"]) - 1
            if not number(answer.get(kind)) or not 0 <= answer[kind] <= upper:
                raise ConnectionError("invalid_numeric_answer")
        if "confidence" in answer and (
                not number(answer["confidence"]) or not 0 <= answer["confidence"] <= 1):
            raise ConnectionError("invalid_confidence")


class DecisionProvider(Protocol):
    def decide(self, data: dict) -> dict: ...


class JevClient:
    ENDPOINTS = {
        "openrouter": "https://openrouter.ai/api/v1/systemone",
        "typesafe": "https://api.typesafe.ai/v1/systemone",
    }

    def __init__(self, key, route="openrouter", endpoint=None):
        if not key or not key.strip():
            raise ValueError("api_key_required")
        self.key = key.strip()
        self.endpoint = endpoint or self.ENDPOINTS[route]

    def decide(self, data):
        validate_input(data)
        start = time.monotonic()
        result = request(self.endpoint, data, self.key)
        validate_response(data, result)
        # Preserve the provider's actual revision and billing metadata, without logging state.
        return {
            "model": result["model"],
            "answers": result["answers"],
            "usage": result.get("usage", {}),
            "id": result.get("id"),
            "provider": result.get("provider"),
            "latencyMs": (time.monotonic() - start) * 1000,
        }


class JevWorker(WorkerInterface):
    def __init__(self, provider: DecisionProvider, worker_id="jev-worker", domain=None):
        super().__init__("jev_decision")
        self.provider = provider
        self.worker_id = worker_id
        self.domain = domain
        self.poll_interval = 1000

    def get_identity(self):
        return self.worker_id

    def execute(self, task):
        # Return typed, sanitized failures; the SDK owns polling and result delivery.
        result = self.get_task_result_from_task(task)
        result.output_data = {}
        try:
            result.output_data = self.provider.decide(task.input_data)
            result.status = TaskResultStatus.COMPLETED
        except ConnectionError as error:
            result.status = TaskResultStatus.FAILED
            result.reason_for_incompletion = str(error)
        except (ValueError, TypeError, KeyError):
            result.status = TaskResultStatus.FAILED_WITH_TERMINAL_ERROR
            result.reason_for_incompletion = "invalid_decision_input_or_response"
        return result


def conductor_configuration(api=None):
    configuration = Configuration(server_api_url=api, log_level="WARNING")
    token = os.environ.get("CONDUCTOR_AUTH_TOKEN")
    if token:
        configuration.update_token(token)
    return configuration


def stop_worker(signum, frame):
    # Unwind the TaskHandler context on SIGTERM as well as Ctrl-C.
    raise KeyboardInterrupt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("smoke", "worker"))
    parser.add_argument("--route", choices=tuple(JevClient.ENDPOINTS), default="openrouter")
    parser.add_argument("--key-file", type=Path)
    parser.add_argument("--request", type=Path, default=Path(__file__).with_name("request.json"))
    parser.add_argument("--api", help="Conductor API URL; otherwise use SDK environment/defaults")
    parser.add_argument("--worker-id", default="jev-worker")
    parser.add_argument("--domain")
    args = parser.parse_args()
    try:
        key = (args.key_file.read_text().strip() if args.key_file else os.environ.get(
            "OPENROUTER_API_KEY" if args.route == "openrouter" else "TYPESAFE_API_KEY"))
        client = JevClient(key, args.route)
        if args.command == "smoke":
            result = client.decide(json.loads(args.request.read_text()))
            print(json.dumps(result, indent=2))
        else:
            signal.signal(signal.SIGTERM, stop_worker)
            with TaskHandler(
                workers=[JevWorker(client, args.worker_id, args.domain)],
                configuration=conductor_configuration(args.api),
                scan_for_annotated_workers=False,
            ) as handler:
                handler.start_processes()
                handler.join_processes()
    except KeyboardInterrupt:
        pass
    except (ValueError, OSError, TypeError, KeyError):
        # Do not print file contents, authentication headers, or provider response bodies.
        parser.exit(1, "Jev operation failed; check credentials, request format, endpoint access, and Conductor task status.\n")


if __name__ == "__main__":
    main()
