"""Jev System One client, independent of Conductor worker or agent orchestration."""

import json
import math
import time
from typing import Protocol
from urllib.error import HTTPError, URLError
from urllib.request import HTTPRedirectHandler, Request, build_opener


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
