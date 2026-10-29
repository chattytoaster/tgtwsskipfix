package com.chatty.tgtwsfix;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.session.MediaSession;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class TgMediaFix implements IXposedHookLoadPackage {
    private static final String TAG = "TgMediaFix";
    private static final String KEY_TAG = "tg_media_tag";

    public static class SuppressLegacy extends XC_MethodReplacement {
        private final String name;
        public SuppressLegacy(String name) { this.name = name; }

        @Override
        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
            XposedBridge.log(TAG + ": Suppressed legacy call: " + name);
            return null;
        }
    }

    public static class SuppressGetHelper extends XC_MethodReplacement {
        @Override
        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
            XposedBridge.log(TAG + ": MediaSessionLegacyHelper.getHelper intercepted, returning null");
            return null;
        }
    }

    public static class SetActiveHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            MediaSession session = (MediaSession) param.thisObject;
            boolean targetActive = (Boolean) param.args[0];
            String tag = (String) XposedHelpers.getAdditionalInstanceField(session, KEY_TAG);

            if (targetActive) {
                if (tag != null && tag.startsWith("MediaSessionHelper")) {
                    XposedBridge.log(TAG + ": FORCED INACTIVE on shadow session: " + tag);
                    param.args[0] = false;
                } else if (tag != null && tag.contains("telegramAudioPlayer")) {
                    XposedBridge.log(TAG + ": Real player session activated: " + tag);
                    try {
                        session.setFlags(3);
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    public static class ConstructorHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            MediaSession session = (MediaSession) param.thisObject;
            Context context = (Context) param.args[0];
            String tag = (String) param.args[1];

            // Store tag in session instance
            XposedHelpers.setAdditionalInstanceField(session, KEY_TAG, tag);
            XposedBridge.log(TAG + ": MediaSession created with tag: " + tag);

            if (tag != null && tag.startsWith("MediaSessionHelper")) {
                XposedBridge.log(TAG + ": Identified MediaSessionHelper, marking inactive");
                try {
                    session.setActive(false);
                } catch (Throwable ignored) {}
            } else if (tag != null && tag.contains("telegramAudioPlayer")) {
                XposedBridge.log(TAG + ": Configuring telegramAudioPlayer");
                try {
                    session.setFlags(3);
                    Intent mbrIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                    mbrIntent.setComponent(new ComponentName(context, "org.telegram.messenger.MusicPlayerReceiver"));
                    PendingIntent pi = PendingIntent.getBroadcast(
                        context,
                        0,
                        mbrIntent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                    );
                    session.setMediaButtonReceiver(pi);
                    XposedBridge.log(TAG + ": Successfully wired MusicPlayerReceiver to telegramAudioPlayer");
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": Failed wiring MusicPlayerReceiver: " + t);
                }
            }
        }
    }

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (lpparam == null || lpparam.packageName == null) return;

        String pkg = lpparam.packageName.toLowerCase();
        if (!pkg.contains("telegram") && !pkg.contains("challegram") && !pkg.contains("nekox")) {
            return;
        }

        XposedBridge.log(TAG + ": Initializing hooks for " + lpparam.packageName);

        // 1. Block legacy AudioManager methods (registerMediaButtonEventReceiver & registerRemoteControlClient)
        try {
            XposedHelpers.findAndHookMethod(
                AudioManager.class,
                "registerMediaButtonEventReceiver",
                ComponentName.class,
                new SuppressLegacy("registerMediaButtonEventReceiver(ComponentName)")
            );
        } catch (Throwable ignored) {}

        try {
            XposedHelpers.findAndHookMethod(
                AudioManager.class,
                "registerMediaButtonEventReceiver",
                PendingIntent.class,
                new SuppressLegacy("registerMediaButtonEventReceiver(PendingIntent)")
            );
        } catch (Throwable ignored) {}

        try {
            Class<?> rccClass = XposedHelpers.findClass("android.media.RemoteControlClient", lpparam.classLoader);
            if (rccClass != null) {
                XposedHelpers.findAndHookMethod(
                    AudioManager.class,
                    "registerRemoteControlClient",
                    rccClass,
                    new SuppressLegacy("registerRemoteControlClient")
                );
                XposedHelpers.findAndHookMethod(
                    AudioManager.class,
                    "unregisterRemoteControlClient",
                    rccClass,
                    new SuppressLegacy("unregisterRemoteControlClient")
                );
            }
        } catch (Throwable ignored) {}

        // 2. Block MediaSessionLegacyHelper entirely if accessed
        try {
            Class<?> legacyHelper = XposedHelpers.findClass("android.media.session.MediaSessionLegacyHelper", lpparam.classLoader);
            if (legacyHelper != null) {
                XposedHelpers.findAndHookMethod(
                    legacyHelper,
                    "getHelper",
                    Context.class,
                    new SuppressGetHelper()
                );
            }
        } catch (Throwable ignored) {}

        // 3. Track tags and control active status on MediaSession
        try {
            XposedHelpers.findAndHookConstructor(
                MediaSession.class,
                Context.class,
                String.class,
                new ConstructorHook()
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook error MediaSession constructor: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                MediaSession.class,
                "setActive",
                boolean.class,
                new SetActiveHook()
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook error MediaSession.setActive: " + t);
        }
    }
}
