"""HTTP dependency for example 16e; uses the shared recording's response bytes.

The agent, HTTP task, credential substitution, and model playback still run on
Conductor. This fixture only replaces the external GitHub endpoint.
"""
import json
import os
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path


def github_response(recordings):
    for path in (recordings / '16e_credentials_http_tool').glob('*.json'):
        for message in json.loads(path.read_text())['request']['messages']:
            for result in message['toolResults']:
                if result['name'] == 'list_github_repos':
                    return result['value']['response']
    raise RuntimeError('Shared GitHub HTTP response is missing')


class Handler(BaseHTTPRequestHandler):
    response = None

    def do_GET(self):
        if self.path != '/users/Conductor/repos?per_page=5&sort=updated':
            self.send_error(404)
            return
        if self.headers.get('Authorization') != 'Bearer playback-test-key':
            self.send_error(401)
            return
        body = json.dumps(self.response['body'], separators=(',', ':')).encode()
        self.send_response_only(self.response['statusCode'], self.response['reasonPhrase'])
        for name, values in self.response['headers'].items():
            if name.lower() == 'content-length':
                continue
            for value in values:
                self.send_header(name, value)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == '__main__':
    Handler.response = github_response(Path(os.environ['CONDUCTOR_RECORDINGS_DIR']))
    HTTPServer(('127.0.0.1', 3002), Handler).serve_forever()
