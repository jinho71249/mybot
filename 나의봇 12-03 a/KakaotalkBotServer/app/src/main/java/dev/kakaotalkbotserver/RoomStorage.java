package dev.kakaotalkbotserver;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.util.HashMap;
import java.util.Map;

public class RoomStorage {
    private HashMap<String, Notification.Action> act = new HashMap<>();
    private Context appContext;

    public RoomStorage(Context c) {
        this.appContext = c;
    }

    public void store(String room, Notification.Action action) {
        if (!act.containsKey(room))
            act.put(room, action);
    }

    public void alert(String room, String msg) {
        if (act.containsKey(room))
            reply(act.get(room), msg);
    }

    public void alert(String msg) {
        for (Map.Entry<String, Notification.Action> entry : act.entrySet())
            reply(entry.getValue(), msg);
    }

    private void reply(Notification.Action session, String value) {
        Intent sendIntent = new Intent();
        Bundle msg = new Bundle();
        for (RemoteInput inputable : session.getRemoteInputs())
            msg.putCharSequence(inputable.getResultKey(), value);
        RemoteInput.addResultsToIntent(session.getRemoteInputs(), sendIntent, msg);

        try {
            session.actionIntent.send(appContext, 0, sendIntent);
        } catch (PendingIntent.CanceledException e) {

        }
    }
}
