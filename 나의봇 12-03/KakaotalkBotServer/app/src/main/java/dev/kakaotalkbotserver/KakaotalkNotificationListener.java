package dev.kakaotalkbotserver;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;

public class KakaotalkNotificationListener extends NotificationListenerService {
    Handler handler;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(getMainLooper());

//        Toast.makeText(getApplicationContext(), "Listener started", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onListenerConnected() {
//        Toast.makeText(getApplicationContext(), "Listener connected", Toast.LENGTH_SHORT).show();

    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }


    @Override
    public void onDestroy() {

    }

    @Override
    public void onNotificationPosted(final StatusBarNotification sbn) {

        if (sbn.getPackageName().equals("com.kakao.talk")) {
            try {
                Notification.WearableExtender wExt = new Notification.WearableExtender(sbn.getNotification());
                for (final Notification.Action act : wExt.getActions()) {

                    if (act.getRemoteInputs() != null && act.getRemoteInputs().length > 0) {
                        if (act.title.toString().toLowerCase().contains("reply") ||
                                act.title.toString().toLowerCase().contains("답장")) {
                            final Bundle data = sbn.getNotification().extras;
                            String room, sender = null, msg;
                            room = data.get("android.subText") == null ? null : data.get("android.subText").toString();
                            final boolean isGroupChat = room != null;


                            if (Build.VERSION.SDK_INT > 23) {
//                                room = data.getString("android.summaryText");
                                sender = data.get("android.title").toString();
                                msg = data.get("android.text").toString();
                            } else {
                                room = data.getString("android.title");
                                if (isGroupChat) {
//                                    String html = Html.toHtml(new SpannableString(data.get("android.text").toString()));
                                    msg = data.get("android.text").toString();
                                } else {
                                    sender = data.get("android.title") == null ? null : data.get("android.title").toString();
                                    room = sender;
                                    msg = data.get("android.text").toString();
                                }
                            }
                            chatHook(msg.trim(), room, isGroupChat, act);
                        }
                    }
                }
            } catch (final Exception ex) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Error :  " + ex.getClass().getName(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    }

    private void chatHook(final String msg, final String room, boolean isGroupChat, final Notification.Action act) {
        try {
//            handler.post(new Runnable() {
//                @Override
//                public void run() {
//                    Toast.makeText(getApplicationContext(),"Listener listened " + msg + " / room " + room,Toast.LENGTH_LONG).show();
//                }
//            });

            if (isGroupChat) {
                MainActivity.getRoomStorage().store(room, act);
            }
            final NotificationResponse resp = MainActivity.getResponse(msg);
            if (resp != null && MainActivity.isEnabled() && resp.isEnabled()) {

                reply(act, resp.getResponse());
            }
        } catch (final Exception ex) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Error :  " + ex.getClass().getName() + room, Toast.LENGTH_LONG).show();
                }
            });
        }
    }


    private void reply(Notification.Action session, String value) {
        Intent sendIntent = new Intent();
        Bundle msg = new Bundle();
        for (RemoteInput inputable : session.getRemoteInputs())
            msg.putCharSequence(inputable.getResultKey(), value);
        RemoteInput.addResultsToIntent(session.getRemoteInputs(), sendIntent, msg);

        try {
            session.actionIntent.send(this, 0, sendIntent);
        } catch (PendingIntent.CanceledException e) {

        }
    }
}
