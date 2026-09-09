#!/usr/bin/env python3
import json, struct, threading, zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def png_bytes(w=64, h=64):
    raw = b''.join(b'\x00' + bytes([20, 90, 160]) * w for _ in range(h))
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')


class OllamaHandler(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def send_json(self, obj):
        body=json.dumps(obj, ensure_ascii=False).encode(); self.send_response(200); self.send_header('Content-Type','application/json'); self.send_header('Content-Length',str(len(body))); self.end_headers(); self.wfile.write(body)
    def do_GET(self):
        if self.path == '/api/tags': return self.send_json({'models':[{'name':'qwen-ci:latest'}]})
        self.send_response(404); self.end_headers()
    def do_POST(self):
        n=int(self.headers.get('Content-Length','0')); self.rfile.read(n)
        if self.path == '/api/generate':
            plan={'scenes':[{'narration':'صحنهٔ نخست برای آزمون.','visual_prompt':'cinematic mountain valley documentary','source_type':'generated','duration_sec':3,'camera':'wide','sfx':'wind','on_screen_text':''},{'narration':'صحنهٔ دوم برای آزمون.','visual_prompt':'documentary close-up hands writing','source_type':'archive','duration_sec':3,'camera':'close','sfx':'paper','on_screen_text':''}]}
            return self.send_json({'response':json.dumps(plan, ensure_ascii=False)})
        self.send_response(404); self.end_headers()


class ComfyHandler(BaseHTTPRequestHandler):
    counter=0
    def log_message(self, *args): pass
    def send_json(self, obj):
        body=json.dumps(obj).encode(); self.send_response(200); self.send_header('Content-Type','application/json'); self.send_header('Content-Length',str(len(body))); self.end_headers(); self.wfile.write(body)
    def do_GET(self):
        if self.path == '/system_stats': return self.send_json({'system':{'ok':True}})
        if self.path.startswith('/history/'):
            pid=self.path.rsplit('/',1)[-1]
            return self.send_json({pid:{'outputs':{'1':{'images':[{'filename':'ci.png','subfolder':'','type':'output'}]}}}})
        if self.path.startswith('/view?'):
            body=png_bytes(); self.send_response(200); self.send_header('Content-Type','image/png'); self.send_header('Content-Length',str(len(body))); self.end_headers(); self.wfile.write(body); return
        self.send_response(404); self.end_headers()
    def do_POST(self):
        n=int(self.headers.get('Content-Length','0')); self.rfile.read(n)
        if self.path == '/prompt':
            ComfyHandler.counter += 1; return self.send_json({'prompt_id':f'ci-{ComfyHandler.counter}'})
        self.send_response(404); self.end_headers()


def run(server): server.serve_forever()

if __name__ == '__main__':
    a=ThreadingHTTPServer(('0.0.0.0',11434),OllamaHandler)
    b=ThreadingHTTPServer(('0.0.0.0',8188),ComfyHandler)
    threading.Thread(target=run,args=(a,),daemon=True).start()
    print('CI Ollama and ComfyUI stubs ready', flush=True)
    b.serve_forever()
