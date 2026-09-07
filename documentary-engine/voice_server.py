#!/usr/bin/env python3
import base64
import json
import os
import re
import shutil
import subprocess
import tempfile
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import quote, urlparse

HOST = os.getenv("DOCSTUDIO_VOICE_HOST", "0.0.0.0")
PORT = int(os.getenv("DOCSTUDIO_VOICE_PORT", "8190"))
ROOT = Path(os.getenv("DOCSTUDIO_VOICE_HOME", str(Path.home() / "DocStudio" / "voice"))).expanduser()
PROFILE_DIR = ROOT / "profiles"
OUTPUT_DIR = ROOT / "output"
PROFILE_DIR.mkdir(parents=True, exist_ok=True)
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
MODEL_CONFIG = os.getenv("DOCSTUDIO_FARSI_MODEL", "hf://mehdi-hf/pocket-tts-farsi/farsi.yaml")
MAX_UPLOAD = 12 * 1024 * 1024

_MODEL = None
_MODEL_LOCK = threading.RLock()
_VOICE_STATES = {}


def ffmpeg_available():
    return shutil.which("ffmpeg") is not None


def normalize_fa(text: str) -> str:
    text = text.replace("ي", "ی").replace("ك", "ک").replace("ى", "ی")
    text = text.replace("ة", "ه").replace("ۀ", "هٔ")
    text = re.sub(r"[\u064B-\u065F\u0670\u06D6-\u06ED]", "", text)
    text = text.replace("ـ", "")
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def split_fa(text: str, max_words: int = 24, max_chars: int = 150):
    text = normalize_fa(text)
    if not text:
        return []
    pieces = re.split(r"(?<=[.!؟?!؛;])\s+|\n+", text)
    chunks = []
    for piece in pieces:
        piece = piece.strip()
        if not piece:
            continue
        words = piece.split()
        current = []
        chars = 0
        for word in words:
            extra = len(word) + (1 if current else 0)
            if current and (len(current) >= max_words or chars + extra > max_chars):
                chunks.append(" ".join(current))
                current, chars = [], 0
            current.append(word)
            chars += extra
        if current:
            chunks.append(" ".join(current))
    return chunks


def run(cmd):
    p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if p.returncode != 0:
        raise RuntimeError((p.stderr or p.stdout)[-4000:])
    return p.stdout


def prepare_reference(raw_path: Path, profile_id: str) -> Path:
    if not ffmpeg_available():
        raise RuntimeError("FFmpeg is required for voice reference preparation")
    out = PROFILE_DIR / f"{profile_id}.wav"
    run([
        "ffmpeg", "-y", "-i", str(raw_path), "-t", "5.0", "-ac", "1", "-ar", "24000",
        "-sample_fmt", "s16", "-af", "highpass=f=60,lowpass=f=11500", str(out)
    ])
    if out.stat().st_size < 50000:
        raise RuntimeError("Reference clip is too short or unreadable")
    return out


def load_model():
    global _MODEL
    with _MODEL_LOCK:
        if _MODEL is None:
            from pocket_tts import TTSModel
            _MODEL = TTSModel.load_model(config=MODEL_CONFIG, temp=0.3)
        return _MODEL


def voice_state(profile_id: str):
    with _MODEL_LOCK:
        if profile_id in _VOICE_STATES:
            return _VOICE_STATES[profile_id]
        wav = PROFILE_DIR / f"{profile_id}.wav"
        if not wav.exists():
            raise RuntimeError("Voice profile not found")
        state = load_model().get_state_for_audio_prompt(str(wav))
        _VOICE_STATES[profile_id] = state
        return state


def write_wav(path: Path, sample_rate: int, audio):
    import numpy as np
    from scipy.io import wavfile
    data = audio.detach().cpu().numpy() if hasattr(audio, "detach") else np.asarray(audio)
    data = np.asarray(data, dtype=np.float32).reshape(-1)
    data = np.clip(data, -1.0, 1.0)
    wavfile.write(str(path), int(sample_rate), (data * 32767.0).astype(np.int16))


def synthesize(profile_id: str, text: str, title: str = "narration"):
    chunks = split_fa(text)
    if not chunks:
        raise RuntimeError("Persian narration text is empty")
    model = load_model()
    state = voice_state(profile_id)
    run_id = f"{int(time.time())}_{uuid.uuid4().hex[:8]}"
    work = Path(tempfile.mkdtemp(prefix="docstudio_voice_"))
    generated = []
    try:
        with _MODEL_LOCK:
            for i, chunk in enumerate(chunks):
                audio = model.generate_audio(state, chunk, frames_after_eos=0)
                part = work / f"part_{i:03d}.wav"
                write_wav(part, model.sample_rate, audio)
                generated.append(part)
        concat = work / "joined.wav"
        list_file = work / "concat.txt"
        silence = work / "silence.wav"
        run(["ffmpeg", "-y", "-f", "lavfi", "-i", "anullsrc=r=24000:cl=mono", "-t", "0.16", "-c:a", "pcm_s16le", str(silence)])
        entries = []
        for idx, part in enumerate(generated):
            entries.append(f"file '{part.as_posix()}'")
            if idx != len(generated) - 1:
                entries.append(f"file '{silence.as_posix()}'")
        list_file.write_text("\n".join(entries), encoding="utf-8")
        run(["ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", str(list_file), "-c:a", "pcm_s16le", str(concat)])
        safe_title = re.sub(r"[^\w\-]+", "_", title, flags=re.UNICODE).strip("_")[:50] or "narration"
        out = OUTPUT_DIR / f"{safe_title}_{run_id}.wav"
        run([
            "ffmpeg", "-y", "-i", str(concat),
            "-af", "highpass=f=65,acompressor=threshold=-18dB:ratio=2.2:attack=12:release=180,loudnorm=I=-16:LRA=7:TP=-1.5",
            "-ar", "24000", "-ac", "1", "-c:a", "pcm_s16le", str(out)
        ])
        return out, len(chunks)
    finally:
        shutil.rmtree(work, ignore_errors=True)


def profile_metadata(profile_id: str):
    meta = PROFILE_DIR / f"{profile_id}.json"
    if not meta.exists():
        return None
    try:
        return json.loads(meta.read_text(encoding="utf-8"))
    except Exception:
        return None


def list_profiles():
    out = []
    for p in PROFILE_DIR.glob("*.json"):
        try:
            out.append(json.loads(p.read_text(encoding="utf-8")))
        except Exception:
            pass
    return sorted(out, key=lambda x: x.get("created_at", 0), reverse=True)


class Handler(BaseHTTPRequestHandler):
    server_version = "DocStudioVoice/1.0"

    def send_json(self, code, obj):
        data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def read_json(self):
        n = int(self.headers.get("Content-Length", "0"))
        if n <= 0 or n > MAX_UPLOAD:
            raise ValueError("Invalid request body size")
        return json.loads(self.rfile.read(n).decode("utf-8"))

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/health":
            return self.send_json(200, {
                "ok": True,
                "service": "DocStudio Persian Voice Clone",
                "version": "1.0.0",
                "engine": "Pocket-TTS Farsi",
                "model": MODEL_CONFIG,
                "model_loaded": _MODEL is not None,
                "ffmpeg": ffmpeg_available(),
                "profiles": len(list_profiles()),
                "offline_after_cache": True,
            })
        if path == "/voice/profiles":
            return self.send_json(200, {"ok": True, "profiles": list_profiles()})
        if path.startswith("/outputs/"):
            f = OUTPUT_DIR / Path(path[len("/outputs/"):]).name
            if not f.exists():
                return self.send_json(404, {"ok": False, "error": "not found"})
            self.send_response(200)
            self.send_header("Content-Type", "audio/wav")
            self.send_header("Content-Length", str(f.stat().st_size))
            self.end_headers()
            with f.open("rb") as src:
                shutil.copyfileobj(src, self.wfile)
            return
        return self.send_json(404, {"ok": False, "error": "unknown endpoint"})

    def do_POST(self):
        path = urlparse(self.path).path
        try:
            if path == "/voice/register":
                body = self.read_json()
                encoded = str(body.get("audio_base64", ""))
                if not encoded:
                    raise ValueError("audio_base64 is required")
                raw = base64.b64decode(encoded, validate=True)
                if len(raw) < 16000 or len(raw) > MAX_UPLOAD:
                    raise ValueError("Reference audio must be a short clean clip")
                profile_id = uuid.uuid4().hex
                suffix = str(body.get("extension", "wav")).lower().strip(".")
                if suffix not in {"wav", "mp3", "m4a", "aac", "ogg", "flac"}:
                    suffix = "bin"
                temp = PROFILE_DIR / f"{profile_id}.upload.{suffix}"
                temp.write_bytes(raw)
                try:
                    wav = prepare_reference(temp, profile_id)
                finally:
                    temp.unlink(missing_ok=True)
                meta = {
                    "id": profile_id,
                    "name": str(body.get("name", "صدای فارسی")).strip() or "صدای فارسی",
                    "created_at": int(time.time()),
                    "reference": wav.name,
                }
                (PROFILE_DIR / f"{profile_id}.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
                _VOICE_STATES.pop(profile_id, None)
                return self.send_json(200, {"ok": True, "profile": meta})

            if path == "/voice/synthesize":
                body = self.read_json()
                profile_id = str(body.get("profile_id", "")).strip()
                text = str(body.get("text", "")).strip()
                if not profile_id:
                    raise ValueError("profile_id is required")
                if not text:
                    raise ValueError("text is required")
                if not profile_metadata(profile_id):
                    raise ValueError("Unknown voice profile")
                out, chunks = synthesize(profile_id, text, str(body.get("title", "narration")))
                return self.send_json(200, {
                    "ok": True,
                    "profile_id": profile_id,
                    "chunks": chunks,
                    "file": out.name,
                    "url": f"/outputs/{quote(out.name)}",
                })

            return self.send_json(404, {"ok": False, "error": "unknown endpoint"})
        except Exception as e:
            return self.send_json(500, {"ok": False, "error": str(e)})

    def log_message(self, fmt, *args):
        print(time.strftime("%Y-%m-%d %H:%M:%S"), self.address_string(), fmt % args)


if __name__ == "__main__":
    print(f"DocStudio Persian Voice Clone on http://{HOST}:{PORT}")
    print("First synthesis downloads the Persian model; after it is cached the engine can run offline.")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
