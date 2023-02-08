package dev.kakaotalkbotserver;

import java.io.Serializable;

public class NotificationResponse implements Serializable {
    private String keyword;
    private String response;
    private boolean enabled = true;

    public NotificationResponse(String key, String resp) {
        this.keyword = key;
        this.response = resp;
    }

    public String getKeyword() {
        return keyword;
    }

    public String getResponse() {
        return response;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
