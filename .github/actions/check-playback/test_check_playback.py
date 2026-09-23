"""Exercise the verifier against an HTTP server serving workflow search results."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer

SCRIPT = Path(__file__).with_name('check-playback.sh')


class PlaybackCheckTest(unittest.TestCase):
    def verify(self, rows, expected=None, total=None):
        response = {'results': rows, 'totalHits': len(rows) if total is None else total}

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                self.send_response(200)
                self.end_headers()
                self.wfile.write(json.dumps(response).encode())

            def log_message(self, *_args):
                pass

        with HTTPServer(('127.0.0.1', 0), Handler) as server, tempfile.TemporaryDirectory() as work:
            thread = threading.Thread(target=server.serve_forever)
            thread.start()
            try:
                env = dict(os.environ)
                env.pop('CONDUCTOR_PLAYBACK_EXPECTED_FAILURES', None)
                if expected is not None:
                    path = Path(work) / 'expected.json'
                    path.write_text(json.dumps(expected))
                    env['CONDUCTOR_PLAYBACK_EXPECTED_FAILURES'] = str(path)
                return subprocess.run(
                    ['sh', str(SCRIPT), f'http://127.0.0.1:{server.server_port}/api'],
                    env=env, capture_output=True, text=True, timeout=10,
                )
            finally:
                server.shutdown()
                thread.join()

    def test_completed_workflows_pass(self):
        self.assertEqual(self.verify([]).returncode, 0)

    def test_failure_without_allowlist_fails(self):
        result = self.verify([{'workflowId': 'guard', 'status': 'FAILED'}])
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('guard', result.stdout)

    def test_exact_expected_failure_passes(self):
        result = self.verify([{'workflowId': 'guard', 'status': 'FAILED'}], ['guard'])
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_unrelated_failure_still_fails(self):
        result = self.verify([
            {'workflowId': 'guard', 'status': 'FAILED'},
            {'workflowId': 'broken', 'status': 'FAILED'},
        ], ['guard'])
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('FAIL: 1 workflows', result.stdout)
        self.assertIn('broken', result.stdout)

    def test_expected_id_in_wrong_state_still_fails(self):
        for status in ['RUNNING', 'PAUSED', 'TERMINATED', 'TIMED_OUT']:
            with self.subTest(status=status):
                result = self.verify([{'workflowId': 'guard', 'status': status}], ['guard'])
                self.assertNotEqual(result.returncode, 0)

    def test_failures_outside_first_page_still_fail(self):
        result = self.verify([{'workflowId': 'guard', 'status': 'FAILED'}], ['guard'], total=101)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('FAIL: 100 workflows', result.stdout)

    def test_invalid_allowlist_fails(self):
        for expected in [{'guard': True}, [123], ['']]:
            with self.subTest(expected=expected):
                self.assertNotEqual(self.verify([], expected).returncode, 0)


if __name__ == '__main__':
    unittest.main()
