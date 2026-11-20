package hmgapi.mods.wireless;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import hmgapi.hook.Hooks;
import hmgapi.hook.LoadedApp;
import hmgapi.hook.MethodHook;
import hmgapi.hook.ModContext;

/**
 * ★ key "Android Auto 알림" (setting "aa_keys", mod 0.3.5, K3 KR): the ★ key opens Android Auto's
 * notifications on the phone.
 *
 * GPM's protocol.InputSource tells the phone the keys the car has (registerKeyCodes(int[]), at each
 * connection's setup; the phone takes only those) and sends a key press with sendKeyEvent(int) (down
 * and up; translateKeycode passes codes it has no rule for as they are). AA key codes are Android's,
 * so the notification key is KeyEvent.KEYCODE_NOTIFICATION (83): it's added to the list, and the ★
 * key's broadcast (mod.json provides.star_key "aa_notifications") sends it on the connection's
 * InputSource. Whether the phone opens its notifications for it is the phone's Android Auto's to say.
 */
final class AaKeys {
    private AaKeys() {}

    static final String INPUT_SOURCE = "com.google.android.projection.protocol.InputSource";
    static final String ACTION_NOTIFICATIONS = "hmgapi.mods.wireless.AA_NOTIFICATIONS";
    static final int KEYCODE_NOTIFICATION = 83;

    private static boolean hooked;
    private static volatile WeakReference<Object> input;
    private static Method send;

    static synchronized void hook(final ModContext mod, LoadedApp app) {
        if (hooked) return;
        hooked = true;
        try {
            Class<?> c = Class.forName(INPUT_SOURCE, false, app.classLoader);
            send = c.getMethod("sendKeyEvent", int.class);
            // mod 0.3.7: sendKeyEvent goes straight to native code, with no check of its own, and
            // shutdown() frees that native side: the connection's InputSource is forgotten before its
            // shutdown, under the same lock as a send, so a ★ press never reaches a freed one
            Hooks.findAndHookMethod(c, "shutdown", new MethodHook() {
                @Override
                protected void beforeHookedMethod(Param p) {
                    synchronized (AaKeys.class) {
                        WeakReference<Object> r = input;
                        if (r != null && r.get() == p.thisObject) input = null;
                    }
                }
            });
            Hooks.findAndHookMethod(c, "registerKeyCodes", int[].class, new MethodHook() {
                @Override
                protected void beforeHookedMethod(Param p) {
                    synchronized (AaKeys.class) {
                        input = new WeakReference<Object>(p.thisObject);
                    }
                    if (!mod.enabled()) return;
                    int[] keys = (int[]) p.args[0];
                    if (keys == null) keys = new int[0];
                    for (int k : keys) if (k == KEYCODE_NOTIFICATION) return;
                    int[] more = new int[keys.length + 1];
                    System.arraycopy(keys, 0, more, 0, keys.length);
                    more[keys.length] = KEYCODE_NOTIFICATION;
                    p.args[0] = more;
                    mod.log("Android Auto: notification key offered to the phone (" + keys.length + " keys + 1)");
                }
            });
        } catch (Throwable t) {
            mod.error("Android Auto: key hook (" + INPUT_SOURCE + ")", t);
            return;
        }
        try {
            app.context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent i) {
                    if (!mod.enabled()) return;
                    synchronized (AaKeys.class) {
                        WeakReference<Object> r = input;
                        Object in = r == null ? null : r.get();
                        if (in == null) {
                            mod.log("Android Auto: ★ key notifications, but no Android Auto connection");
                            return;
                        }
                        try {
                            send.invoke(in, KEYCODE_NOTIFICATION);
                            mod.log("Android Auto: ★ key: notification key sent");
                        } catch (Throwable t) {
                            mod.error("Android Auto: notification key", t);
                        }
                    }
                }
            }, new IntentFilter(ACTION_NOTIFICATIONS));
            mod.log("Android Auto: ★ key notifications in " + app.processName);
        } catch (Throwable t) {
            mod.error("Android Auto: ★ key receiver", t);
        }
    }
}
