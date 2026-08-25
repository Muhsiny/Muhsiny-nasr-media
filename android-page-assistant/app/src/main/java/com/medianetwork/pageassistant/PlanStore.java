package com.medianetwork.pageassistant;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

public final class PlanStore {
    private static final String PREF = "page_assistant";
    private static final String KEY_QUEUE = "queue";
    private static final String KEY_INDEX = "index";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_STATUS = "status";

    public static final class Plan {
        public final String name;
        public final String category;
        public final String bio;
        public Plan(String name, String category, String bio) {
            this.name = name;
            this.category = category;
            this.bio = bio;
        }
        String encode() {
            return sanitize(name) + "\t" + sanitize(category) + "\t" + sanitize(bio);
        }
        static Plan decode(String line) {
            String[] p = line.split("\\t", -1);
            if (p.length == 0 || p[0].trim().isEmpty()) return null;
            return new Plan(p[0], p.length > 1 ? p[1] : "News & media website", p.length > 2 ? p[2] : "");
        }
        private static String sanitize(String s) {
            return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').trim();
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void saveQueue(Context c, List<Plan> plans) {
        StringBuilder b = new StringBuilder();
        for (Plan p : plans) {
            if (b.length() > 0) b.append('\n');
            b.append(p.encode());
        }
        prefs(c).edit().putString(KEY_QUEUE, b.toString()).putInt(KEY_INDEX, 0).putBoolean(KEY_ACTIVE, false).putString(KEY_STATUS, "صف ذخیره شد").apply();
    }

    public static List<Plan> loadQueue(Context c) {
        List<Plan> out = new ArrayList<>();
        String raw = prefs(c).getString(KEY_QUEUE, "");
        for (String line : raw.split("\\n")) {
            Plan p = Plan.decode(line);
            if (p != null) out.add(p);
        }
        return out;
    }

    public static int index(Context c) { return prefs(c).getInt(KEY_INDEX, 0); }
    public static Plan current(Context c) {
        List<Plan> q = loadQueue(c);
        int i = index(c);
        return i >= 0 && i < q.size() ? q.get(i) : null;
    }
    public static int size(Context c) { return loadQueue(c).size(); }
    public static void setActive(Context c, boolean active) { prefs(c).edit().putBoolean(KEY_ACTIVE, active).apply(); }
    public static boolean isActive(Context c) { return prefs(c).getBoolean(KEY_ACTIVE, false); }
    public static void setStatus(Context c, String status) { prefs(c).edit().putString(KEY_STATUS, status).apply(); }
    public static String status(Context c) { return prefs(c).getString(KEY_STATUS, "آماده"); }
    public static void advance(Context c) {
        int next = index(c) + 1;
        prefs(c).edit().putInt(KEY_INDEX, next).putBoolean(KEY_ACTIVE, false).putString(KEY_STATUS, next < size(c) ? "مورد بعدی آماده است" : "صف تمام شد").apply();
    }
    public static void reset(Context c) { prefs(c).edit().putInt(KEY_INDEX, 0).putBoolean(KEY_ACTIVE, false).putString(KEY_STATUS, "صف از ابتدا آماده شد").apply(); }

    private PlanStore() {}
}
