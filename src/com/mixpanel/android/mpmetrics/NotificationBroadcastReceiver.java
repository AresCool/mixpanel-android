package com.mixpanel.android.mpmetrics;

import java.util.HashMap;
import java.util.Map;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

class NotificationBroadcastReceiver extends BroadcastReceiver {

    private static String LOGTAG = "NotificationBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if ("com.google.android.c2dm.intent.REGISTRATION".equals(action)) {
            handleRegistrationIntent(intent);
        } else if ("com.google.android.c2dm.intent.RECEIVE".equals(action)) {
            handleNotificationIntent(context, intent);
        }
    }

    /* package */ static synchronized void registerIfNeeded(Context registrationContext) {
        if (sAlreadyRegistered.containsKey(registrationContext)) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "NotificationBroadcastReceiver already registered");
            return;
        }

        NotificationBroadcastReceiver registrar = new NotificationBroadcastReceiver();
        IntentFilter registrations = new IntentFilter();
        registrations.addAction("com.google.android.c2dm.intent.RECEIVE");
        registrations.addAction("com.google.android.c2dm.intent.REGISTRATION");
        registrations.addCategory(registrationContext.getPackageName());

        registrationContext.registerReceiver(registrar, registrations, "com.google.android.c2dm.permission.SEND", null);
        sAlreadyRegistered.put(registrationContext, true);

        if (MPConfig.DEBUG) Log.d(LOGTAG, "Registered to recieve notifications for package name " + registrationContext.getPackageName());
    }


    private void handleRegistrationIntent(Intent intent) {
        String registration = intent.getStringExtra("registration_id");

        if (intent.getStringExtra("error") != null) {
            Log.e(LOGTAG, "Error when registering for GCM: " + intent.getStringExtra("error"));
        } else if (registration != null) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "registering GCM ID: " + registration);

            Map<String, MixpanelAPI> allMetrics = MixpanelAPI.allInstances();
            for (String token : allMetrics.keySet()) {
                allMetrics.get(token).getPeople().setPushRegistrationId(registration);
            }
        } else if (intent.getStringExtra("unregistered") != null) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "unregistering from GCM");

            Map<String, MixpanelAPI> allMetrics = MixpanelAPI.allInstances();
            for (String token : allMetrics.keySet()) {
                allMetrics.get(token).getPeople().clearPushRegistrationId();
            }
        }
    }

    private void handleNotificationIntent(Context context, Intent intent) {
        String message = intent.getExtras().getString("mp_message");

        if (message == null) return;
        if (MPConfig.DEBUG) Log.d(LOGTAG, "MP GCM notification received: " + message);

        PackageManager manager = context.getPackageManager();
        Intent appIntent = manager.getLaunchIntentForPackage(context.getPackageName());
        CharSequence notificationTitle = "";
        int notificationIcon = android.R.drawable.sym_def_app_icon;
        try {
            ApplicationInfo appInfo = manager.getApplicationInfo(context.getPackageName(), 0);
            notificationTitle = manager.getApplicationLabel(appInfo);
            notificationIcon = appInfo.icon;
        } catch (NameNotFoundException e) {
            // In this case, use a blank title and default icon
        }

        PendingIntent contentIntent = PendingIntent.getActivity(
            context.getApplicationContext(),
            0,
            appIntent,   // add this pass null to intent
            PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification n = new Notification(notificationIcon, message, System.currentTimeMillis());
        n.flags |= Notification.FLAG_AUTO_CANCEL;
        n.setLatestEventInfo(context, notificationTitle, message, contentIntent);
        nm.notify(0, n);
    }

    private static Map<Context, Boolean> sAlreadyRegistered = new HashMap<Context, Boolean>();
}
