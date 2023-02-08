package dev.kakaotalkbotserver;

import java.io.Serializable;
import java.util.Calendar;

public class AutoAlertNotification implements Serializable {
    // Hour/Minute/Second
    private int[] timer;
    private String resp;
    private boolean enabled = false;
    public AutoAlertNotification(int[] timer,String alert){
        this.timer = timer;
        this.resp = alert;
    }

    public boolean isTime(Calendar c){
        return c.get(Calendar.HOUR_OF_DAY) == timer[0] && c.get(Calendar.MINUTE) == timer[1];
    }

    public String getMessage() {
        return resp;
    }

    public int[] getTimer() {
        return timer;
    }

    public void setEnabled(boolean b) {
        enabled = b;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
