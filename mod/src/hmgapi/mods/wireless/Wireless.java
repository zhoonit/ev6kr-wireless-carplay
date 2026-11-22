package hmgapi.mods.wireless;

import android.content.ComponentName;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;

import hmgapi.hook.Car;
import hmgapi.hook.LoadedApp;
import hmgapi.hook.MethodHook;
import hmgapi.hook.ModContext;
import hmgapi.hook.ModEntry;
import hmgapi.hook.Points;

/**
 * The Wireless mod's hooks (the rest of the mod is file changes in patches.json).
 *
 * Phone Projection menu (setting "menu"): with the wireless CarPlay/AA features
 * on, HKMC_BtSettings opens the wireless projection page for Settings > Phone
 * Projection (no split screen, and only phones the stock wireless service paired).
 * Stock US/Canada opens Device Connections. On US/Canada units the hook on the
 * "open the wireless page" call does what the non-wireless branch does instead.
 * Through HMG-API's hook points (btsettings.projection_helper.*): HMG-API's map for
 * this car says what they are in this build.
 *
 * USB drives (setting "usb_drives"): the Android Auto app (HKMC_GPM) starts a wired
 * session for every USB_DEVICE_ATTACHED, so a USB drive (install stick, music) leaves
 * GalConnectionManager "Already AA Starting" with a wired start that never connects, and
 * wireless Android Auto is refused until the head unit restarts. The hook on
 * Context.startService in that app drops a wired start (startMode 1) for a device whose
 * interfaces are all mass storage (8) or hub (9). Phones and devices with no interfaces
 * start as before (hook point framework.context_impl.start_service).
 */
public final class Wireless extends ModEntry {
    static final String BTSETTINGS = "com.android.btsettings";
    static final String GPM = "com.google.android.projection.sink";
    static final String GAL_SERVICE = "com.google.android.projection.service.GalReceiverService";
    static final int USB_CLASS_MASS_STORAGE = 8, USB_CLASS_HUB = 9;
    static final String HELPER = "btsettings.projection_helper";

    static final String NAVIGATION = "com.mnsoft.navi";
    static final String DIPO = "com.daudio.app.dipo";
    static final String IAP2 = "com.tcc.iap2server";
    /** from DiPO's CarPlay Bluetooth bridge: start / stop an iAP2 session over Bluetooth */
    static final String BT_ATTACHED = "com.ev6.cpwl.BT_ATTACHED", BT_DETACHED = "com.ev6.cpwl.BT_DETACHED";
    static final String USB_CARPLAY = "android.hardware.usb.action.CARPLAY_DEVICE_ATTACHED";
    static final String USB_DIGITAL_AUDIO = "android.hardware.usb.action.DIGITAL_AUDIO_DEVICE_ATTACHED";
    /** the iAP2 component of the Bluetooth session we asked for (0: none) */
    static volatile int btComponent;
    /** the iAP2 server's route guidance flag was set (logged once per process) */
    static volatile boolean rgOffered;
    /**
     * NavigationCluster's turn-by-turn calls: the car navigation's own guidance, off while a phone guides.
     * Its lane frames (update_lane_info) are held back only while the phone's lanes are on the cluster
     * (AaTurns.ACTION_PHONE_LANES, mod 0.3.8): K3 KR's car navigation sends its own lanes at crossings
     * even without a route, and 0.3.4, which held them back always (as Korea Projection's
     * wireless-carplay-aa 2.3.0), left no lanes at all with a map app that sends none
     */
    static final String[] NAVI_TBT = { "update_tbt_info", "update_tbt_sub_info", "update_tbt_sub_info_b", "update_tbt_sub_info2",
        "update_tbt_sub_info2_b", "update_tbt_sub_info_2c", "update_remain_dis", "update_tp_crossroad_name",
        "update_tp_destination_name", "update_tp_turn_list", "update_tp_turn_list_new", "update_tp_lane_info" };
    /** the phone's lanes are on the cluster (AaTurns.ACTION_PHONE_LANES), in the car navigation's process */
    static volatile boolean phoneLanes;

    /**
     * Is a phone guiding, for the car navigation's cluster calls (mod 0.4.0, K3 KR): Car.phoneGuiding
     * makes a GPM manager and asks GPM over binder, then DiPO too, on every call, and the car
     * navigation makes several a second. The answer is kept GUIDING_MS, and with CarPlay off
     * (setting "bridge") only Android Auto is asked.
     */
    static final long GUIDING_MS = 500L;
    private static volatile long guidingAt;
    private static volatile boolean guidingNow;

    static boolean guiding(Context c, boolean carPlay) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (guidingAt == 0 || now - guidingAt >= GUIDING_MS) {
            guidingNow = carPlay ? Car.phoneGuiding(c) : Car.androidAutoGuiding(c);
            guidingAt = now;
        }
        return guidingNow;
    }

    /** EV6 KR: the cluster's song line and the voice button, in system_server (SystemHooks) */
    @Override
    public void zygoteInit(ModContext mod) {
        SystemHooks.zygote(mod);
    }

    @Override
    public void appLoaded(final ModContext mod, LoadedApp app) {
        if (NAVIGATION.equals(app.packageName)) navigation(mod, app);
        if (IAP2.equals(app.packageName)) iap2(mod, app);
        if (GPM.equals(app.packageName) && mod.settingBoolean("usb_drives", true)) usbDrives(mod, app);
        if (GPM.equals(app.packageName) && mod.settingBoolean("tbt", true)) gpmTurnByTurn(mod, app);
        // K3 KR: wireless Android Auto on the USB dongle's AP (AaWifi; does nothing while no dongle interface has the AP address)
        if ((GPM.equals(app.packageName) || AaWifi.PHONEPROJ.equals(app.packageName)) && mod.settingBoolean("aa_wifi", true)) AaWifi.hook(mod, app);
        // K3 KR: the ★ key's "Android Auto 알림" (AaKeys)
        if (GPM.equals(app.packageName) && mod.settingBoolean("aa_keys", true)) AaKeys.hook(mod, app);
        // EV6 KR: no Phone Projection menu hook (US/Canada only in wireless-carplay-aa, and HMG-API's
        // on-car mapper misses btsettings.projection_helper on the Korean BtSettings: one unmapped
        // point would turn all of this mod's hooks off)
        if (DIPO.equals(app.packageName) && (mod.settingBoolean("tbt", true) || mod.settingBoolean("bridge", true) || mod.settingBoolean("audio", true))) dipo(mod, app);
    }

    /**
     * DiPO (CarPlay): turn-by-turn, the Bluetooth bridge and Wi-Fi audio, in the mod's DiPO part
     * (hmgapi.mods.wireless.dipo.Dipo), which links against DiPO's iAP2 and Bluetooth
     * libraries.
     */
    private void dipo(ModContext mod, LoadedApp app) {
        try {
            mod.appPart(app, "hmgapi.mods.wireless.dipo.Dipo").getMethod("start", ModContext.class, LoadedApp.class).invoke(null, mod, app);
        } catch (Throwable t) {
            mod.error("DiPO part", t instanceof java.lang.reflect.InvocationTargetException ? t.getCause() : t);
        }
    }

    /**
     * In the car navigation app, while a phone guides (Android Auto or CarPlay): its own
     * turn-by-turn calls to the cluster and HUD are skipped, so they don't fight the
     * phone's. Its speed limit, cameras, traps, warning distances, lanes and driver-assist
     * data still go through (phones carry none of that). Framework calls only: no app names.
     */
    private void navigation(final ModContext mod, final LoadedApp app) {
        if (!mod.settingBoolean("tbt", true)) return;
        final boolean carPlay = mod.settingBoolean("bridge", true);
        MethodHook skip = new MethodHook() {
            @Override
            protected void beforeHookedMethod(Param p) {
                if (guiding(app.context, carPlay)) p.setResult(null);
            }
        };
        for (String m : NAVI_TBT) mod.hook(null, "framework.navigation_cluster." + m, skip);
        mod.hook(null, "framework.navigation_cluster.update_lane_info", new MethodHook() {
            @Override
            protected void beforeHookedMethod(Param p) {
                if (phoneLanes && guiding(app.context, carPlay)) p.setResult(null);
            }
        });
        try {
            app.context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent i) {
                    boolean on = i.getBooleanExtra("on", false);
                    if (on != phoneLanes) mod.log("navigation: the phone's lanes " + (on ? "on the cluster, the car's held back" : "gone, the car's lanes again"));
                    phoneLanes = on;
                }
            }, new IntentFilter(AaTurns.ACTION_PHONE_LANES));
        } catch (Throwable t) {
            mod.error("navigation: phone lanes receiver", t);
        }
        mod.log("navigation: turn-by-turn yields to the phone in " + app.processName);
    }

    /**
     * The iAP2 server. Turn-by-turn ("tbt"): it offers RouteGuidance to the iPhone only
     * when the car's stepofspec is 999 or more (US/Canada: 105), so HMG-API sets that one
     * flag after it decides. Bluetooth bridge ("bridge"): DiPO's bridge asks for an iAP2
     * session over Bluetooth (BT_ATTACHED / BT_DETACHED, from apps holding
     * BLUETOOTH_PRIVILEGED); a USB CarPlay attach ends it first, before the server's own
     * receiver handles that attach.
     */
    private void iap2(final ModContext mod, LoadedApp app) {
        final ClassLoader cl = app.classLoader;
        // K3 KR (mod 0.4.0): CarPlay's route guidance offer only with CarPlay on
        if (mod.settingBoolean("tbt", true) && mod.settingBoolean("bridge", true)) {
            mod.hook(cl, "iap2.init_info.check_supports_features", new MethodHook() {
                @Override
                protected void afterHookedMethod(Param p) {
                    try {
                        Points.field(cl, "iap2.init_info.route_guidance").setInt(null, 1);
                        if (!rgOffered) {
                            rgOffered = true;
                            mod.log("iAP2: route guidance offered to the phone (stepofspec " + Car.stepOfSpec() + ")");
                        }
                    } catch (Exception e) {
                        mod.error("route guidance flag", e);
                    }
                }
            });
        }
        if (!mod.settingBoolean("bridge", true)) return;
        // EV6 KR: what the iAP2 server hands the phone for wireless CarPlay (the passphrase only as
        // its length and whether it matches softap.conf): the phone kept failing the hotspot's WPA
        // handshake with it while the same passphrase typed by hand worked (fourth car test)
        try {
            hmgapi.hook.Hooks.findAndHookMethod("com.tcc.iap2server.Iap2Service", cl, "doSendAccessoryConfigurationInformation",
                    String.class, boolean.class, String.class, boolean.class, int.class, boolean.class, int.class, new MethodHook() {
                @Override
                protected void beforeHookedMethod(Param p) {
                    String psk = (String) p.args[2];
                    String file = softApPsk();
                    mod.log("iAP2 Wi-Fi info for the phone: SSID " + p.args[0] + ", passphrase " + (Boolean.TRUE.equals(p.args[1]) ? "" : "(flag off) ")
                            + describePsk(psk) + (file == null ? "" : file.equals(psk) ? ", same as softap.conf" : ", DIFFERENT from softap.conf (" + describePsk(file) + ")")
                            + ", security " + (Boolean.TRUE.equals(p.args[3]) ? String.valueOf(p.args[4]) : "(flag off)")
                            + (Boolean.TRUE.equals(p.args[5]) ? ", channel " + p.args[6] : ", no channel"));
                }
            });
        } catch (Throwable t) {
            mod.error("iAP2 Wi-Fi info hook", t);
        }
        mod.hook(cl, "iap2.service.on_create", new MethodHook() {
            @Override
            protected void afterHookedMethod(Param p) {
                final Object svc = p.thisObject;
                IntentFilter f = new IntentFilter();
                f.addAction(BT_ATTACHED);
                f.addAction(BT_DETACHED);
                ((Context) svc).registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context c, Intent i) {
                        try {
                            if (BT_ATTACHED.equals(i.getAction())) btAttach(mod, cl, svc, i);
                            else stopBt(mod, cl, svc, "detach");
                        } catch (Throwable t) {
                            mod.error("bridge " + i.getAction(), t);
                        }
                    }
                }, f, "android.permission.BLUETOOTH_PRIVILEGED", null);
                mod.log("bridge: iAP2 listens for DiPO's Bluetooth sessions");
            }
        });
        mod.hook(cl, "iap2.service_receiver.on_receive", new MethodHook() {
            @Override
            protected void beforeHookedMethod(Param p) {
                Intent i = (Intent) p.args[1];
                String a = i == null ? null : i.getAction();
                if (!USB_CARPLAY.equals(a) && !USB_DIGITAL_AUDIO.equals(a)) return;
                try {
                    stopBt(mod, cl, Points.get(cl, "iap2.service_receiver.service", p.thisObject), "usb attach");
                } catch (Throwable t) {
                    mod.error("bridge: usb attach", t);
                }
            }
        });
    }

    static void btAttach(ModContext mod, ClassLoader cl, Object svc, Intent i) {
        int comp = i.getIntExtra("component", 4);
        if (comp < 3 || comp > 5) comp = 4;
        String serial = i.getStringExtra("serial");
        if (btComponent == 0 && Boolean.TRUE.equals(Points.call(cl, "iap2.service.is_connected", null))) {
            mod.log("bridge: attach ignored, another iAP2 session is up (component "
                    + Points.call(cl, "iap2.service.get_component", null) + ")");
            return;
        }
        stopBt(mod, cl, svc, "re-attach");
        btComponent = comp;
        Points.call(cl, "iap2.service.set_device_serial", svc, serial);
        Points.call(cl, "iap2.service.receive_connect", svc, comp);
        mod.log("bridge: Bluetooth iAP2 session requested, component " + comp + " serial " + serial);
    }

    static void stopBt(ModContext mod, ClassLoader cl, Object svc, String why) {
        int c = btComponent;
        if (c == 0) return;
        btComponent = 0;
        Points.call(cl, "iap2.service.set_device_serial", svc, (Object) null);
        Points.call(cl, "iap2.service.receive_disconnect", svc, c);
        mod.log("bridge: Bluetooth iAP2 session stopped, component " + c + " (" + why + ")");
    }

    /**
     * Android Auto turn-by-turn (GPM). GPM's own turn-by-turn manager only exists when
     * the car's stepofspec is 999 or more (US/Canada: 105), and it claims the cluster in a
     * way these cars don't give back. HMG-API leaves GPM's manager off and runs its own
     * (AaTurns) from the AA library's callbacks, sending through HMG-API's Cluster API.
     * GPM tells the phone its cluster takes pictures (IMAGE); HMG-API says ENUM, so the
     * phone sends turn data the cluster can show. One AaTurns per connection (GalReceiver),
     * all on one thread.
     */
    private void gpmTurnByTurn(final ModContext mod, final LoadedApp app) {
        final ClassLoader cl = app.classLoader;
        if (Car.stepOfSpec() >= 999) {
            mod.log("Android Auto: stepofspec is 999 or more, GPM's own turn-by-turn runs (HMG-API's stays off)");
            return;
        }
        // the AA library: protocol 1.6 and the turn-by-turn extras packed into its callbacks, in memory (file stays stock).
        // K3 KR's libautoreceiver_infobank.so (48ee793a) fits aa-tbt-48ee793a; EV6 KR's doesn't (logged, the hooks below find nothing)
        mod.nativePatch(app, "libautoreceiver_infobank.so");
        mod.hook(cl, "gpm.navigation_status.init", new MethodHook() {
            @Override
            protected void beforeHookedMethod(Param p) {
                if (p.args[3] == Points.get(cl, "gpm.cluster_type.image", null)) p.args[3] = Points.get(cl, "gpm.cluster_type.enum", null);
            }
        });
        final java.util.Map<Object, AaTurns> turns = new java.util.WeakHashMap<Object, AaTurns>();
        final android.os.HandlerThread thread = new android.os.HandlerThread("hmgapi-aa-tbt");
        thread.start();
        final android.os.Handler handler = new android.os.Handler(thread.getLooper());
        abstract class OnTurns extends MethodHook {
            abstract void run(AaTurns t, Object[] a);

            /** receiver: the GalReceiver; create: make its AaTurns if there's none yet */
            void post(final Object receiver, final boolean create, final Object[] args) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AaTurns t;
                            synchronized (turns) {
                                t = turns.get(receiver);
                                if (t == null && create) {
                                    hmgapi.hook.Cluster c = hmgapi.hook.Cluster.of(app.context);
                                    if (c == null) return;
                                    turns.put(receiver, t = new AaTurns(app.context, c, handler));
                                    mod.log("Android Auto: turn-by-turn to the cluster for this connection");
                                }
                            }
                            if (t != null) OnTurns.this.run(t, args);
                        } catch (Throwable x) {
                            mod.error("Android Auto turn-by-turn", x);
                        }
                    }
                });
            }

            @Override
            protected void afterHookedMethod(Param p) {
                post(Points.get(cl, "gpm.navigation_status.receiver", p.thisObject), true, p.args.clone());
            }
        }
        mod.hook(cl, "gpm.navigation_status.next_turn", new OnTurns() {
            @Override
            void run(AaTurns t, Object[] a) {
                t.nextTurn((String) a[0], (Integer) a[1], (Integer) a[2], (Integer) a[4], (Integer) a[5]);
            }
        });
        mod.hook(cl, "gpm.navigation_status.next_turn_distance", new OnTurns() {
            @Override
            void run(AaTurns t, Object[] a) {
                t.nextTurnDistance((Integer) a[0], (Integer) a[1], (Integer) a[2], (Integer) a[3]);
            }
        });
        mod.hook(cl, "gpm.navigation_status.status", new OnTurns() {
            @Override
            void run(AaTurns t, Object[] a) {
                t.status((Integer) a[0]);
            }
        });
        mod.hook(cl, "gpm.receiver.send_navigation_focus", new OnTurns() {
            @Override
            void run(AaTurns t, Object[] a) {
                if (a[0].equals(Points.get(cl, "gpm.receiver.focus_native", null))) t.stop();
            }

            @Override
            protected void afterHookedMethod(Param p) {
                post(p.thisObject, false, p.args.clone());
            }
        });
        mod.hook(cl, "gpm.receiver.destroy", new OnTurns() {
            @Override
            void run(AaTurns t, Object[] a) {
                t.end();
                synchronized (turns) {
                    turns.values().remove(t);
                }
            }

            @Override
            protected void beforeHookedMethod(Param p) {
                post(p.thisObject, false, new Object[0]);
            }

            @Override
            protected void afterHookedMethod(Param p) {}
        });
    }

    private void usbDrives(final ModContext mod, LoadedApp app) {
        mod.hook(null, "framework.context_impl.start_service", new MethodHook() {
            @Override
            protected void beforeHookedMethod(Param p) {
                Intent i = (Intent) p.args[0];
                ComponentName c = i == null ? null : i.getComponent();
                if (c == null || !GAL_SERVICE.equals(c.getClassName()) || i.getIntExtra("startMode", -1) != 1) return;
                UsbDevice d = i.getParcelableExtra("device");
                if (!notPhone(d)) return;
                p.setResult(null);
                mod.log((wifiDongle(d) ? "USB Wi-Fi dongle (" : "USB drive or hub (") + d.getDeviceName() + "), not starting wired Android Auto");
            }
        });
        mod.log("USB drive hook in " + app.processName);
    }

    /** "12 chars, 2 non-alphanumeric" (never the passphrase itself) */
    static String describePsk(String p) {
        if (p == null) return "null";
        int n = 0;
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) n++;
        }
        return p.length() + " chars, " + n + " non-alphanumeric";
    }

    /** ConnectivityPSK from /data/misc/wifi/softap.conf (WifiApConfigStore format 3), or null */
    static String softApPsk() {
        java.io.DataInputStream in = null;
        try {
            in = new java.io.DataInputStream(new java.io.BufferedInputStream(new java.io.FileInputStream("/data/misc/wifi/softap.conf")));
            if (in.readInt() != 3) return null;
            in.readUTF();   // SSID
            in.readUTF();   // ConnectivitySSID
            in.readInt();   // auth type
            in.readUTF();   // preSharedKey
            return in.readUTF();   // ConnectivityPSK
        } catch (Throwable t) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                    // nothing to do
                }
            }
        }
    }

    /**
     * USB Wi-Fi dongles the hotspot drivers take (88x2bu, 8851bu; usbwifi.hotspot's list): GPM would
     * try wired Android Auto on them too, and a wired start that never connects blocks wireless
     */
    static final String[] WIFI_DONGLES = { "0bda:b812", "0bda:b82c", "0bda:b81a", "0bda:b820", "0bda:b82b",
        "2357:012d", "2357:0138", "2357:0115", "0b05:184c", "0b05:1841", "0b05:19aa", "7392:b822", "7392:c822",
        "2001:331c", "2001:331e", "0846:9055", "13b1:0043", "056e:4011",
        "0bda:b831", "0bda:b851", "0bda:b832", "0bda:1a2b", "3625:010b", "2357:0142", "2c4e:0128", "2001:332a",
        "7392:e611", "056e:4027", "056e:4028", "0411:03ed" };

    static boolean wifiDongle(UsbDevice d) {
        String id = String.format("%04x:%04x", d.getVendorId(), d.getProductId());
        for (String w : WIFI_DONGLES) if (w.equals(id)) return true;
        return false;
    }

    /** true when the device is a known USB Wi-Fi dongle, or has interfaces and every one is mass storage or a hub */
    static boolean notPhone(UsbDevice d) {
        if (d == null) return false;
        if (wifiDongle(d)) return true;
        if (d.getInterfaceCount() == 0) return false;
        for (int k = 0; k < d.getInterfaceCount(); k++) {
            int cls = d.getInterface(k).getInterfaceClass();
            if (cls != USB_CLASS_MASS_STORAGE && cls != USB_CLASS_HUB) return false;
        }
        return true;
    }

    private void phoneProjectionMenu(final ModContext mod, LoadedApp app) {
        final ClassLoader cl = app.classLoader;
        mod.hook(cl, HELPER + ".open_wireless_page", new MethodHook() {
            @Override
            protected void beforeHookedMethod(Param p) {
                int region = Car.region();
                if (region != Car.REGION_US && region != Car.REGION_CANADA) return;   // other regions: stock choice
                Context c = (Context) p.args[0];
                if (Boolean.TRUE.equals(Points.call(cl, HELPER + ".device_connections_available", null))) {
                    Points.call(cl, HELPER + ".open_device_connections", null, c, 1);
                } else {
                    Points.call(cl, HELPER + ".open_other_page", null, c);
                }
                p.setResult(null);
                mod.log("Phone Projection: Device Connections (US/Canada), not the wireless page");
            }
        });
        mod.log("Phone Projection menu hook in " + app.processName);
    }
}
