package local.tvhome.keys;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Launches the app assigned to a remote-control key. Assignments live in {@link Prefs}. */
public class KeyService extends AccessibilityService {
    private static final String TAG = "TvKeys";

    interface Learner { void onKey(int code); }

    /** Set by MainActivity while it waits for the user to press the button to add. */
    static volatile Learner learner;
    static volatile boolean connected;
    private int swallowUp = -1;

    @Override
    protected void onServiceConnected() {
        connected = true;
        Log.i(TAG, "connected, " + Prefs.codes(this).length + " keys listed");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        connected = false;
        return super.onUnbind(intent);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;

        if (code == swallowUp) {
            if (!down) swallowUp = -1;
            return true;
        }
        Learner l = learner;
        if (l != null) {
            if (isNavigation(code)) return false;
            if (down) {
                swallowUp = code;
                l.onKey(code);
            }
            return true;
        }

        String pkg = Prefs.target(this, code);
        if (pkg.isEmpty()) return false;
        // Swallow down, repeats and up so the stock handler never sees the key.
        if (down && event.getRepeatCount() == 0) launch(pkg);
        return true;
    }

    /** Keys that must keep working while the "press a button" dialog is open. */
    private static boolean isNavigation(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                return true;
            default:
                return false;
        }
    }

    private void launch(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) {
            Log.w(TAG, "no launch intent for " + pkg);
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            Log.i(TAG, "launched " + pkg);
        } catch (Exception e) {
            Log.e(TAG, "cannot launch " + pkg, e);
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
}
