#!/usr/bin/env python3
import argparse, json, os, secrets, shutil, subprocess, threading, time, uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib import request, parse

ROOT = Path(__file__).resolve().parent
STATE = ROOT / '.state'
STATE.mkdir(exist_ok=True)
UPLOADS = STATE / 'uploads'
UPLOADS.mkdir(exist_ok=True)
JOBS = {}
LOCK = threading.Lock()


def _json_request(url, method='GET', payload=None, timeout=4):
    data = None if payload is None else json.dumps(payload).encode('utf-8')
    req = request.Request(url, data=data, method=method, headers={'Content-Type': 'application/json'})
    with request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode('utf-8'))


def _comfy_url():
    return os.getenv('DOCSTUDIO_COMFY_URL', 'http://127.0.0.1:8188').rstrip('/')


def _ollama_url():
    return os.getenv('DOCSTUDIO_OLLAMA_URL', 'http://127.0.0.1:11434').rstrip('/')


def _workflow_path(kind):
    env = os.getenv(f'DOCSTUDIO_{kind.upper()}_WORKFLOW')
    if env:
        return Path(env)
    return ROOT / 'workflows' / f'{kind}.json'


def comfy_ready():
    try:
        _json_request(_comfy_url() + '/system_stats')
        return True
    except Exception:
        return False


def ollama_models():
    try:
        data = _json_request(_ollama_url() + '/api/tags')
        return [m.get('name') or m.get('model') for m in data.get('models', []) if (m.get('name') or m.get('model'))]
    except Exception:
        return []


def whisper_config():
    exe = os.getenv('DOCSTUDIO_WHISPER_BIN') or shutil.which('whisper-cli')
    model = os.getenv('DOCSTUDIO_WHISPER_MODEL')
    if not exe or not model:
        return None
    exe_path = Path(exe)
    model_path = Path(model)
    if not exe_path.exists() or not model_path.exists():
        return None
    return str(exe_path), str(model_path)


def whisper_ready():
    return whisper_config() is not None


def capabilities():
    comfy = comfy_ready()
    models = ollama_models()
    image = comfy and _workflow_path('image').exists()
    video = comfy and _workflow_path('video').exists()
    whisper = whisper_ready()
    return {
        'protocol': 1,
        'director_ai': bool(models),
        'director_model': models[0] if models else None,
        'transcribe': whisper,
        'image': image,
        'video': video,
        # Do not advertise a capability until both protocol endpoint and app action exist.
        'upscale': False,
        'engines': [x for x, ok in [
            ('ollama', bool(models)), ('comfyui', comfy), ('whisper.cpp', whisper), ('ffmpeg', bool(shutil.which('ffmpeg')))
        ] if ok],
        'message': 'Only capabilities that passed local detection and have a usable protocol action are enabled.'
    }


def choose_model():
    models = ollama_models()
    preferred = ('qwen', 'llama', 'gemma', 'mistral')
    for p in preferred:
        for m in models:
            if p in m.lower():
                return m
    return models[0] if models else None


def director_plan(script):
    model = choose_model()
    if not model:
        raise RuntimeError('No local Ollama model is installed.')
    prompt = (
        'You are a documentary director. Return ONLY valid JSON with key scenes. '
        'Each scene must contain: narration, visual_prompt, source_type (archive|generated|map|text), '
        'duration_sec, camera, sfx, on_screen_text. Preserve Persian/Dari facts and do not invent sources. '
        'For historical claims prefer archive and mark generated reconstructions clearly. Script:\n' + script
    )
    data = _json_request(_ollama_url() + '/api/generate', 'POST', {
        'model': model, 'prompt': prompt, 'stream': False, 'format': 'json'
    }, timeout=300)
    text = data.get('response', '').strip()
    try:
        return json.loads(text)
    except Exception:
        return {'raw': text, 'model': model}


def render_template(kind, params):
    path = _workflow_path(kind)
    if not path.exists():
        raise RuntimeError(f'{kind} workflow is not installed.')
    text = path.read_text('utf-8')
    replacements = {
        '{{PROMPT}}': json.dumps(params.get('prompt', ''), ensure_ascii=False)[1:-1],
        '{{NEGATIVE}}': json.dumps(params.get('negative', ''), ensure_ascii=False)[1:-1],
        '{{WIDTH}}': str(int(params.get('width', 1024))),
        '{{HEIGHT}}': str(int(params.get('height', 576))),
        '{{SECONDS}}': str(float(params.get('seconds', 5))),
        '{{SEED}}': str(int(params.get('seed', secrets.randbelow(2**31 - 1))))
    }
    for k, v in replacements.items():
        text = text.replace(k, v)
    return json.loads(text)


def comfy_generate(kind, params):
    workflow = render_template(kind, params)
    queued = _json_request(_comfy_url() + '/prompt', 'POST', {'prompt': workflow}, timeout=30)
    prompt_id = queued.get('prompt_id')
    if not prompt_id:
        raise RuntimeError('ComfyUI did not return prompt_id')
    deadline = time.time() + float(os.getenv('DOCSTUDIO_JOB_TIMEOUT', '1800'))
    while time.time() < deadline:
        try:
            hist = _json_request(_comfy_url() + f'/history/{prompt_id}', timeout=10)
            record = hist.get(prompt_id)
            if record:
                outputs = record.get('outputs', {})
                candidates = []
                for node in outputs.values():
                    for key in ('images', 'gifs', 'videos', 'audio'):
                        for item in node.get(key, []) or []:
                            if item.get('filename'):
                                candidates.append(item)
                if candidates:
                    return {'prompt_id': prompt_id, 'files': candidates}
        except Exception:
            pass
        time.sleep(2)
    raise TimeoutError('Generation timed out')


def transcribe_file(media_path):
    config = whisper_config()
    if not config:
        raise RuntimeError('whisper.cpp is not configured.')
    exe, model = config
    out_base = UPLOADS / ('transcript_' + uuid.uuid4().hex)
    command = [exe, '-m', model, '-f', str(media_path), '-otxt', '-of', str(out_base)]
    language = os.getenv('DOCSTUDIO_WHISPER_LANGUAGE', '').strip()
    if language:
        command += ['-l', language]
    timeout = int(os.getenv('DOCSTUDIO_TRANSCRIBE_TIMEOUT', '1800'))
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout, check=False)
    text_file = Path(str(out_base) + '.txt')
    try:
        if result.returncode != 0:
            detail = result.stderr.decode('utf-8', errors='replace')[-1200:]
            raise RuntimeError('whisper.cpp failed: ' + detail)
        if not text_file.exists():
            raise RuntimeError('whisper.cpp did not produce a transcript file.')
        text = text_file.read_text('utf-8', errors='replace').strip()
        if not text:
            raise RuntimeError('Transcript is empty.')
        return text
    finally:
        for candidate in UPLOADS.glob(out_base.name + '.*'):
            try:
                candidate.unlink()
            except OSError:
                pass


def new_job(kind, params):
    jid = uuid.uuid4().hex
    with LOCK:
        JOBS[jid] = {'id': jid, 'kind': kind, 'status': 'queued', 'created': time.time()}

    def run():
        try:
            with LOCK:
                JOBS[jid]['status'] = 'running'
            result = comfy_generate(kind, params)
            with LOCK:
                JOBS[jid]['status'] = 'done'
                JOBS[jid]['result'] = result
        except Exception as e:
            with LOCK:
                JOBS[jid]['status'] = 'error'
                JOBS[jid]['error'] = str(e)

    threading.Thread(target=run, daemon=True).start()
    return jid


def token_value(explicit=None):
    if explicit:
        return explicit
    p = STATE / 'token.txt'
    if p.exists():
        return p.read_text('utf-8').strip()
    t = secrets.token_urlsafe(24)
    p.write_text(t, 'utf-8')
    return t


class Handler(BaseHTTPRequestHandler):
    server_version = 'DocStudioOpen/1.1'

    def log_message(self, fmt, *args):
        print('[worker]', fmt % args)

    def _auth(self):
        return self.headers.get('Authorization', '') == 'Bearer ' + self.server.token

    def _send(self, code, obj, ctype='application/json; charset=utf-8'):
        body = obj if isinstance(obj, (bytes, bytearray)) else json.dumps(obj, ensure_ascii=False).encode('utf-8')
        self.send_response(code)
        self.send_header('Content-Type', ctype)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _body(self):
        n = int(self.headers.get('Content-Length', '0'))
        raw = self.rfile.read(n) if n else b'{}'
        return json.loads(raw.decode('utf-8'))

    def _receive_media(self):
        try:
            n = int(self.headers.get('Content-Length', '0'))
        except ValueError:
            raise ValueError('invalid Content-Length')
        max_bytes = int(os.getenv('DOCSTUDIO_MAX_UPLOAD_BYTES', str(512 * 1024 * 1024)))
        if n <= 0:
            raise ValueError('empty media upload')
        if n > max_bytes:
            raise ValueError('media upload exceeds configured limit')
        raw_name = self.headers.get('X-Filename', 'upload.media')
        suffix = Path(raw_name).suffix.lower()
        if not suffix or len(suffix) > 10 or not suffix[1:].isalnum():
            suffix = '.media'
        target = UPLOADS / ('upload_' + uuid.uuid4().hex + suffix)
        remaining = n
        with target.open('wb') as out:
            while remaining:
                chunk = self.rfile.read(min(1024 * 1024, remaining))
                if not chunk:
                    raise ValueError('upload ended early')
                out.write(chunk)
                remaining -= len(chunk)
        return target

    def do_GET(self):
        if self.path == '/v1/ping':
            return self._send(200, {'ok': True, 'name': 'DocStudio Open Worker'})
        if not self._auth():
            return self._send(401, {'error': 'unauthorized'})
        if self.path == '/v1/capabilities':
            return self._send(200, capabilities())
        if self.path.startswith('/v1/jobs/'):
            jid = self.path.rsplit('/', 1)[-1]
            with LOCK:
                job = JOBS.get(jid)
            return self._send(200 if job else 404, job or {'error': 'not found'})
        if self.path.startswith('/v1/comfy/view?'):
            q = parse.parse_qs(parse.urlsplit(self.path).query)
            params = {k: v[0] for k, v in q.items() if v}
            if 'filename' not in params:
                return self._send(400, {'error': 'filename required'})
            url = _comfy_url() + '/view?' + parse.urlencode(params)
            try:
                with request.urlopen(url, timeout=60) as r:
                    data = r.read()
                    ctype = r.headers.get('Content-Type', 'application/octet-stream')
                return self._send(200, data, ctype)
            except Exception as e:
                return self._send(502, {'error': str(e)})
        return self._send(404, {'error': 'not found'})

    def do_POST(self):
        if not self._auth():
            return self._send(401, {'error': 'unauthorized'})
        if self.path == '/v1/transcribe':
            if not whisper_ready():
                return self._send(503, {'error': 'whisper.cpp is not ready'})
            media = None
            try:
                media = self._receive_media()
                text = transcribe_file(media)
                return self._send(200, {'text': text, 'engine': 'whisper.cpp'})
            except ValueError as e:
                return self._send(400, {'error': str(e)})
            except subprocess.TimeoutExpired:
                return self._send(504, {'error': 'transcription timed out'})
            except Exception as e:
                return self._send(500, {'error': str(e)})
            finally:
                if media is not None:
                    try:
                        media.unlink()
                    except OSError:
                        pass
        try:
            body = self._body()
        except Exception as e:
            return self._send(400, {'error': 'invalid json: ' + str(e)})
        if self.path == '/v1/director/plan':
            try:
                return self._send(200, {'plan': director_plan(str(body.get('script', '')))})
            except Exception as e:
                return self._send(503, {'error': str(e)})
        if self.path in ('/v1/image/generate', '/v1/video/generate'):
            kind = 'image' if '/image/' in self.path else 'video'
            caps = capabilities()
            if not caps.get(kind):
                return self._send(503, {'error': f'{kind} engine not ready'})
            return self._send(202, {'job_id': new_job(kind, body)})
        return self._send(404, {'error': 'not found'})


def main():
    ap = argparse.ArgumentParser(description='Free local compute worker for Documentary Studio V7')
    ap.add_argument('--host', default='0.0.0.0')
    ap.add_argument('--port', type=int, default=8190)
    ap.add_argument('--token')
    args = ap.parse_args()
    token = token_value(args.token)
    httpd = ThreadingHTTPServer((args.host, args.port), Handler)
    httpd.token = token
    print('Documentary Studio Open Worker')
    print(f'Listening: http://{args.host}:{args.port}')
    print('Token:', token)
    print('Capabilities:', json.dumps(capabilities(), ensure_ascii=False))
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == '__main__':
    main()
