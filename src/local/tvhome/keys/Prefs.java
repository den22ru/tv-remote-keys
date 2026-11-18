package local.tvhome.keys;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.TreeSet;

/** Key code -> package mapping, stored as "key_<code>" -> package ("" = listed but unassigned). */
final class Prefs {
    private static final String TAG = "TvKeys";
    private static final String PREFIX = "key_";
    /** Shortcut buttons of the Xiaomi TV Box S 2nd Gen remote; always listed. */
    static final int[] KNOWN = {193, 194, 192};

    private Prefs() { }

    static SharedPreferences get(Context c) {
        SharedPreferences p = c.getApplicationContext().getSharedPreferences("keys", Context.MODE_PRIVATE);
        if (!p.getBoolean("seeded", false)) seed(c, p);
        return p;
    }

    private static void seed(Context c, SharedPreferences p) {
        SharedPreferences.Editor e = p.edit().putBoolean("seeded", true);
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(c.getAssets().open("keys.txt"), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                int hash = line.indexOf('#');
                if (hash >= 0) line = line.substring(0, hash);
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 2) continue;
                try {
                    e.putString(PREFIX + Integer.parseInt(parts[0]), parts[1]);
                } catch (NumberFormatException ex) {
                    Log.w(TAG, "bad line: " + line);
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "cannot read keys.txt", ex);
        }
        e.apply();
    }

    /** Package assigned to the key, or "" when none. */
    static String target(Context c, int code) {
        return get(c).getString(PREFIX + code, "");
    }

    static void set(Context c, int code, String pkg) {
        get(c).edit().putString(PREFIX + code, pkg).apply();
    }

    static void remove(Context c, int code) {
        get(c).edit().remove(PREFIX + code).apply();
    }

    /** Known buttons first, then any the user added, ascending. */
    static int[] codes(Context c) {
        TreeSet<Integer> extra = new TreeSet<>();
        for (Map.Entry<String, ?> en : get(c).getAll().entrySet()) {
            String k = en.getKey();
            if (!k.startsWith(PREFIX)) continue;
            try {
                extra.add(Integer.parseInt(k.substring(PREFIX.length())));
            } catch (NumberFormatException ignored) { }
        }
        for (int k : KNOWN) extra.remove(k);
        int[] out = new int[KNOWN.length + extra.size()];
        int i = 0;
        for (int k : KNOWN) out[i++] = k;
        for (int k : extra) out[i++] = k;
        return out;
    }

    static boolean isKnown(int code) {
        for (int k : KNOWN) if (k == code) return true;
        return false;
    }

    static String name(int code) {
        switch (code) {
            case 193: return "Кнопка Netflix";
            case 194: return "Кнопка Prime Video";
            case 192: return "Кнопка Xiaomi TV+";
            default:
                return "Кнопка " + KeyEvent.keyCodeToString(code).replace("KEYCODE_", "") + " (" + code + ")";
        }
    }
}
