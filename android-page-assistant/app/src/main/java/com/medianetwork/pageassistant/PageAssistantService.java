package com.medianetwork.pageassistant;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PageAssistantService extends AccessibilityService {
    private long lastActionAt = 0L;
    private String lastAction = "";

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!PlanStore.isActive(this)) return;
        long now = System.currentTimeMillis();
        if (now - lastActionAt < 500) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        PlanStore.Plan plan = PlanStore.current(this);
        if (plan == null) { PlanStore.setActive(this, false); return; }

        String screen = collectText(root).toLowerCase(Locale.ROOT);
        if (hasAny(screen, "temporarily blocked", "try again later", "security check", "captcha", "suspicious activity", "موقتاً مسدود", "موقتا مسدود", "بعداً دوباره", "بررسی امنیتی", "فعالیت مشکوک", "تأیید هویت", "تایید هویت")) {
            PlanStore.setStatus(this, "متوقف شد: Facebook بررسی یا محدودیت نشان داد.");
            PlanStore.setActive(this, false);
            return;
        }
        if (!hasAny(screen, "page", "category", "create", "صفحه", "دسته", "ایجاد")) return;

        if (hasAny(screen, "your page is ready", "page created", "صفحه شما آماده", "صفحه ایجاد شد", "صفحه‌تان آماده")) {
            PlanStore.setStatus(this, "Facebook ساخت صفحه را تأیید کرده؛ به برنامه برگرد و «این صفحه واقعاً ساخته شد» را بزن.");
            PlanStore.setActive(this, false);
            return;
        }

        AccessibilityNodeInfo nameField = findEditable(root, new String[]{"page name","name your page","نام صفحه","نام پیج"});
        if (nameField != null && needsText(nameField, plan.name)) {
            setText(nameField, plan.name, "name");
            PlanStore.setStatus(this, "نام صفحه تکمیل شد: " + plan.name);
            return;
        }

        AccessibilityNodeInfo bioField = findEditable(root, new String[]{"bio","description","about","معرفی","توضیح","درباره"});
        if (bioField != null && !plan.bio.isEmpty() && needsText(bioField, plan.bio)) {
            setText(bioField, plan.bio, "bio");
            PlanStore.setStatus(this, "معرفی صفحه تکمیل شد.");
            return;
        }

        if (hasAny(screen, "select category", "choose category", "category", "دسته‌بندی", "دسته بندی")) {
            AccessibilityNodeInfo categoryField = findEditable(root, new String[]{"search categories","search category","category","جستجوی دسته","دسته"});
            if (categoryField != null && needsText(categoryField, plan.category)) {
                setText(categoryField, plan.category, "category");
                PlanStore.setStatus(this, "دسته جستجو شد: " + plan.category);
                return;
            }
            AccessibilityNodeInfo choice = findText(root, plan.category);
            if (choice != null) {
                clickNode(choice, "category-choice");
                PlanStore.setStatus(this, "دسته انتخاب شد.");
                return;
            }
            AccessibilityNodeInfo openCategory = findByTokens(root, new String[]{"category","دسته‌بندی","دسته بندی"});
            if (openCategory != null && clickNode(openCategory, "open-category")) return;
        }

        AccessibilityNodeInfo next = findExactButton(root, new String[]{"next","continue","done","بعدی","ادامه","تکمیل"});
        if (next != null && clickNode(next, "next")) {
            PlanStore.setStatus(this, "به مرحله بعد رفت.");
            return;
        }

        AccessibilityNodeInfo create = findExactButton(root, new String[]{"create","create page","ایجاد","ساخت صفحه","ایجاد صفحه"});
        if (create != null) {
            PlanStore.setStatus(this, "آماده تأیید نهایی است؛ دکمه Create/ایجاد را خودت بزن.");
        }
    }

    @Override public void onInterrupt() {
        PlanStore.setStatus(this, "Accessibility متوقف شد.");
    }

    @Override protected void onServiceConnected() {
        PlanStore.setStatus(this, "Accessibility فعال است؛ صف آماده اجراست.");
    }

    private boolean needsText(AccessibilityNodeInfo n, String wanted) {
        CharSequence t = n.getText();
        return t == null || !wanted.equals(t.toString().trim());
    }

    private void setText(AccessibilityNodeInfo node, String value, String key) {
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        lastActionAt = System.currentTimeMillis();
        lastAction = key;
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo root, String[] tokens) {
        List<AccessibilityNodeInfo> nodes = allNodes(root);
        AccessibilityNodeInfo fallback = null;
        for (AccessibilityNodeInfo n : nodes) {
            CharSequence cls = n.getClassName();
            boolean editable = n.isEditable() || (cls != null && cls.toString().toLowerCase(Locale.ROOT).contains("edittext"));
            if (!editable) continue;
            if (fallback == null) fallback = n;
            String meta = meta(n);
            for (String token : tokens) if (meta.contains(token.toLowerCase(Locale.ROOT))) return n;
        }
        return fallback;
    }

    private AccessibilityNodeInfo findExactButton(AccessibilityNodeInfo root, String[] labels) {
        for (AccessibilityNodeInfo n : allNodes(root)) {
            String text = nodeText(n).trim().toLowerCase(Locale.ROOT);
            if (text.isEmpty()) continue;
            for (String label : labels) {
                if (text.equals(label.toLowerCase(Locale.ROOT))) return n;
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findByTokens(AccessibilityNodeInfo root, String[] tokens) {
        for (AccessibilityNodeInfo n : allNodes(root)) {
            String m = meta(n);
            for (String token : tokens) if (m.contains(token.toLowerCase(Locale.ROOT))) return n;
        }
        return null;
    }

    private AccessibilityNodeInfo findText(AccessibilityNodeInfo root, String wanted) {
        if (wanted == null || wanted.trim().isEmpty()) return null;
        String w = wanted.toLowerCase(Locale.ROOT);
        for (AccessibilityNodeInfo n : allNodes(root)) if (nodeText(n).toLowerCase(Locale.ROOT).contains(w)) return n;
        return null;
    }

    private boolean clickNode(AccessibilityNodeInfo node, String action) {
        if (action.equals(lastAction) && System.currentTimeMillis() - lastActionAt < 1600) return false;
        AccessibilityNodeInfo n = node;
        for (int i = 0; i < 5 && n != null; i++) {
            if (n.isClickable()) {
                boolean ok = n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                if (ok) { lastAction = action; lastActionAt = System.currentTimeMillis(); }
                return ok;
            }
            n = n.getParent();
        }
        return false;
    }

    private List<AccessibilityNodeInfo> allNodes(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> out = new ArrayList<>();
        walk(root, out, 0);
        return out;
    }

    private void walk(AccessibilityNodeInfo n, List<AccessibilityNodeInfo> out, int depth) {
        if (n == null || depth > 40 || out.size() > 1500) return;
        out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) walk(n.getChild(i), out, depth + 1);
    }

    private String collectText(AccessibilityNodeInfo root) {
        StringBuilder b = new StringBuilder();
        for (AccessibilityNodeInfo n : allNodes(root)) {
            String t = nodeText(n);
            if (!t.isEmpty()) b.append(' ').append(t);
        }
        return b.toString();
    }

    private String meta(AccessibilityNodeInfo n) {
        StringBuilder b = new StringBuilder(nodeText(n));
        if (n.getContentDescription() != null) b.append(' ').append(n.getContentDescription());
        if (Build.VERSION.SDK_INT >= 26 && n.getHintText() != null) b.append(' ').append(n.getHintText());
        if (n.getViewIdResourceName() != null) b.append(' ').append(n.getViewIdResourceName());
        return b.toString().toLowerCase(Locale.ROOT);
    }

    private String nodeText(AccessibilityNodeInfo n) {
        return n.getText() == null ? "" : n.getText().toString();
    }

    private boolean hasAny(String hay, String... needles) {
        for (String n : needles) if (hay.contains(n.toLowerCase(Locale.ROOT))) return true;
        return false;
    }
}
