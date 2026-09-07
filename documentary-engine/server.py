#!/usr/bin/env python3
import json, mimetypes, os, random, shutil, subprocess, time, uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import quote, urlencode, urlparse
from urllib.request import Request, urlopen

HOST = os.getenv("DOCSTUDIO_HOST", "0.0.0.0")
PORT = int(os.getenv("DOCSTUDIO_PORT", "8188"))
COMFY_URL = os.getenv("COMFY_URL", "http://127.0.0.1:8189").rstrip("/")
OLLAMA_URL = os.getenv("OLLAMA_URL", "http://127.0.0.1:11434").rstrip("/")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "").strip()
IMAGE_CHECKPOINT = os.getenv("IMAGE_CHECKPOINT", "").strip()
WAN_WORKFLOW_API = os.getenv("WAN_WORKFLOW_API", "").strip()
OUTPUT_DIR = Path(os.getenv("DOCSTUDIO_OUTPUT", str(Path.home() / "DocStudio" / "output"))).expanduser()
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

WAN_MODEL = "wan2.2_ti2v_5B_fp16.safetensors"
WAN_CLIP = "umt5_xxl_fp8_e4m3fn_scaled.safetensors"
WAN_VAE = "wan2.2_vae.safetensors"
REALISM_SUFFIX = (
    "photorealistic documentary photography, authentic real-world location, natural human anatomy and skin texture, "
    "realistic lighting, physically plausible materials, candid documentary framing, cinematic but factual, "
    "natural motion, high detail, natural color science, no illustration, no cartoon, no anime, no CGI look, no plastic skin"
)
NEGATIVE_PROMPT = (
    "cartoon, anime, illustration, painting, 3d render, cgi, doll, plastic skin, wax face, deformed hands, extra fingers, "
    "duplicated people, distorted face, oversaturated, fantasy costume, static pose, flicker, text, watermark, logo"
)


def http_json(url, method="GET", payload=None, timeout=20):
    data, headers = None, {"Accept": "application/json"}
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    with urlopen(Request(url, data=data, headers=headers, method=method), timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8"))


def comfy_json(path, method="GET", payload=None, timeout=30):
    return http_json(f"{COMFY_URL}{path}", method, payload, timeout)


def comfy_available():
    try:
        comfy_json("/system_stats", timeout=3)
        return True
    except Exception:
        return False


def ffmpeg_available():
    return shutil.which("ffmpeg") is not None


def has_persian(text):
    return any("\u0600" <= ch <= "\u06ff" for ch in text)


def translate_visual_prompt(text):
    if not OLLAMA_MODEL or not has_persian(text):
        return text
    task = (
        "Translate this Persian/Dari documentary scene into one concise English visual-generation prompt. "
        "Preserve names, location, historical era, clothing and architecture. Do not invent symbols or fantasy. "
        "Return only the English visual prompt.\n\n" + text
    )
    try:
        out = http_json(f"{OLLAMA_URL}/api/generate", "POST", {"model": OLLAMA_MODEL, "prompt": task, "stream": False}, 120)
        return str(out.get("response", "")).strip() or text
    except Exception:
        return text


def visual_prompt(text):
    return f"{translate_visual_prompt(text.strip())}. {REALISM_SUFFIX}"


def extract_choices(value):
    if isinstance(value, list) and value:
        if isinstance(value[0], list):
            return [str(x) for x in value[0]]
        if all(isinstance(x, str) for x in value):
            return value
    return []


def object_info(node_class=None):
    try:
        path = f"/object_info/{quote(node_class)}" if node_class else "/object_info"
        return comfy_json(path, timeout=20)
    except Exception:
        return {}


def model_choices(node_class, field):
    info = object_info(node_class)
    node = info.get(node_class, info)
    return extract_choices(node.get("input", {}).get("required", {}).get(field, [])) if isinstance(node, dict) else []


def available_checkpoints():
    return model_choices("CheckpointLoaderSimple", "ckpt_name")


def choose_checkpoint():
    names = available_checkpoints()
    if IMAGE_CHECKPOINT:
        if IMAGE_CHECKPOINT in names or not names:
            return IMAGE_CHECKPOINT
        raise RuntimeError(f"Configured IMAGE_CHECKPOINT not found: {IMAGE_CHECKPOINT}")
    if not names:
        raise RuntimeError("No local image checkpoint found in ComfyUI.")
    for key in ("realvis", "juggernaut", "realistic", "photoreal", "cinematic", "sdxl"):
        for name in names:
            if key in name.lower():
                return name
    return names[0]


def build_image_workflow(prompt, width=1024, height=576, seed=None):
    seed = int(seed if seed is not None else random.randrange(1, 2**31 - 1))
    return {
        "3": {"class_type": "KSampler", "inputs": {"seed": seed, "steps": 35, "cfg": 5.0, "sampler_name": "dpmpp_2m", "scheduler": "karras", "denoise": 1.0, "model": ["4", 0], "positive": ["6", 0], "negative": ["7", 0], "latent_image": ["5", 0]}},
        "4": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": choose_checkpoint()}},
        "5": {"class_type": "EmptyLatentImage", "inputs": {"width": int(width), "height": int(height), "batch_size": 1}},
        "6": {"class_type": "CLIPTextEncode", "inputs": {"text": prompt, "clip": ["4", 1]}},
        "7": {"class_type": "CLIPTextEncode", "inputs": {"text": NEGATIVE_PROMPT, "clip": ["4", 1]}},
        "8": {"class_type": "VAEDecode", "inputs": {"samples": ["3", 0], "vae": ["4", 2]}},
        "9": {"class_type": "SaveImage", "inputs": {"filename_prefix": "docstudio_real", "images": ["8", 0]}},
    }


def wan_native_state():
    info = object_info()
    required_nodes = {"UNETLoader", "CLIPLoader", "VAELoader", "ModelSamplingSD3", "CLIPTextEncode", "Wan22ImageToVideoLatent", "KSampler", "VAEDecode", "CreateVideo", "SaveVideo"}
    missing_nodes = sorted(required_nodes - set(info)) if info else sorted(required_nodes)
    unets = model_choices("UNETLoader", "unet_name")
    clips = model_choices("CLIPLoader", "clip_name")
    vaes = model_choices("VAELoader", "vae_name")
    missing_models = []
    if WAN_MODEL not in unets:
        missing_models.append(WAN_MODEL)
    if WAN_CLIP not in clips:
        missing_models.append(WAN_CLIP)
    if WAN_VAE not in vaes:
        missing_models.append(WAN_VAE)
    return {"ready": not missing_nodes and not missing_models, "missing_nodes": missing_nodes, "missing_models": missing_models}


def build_wan_native_workflow(text, width=832, height=480, length=81, steps=20):
    prompt = visual_prompt(text)
    return {
        "1": {"class_type": "UNETLoader", "inputs": {"unet_name": WAN_MODEL, "weight_dtype": "default"}},
        "2": {"class_type": "CLIPLoader", "inputs": {"clip_name": WAN_CLIP, "type": "wan", "device": "default"}},
        "3": {"class_type": "VAELoader", "inputs": {"vae_name": WAN_VAE}},
        "4": {"class_type": "ModelSamplingSD3", "inputs": {"model": ["1", 0], "shift": 8}},
        "5": {"class_type": "CLIPTextEncode", "inputs": {"clip": ["2", 0], "text": prompt}},
        "6": {"class_type": "CLIPTextEncode", "inputs": {"clip": ["2", 0], "text": NEGATIVE_PROMPT + ", unnatural motion, jitter, camera shake"}},
        "7": {"class_type": "Wan22ImageToVideoLatent", "inputs": {"vae": ["3", 0], "width": int(width), "height": int(height), "length": int(length), "batch_size": 1}},
        "8": {"class_type": "KSampler", "inputs": {"model": ["4", 0], "seed": random.randrange(1, 2**31 - 1), "steps": int(steps), "cfg": 5, "sampler_name": "uni_pc", "scheduler": "simple", "positive": ["5", 0], "negative": ["6", 0], "latent_image": ["7", 0], "denoise": 1.0}},
        "9": {"class_type": "VAEDecode", "inputs": {"samples": ["8", 0], "vae": ["3", 0]}},
        "10": {"class_type": "CreateVideo", "inputs": {"images": ["9", 0], "fps": 24.0}},
        "11": {"class_type": "SaveVideo", "inputs": {"video": ["10", 0], "filename_prefix": "video/DocStudio_Wan22", "format": "mp4", "codec": "h264"}},
    }


def submit_workflow(workflow):
    result = comfy_json("/prompt", "POST", {"prompt": workflow, "client_id": str(uuid.uuid4())}, 30)
    if result.get("error"):
        raise RuntimeError(str(result.get("error")))
    prompt_id = result.get("prompt_id")
    if not prompt_id:
        raise RuntimeError(f"ComfyUI did not return prompt_id: {result}")
    return prompt_id


def wait_history(prompt_id, timeout=900):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            data = comfy_json(f"/history/{quote(prompt_id)}", timeout=15)
            item = data.get(prompt_id) if isinstance(data, dict) else None
            if not item and isinstance(data, dict) and "outputs" in data:
                item = data
            if item:
                status = item.get("status", {}) if isinstance(item, dict) else {}
                for msg in status.get("messages", []):
                    if isinstance(msg, list) and msg and msg[0] == "execution_error":
                        raise RuntimeError(json.dumps(msg, ensure_ascii=False))
                if status.get("completed") or item.get("outputs"):
                    return item
        except RuntimeError:
            raise
        except Exception:
            pass
        time.sleep(1.5)
    raise TimeoutError("Generation timed out")


def find_files(obj):
    out = []
    if isinstance(obj, dict):
        if "filename" in obj:
            out.append({"filename": str(obj.get("filename", "")), "subfolder": str(obj.get("subfolder", "")), "type": str(obj.get("type", "output"))})
        for value in obj.values():
            out.extend(find_files(value))
    elif isinstance(obj, list):
        for value in obj:
            out.extend(find_files(value))
    return out


def download_comfy_file(item, prefix="media"):
    query = urlencode({"filename": item["filename"], "subfolder": item.get("subfolder", ""), "type": item.get("type", "output")})
    suffix = Path(item["filename"]).suffix or ".bin"
    dest = OUTPUT_DIR / f"{prefix}_{int(time.time())}_{uuid.uuid4().hex[:8]}{suffix}"
    with urlopen(f"{COMFY_URL}/view?{query}", timeout=120) as r, dest.open("wb") as f:
        shutil.copyfileobj(r, f)
    return dest


def collect_media(history, video=False):
    files = find_files(history.get("outputs", history))
    if video:
        files = [f for f in files if Path(f["filename"]).suffix.lower() in {".mp4", ".webm", ".mov", ".gif"}]
    if not files:
        raise RuntimeError("Generation finished but no media file was reported")
    return files[0]


def generate_image(text, width=1024, height=576):
    if not comfy_available():
        raise RuntimeError(f"ComfyUI is not reachable at {COMFY_URL}")
    prompt = visual_prompt(text)
    prompt_id = submit_workflow(build_image_workflow(prompt, width, height))
    item = collect_media(wait_history(prompt_id))
    path = download_comfy_file(item, "image")
    return {"backend": "comfyui-photoreal", "path": str(path), "prompt": prompt, "prompt_id": prompt_id}


def template_replace(obj, prompt, negative, seed):
    if isinstance(obj, dict):
        return {k: template_replace(v, prompt, negative, seed) for k, v in obj.items()}
    if isinstance(obj, list):
        return [template_replace(v, prompt, negative, seed) for v in obj]
    if obj == "{{PROMPT}}":
        return prompt
    if obj == "{{NEGATIVE_PROMPT}}":
        return negative
    if obj == "{{SEED}}":
        return seed
    return obj


def generate_external_wan(text):
    path = Path(WAN_WORKFLOW_API).expanduser()
    if not path.exists():
        raise RuntimeError(f"WAN_WORKFLOW_API does not exist: {path}")
    workflow = template_replace(json.loads(path.read_text(encoding="utf-8")), visual_prompt(text), NEGATIVE_PROMPT, random.randrange(1, 2**31 - 1))
    prompt_id = submit_workflow(workflow)
    item = collect_media(wait_history(prompt_id, 1800), video=True)
    out = download_comfy_file(item, "wan_video")
    return {"backend": "wan-custom-workflow", "path": str(out), "prompt_id": prompt_id}


def generate_native_wan(text):
    state = wan_native_state()
    if not state["ready"]:
        raise RuntimeError("Wan 2.2 5B is not ready: " + json.dumps(state, ensure_ascii=False))
    prompt_id = submit_workflow(build_wan_native_workflow(text))
    item = collect_media(wait_history(prompt_id, 1800), video=True)
    out = download_comfy_file(item, "wan22_video")
    return {"backend": "wan2.2-5b-native", "path": str(out), "prompt_id": prompt_id}


def make_documentary_motion(image_path, seconds=8):
    if not ffmpeg_available():
        raise RuntimeError("FFmpeg was not found")
    src = Path(image_path)
    if not src.exists():
        raise RuntimeError(f"Image not found: {src}")
    out = OUTPUT_DIR / f"docvideo_{int(time.time())}_{uuid.uuid4().hex[:8]}.mp4"
    frames = max(30, int(seconds * 30))
    vf = "scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080," + f"zoompan=z='min(zoom+0.00055,1.10)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d={frames}:s=1920x1080:fps=30,format=yuv420p"
    cmd = ["ffmpeg", "-y", "-loop", "1", "-i", str(src), "-vf", vf, "-frames:v", str(frames), "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-movflags", "+faststart", str(out)]
    proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr[-3000:])
    return out


def generate_video(text):
    errors = []
    if WAN_WORKFLOW_API:
        try:
            return generate_external_wan(text)
        except Exception as e:
            errors.append(f"custom Wan: {e}")
    if wan_native_state()["ready"]:
        try:
            return generate_native_wan(text)
        except Exception as e:
            errors.append(f"native Wan: {e}")
    image = generate_image(text)
    out = make_documentary_motion(image["path"])
    result = {"backend": "documentary-motion-fallback", "path": str(out), "source_image": image["path"]}
    if errors:
        result["generative_video_errors"] = errors
    return result


def status_payload():
    comfy_ok = comfy_available()
    wan = wan_native_state() if comfy_ok else {"ready": False, "missing_nodes": [], "missing_models": [WAN_MODEL, WAN_CLIP, WAN_VAE]}
    return {
        "ok": True,
        "service": "Documentary Studio Local Engine",
        "version": "0.3.0",
        "comfyui": {"url": COMFY_URL, "reachable": comfy_ok, "checkpoints": len(available_checkpoints()) if comfy_ok else 0},
        "ffmpeg": {"available": ffmpeg_available(), "path": shutil.which("ffmpeg")},
        "ollama": {"enabled": bool(OLLAMA_MODEL), "model": OLLAMA_MODEL or None},
        "wan": {"native_5b": wan, "custom_workflow": WAN_WORKFLOW_API or None},
        "output_dir": str(OUTPUT_DIR),
    }


class Handler(BaseHTTPRequestHandler):
    server_version = "DocStudioLocal/0.3"

    def send_json(self, code, obj):
        data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/system_stats":
            return self.send_json(200, status_payload())
        if parsed.path.startswith("/outputs/"):
            path = OUTPUT_DIR / Path(parsed.path[len("/outputs/"):]).name
            if not path.exists():
                return self.send_json(404, {"ok": False, "error": "not found"})
            self.send_response(200)
            self.send_header("Content-Type", mimetypes.guess_type(path.name)[0] or "application/octet-stream")
            self.send_header("Content-Length", str(path.stat().st_size))
            self.end_headers()
            with path.open("rb") as f:
                shutil.copyfileobj(f, self.wfile)
            return
        return self.send_json(404, {"ok": False, "error": "unknown endpoint"})

    def do_POST(self):
        if urlparse(self.path).path != "/docstudio/generate":
            return self.send_json(404, {"ok": False, "error": "unknown endpoint"})
        try:
            body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", "0"))).decode("utf-8"))
            kind = str(body.get("type", "image")).lower().strip()
            prompt = str(body.get("prompt", "")).strip()
            if not prompt:
                raise ValueError("prompt is required")
            if kind == "image":
                result = generate_image(prompt, int(body.get("width", 1024)), int(body.get("height", 576)))
            elif kind == "video":
                result = generate_video(prompt)
            else:
                raise ValueError("type must be image or video")
            media_path = Path(result["path"])
            result["url"] = f"/outputs/{quote(media_path.name)}"
            return self.send_json(200, {"ok": True, **result})
        except Exception as e:
            return self.send_json(500, {"ok": False, "error": str(e)})

    def log_message(self, fmt, *args):
        print(time.strftime("%Y-%m-%d %H:%M:%S"), self.address_string(), fmt % args)


if __name__ == "__main__":
    print(f"Documentary Studio Local Engine 0.3 on http://{HOST}:{PORT}")
    print(f"ComfyUI: {COMFY_URL}")
    print(f"Output: {OUTPUT_DIR}")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
