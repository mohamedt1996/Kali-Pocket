#!/usr/bin/env python3
"""Kali Pocket: authenticated, fixed-action NetHunter Rootless controller."""
import argparse
import hmac
import ipaddress
import json
import os
import shutil
import socket
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MAX_BODY = 64 * 1024


def validate_endpoint(value):
    parsed = urllib.parse.urlsplit(value)
    if parsed.scheme not in ('http', 'https') or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError('Use an http(s) server URL without credentials, query or fragment')
    return value.rstrip('/')


def desktop_running():
    return os.path.exists('/tmp/.tiny.vnc')


class Controller:
    def __init__(self, token, llm_url, model, api_key='', lan=False):
        if len(token) < 32:
            raise ValueError('Bridge token must have at least 32 characters')
        self.token, self.llm_url, self.model = token, validate_endpoint(llm_url), model
        self.api_key, self.lan = api_key, lan
        self.lock = threading.Lock()
        self.session_state, self.last_error = 'idle', None

    def status(self):
        return {'name': 'Kali Pocket', 'version': '0.1.0',
                'runtime_installed': os.path.exists('/opt/kali-pocket/desktop-ready'),
                'desktop_running': desktop_running(), 'session_action': self.session_state,
                'last_error': self.last_error, 'vnc_address': 'unix:/tmp/.tiny.vnc',
                'lan_enabled': self.lan, 'llm_url': self.llm_url, 'model': self.model}

    def session(self, action):
        if not os.path.exists('/opt/kali-pocket/start-desktop.sh'):
            raise ValueError('Install NetHunter Rootless first; see Setup')
        if not self.lock.acquire(blocking=False):
            raise BlockingIOError('Another desktop action is in progress')
        self.session_state, self.last_error = action, None
        def work():
            try:
                # No shell, no user-supplied commands. KeX password is set in Termux.
                command = ['bash', '/opt/kali-pocket/start-desktop.sh'] if action == 'start' else ['pkill', '-x', 'Xtigervnc']
                result = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                        stderr=subprocess.STDOUT, text=True, timeout=60)
                if result.returncode:
                    self.last_error = 'KeX command failed. Run nh kex passwd, then nh kex in Termux to diagnose.'
            except (OSError, subprocess.TimeoutExpired):
                self.last_error = 'KeX command could not finish. Check the Termux session.'
            finally:
                self.session_state = 'idle'
                self.lock.release()
        threading.Thread(target=work, daemon=True).start()
        return {'accepted': action}

    def chat(self, body):
        messages = body.get('messages')
        if not isinstance(messages, list) or not messages or len(messages) > 40:
            raise ValueError('Provide 1–40 messages')
        for message in messages:
            if not isinstance(message, dict) or message.get('role') not in ('user', 'assistant', 'system') or not isinstance(message.get('content'), str):
                raise ValueError('Invalid message')
        payload = json.dumps({'model': self.model, 'messages': messages, 'stream': False}).encode()
        headers = {'Content-Type': 'application/json'}
        if self.api_key:
            headers['Authorization'] = 'Bearer ' + self.api_key
        request = urllib.request.Request(self.llm_url + '/chat/completions', data=payload, headers=headers)
        # Do not forward an API key across an HTTP redirect.
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, req, fp, code, msg, headers, newurl):
                return None
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
        try:
            with opener.open(request, timeout=90) as response:
                data = response.read(1024 * 1024 + 1)
                if len(data) > 1024 * 1024:
                    raise ValueError('LLM response too large')
                answer = json.loads(data)['choices'][0]['message']['content']
                if not isinstance(answer, str):
                    raise ValueError('LLM returned no text')
                return {'reply': answer}
        except (urllib.error.URLError, KeyError, IndexError, TypeError, json.JSONDecodeError) as exc:
            raise ConnectionError('LLM unavailable or incompatible. Check server URL, model and API key.') from exc


def handler_for(controller):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = 'HTTP/1.0'
        def setup(self):
            super().setup()
            self.connection.settimeout(100)

        def log_message(self, *_):
            pass  # Never log tokens or chat content.

        def send_json(self, code, body):
            data = json.dumps(body).encode()
            self.send_response(code)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(data)))
            self.send_header('Cache-Control', 'no-store')
            self.end_headers()
            self.wfile.write(data)

        def authorized(self):
            expected = 'Bearer ' + controller.token
            if not hmac.compare_digest(self.headers.get('Authorization', '').encode(), expected.encode()):
                self.send_json(401, {'error': 'Unauthorized'})
                return False
            return True

        def do_GET(self):
            if not self.authorized():
                return
            if self.path == '/v1/status':
                self.send_json(200, controller.status())
            else:
                self.send_json(404, {'error': 'Unknown route'})

        def do_POST(self):
            if not self.authorized():
                return
            try:
                length = int(self.headers.get('Content-Length', '0'))
                if length <= 0 or length > MAX_BODY or self.headers.get('Transfer-Encoding'):
                    self.send_json(413, {'error': 'Body must be between 1 and 65536 bytes'})
                    return
                raw = self.rfile.read(length)
                if len(raw) != length:
                    raise ValueError('Incomplete request')
                body = json.loads(raw)
                if not isinstance(body, dict):
                    raise ValueError('JSON object required')
                if self.path in ('/v1/session/start', '/v1/session/stop'):
                    self.send_json(202, controller.session(self.path.rsplit('/', 1)[1]))
                elif self.path == '/v1/chat':
                    self.send_json(200, controller.chat(body))
                else:
                    self.send_json(404, {'error': 'Unknown route'})
            except BlockingIOError as exc:
                self.send_json(409, {'error': str(exc)})
            except (ValueError, UnicodeDecodeError) as exc:
                self.send_json(400, {'error': str(exc)})
            except ConnectionError as exc:
                self.send_json(502, {'error': str(exc)})
            except (TimeoutError, OSError):
                self.send_json(504, {'error': 'Connection timed out or closed'})
    return Handler


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', required=True)
    args = parser.parse_args()
    with open(args.config, encoding='utf-8') as source:
        config = json.load(source)
    controller = Controller(config['token'], config['llm_url'], config['model'], config.get('api_key', ''), config.get('lan', False))
    server = ThreadingHTTPServer(('0.0.0.0' if controller.lan else '127.0.0.1', 8765), handler_for(controller))
    server.daemon_threads = True
    print('Kali Pocket bridge listening on port 8765; LAN ' + ('enabled' if controller.lan else 'disabled'), flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == '__main__':
    main()
