# Documentary Studio V7 Open Worker

A zero-subscription local compute bridge for Documentary Studio. The Android app keeps editing, Persian narration and final Media3 rendering on-device; this worker exposes only locally available heavy AI engines.

## Principles

- No proprietary paywall bypassing.
- No mandatory cloud account, API key or credits.
- Capability truth: a feature is advertised only after its local engine and required workflow are detected.
- Historical integrity: AI Director distinguishes `archive` from `generated`; the Android app never silently replaces an archive scene with generated media.
- Token authentication is mandatory for all private endpoints.

## Start

```bash
python server.py --host 0.0.0.0 --port 8190
```

The first run creates `.state/token.txt` and prints the token. Enter the machine LAN address (for example `http://192.168.1.10:8190`) and this token in V7.

## Optional local engines

### AI Director

Run an Ollama-compatible local server on `127.0.0.1:11434`. The worker discovers installed models automatically and prefers Qwen/Llama/Gemma/Mistral names. No AI Director button is enabled in Android if no model is detected.

### Image / video generation

Run ComfyUI on `127.0.0.1:8188`. Export API-format workflows and save them as:

- `workflows/image.json`
- `workflows/video.json`
- optional `workflows/upscale.json`

Or set `DOCSTUDIO_IMAGE_WORKFLOW`, `DOCSTUDIO_VIDEO_WORKFLOW`, or `DOCSTUDIO_UPSCALE_WORKFLOW` to absolute paths.

Workflow JSON may contain these placeholders:

- `{{PROMPT}}`
- `{{NEGATIVE}}`
- `{{WIDTH}}`
- `{{HEIGHT}}`
- `{{SECONDS}}`
- `{{SEED}}`

This keeps the Android app independent of any single image/video model. A community computer can use Wan, LTX, Hunyuan, Flux, Qwen Image, or another ComfyUI-compatible open workflow without changing the APK.

### Whisper.cpp

Set:

```text
DOCSTUDIO_WHISPER_BIN=/path/to/whisper-cli
DOCSTUDIO_WHISPER_MODEL=/path/to/model.gguf
```

The current protocol reports Whisper availability; file-upload transcription UI is intentionally not exposed in V7 until its Android end-to-end path is implemented and tested.

## Security

The Android client permits cleartext HTTP only for private/loopback LAN addresses in application code. Public HTTP endpoints are rejected. HTTPS is supported. All capability, planning, generation, job and file-proxy endpoints require `Authorization: Bearer <token>`.

## CI contract

CI starts fake Ollama/ComfyUI dependencies but the real `server.py`. Android emulator connects through `10.0.2.2`, requests real capabilities and a real plan from the protocol, generates a test asset, downloads it, and inserts it into a real DocumentaryProject. The archive scene must remain unmodified or the test fails.
