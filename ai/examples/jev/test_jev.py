from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import threading
import unittest

import jev as w


class JevFixture(unittest.TestCase):
    def setUp(self):
        self.calls = []
        self.data = {"model": "jev-1.13", "state": "Duplicate invoice charge.", "questions": {
            "team": {"type": "choice", "instructions": "Select the responsible team.",
                     "criteria": {"billing": "Payment issue", "technical": "Software issue"}}}}
        self.response = {"model": "typesafe/jev-test", "provider": "TypeSafe",
                         "answers": {"team": {"type": "choice", "choice": "billing", "confidence": 0.9}},
                         "usage": {"input_tokens": 40, "output_tokens": 10, "cost": 0.00001}}
        self.status = 200
        owner = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_POST(self):
                body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                owner.calls.append((self.path, body, self.headers.get("Authorization")))
                self.send_response(owner.status)
                if owner.status == 302:
                    self.send_header("Location", owner.url + "/redirected")
                self.end_headers()
                self.wfile.write(json.dumps(owner.response).encode())

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.url = "http://127.0.0.1:" + str(self.server.server_port)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.client = w.JevClient("test-only-key", endpoint=self.url + "/v1/systemone")

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()


class JevTest(JevFixture):
    def test_typed_payload_auth_revision_and_cost(self):
        result = self.client.decide(self.data)
        self.assertEqual(("/v1/systemone", self.data, "Bearer test-only-key"), self.calls[0])
        self.assertEqual("billing", result["answers"]["team"]["choice"])
        self.assertEqual("typesafe/jev-test", result["model"])
        self.assertEqual(0.00001, result["usage"]["cost"])
        self.assertGreaterEqual(result["latencyMs"], 0)

    def test_score_and_noul(self):
        self.data["questions"] = {
            "severity": {"type": "score", "instructions": "Rate severity.", "criteria": ["Low", "High"]},
            "urgent": {"type": "noul", "instructions": "Is the issue urgent?"}}
        self.response["answers"] = {"severity": {"type": "score", "score": 0.7},
                                    "urgent": {"type": "noul", "noul": 0.8}}
        self.assertEqual(self.response["answers"], self.client.decide(self.data)["answers"])

    def test_invalid_input_does_not_call_provider(self):
        self.data["questions"]["team"]["criteria"] = {}
        with self.assertRaises(ValueError):
            self.client.decide(self.data)
        self.assertEqual([], self.calls)

    def test_unknown_response_choice_rejected(self):
        self.response["answers"]["team"]["choice"] = "invented"
        with self.assertRaisesRegex(w.ConnectionError, "invalid_choice"):
            self.client.decide(self.data)

    def test_missing_answer_rejected(self):
        self.response["answers"] = {}
        with self.assertRaisesRegex(w.ConnectionError, "invalid_response"):
            self.client.decide(self.data)

    def test_malformed_probability_rejected(self):
        self.response["answers"]["team"]["confidence"] = float("nan")
        with self.assertRaisesRegex(w.ConnectionError, "invalid_confidence"):
            self.client.decide(self.data)

    def test_http_errors_do_not_expose_response_body(self):
        self.status, self.response = 401, {"error": "private-provider-body"}
        with self.assertRaisesRegex(w.ConnectionError, "^http_status_401$"):
            self.client.decide(self.data)
        self.assertEqual(1, len(self.calls))

    def test_redirects_do_not_forward_credentials(self):
        self.status = 302
        with self.assertRaisesRegex(w.ConnectionError, "^http_status_302$"):
            self.client.decide(self.data)
        self.assertEqual(1, len(self.calls))



if __name__ == "__main__":
    unittest.main()
