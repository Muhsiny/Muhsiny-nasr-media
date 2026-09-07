#!/usr/bin/env python3
"""Documentary Studio Engine Manager — Windows one-click companion.

Built to make the Android Documentary Studio usable without terminal work.
The manager installs only local/open components and keeps models on the user's disk.
"""
from __future__ import annotations

import ctypes
import hashlib
import json
import os
import platform
import queue
import shutil
import socket
import subprocess
import sys
import threading
import time
import urllib.request
import zipfile
from pathlib import Path
from tkinter import BOTH, END, LEFT, RIGHT, X, Button, Frame, Label, StringVar, Text, Tk, messagebox, ttk

APP_VERSION = "0.1.0"
BASE = Path(os.environ.get("LOCALAPPDATA", str(Path.home()))) / "DocumentaryStudio"
DOWNLOADS = BASE / "downloads"
COMFY_ROOT = BASE / "ComfyUI_windows_portable"
COMFY_DIR = COMFY_ROOT / "ComfyUI"
PYTHON = COMFY_ROOT / "python_embeded" / "python.exe"
ENGINE_DIR = BASE / "engine"
ENGINE_FILE = ENGINE_DIR / "server.py"
FFMPEG_ROOT = BASE / "ffmpeg"
LOG_DIR = BASE / "logs"

COMFY_PORT = 8189
ENGINE_PORT = 8188

URLS = {
    "comfy": "https://github.com/comfyanonymous/ComfyUI/releases/latest/download/ComfyUI_windows_portable_nvidia.7z",
    "7zr": "https://www.7-zip.org/a/7zr.exe",
    "ffmpeg": "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip",
    "image": "https://huggingface.co/SG161222/RealVisXL_V5.0/resolve/main/RealVisXL_V5.0_fp16.safetensors?download=true",
    "wan_model": "https://huggingface.co/Comfy-Org/Wan_2.2_ComfyUI_Repackaged/resolve/main/split_files/diffusion_models/wan2.2_ti2v_5B_fp16.safetensors?download=true",
    "wan_clip": "https://huggingface.co/Comfy-Org/Wan_2.2_ComfyUI_Repackaged/resolve/main/split_files/text_encoders/umt5_xxl_fp8_e4m3fn_scaled.safetensors?download=true",
    "wan_vae": "https://huggingface.co/Comfy-Org/Wan_2.2_ComfyUI_Repackaged/resolve/main/split_files/vae/wan2.2_vae.safetensors?download=true",
}

SHA256 = {
    "image": "6a35a7855770ae9820a3c931d4964c3817b6d9e3c6f9c4dabb5b3a94e5643b80",
    "wan_model": "456f901338bd9eadbded3828b819109a9b68e8a525ca5cf8d0049a69fcfeca1e",
    "wan_clip": "c3355d30191f1f066b26d93fba017ae9809dce6c627dda5f6a66eaa651204f68",
    "wan_vae": "e40321bd36b9709991dae2530eb4ac303dd168276980d3e9bc4b6e2b75fed156",
}

SIZES_GB = {"image": 6.94, "wan_model": 10.0, "wan_clip": 6.74, "wan_vae": 1.41}


def resource_path(name: str) -> Path:
    root = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent))
    return root / name


def human(n: float) -> str:
    units = ["B", "KB", "MB", "GB", "TB"]
    n = float(n)
    for unit in units:
        if n < 1024 or unit == units[-1]:
            return f"{n:.1f} {unit}"
        n /= 1024
    return f"{n:.1f} TB"


def total_ram_gb() -> float:
    class MEMORYSTATUSEX(ctypes.Structure):
        _fields_ = [("dwLength", ctypes.c_ulong), ("dwMemoryLoad", ctypes.c_ulong), ("ullTotalPhys", ctypes.c_ulonglong),
                    ("ullAvailPhys", ctypes.c_ulonglong), ("ullTotalPageFile", ctypes.c_ulonglong), ("ullAvailPageFile", ctypes.c_ulonglong),
                    ("ullTotalVirtual", ctypes.c_ulonglong), ("ullAvailVirtual", ctypes.c_ulonglong), ("ullAvailExtendedVirtual", ctypes.c_ulonglong)]
    try:
        s = MEMORYSTATUSEX(); s.dwLength = ctypes.sizeof(s)
        ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(s))
        return s.ullTotalPhys / 1024**3
    except Exception:
        return 0.0


def gpu_info() -> tuple[str, float]:
    try:
        out = subprocess.check_output([
            "nvidia-smi", "--query-gpu=name,memory.total", "--format=csv,noheader,nounits"
        ], text=True, stderr=subprocess.STDOUT, timeout=8).strip().splitlines()[0]
        name, mem = out.rsplit(",", 1)
        return name.strip(), float(mem.strip()) / 1024.0
    except Exception:
        return "NVIDIA شناسایی نشد", 0.0


def local_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80)); return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        s.close()


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(4 * 1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def download(url: str, dest: Path, progress=None, stop=None) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    part = dest.with_suffix(dest.suffix + ".part")
    existing = part.stat().st_size if part.exists() else 0
    headers = {"User-Agent": "DocumentaryStudio/0.1"}
    if existing:
        headers["Range"] = f"bytes={existing}-"
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=60) as r:
        status = getattr(r, "status", 200)
        if existing and status != 206:
            existing = 0
            try: part.unlink()
            except FileNotFoundError: pass
        mode = "ab" if existing else "wb"
        total_hdr = int(r.headers.get("Content-Length", "0") or 0)
        total = existing + total_hdr if total_hdr else 0
        done = existing
        with part.open(mode) as f:
            while True:
                if stop and stop.is_set():
                    raise RuntimeError("دانلود لغو شد")
                block = r.read(1024 * 1024)
                if not block: break
                f.write(block); done += len(block)
                if progress: progress(done, total)
    part.replace(dest)


def verify(path: Path, expected: str) -> bool:
    return path.exists() and sha256(path).lower() == expected.lower()


def find_ffmpeg() -> Path | None:
    if FFMPEG_ROOT.exists():
        hits = list(FFMPEG_ROOT.rglob("ffmpeg.exe"))
        if hits: return hits[0]
    return None


def comfy_models() -> dict[str, Path]:
    return {
        "image": COMFY_DIR / "models" / "checkpoints" / "RealVisXL_V5.0_fp16.safetensors",
        "wan_model": COMFY_DIR / "models" / "diffusion_models" / "wan2.2_ti2v_5B_fp16.safetensors",
        "wan_clip": COMFY_DIR / "models" / "text_encoders" / "umt5_xxl_fp8_e4m3fn_scaled.safetensors",
        "wan_vae": COMFY_DIR / "models" / "vae" / "wan2.2_vae.safetensors",
    }


class Manager:
    def __init__(self):
        self.root = Tk()
        self.root.title(f"استدیوی مستند — مدیر موتور {APP_VERSION}")
        self.root.geometry("820x700")
        self.root.minsize(760, 620)
        self.root.configure(bg="#0a0f14")
        try: self.root.iconbitmap(str(resource_path("docstudio.ico")))
        except Exception: pass
        self.q: queue.Queue = queue.Queue()
        self.stop = threading.Event()
        self.progress = StringVar(value="آماده")
        self.status = StringVar(value="در حال بررسی سیستم…")
        self._build_ui()
        self.root.after(100, self._poll)
        self.root.after(250, self.refresh)

    def _build_ui(self):
        title = Label(self.root, text="استدیوی مستند", fg="#ffffff", bg="#0a0f14", font=("Segoe UI", 24, "bold"))
        title.pack(pady=(22, 2))
        Label(self.root, text="مدیر موتور محلی — بدون اعتبار روزانه و بدون API اجباری", fg="#d7b56d", bg="#0a0f14", font=("Segoe UI", 11)).pack(pady=(0, 16))

        card = Frame(self.root, bg="#111820", padx=18, pady=14)
        card.pack(fill=X, padx=20, pady=8)
        Label(card, textvariable=self.status, justify=LEFT, anchor="w", fg="#f2f5f7", bg="#111820", font=("Segoe UI", 11)).pack(fill=X)

        self.bar = ttk.Progressbar(self.root, mode="determinate", maximum=100)
        self.bar.pack(fill=X, padx=20, pady=(10, 2))
        Label(self.root, textvariable=self.progress, fg="#aeb8c2", bg="#0a0f14", font=("Segoe UI", 10)).pack(fill=X, padx=20)

        buttons = Frame(self.root, bg="#0a0f14")
        buttons.pack(fill=X, padx=20, pady=14)
        self.install_image = self._button(buttons, "نصب موتور تصویر طبیعی", lambda: self.run_task(self.install_image_pack))
        self.install_image.pack(side=LEFT, fill=X, expand=True, padx=(0, 6))
        self.install_video = self._button(buttons, "نصب موتور ویدیوی طبیعی", lambda: self.run_task(self.install_video_pack))
        self.install_video.pack(side=LEFT, fill=X, expand=True, padx=6)
        self.start_btn = self._button(buttons, "روشن کردن موتور", lambda: self.run_task(self.start_services))
        self.start_btn.pack(side=LEFT, fill=X, expand=True, padx=(6, 0))

        row2 = Frame(self.root, bg="#0a0f14")
        row2.pack(fill=X, padx=20, pady=(0, 10))
        self._button(row2, "بررسی دوباره", self.refresh).pack(side=LEFT, fill=X, expand=True, padx=(0, 5))
        self._button(row2, "باز کردن پوشه خروجی", self.open_output).pack(side=LEFT, fill=X, expand=True, padx=5)
        self._button(row2, "توقف", self.cancel).pack(side=LEFT, fill=X, expand=True, padx=(5, 0))

        self.log = Text(self.root, height=18, bg="#0d141b", fg="#dfe6eb", insertbackground="white", relief="flat", font=("Consolas", 9), wrap="word")
        self.log.pack(fill=BOTH, expand=True, padx=20, pady=(4, 18))
        self.log.insert(END, "مدیر موتور آماده است.\n")
        self.log.configure(state="disabled")

    def _button(self, parent, text, cmd):
        return Button(parent, text=text, command=cmd, bg="#d7b56d", fg="#0a0f14", activebackground="#e5c77f", relief="flat", padx=10, pady=10, font=("Segoe UI", 9, "bold"))

    def emit(self, kind, data): self.q.put((kind, data))

    def _poll(self):
        try:
            while True:
                kind, data = self.q.get_nowait()
                if kind == "log":
                    self.log.configure(state="normal"); self.log.insert(END, str(data) + "\n"); self.log.see(END); self.log.configure(state="disabled")
                elif kind == "progress":
                    pct, text = data; self.bar["value"] = pct; self.progress.set(text)
                elif kind == "refresh": self.refresh()
                elif kind == "error": messagebox.showerror("خطا", str(data))
                elif kind == "info": messagebox.showinfo("استدیوی مستند", str(data))
        except queue.Empty: pass
        self.root.after(120, self._poll)

    def run_task(self, fn):
        self.stop.clear()
        threading.Thread(target=self._task_wrapper, args=(fn,), daemon=True).start()

    def _task_wrapper(self, fn):
        try: fn()
        except Exception as e:
            self.emit("log", f"خطا: {e}"); self.emit("error", str(e))
        finally: self.emit("refresh", None)

    def cancel(self): self.stop.set(); self.emit("log", "درخواست توقف ثبت شد.")

    def hw(self):
        gpu, vram = gpu_info(); ram = total_ram_gb(); disk = shutil.disk_usage(BASE.parent if BASE.parent.exists() else Path.home()).free / 1024**3
        return gpu, vram, ram, disk

    def refresh(self):
        BASE.mkdir(parents=True, exist_ok=True)
        gpu, vram, ram, disk = self.hw(); models = comfy_models()
        image_ready = models["image"].exists()
        wan_ready = all(models[k].exists() for k in ("wan_model", "wan_clip", "wan_vae"))
        comfy_ready = PYTHON.exists() and (COMFY_DIR / "main.py").exists()
        ff = find_ffmpeg()
        ip = local_ip()
        video_note = "مناسب برای Wan 2.2 5B" if vram >= 8 else "Wan سنگین است؛ fallback مستند فعال می‌ماند"
        self.status.set(
            f"GPU: {gpu} | VRAM: {vram:.1f} GB\nRAM: {ram:.1f} GB | فضای آزاد: {disk:.1f} GB\n"
            f"ComfyUI: {'✓' if comfy_ready else '—'}   تصویر: {'✓' if image_ready else '—'}   Wan: {'✓' if wan_ready else '—'}   FFmpeg: {'✓' if ff else '—'}\n"
            f"{video_note}\nآدرس برای اپ اندروید: http://{ip}:{ENGINE_PORT}"
        )
        self.install_video.configure(state="normal" if vram >= 8 or wan_ready else "normal")

    def progress_cb(self, label):
        last = [0.0]
        def cb(done, total):
            now = time.time()
            if now - last[0] < .15 and total and done < total: return
            last[0] = now
            pct = min(100, done * 100 / total) if total else 0
            text = f"{label}: {human(done)}" + (f" / {human(total)}" if total else "")
            self.emit("progress", (pct, text))
        return cb

    def ensure_comfy(self):
        if PYTHON.exists() and (COMFY_DIR / "main.py").exists():
            self.emit("log", "ComfyUI Portable موجود است."); return
        if platform.system() != "Windows": raise RuntimeError("این مدیر فعلاً برای Windows ساخته شده است.")
        DOWNLOADS.mkdir(parents=True, exist_ok=True)
        seven = DOWNLOADS / "7zr.exe"
        archive = DOWNLOADS / "ComfyUI_windows_portable_nvidia.7z"
        if not seven.exists():
            self.emit("log", "دریافت 7-Zip standalone…"); download(URLS["7zr"], seven, self.progress_cb("7-Zip"), self.stop)
        if not archive.exists():
            self.emit("log", "دریافت ComfyUI Portable رسمی…"); download(URLS["comfy"], archive, self.progress_cb("ComfyUI"), self.stop)
        self.emit("progress", (0, "استخراج ComfyUI…")); self.emit("log", "استخراج ComfyUI…")
        BASE.mkdir(parents=True, exist_ok=True)
        proc = subprocess.run([str(seven), "x", str(archive), f"-o{BASE}", "-y"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        if proc.returncode != 0 or not PYTHON.exists(): raise RuntimeError("استخراج ComfyUI ناموفق بود.\n" + proc.stdout[-2000:])
        self.emit("log", "ComfyUI آماده شد ✓")

    def ensure_ffmpeg(self):
        existing = find_ffmpeg()
        if existing: return existing
        DOWNLOADS.mkdir(parents=True, exist_ok=True)
        archive = DOWNLOADS / "ffmpeg-release-essentials.zip"
        if not archive.exists():
            self.emit("log", "دریافت FFmpeg…"); download(URLS["ffmpeg"], archive, self.progress_cb("FFmpeg"), self.stop)
        if FFMPEG_ROOT.exists(): shutil.rmtree(FFMPEG_ROOT, ignore_errors=True)
        FFMPEG_ROOT.mkdir(parents=True, exist_ok=True)
        self.emit("progress", (0, "استخراج FFmpeg…"))
        with zipfile.ZipFile(archive) as z: z.extractall(FFMPEG_ROOT)
        ff = find_ffmpeg()
        if not ff: raise RuntimeError("ffmpeg.exe پس از استخراج پیدا نشد")
        self.emit("log", f"FFmpeg آماده شد ✓ {ff}"); return ff

    def install_model(self, key):
        dest = comfy_models()[key]
        expected = SHA256[key]
        if verify(dest, expected):
            self.emit("log", f"{dest.name} قبلاً سالم نصب شده است ✓"); return
        if dest.exists():
            self.emit("log", f"فایل ناقص/متفاوت حذف شد: {dest.name}"); dest.unlink()
        dest.parent.mkdir(parents=True, exist_ok=True)
        self.emit("log", f"دریافت {dest.name} (~{SIZES_GB[key]} GB)…")
        download(URLS[key], dest, self.progress_cb(dest.name), self.stop)
        self.emit("progress", (0, f"بررسی SHA256 {dest.name}…"))
        if not verify(dest, expected):
            try: dest.unlink()
            except Exception: pass
            raise RuntimeError(f"اعتبار فایل {dest.name} تأیید نشد؛ فایل حذف شد.")
        self.emit("log", f"{dest.name} سالم نصب شد ✓")

    def install_engine_file(self):
        ENGINE_DIR.mkdir(parents=True, exist_ok=True)
        bundled = resource_path("server.py")
        if not bundled.exists(): raise RuntimeError("فایل server.py داخل بسته پیدا نشد")
        shutil.copy2(bundled, ENGINE_FILE)

    def install_image_pack(self):
        _, _, _, disk = self.hw()
        if disk < 15: raise RuntimeError("برای بسته تصویر حداقل حدود 15GB فضای آزاد لازم است.")
        self.ensure_comfy(); self.ensure_ffmpeg(); self.install_engine_file(); self.install_model("image")
        self.emit("progress", (100, "موتور تصویر آماده است ✓")); self.emit("info", "موتور تصویر واقع‌گرایانه نصب شد. حالا «روشن کردن موتور» را بزن.")

    def install_video_pack(self):
        gpu, vram, _, disk = self.hw()
        if disk < 28: raise RuntimeError("برای بسته Wan 2.2 به حدود 28GB فضای آزاد نیاز است.")
        if vram and vram < 8:
            if not messagebox.askyesno("سخت‌افزار", f"VRAM شناسایی‌شده {vram:.1f}GB است. Wan 2.2 5B ممکن است بسیار کند یا ناموفق باشد. ادامه؟"):
                return
        self.ensure_comfy(); self.ensure_ffmpeg(); self.install_engine_file()
        # Image model is also installed so the stable fallback always exists.
        self.install_model("image")
        for key in ("wan_model", "wan_clip", "wan_vae"): self.install_model(key)
        self.emit("progress", (100, "موتور ویدیوی Wan 2.2 آماده است ✓")); self.emit("info", "بسته ویدیوی محلی نصب شد. حالا «روشن کردن موتور» را بزن.")

    def start_services(self):
        if not PYTHON.exists(): raise RuntimeError("نخست موتور تصویر را نصب کن.")
        self.install_engine_file(); ff = self.ensure_ffmpeg()
        LOG_DIR.mkdir(parents=True, exist_ok=True)
        env = os.environ.copy(); env["COMFY_URL"] = f"http://127.0.0.1:{COMFY_PORT}"
        env["DOCSTUDIO_PORT"] = str(ENGINE_PORT); env["DOCSTUDIO_HOST"] = "0.0.0.0"
        env["PATH"] = str(ff.parent) + os.pathsep + env.get("PATH", "")
        flags = getattr(subprocess, "CREATE_NEW_CONSOLE", 0)
        # Start ComfyUI only if its port is not already alive.
        if not self.port_open("127.0.0.1", COMFY_PORT):
            comfy_log = open(LOG_DIR / "comfyui.log", "a", encoding="utf-8")
            subprocess.Popen([str(PYTHON), "-s", str(COMFY_DIR / "main.py"), "--listen", "127.0.0.1", "--port", str(COMFY_PORT), "--windows-standalone-build"], cwd=str(COMFY_ROOT), env=env, stdout=comfy_log, stderr=subprocess.STDOUT, creationflags=flags)
            self.emit("log", "ComfyUI در حال روشن‌شدن است…")
        else: self.emit("log", "ComfyUI از قبل روشن است.")
        if not self.port_open("127.0.0.1", ENGINE_PORT):
            engine_log = open(LOG_DIR / "engine.log", "a", encoding="utf-8")
            subprocess.Popen([str(PYTHON), str(ENGINE_FILE)], cwd=str(ENGINE_DIR), env=env, stdout=engine_log, stderr=subprocess.STDOUT, creationflags=flags)
            self.emit("log", "موتور استدیوی مستند در حال روشن‌شدن است…")
        else: self.emit("log", "موتور استدیوی مستند از قبل روشن است.")
        deadline = time.time() + 90
        while time.time() < deadline:
            if self.port_open("127.0.0.1", ENGINE_PORT): break
            time.sleep(1)
        if not self.port_open("127.0.0.1", ENGINE_PORT): raise RuntimeError("موتور در پورت 8188 بالا نیامد؛ log را بررسی می‌کنم.")
        ip = local_ip(); self.emit("progress", (100, f"روشن ✓  http://{ip}:{ENGINE_PORT}")); self.emit("log", f"برای اپ اندروید: http://{ip}:{ENGINE_PORT}")
        self.emit("info", f"موتور روشن شد.\n\nآدرس: http://{ip}:{ENGINE_PORT}\n\nاپ اندروید می‌تواند به آن وصل شود.")

    @staticmethod
    def port_open(host, port):
        try:
            with socket.create_connection((host, port), timeout=.5): return True
        except OSError: return False

    def open_output(self):
        out = Path.home() / "DocStudio" / "output"; out.mkdir(parents=True, exist_ok=True)
        try: os.startfile(out)
        except Exception: pass

    def run(self): self.root.mainloop()


if __name__ == "__main__":
    Manager().run()
