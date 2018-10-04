package com.commontime.plugin.notification;

import android.app.Activity;

public class PushSingleton {
    private static final PushSingleton ourInstance = new PushSingleton();
    private Activity activity;

    public static PushSingleton getInstance() {
        return ourInstance;
    }

    private PushSingleton() {
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    public Activity getActivity() {
        return activity;
    }
}
