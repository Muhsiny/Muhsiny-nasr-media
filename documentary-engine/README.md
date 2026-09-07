# Documentary Studio Local Engine 0.2

هستهٔ محلی و بدون API پولی برای استدیوی مستند.

## مسیر تولید

- متن فارسی/دری از اپ دریافت می‌شود.
- اگر Ollama محلی فعال باشد، متن صحنه برای مدل تصویری به پرامپت دقیق انگلیسی تبدیل می‌شود؛ در غیر آن متن اصلی حفظ می‌شود.
- تصویر توسط ComfyUI و یک checkpoint محلی SD/SDXL ساخته می‌شود.
- موتور به‌صورت خودکار checkpointهای موجود را می‌خواند و مدل‌هایی با نام‌های RealVis/Juggernaut/Realistic/Photoreal را در اولویت می‌گذارد.
- برای ویدیوی پایه، همان تصویر با FFmpeg به کلیپ 1080p دارای حرکت نرم دوربین تبدیل می‌شود.
- اگر `WAN_WORKFLOW_API` تنظیم باشد، موتور می‌تواند یک workflow API-format از Wan را اجرا کند و در صورت شکست، به مسیر مستندِ پایدار برگردد.

## API

- `GET /system_stats` — وضعیت واقعی ComfyUI، تعداد checkpointها، FFmpeg، Ollama و Wan.
- `POST /docstudio/generate` — بدنه JSON با `type=image|video` و `prompt`.
- `GET /outputs/<filename>` — دریافت خروجی ساخته‌شده.

## واقع‌گرایی

در هسته، prompt مثبت به‌طور ثابت بر عکاسی مستند، نور طبیعی، پوست و آناتومی طبیعی و محیط واقعی تأکید می‌کند. موارد cartoon/anime/illustration/CGI/plastic skin در negative prompt قرار دارند.

## نکتهٔ فنی

خود این سرور فقط به Python استاندارد نیاز دارد. تولید تصویر به ComfyUI محلی و یک مدل نصب‌شده نیاز دارد؛ ویدیوهای FFmpeg نیز به FFmpeg محلی نیاز دارند. هدف این معماری این است که هیچ دقیقه/اعتبار روزانه یا API اجباری وجود نداشته باشد.
