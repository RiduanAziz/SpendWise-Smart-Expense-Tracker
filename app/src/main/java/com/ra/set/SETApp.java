package com.ra.set;

import android.app.Application;

import com.google.firebase.FirebaseApp;
import com.ra.set.utils.SessionTimeout;

public class SETApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Firebase
        FirebaseApp.initializeApp(this);

        // Initialize global session timeout (30 minutes)
        try {
            SessionTimeout.initialize(this, 30L * 60L * 1000L);
        } catch (Exception ignored) { }
    }
}
