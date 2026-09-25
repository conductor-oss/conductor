import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer

from jev_agent import create_request, invoke_agent


class AgentTest(unittest.TestCase):
    def test_example_uses_agent_api_for_compile_and_start(self):
        received = []

        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                received.append((self.path, body))
                self.send_response(200)
                self.end_headers()
                self.wfile.write(b'{}')

            def log_message(self, *args):
                pass

        server = HTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            url = f"http://127.0.0.1:{server.server_port}/api"
            invoke_agent(url, "plan", "Duplicate charge")
            invoke_agent(url, "run", "Duplicate charge")
        finally:
            server.shutdown()
            server.server_close()
            thread.join()
        self.assertEqual(["/api/agent/compile", "/api/agent/start"], [p for p, _ in received])
        for _, body in received:
            self.assertEqual("jev", body["agentConfig"]["kind"])
            self.assertEqual("jev-1.13", body["agentConfig"]["model"])
            self.assertNotIn("tools", body["agentConfig"])
            self.assertEqual("Duplicate charge", body["prompt"])

    def test_rejects_empty_state(self):
        with self.assertRaises(ValueError):
            create_request(" ")


if __name__ == "__main__":
    unittest.main()
