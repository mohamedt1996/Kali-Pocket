import importlib.util
import json
import os
import subprocess
import threading
import unittest
import urllib.request
import urllib.error
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('bridge', Path(__file__).resolve().parents[1] / 'bridge/bridge.py')
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)
TOKEN = 'a' * 48

class FakeLLM(BaseHTTPRequestHandler):
    received = None
    def log_message(self, *_): pass
    def do_POST(self):
        FakeLLM.received = (self.path, json.loads(self.rfile.read(int(self.headers['Content-Length']))), self.headers.get('Authorization'))
        payload = json.dumps({'choices': [{'message': {'content': 'Hello from LAN model'}}]}).encode()
        self.send_response(200)
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

class BridgeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.llm = ThreadingHTTPServer(('127.0.0.1', 0), FakeLLM)
        threading.Thread(target=cls.llm.serve_forever, daemon=True).start()
        cls.controller = bridge.Controller(TOKEN, f'http://127.0.0.1:{cls.llm.server_port}/v1', 'test-model', 'llm-secret')
        cls.server = ThreadingHTTPServer(('127.0.0.1', 0), bridge.handler_for(cls.controller))
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()
        cls.base = f'http://127.0.0.1:{cls.server.server_port}'

    @classmethod
    def tearDownClass(cls):
        for server in (cls.server, cls.llm):
            server.shutdown()
            server.server_close()

    def request(self, path, body=None, token=TOKEN):
        headers = {'Authorization': 'Bearer ' + token}
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(self.base + path, data=data, headers=headers)
        try:
            with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(request) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            return error.code, json.load(error)

    def test_status_requires_token(self):
        self.assertEqual(self.request('/v1/status', token='bad')[0], 401)
        self.assertEqual(self.request('/v1/status', token='')[0], 401)

    def test_status_and_unknown_route(self):
        with patch.object(bridge, 'desktop_running', return_value=False):
            status, body = self.request('/v1/status')
        self.assertEqual(status, 200)
        self.assertFalse(body['desktop_running'])
        self.assertNotIn('token', body)
        self.assertNotIn('api_key', body)
        self.assertEqual(self.request('/v1/execute', {'command': 'id'})[0], 404)

    def test_chat_forwards_to_openai_endpoint(self):
        status, result = self.request('/v1/chat', {'messages': [{'role': 'user', 'content': 'Hello'}]})
        self.assertEqual(status, 200)
        self.assertEqual(result['reply'], 'Hello from LAN model')
        path, body, auth = FakeLLM.received
        self.assertEqual(path, '/v1/chat/completions')
        self.assertEqual(body['model'], 'test-model')
        self.assertFalse(body['stream'])
        self.assertEqual(auth, 'Bearer llm-secret')

    def test_invalid_messages_rejected(self):
        for messages in ([], 'bad', [{'role': 'tool', 'content': 'x'}], [{'role': 'user', 'content': 4}]):
            self.assertEqual(self.request('/v1/chat', {'messages': messages})[0], 400)

    def test_request_size_limited(self):
        self.assertEqual(self.request('/v1/chat', {'messages': 'x' * 70000})[0], 413)

    def test_missing_nethunter_reports_actionable_error(self):
        with patch.object(bridge.shutil, 'which', return_value=None):
            status, body = self.request('/v1/session/start', {})
        self.assertEqual(status, 400)
        self.assertIn('Install NetHunter', body['error'])

    def test_session_uses_fixed_command(self):
        with patch.object(bridge.shutil, 'which', return_value='/termux/nh'), patch.object(bridge.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0)) as run:
            self.assertEqual(self.request('/v1/session/start', {})[0], 202)
            self.assertTrue(self.controller.lock.acquire(timeout=2))
            self.controller.lock.release()
            self.assertEqual(run.call_args.args[0], ['nh', 'kex', 'start'])
            self.assertNotIn('shell', run.call_args.kwargs)

    def test_busy_session(self):
        with patch.object(bridge.shutil, 'which', return_value='/termux/nh'):
            self.controller.lock.acquire()
            try: self.assertEqual(self.request('/v1/session/stop', {})[0], 409)
            finally: self.controller.lock.release()

    def test_endpoint_and_token_validation(self):
        for endpoint in ('file:///etc/passwd', 'http://user:pass@host/v1', 'http://host/v1?q=x', 'http://host/#x'):
            with self.assertRaises(ValueError): bridge.validate_endpoint(endpoint)
        with self.assertRaises(ValueError): bridge.Controller('weak', 'http://localhost/v1', 'model')

    def test_android_asset_matches_source(self):
        root = Path(__file__).resolve().parents[1]
        self.assertEqual((root/'bridge/bridge.py').read_bytes(), (root/'app/src/main/assets/bridge.py').read_bytes())

if __name__ == '__main__': unittest.main()
