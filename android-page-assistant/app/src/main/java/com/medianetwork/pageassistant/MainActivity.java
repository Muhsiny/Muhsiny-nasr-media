package com.medianetwork.pageassistant;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private EditText queueBox;
    private EditText countBox;
    private EditText categoryBox;
    private TextView statusView;

    private final String[] first = {"دیدبان","روایت","نبض","افق","پنجره","میدان","سپهر","نگاه","پژواک","مسیر","آینه","زاویه","سرخط","نقطه","مدار","پیوند","لحظه","تراز","منظر","فردا","گفت‌وگو","جامعه","خبر","دیدگاه","چشم‌انداز"};
    private final String[] second = {"امروز","نو","جامعه","فردا","جهان","مردم","روز","اندیشه","واقعیت","دید","تحلیل","روشن","زمان","زندگی","فرهنگ","دانش","اقتصاد","نسل","آینده","گزارش","تصویر","صدا","آگاه","همراه","مدنی"};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Media Page Workspace");
        setContentView(buildUi());
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(28));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root);

        TextView title = text("Media Page Workspace", 25, true);
        title.setTextColor(Color.rgb(20, 88, 61));
        root.addView(title);
        root.addView(text("Facebook دیگر داخل برنامه لاگین نمی‌شود. صفحهٔ ساخت در Facebook اصلیِ نصب‌شده باز می‌شود و اگر در دسترس نباشد در Chrome اصلی باز خواهد شد تا همان نشست شناخته‌شدهٔ حساب تو استفاده شود.", 14, false));

        statusView = text("", 14, true);
        statusView.setPadding(0, dp(12), 0, dp(12));
        root.addView(statusView);

        root.addView(section("۱) ساخت صف"));
        countBox = input("تعداد طرح در این نوبت (۱ تا ۵۰۰)");
        countBox.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        countBox.setText("20");
        root.addView(countBox);
        categoryBox = input("دسته Facebook");
        categoryBox.setText("News & media website");
        root.addView(categoryBox);
        Button generate = button("ساخت نام‌های متفاوت");
        generate.setOnClickListener(v -> generatePlans());
        root.addView(generate);

        queueBox = input("هر خط: نام | دسته | معرفی کوتاه");
        queueBox.setMinLines(8);
        queueBox.setGravity(Gravity.TOP | Gravity.RIGHT);
        root.addView(queueBox);
        Button save = button("ذخیره صف");
        save.setOnClickListener(v -> saveQueue());
        root.addView(save);

        root.addView(section("۲) ساخت در Facebook اصلی"));
        Button open = button("بازکردن ساخت صفحه در Facebook اصلی");
        open.setOnClickListener(v -> openTrustedFacebook());
        root.addView(open);

        Button copyName = button("کپی نام مورد فعلی");
        copyName.setOnClickListener(v -> copyField("name"));
        root.addView(copyName);

        Button copyCategory = button("کپی دسته مورد فعلی");
        copyCategory.setOnClickListener(v -> copyField("category"));
        root.addView(copyCategory);

        Button copyBio = button("کپی معرفی مورد فعلی");
        copyBio.setOnClickListener(v -> copyField("bio"));
        root.addView(copyBio);

        Button copyAll = button("کپی همه مشخصات");
        copyAll.setOnClickListener(v -> copyField("all"));
        root.addView(copyAll);

        Button confirm = button("این صفحه واقعاً ساخته شد → مورد بعدی");
        confirm.setOnClickListener(v -> {
            PlanStore.advance(this);
            toast("ثبت شد؛ مورد بعدی آماده است.");
            refresh();
        });
        root.addView(confirm);

        Button reset = button("بازگشت صف به ابتدا");
        reset.setOnClickListener(v -> { PlanStore.reset(this); refresh(); });
        root.addView(reset);
        return scroll;
    }

    private void generatePlans() {
        int n = 20;
        try { n = Integer.parseInt(countBox.getText().toString().trim()); } catch (Exception ignored) {}
        n = Math.max(1, Math.min(500, n));
        String category = categoryBox.getText().toString().trim();
        if (category.isEmpty()) category = "News & media website";
        int totalCombos = first.length * second.length;
        int start = (int) (System.currentTimeMillis() % totalCombos);
        int step = 37;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) {
            int combo = (start + i * step) % totalCombos;
            String name = first[combo / second.length] + " " + second[combo % second.length];
            String bio = "رسانه مستقل با تمرکز بر خبر، جامعه و روایت رویدادها.";
            if (b.length() > 0) b.append('\n');
            b.append(name).append(" | ").append(category).append(" | ").append(bio);
        }
        queueBox.setText(b.toString());
    }

    private void saveQueue() {
        List<PlanStore.Plan> plans = new ArrayList<>();
        for (String line : queueBox.getText().toString().split("\\n")) {
            String[] p = line.split("\\|", -1);
            if (p.length == 0 || p[0].trim().isEmpty()) continue;
            plans.add(new PlanStore.Plan(p[0].trim(), p.length > 1 ? p[1].trim() : "News & media website", p.length > 2 ? p[2].trim() : ""));
        }
        if (plans.isEmpty()) { toast("صف معتبر نیست."); return; }
        PlanStore.saveQueue(this, plans);
        toast(plans.size() + " مورد ذخیره شد.");
        refresh();
    }

    private void openTrustedFacebook() {
        PlanStore.Plan p = PlanStore.current(this);
        if (p == null) { toast("اول صف را بساز و ذخیره کن."); return; }

        // Put the page name on the clipboard before leaving the app, so the first field is ready to paste.
        putClipboard("page-name", p.name);
        Uri createUri = Uri.parse("https://www.facebook.com/pages/creation/");

        Intent fb = new Intent(Intent.ACTION_VIEW, createUri);
        fb.addCategory(Intent.CATEGORY_BROWSABLE);
        fb.setPackage("com.facebook.katana");
        try {
            startActivity(fb);
            PlanStore.setStatus(this, "Facebook اصلی باز شد؛ نام «" + p.name + "» در کلیپ‌بورد آماده است.");
            return;
        } catch (ActivityNotFoundException ignored) {}

        Intent chrome = new Intent(Intent.ACTION_VIEW, createUri);
        chrome.addCategory(Intent.CATEGORY_BROWSABLE);
        chrome.setPackage("com.android.chrome");
        try {
            startActivity(chrome);
            PlanStore.setStatus(this, "Chrome اصلی باز شد؛ نام «" + p.name + "» در کلیپ‌بورد آماده است.");
            return;
        } catch (ActivityNotFoundException ignored) {}

        Intent browser = new Intent(Intent.ACTION_VIEW, createUri);
        browser.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            startActivity(browser);
            PlanStore.setStatus(this, "مرورگر اصلی دستگاه باز شد؛ نام مورد فعلی در کلیپ‌بورد آماده است.");
        } catch (ActivityNotFoundException e) {
            toast("Facebook یا مرورگر قابل استفاده پیدا نشد.");
        }
    }

    private void copyField(String which) {
        PlanStore.Plan p = PlanStore.current(this);
        if (p == null) { toast("مورد فعالی وجود ندارد."); return; }
        String label;
        String value;
        if ("name".equals(which)) {
            label = "page-name";
            value = p.name;
        } else if ("category".equals(which)) {
            label = "page-category";
            value = p.category;
        } else if ("bio".equals(which)) {
            label = "page-bio";
            value = p.bio;
        } else {
            label = "page-plan";
            value = p.name + "\n" + p.category + "\n" + p.bio;
        }
        putClipboard(label, value);
        toast("کپی شد.");
    }

    private void putClipboard(String label, String value) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, value));
    }

    private void refresh() {
        if (statusView == null) return;
        int total = PlanStore.size(this), index = PlanStore.index(this);
        PlanStore.Plan p = PlanStore.current(this);
        String current = p == null ? "—" : p.name;
        statusView.setText("وضعیت: " + PlanStore.status(this) + "\nپیشرفت: " + Math.min(index, total) + " / " + total + "\nمورد فعلی: " + current);
    }

    private TextView section(String s) { TextView t = text(s, 18, true); t.setPadding(0, dp(22), 0, dp(8)); return t; }
    private TextView text(String s, int sp, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(Color.rgb(35, 45, 40)); if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD); t.setGravity(Gravity.RIGHT); t.setLineSpacing(0, 1.2f); return t; }
    private EditText input(String hint) { EditText e = new EditText(this); e.setHint(hint); e.setTextSize(15); e.setGravity(Gravity.RIGHT); e.setPadding(dp(12), dp(12), dp(12), dp(12)); return e; }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(7), 0, dp(2)); b.setLayoutParams(lp); return b; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
