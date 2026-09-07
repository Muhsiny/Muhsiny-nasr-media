from pathlib import Path
import asyncio
import edge_tts

TEXT = "در این مستند، روایت را آرام، متین و با تلفظ دقیق فارسی دنبال می‌کنیم. اسناد و تصاویر را کنار هم می‌گذاریم تا تصویر روشن‌تری از واقعیت به دست آید."

VOICES = [
    ("fa-IR-FaridNeural", "farid-neural-fa-IR.mp3", "-8%"),
    ("fa-IR-DilaraNeural", "dilara-neural-fa-IR.mp3", "-6%"),
]

async def render(voice: str, filename: str, rate: str):
    out = Path(filename)
    communicate = edge_tts.Communicate(TEXT, voice, rate=rate, pitch="+0Hz", volume="+0%")
    await communicate.save(str(out))
    if not out.exists() or out.stat().st_size < 1000:
        raise RuntimeError(f"{voice} output was not created")
    print(voice, out, out.stat().st_size)

async def main():
    for voice, filename, rate in VOICES:
        await render(voice, filename, rate)

asyncio.run(main())
