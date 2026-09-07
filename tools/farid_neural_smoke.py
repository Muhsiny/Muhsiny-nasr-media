from pathlib import Path
import asyncio
import edge_tts

TEXT = "در این مستند، روایت را آرام، متین و با تلفظ دقیق فارسی دنبال می‌کنیم. اسناد و تصاویر را کنار هم می‌گذاریم تا تصویر روشن‌تری از واقعیت به دست آید."

async def main():
    out = Path("farid-neural-fa-IR.mp3")
    communicate = edge_tts.Communicate(TEXT, "fa-IR-FaridNeural", rate="-8%", pitch="+0Hz", volume="+0%")
    await communicate.save(str(out))
    if not out.exists() or out.stat().st_size < 1000:
        raise RuntimeError("Farid Neural output was not created")
    print(out, out.stat().st_size)

asyncio.run(main())
