package hmgapi.mods.wireless;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import android.net.wifi.WifiManager;

import hmgapi.hook.Hooks;
import hmgapi.hook.LoadedApp;
import hmgapi.hook.MethodHook;
import hmgapi.hook.ModContext;

/**
 * Wireless Android Auto on the USB dongle's hotspot (setting "aa_wifi"), for units without a
 * Wi-Fi chip (K3 KR BDPE 260902: HW Variant 3, no wlan0).
 *
 * The Android Auto app (HKMC_GPM) and Phone Projection (com.daudio.app.phoneprojection) send the
 * phone the hotspot's details only when WifiManager.isWifiApEnabled() is true and
 * getWifiApMode() is 2 (the projection AP); the stock Wi-Fi service knows nothing of the dongle's
 * AP, so they stop there. While a dongle interface (rtwlanN) holds a private IPv4 address, in
 * these two processes only, both answer as the projection AP would, and the AP on/off and
 * configuration calls don't reach the stock Wi-Fi service (the dongle's AP is usbwifi.hotspot's).
 * Same as Korea Projection's AaWifi (StopDragon/HMG-API korea-build, AGPL-3.0), whose signal is a
 * property its package sets; here the signal is the dongle's address itself.
 *
 * The host IP and BSSID: GPM's sink.wireless.a.dS() (IP and BSSID) and dT() (IP) walk
 * NetworkInterface.getNetworkInterfaces() and skip every interface whose getDisplayName() isn't
 * "wlan0" ("Failed to find host IP" otherwise, and that attempt ends). While one of them runs, on
 * its thread, the dongle's AP interface answers getDisplayName() and getName() with "wlan0" (mod
 * 0.3.0 had getName only: still "Failed to find host IP"), so they take its address and MAC as they
 * would the chip's. Never when a real wlan0 is there. SSID and passphrase come from
 * getWifiApConfiguration (softap.conf), which ev6kr.usbap.sync keeps the dongle's AP on.
 */
final class AaWifi {
    private AaWifi() {}

    static final String PHONEPROJ = "com.daudio.app.phoneprojection";
    static final String GPM_WIFI = "com.google.android.projection.sink.wireless.a";
    static final String WLAN0 = "wlan0";
    /** WifiManager.getWifiApMode: the CarPlay/Android Auto AP */
    static final int AP_MODE_PROJECTION = 2;
    static final long CACHE_MS = 2000L;

    private static boolean hooked;
    private static int blockLogs;
    /** dS/dT running on this thread (nesting depth) */
    private static final ThreadLocal<int[]> IN_HOST = new ThreadLocal<int[]>() {
        @Override
        protected int[] initialValue() {
            return new int[1];
        }
    };
    private static volatile String apIf;
    private static volatile long apIfAt;
    private static boolean apLogged;

    /**
     * The dongle's AP interface: the rtwlan interface with a private IPv4 address (the highest
     * index if more than one), or null (none, or a real wlan0 is there). Cached for CACHE_MS.
     */
    static String apInterface() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (apIfAt == 0) return scan(now);   // the first answer in this process: wait for it
        if (now - apIfAt >= CACHE_MS) refreshLater();
        return apIf;
    }

    /**
     * mod 0.3.6: after the first scan, the scan runs on the mod's own thread, so the apps' calls
     * (isWifiApEnabled and getWifiApMode on their own threads, the UI's too) never wait for it;
     * they get the last answer, at most CACHE_MS and one scan old.
     */
    private static android.os.Handler scanner;
    private static boolean scanQueued;

    private static synchronized void refreshLater() {
        if (scanQueued) return;
        scanQueued = true;
        if (scanner == null) {
            android.os.HandlerThread t = new android.os.HandlerThread("hmgapi-aa-wifi");
            t.start();
            scanner = new android.os.Handler(t.getLooper());
        }
        scanner.post(new Runnable() {
            @Override
            public void run() {
                try {
                    scan(android.os.SystemClock.elapsedRealtime());
                } finally {
                    synchronized (AaWifi.class) {
                        scanQueued = false;
                    }
                }
            }
        });
    }

    private static String scan(long now) {
        String best = null;
        int bestIndex = -1;
        int[] d = IN_HOST.get();
        int depth = d[0];
        d[0] = 0;   // the real names here, even inside dS/dT (the getName hook would come back here)
        try {
            Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
            while (e != null && e.hasMoreElements()) {
                NetworkInterface n = e.nextElement();
                String name = n.getName();
                if (WLAN0.equals(name)) {
                    best = null;
                    break;
                }
                if (name == null || !name.startsWith("rtwlan") || !hasPrivateV4(n)) continue;
                if (n.getIndex() > bestIndex) {
                    best = name;
                    bestIndex = n.getIndex();
                }
            }
        } catch (Throwable t) {
            best = null;
        } finally {
            d[0] = depth;
        }
        apIf = best;
        apIfAt = now;
        return best;
    }

    static boolean hasPrivateV4(NetworkInterface n) {
        Enumeration<InetAddress> a = n.getInetAddresses();
        while (a != null && a.hasMoreElements()) {
            InetAddress x = a.nextElement();
            if (x instanceof Inet4Address && x.isSiteLocalAddress()) return true;
        }
        return false;
    }

    static boolean on(ModContext mod) {
        return mod.enabled() && apInterface() != null;
    }

    static synchronized void hook(final ModContext mod, LoadedApp app) {
        if (hooked) return;
        hooked = true;
        int n = 0;
        n += answer(mod, "isWifiApEnabled", Boolean.TRUE);
        n += answer(mod, "getWifiApMode", Integer.valueOf(AP_MODE_PROJECTION));
        n += block(mod, "setWifiApEnabledForConnectivity", true);
        n += block(mod, "setWifiApEnabled", true);
        n += block(mod, "setWifiApConfiguration", false);
        mod.log("Android Auto: AP hooks in " + app.processName + ": " + n + " of 5 (active while a dongle interface has the AP address)");
        if (Wireless.GPM.equals(app.packageName)) hostInterface(mod, app);
    }

    private static int answer(final ModContext mod, final String name, final Object result) {
        try {
            Hooks.findAndHookMethod(WifiManager.class, name, new MethodHook() {
                @Override
                protected void beforeHookedMethod(Param p) {
                    if (!on(mod)) return;
                    p.setResult(result);
                    if (!apLogged) {
                        apLogged = true;
                        mod.log("Android Auto: " + name + " answered for the dongle's AP on " + apInterface());
                    }
                }
            });
            return 1;
        } catch (Throwable t) {
            mod.error("Android Auto: no WifiManager." + name, t);
            return 0;
        }
    }

    /** setWifiApEnabled*(config, enable) and setWifiApConfiguration(config): not to the stock Wi-Fi service while the dongle's AP runs */
    private static int block(final ModContext mod, final String name, boolean withEnable) {
        try {
            MethodHook h = new MethodHook() {
                @Override
                protected void beforeHookedMethod(Param p) {
                    if (!on(mod)) return;
                    p.setResult(Boolean.TRUE);
                    if (blockLogs < 5) {
                        blockLogs++;
                        mod.log("Android Auto: " + name + (p.args.length > 1 ? "(" + p.args[1] + ")" : "") + " skipped, the dongle runs the AP");
                    }
                }
            };
            if (withEnable) Hooks.findAndHookMethod(WifiManager.class, name, "android.net.wifi.WifiConfiguration", boolean.class, h);
            else Hooks.findAndHookMethod(WifiManager.class, name, "android.net.wifi.WifiConfiguration", h);
            return 1;
        } catch (Throwable t) {
            mod.error("Android Auto: no WifiManager." + name, t);
            return 0;
        }
    }

    /** GPM: the dongle's AP interface is "wlan0" to dS()/dT() (host IP and BSSID), on their thread only */
    private static void hostInterface(final ModContext mod, LoadedApp app) {
        try {
            Class<?> c = Class.forName(GPM_WIFI, false, app.classLoader);
            MethodHook depth = new MethodHook() {
                @Override
                protected void beforeHookedMethod(Param p) {
                    IN_HOST.get()[0]++;
                }

                @Override
                protected void afterHookedMethod(Param p) {
                    int[] d = IN_HOST.get();
                    if (d[0] > 0) d[0]--;
                    if (p.hasThrowable() && on(mod)) mod.log("Android Auto: host interface lookup failed on " + apInterface() + ": " + p.getThrowable());
                }
            };
            Hooks.findAndHookMethod(c, "dS", depth);
            Hooks.findAndHookMethod(c, "dT", depth);
            MethodHook name = new MethodHook() {
                @Override
                protected void afterHookedMethod(Param p) {
                    if (IN_HOST.get()[0] == 0 || !mod.enabled()) return;
                    Object r = p.getResult();
                    if (r != null && r.equals(apInterface())) p.setResult(WLAN0);
                }
            };
            // K3 GPM compares getDisplayName() (vtable 11) with "wlan0", not getName(): both answer the same
            Hooks.findAndHookMethod(NetworkInterface.class, "getDisplayName", name);
            Hooks.findAndHookMethod(NetworkInterface.class, "getName", name);
            mod.log("Android Auto: host IP and BSSID from the dongle's AP interface in " + app.processName);
        } catch (Throwable t) {
            mod.error("Android Auto: GPM host interface hook (" + GPM_WIFI + " dS/dT)", t);
        }
    }
}
