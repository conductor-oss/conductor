"""A stand-in for a slow third-party job API.

Run it before starting the workflow:

    python3 job_stub_service.py            # http://localhost:8089

Endpoints
    POST /jobs                 -> 202, returns {"jobId": "..."} and starts a job
    GET  /jobs/{id}            -> {"jobId","state","progress","result"}
                                  state goes QUEUED -> RUNNING -> SUCCEEDED
    POST /jobs/{id}/fail       -> force the job to FAILED on its next poll
    GET  /polls                -> how many times each job has been polled

The job advances one step per poll, so a workflow that polls it will see
QUEUED, then RUNNING, then SUCCEEDED, without any wall-clock waiting.
"""

import json
import uuid
from http.server import BaseHTTPRequestHandler, HTTPServer

JOBS = {}
POLLS = {}
STATES = ["QUEUED", "RUNNING", "RUNNING", "SUCCEEDED"]


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, payload):
        body = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        path = self.path.split("?")[0]
        if path == "/jobs":
            job_id = f"job-{uuid.uuid4().hex[:8]}"
            JOBS[job_id] = {"step": 0, "failed": False}
            POLLS[job_id] = 0
            self._send(202, {"jobId": job_id, "state": "QUEUED"})
        elif path.endswith("/fail"):
            job_id = path.split("/")[2]
            if job_id in JOBS:
                JOBS[job_id]["failed"] = True
                self._send(200, {"jobId": job_id, "willFail": True})
            else:
                self._send(404, {"error": "no such job"})
        else:
            self._send(404, {"error": "not found"})

    def do_GET(self):
        path = self.path.split("?")[0]
        if path == "/polls":
            self._send(200, {"polls": POLLS})
            return
        if path.startswith("/jobs/"):
            job_id = path.split("/")[2]
            job = JOBS.get(job_id)
            if not job:
                self._send(404, {"error": "no such job"})
                return
            POLLS[job_id] = POLLS.get(job_id, 0) + 1
            if job["failed"]:
                self._send(200, {"jobId": job_id, "state": "FAILED",
                                 "progress": 100, "error": "upstream rejected the job"})
                return
            state = STATES[min(job["step"], len(STATES) - 1)]
            job["step"] += 1
            payload = {"jobId": job_id, "state": state,
                       "progress": min(100, job["step"] * 33)}
            if state == "SUCCEEDED":
                payload["result"] = {"rows": 4211, "artifact": f"s3://exports/{job_id}.csv"}
            self._send(200, payload)
            return
        self._send(404, {"error": "not found"})

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    print("job stub listening on http://localhost:8089")
    HTTPServer(("127.0.0.1", 8089), Handler).serve_forever()
