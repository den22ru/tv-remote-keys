package local.tvhome.keys;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Settings screen: pick which app each remote button opens. */
public class MainActivity extends Activity {
    private ListView list;
    private TextView status;
    private int[] codes = new int[0];

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = dp(48);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(32), pad, dp(24));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        status.setPadding(0, dp(6), 0, dp(14));
        root.addView(status);

        list = new ListView(this);
        list.setOnItemClickListener((parent, view, pos, id) -> {
            if (pos < codes.length) pickApp(codes[pos]);
            else learn();
        });
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        list.requestFocus();
    }

    @Override
    protected void onPause() {
        KeyService.learner = null;
        super.onPause();
    }

    private void refresh() {
        status.setText(KeyService.connected
                ? "Выберите кнопку, чтобы изменить приложение."
                : "Служба выключена — кнопки не работают. Включите «Кнопки пульта» в "
                + "Настройки → Система → Специальные возможности.");
        int keep = list.getSelectedItemPosition();
        codes = Prefs.codes(this);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int code : codes) {
            String pkg = Prefs.target(this, code);
            rows.add(row(Prefs.name(code), pkg.isEmpty() ? "не назначено" : "→ " + label(pkg)));
        }
        rows.add(row("+ Добавить другую кнопку", "Нажмите её на пульте, затем выберите приложение"));
        list.setAdapter(new SimpleAdapter(this, rows, android.R.layout.simple_list_item_2,
                new String[] {"a", "b"}, new int[] {android.R.id.text1, android.R.id.text2}));
        if (keep >= 0 && keep < rows.size()) list.setSelection(keep);
    }

    private static Map<String, String> row(String a, String b) {
        Map<String, String> m = new HashMap<>();
        m.put("a", a);
        m.put("b", b);
        return m;
    }

    private String label(String pkg) {
        PackageManager pm = getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg + " (не установлено)";
        }
    }

    /** Launchable apps as label -> package, sorted by label; TV apps and phone-style apps both. */
    private TreeMap<String, String> apps() {
        PackageManager pm = getPackageManager();
        Map<String, String> byPkg = new HashMap<>();
        for (String cat : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || byPkg.containsKey(pkg)) continue;
                byPkg.put(pkg, ri.activityInfo.applicationInfo.loadLabel(pm).toString());
            }
        }
        TreeMap<String, String> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, String> e : byPkg.entrySet()) {
            String name = e.getValue();
            // Two apps can share a label; keep both.
            out.put(out.containsKey(name) ? name + " (" + e.getKey() + ")" : name, e.getKey());
        }
        return out;
    }

    private void pickApp(int code) {
        TreeMap<String, String> apps = apps();
        List<String> labels = new ArrayList<>();
        List<String> pkgs = new ArrayList<>();
        labels.add("Ничего (кнопка работает как раньше)");
        pkgs.add("");
        if (!Prefs.isKnown(code)) {
            labels.add("Убрать кнопку из списка");
            pkgs.add(null);
        }
        String current = Prefs.target(this, code);
        int checked = 0;
        for (Map.Entry<String, String> e : apps.entrySet()) {
            if (e.getValue().equals(current)) checked = labels.size();
            labels.add(e.getKey());
            pkgs.add(e.getValue());
        }
        new AlertDialog.Builder(this)
                .setTitle(Prefs.name(code))
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, (d, which) -> {
                    String pkg = pkgs.get(which);
                    if (pkg == null) Prefs.remove(this, code);
                    else Prefs.set(this, code, pkg);
                    d.dismiss();
                    refresh();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void learn() {
        if (!KeyService.connected) {
            new AlertDialog.Builder(this)
                    .setMessage("Сначала включите службу «Кнопки пульта» в Настройки → Система → "
                            + "Специальные возможности.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        TextView msg = new TextView(this);
        msg.setText("Нажмите на пульте кнопку, которую хотите назначить.\n\n"
                + "Стрелки, OK, «Назад», «Домой» и громкость назначить нельзя.");
        msg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(dp(24), dp(24), dp(24), dp(8));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(msg)
                .setNegativeButton("Отмена", null)
                .setOnDismissListener(d -> KeyService.learner = null)
                .create();
        KeyService.learner = code -> runOnUiThread(() -> {
            KeyService.learner = null;
            dialog.dismiss();
            if (Prefs.target(this, code).isEmpty()) Prefs.set(this, code, "");
            refresh();
            pickApp(code);
        });
        dialog.show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
