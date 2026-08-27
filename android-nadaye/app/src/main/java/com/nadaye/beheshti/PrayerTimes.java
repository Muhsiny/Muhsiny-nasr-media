package com.nadaye.beheshti;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public final class PrayerTimes {
    private PrayerTimes() {}

    public static final class Result {
        public final LinkedHashMap<String, Long> millis = new LinkedHashMap<>();
        public String get(String key) { Long v = millis.get(key); return v == null ? "--:--" : fmt(v); }
    }

    public static Result calculate(long dayMillis, double latitude, double longitude) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(dayMillis);
        c.set(Calendar.HOUR_OF_DAY, 12); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        int n = c.get(Calendar.DAY_OF_YEAR);
        double gamma = 2.0 * Math.PI / 365.0 * (n - 1);
        double eqTime = 229.18 * (0.000075 + 0.001868*Math.cos(gamma) - 0.032077*Math.sin(gamma) - 0.014615*Math.cos(2*gamma) - 0.040849*Math.sin(2*gamma));
        double decl = 0.006918 - 0.399912*Math.cos(gamma) + 0.070257*Math.sin(gamma) - 0.006758*Math.cos(2*gamma) + 0.000907*Math.sin(2*gamma) - 0.002697*Math.cos(3*gamma) + 0.00148*Math.sin(3*gamma);
        double tz = TimeZone.getDefault().getOffset(c.getTimeInMillis()) / 3600000.0;
        double solarNoon = 720.0 - 4.0*longitude - eqTime + tz*60.0;
        Result r = new Result();
        r.millis.put("فجر", minuteToMillis(c, solarNoon - angleMinutes(latitude, decl, -16.0)));
        r.millis.put("طلوع", minuteToMillis(c, solarNoon - angleMinutes(latitude, decl, -0.833)));
        r.millis.put("ظهر", minuteToMillis(c, solarNoon));
        r.millis.put("مغرب", minuteToMillis(c, solarNoon + angleMinutes(latitude, decl, -4.0)));
        r.millis.put("عشاء", minuteToMillis(c, solarNoon + angleMinutes(latitude, decl, -14.0)));
        return r;
    }

    private static double angleMinutes(double latDeg, double declRad, double altitudeDeg) {
        double lat = Math.toRadians(latDeg);
        double alt = Math.toRadians(altitudeDeg);
        double cosH = (Math.sin(alt) - Math.sin(lat)*Math.sin(declRad)) / (Math.cos(lat)*Math.cos(declRad));
        cosH = Math.max(-1.0, Math.min(1.0, cosH));
        return Math.toDegrees(Math.acos(cosH)) * 4.0;
    }

    private static long minuteToMillis(Calendar base, double minute) {
        Calendar d = (Calendar) base.clone();
        d.set(Calendar.HOUR_OF_DAY, 0); d.set(Calendar.MINUTE, 0); d.set(Calendar.SECOND, 0); d.set(Calendar.MILLISECOND, 0);
        return d.getTimeInMillis() + Math.round(minute * 60000.0);
    }

    public static String fmt(long millis) {
        return new SimpleDateFormat("HH:mm", new Locale("fa")).format(new Date(millis));
    }

    public static String nextPrayer(Result r, long now) {
        for (Map.Entry<String,Long> e : r.millis.entrySet()) if (e.getValue() > now) return e.getKey() + "  " + fmt(e.getValue());
        return "فجر فردا";
    }
}
