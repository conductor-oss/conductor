"""Exercise the shared verifier through its HTTP interface."""
import copy
import json
import os
from pathlib import Path
import subprocess
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

SCRIPT = Path(__file__).with_name('check-playback.sh')


def rejection():
    return {
        'workflowId': 'arbitrary-id', 'workflowType': 'any-agent', 'status': 'FAILED',
        'reasonForIncompletion': 'policy rejected the response',
        'tasks': [
            {'taskType': 'LLM_CHAT_COMPLETE', 'status': 'COMPLETED'},
            {'referenceTaskName': 'decision__1', 'status': 'COMPLETED',
             'workflowTask': {'taskReferenceName': 'decision'},
             'outputData': {'result': {'guardrail_name': 'any-policy', 'passed': False,
                                       'on_fail': 'raise', 'message': 'policy rejected the response'}}},
            {'taskType': 'TERMINATE', 'status': 'COMPLETED',
             'inputData': {'terminationStatus': 'FAILED', 'terminationReason': 'policy rejected the response'},
             'workflowTask': {'inputParameters': {'terminationReason': '${decision.output.result.message}'}}},
        ],
    }


class PlaybackCheckTest(unittest.TestCase):
    def verify(self, workflows, total=None):
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                url = urlparse(self.path)
                if url.path == '/api/workflow/search':
                    start = int(parse_qs(url.query)['start'][0])
                    response = {'results': workflows[start:start + 100],
                                'totalHits': len(workflows) if total is None else total}
                else:
                    workflow_id = url.path.rsplit('/', 1)[-1]
                    response = next(w for w in workflows if w['workflowId'] == workflow_id)
                self.send_response(200)
                self.end_headers()
                self.wfile.write(json.dumps(response).encode())

            def log_message(self, *_args):
                pass

        with HTTPServer(('127.0.0.1', 0), Handler) as server:
            thread = threading.Thread(target=server.serve_forever)
            thread.start()
            try:
                return subprocess.run(
                    ['sh', str(SCRIPT), f'http://127.0.0.1:{server.server_port}/api'],
                    env=os.environ, capture_output=True, text=True, timeout=30,
                )
            finally:
                server.shutdown()
                thread.join()

    def assert_failed(self, workflow):
        self.assertNotEqual(self.verify([workflow]).returncode, 0)

    def test_completed_workflows_pass(self):
        self.assertEqual(self.verify([]).returncode, 0)

    def test_guardrail_rejection_passes_without_sdk_metadata(self):
        result = self.verify([rejection()])
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_worker_guardrail_rejection_passes(self):
        workflow = rejection()
        guard = workflow['tasks'][1]
        guard['outputData'] = guard['outputData']['result']
        workflow['tasks'][2]['workflowTask']['inputParameters']['terminationReason'] = '${decision.output.message}'
        self.assertEqual(self.verify([workflow]).returncode, 0)

    def test_unrelated_failure_is_not_a_guardrail_rejection(self):
        self.assert_failed({'workflowId': 'broken', 'status': 'FAILED', 'tasks': []})

    def test_failed_llm_task_fails_even_with_guardrail_output(self):
        workflow = rejection()
        workflow['tasks'][0]['status'] = 'FAILED'
        self.assert_failed(workflow)

    def test_rejection_followed_by_an_unrelated_failure_fails(self):
        workflow = rejection()
        workflow['reasonForIncompletion'] = 'worker crashed'
        self.assert_failed(workflow)

    def test_termination_must_reference_the_rejecting_guardrail(self):
        workflow = rejection()
        workflow['tasks'][2]['workflowTask']['inputParameters']['terminationReason'] = '${another_task.output.message}'
        self.assert_failed(workflow)

    def test_missing_or_incomplete_termination_fails(self):
        workflow = rejection()
        workflow['tasks'].pop()
        self.assert_failed(workflow)
        workflow = rejection()
        workflow['tasks'][2]['status'] = 'IN_PROGRESS'
        self.assert_failed(workflow)

    def test_non_rejecting_decisions_fail(self):
        for field, value in [('passed', True), ('on_fail', 'retry'), ('guardrail_name', '')]:
            with self.subTest(field=field):
                workflow = rejection()
                workflow['tasks'][1]['outputData']['result'][field] = value
                self.assert_failed(workflow)

    def test_unfinished_and_other_terminal_states_fail(self):
        for status in ['RUNNING', 'PAUSED', 'TERMINATED', 'TIMED_OUT']:
            with self.subTest(status=status):
                workflow = rejection()
                workflow['status'] = status
                self.assert_failed(workflow)

    def test_unrelated_failure_after_first_page_fails(self):
        workflows = []
        for index in range(100):
            workflow = copy.deepcopy(rejection())
            workflow['workflowId'] = str(index)
            workflows.append(workflow)
        workflows.append({'workflowId': 'broken', 'status': 'FAILED', 'tasks': []})
        result = self.verify(workflows)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('broken', result.stdout)

    def test_incomplete_search_page_fails(self):
        self.assertNotEqual(self.verify([], total=1).returncode, 0)


if __name__ == '__main__':
    unittest.main()
