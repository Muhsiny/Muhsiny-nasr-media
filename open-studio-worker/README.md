# Documentary Studio V7 Open Worker

A zero-subscription local compute bridge for Documentary Studio V7. The Android app keeps project editing, Persian narration and final Media3 rendering on-device; this worker exposes only heavy AI engines that are actually available on a local computer or a trusted community machine.

## Principles

- No proprietary paywall bypassing.
- No mandatory cloud account, API key or credits.
- Capability truth: a feature is advertised only after its local engine is detected **and** the V7 protocol has a usable action for it.
- Historical integrity: AI Director distinguishes `archive` from `generated`; Android never silently replaces an archive scene with generated media.
- Token authentication is mandatory for all private endpoints.
- Temporary transcription uploads are removed after processing.

## Start

```bash
python server.py --host 0.0.0.0 --port 8190
```

The first run creates `.state/token.txt` and prints the token. Enter the machine LAN address (for example `http://192.168.1.10:8190`) and this token in V7.

## Optional local engines

### AI Director

Run an Ollama-compatible local server on `127.0.0.1:11434`. The worker discovers installed models automatically and prefers Qwen/Llama/Gemma/Mistral names. No AI Director button is enabled in Android if no local model is detected.

### Image / video generation

Run ComfyUI on `127.0.0.1:8188`. Export API-format workflows and save them as:

- `workflows/image.json`
- `workflows/video.json`

Or set `DOCSTUDIO_IMAGE_WORKFLOW` and `DOCSTUDIO_VIDEO_WORKFLOW` to absolute paths.

Workflow JSON may contain these placeholders:

- `{{PROMPT}}`
- `{{NEGATIVE}}`
- `{{WIDTH}}`
- `{{HEIGHT}}`
- `{{SECONDS}}`
- `{{SEED}}`

This keeps Android independent of any single image/video model. A community computer can use any suitably licensed ComfyUI-compatible workflow without changing the APK.

### Whisper.cpp transcription

Set:

```text
DOCSTUDIO_WHISPER_BIN=/path/to/whisper-cli
DOCSTUDIO_WHISPER_MODEL=/path/to/model.gguf
```

Optional language override:

```text
DOCSTUDIO_WHISPER_LANGUAGE=fa
```

When both executable and model are present, V7 enables **رونویسی صوت/ویدیو و افزودن به پروژه**. Android copies the selected file to a temporary local upload, streams it to `/v1/transcribe`, the worker invokes whisper.cpp, returns UTF-8 text, and removes temporary worker files. The transcript is appended to the active project and split into real subtitle-enabled scenes.

Maximum upload defaults to 512 MiB and can be changed with `DOCSTUDIO_MAX_UPLOAD_BYTES`. Transcription timeout defaults to 1800 seconds and can be changed with `DOCSTUDIO_TRANSCRIBE_TIMEOUT`.

### Upscale

Upscale is deliberately reported as unavailable in protocol 1 until a complete upload → workflow → returned-asset path and Android UI action are implemented and tested. V7 never advertises this as working merely because a ComfyUI workflow file exists.

## Security

The Android client permits cleartext HTTP only for private/loopback LAN addresses in application code. Public HTTP endpoints are rejected. HTTPS is supported. All capability, planning, transcription, generation, job and file-proxy endpoints require `Authorization: Bearer <token>`.

## CI contract

CI runs the real worker and real Android client against deterministic local stand-ins for external model engines. It verifies:

- capability detection and token-protected protocol;
- binary transcription upload and returned Persian text;
- AI Director plan flow;
- generated asset download into a real DocumentaryProject;
- archive scenes remain unmodified;
- bundled on-device Persian PocketTTS runtime/model assets;
- real Android Media3 export tests;
- Android API 35 instrumentation before the APK artifact is published.

Model quality is not faked by these deterministic CI stand-ins: on a real installation, AI and transcription quality depend on the open models and hardware the user installs on the local worker.
