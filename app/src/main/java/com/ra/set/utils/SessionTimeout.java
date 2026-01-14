package com.ra.set.utils;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.ra.set.activities.LoginActivity;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

public class SessionTimeout {

    private static final long DEFAULT_TIMEOUT_MS = 30L * 60L * 1000L; // 30 minutes
    private static long timeoutMs = DEFAULT_TIMEOUT_MS;

    private static Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable timeoutRunnable;
    private static WeakReference<Context> appCtxRef;

    // store per-activity touch listeners so we can remove them
    private static final Map<Activity, View.OnTouchListener> listeners = new HashMap<>();

    public static void initialize(@NonNull Application app, long timeoutMilliseconds) {
        appCtxRef = new WeakReference<>(app.getApplicationContext());
        timeoutMs = timeoutMilliseconds > 0 ? timeoutMilliseconds : DEFAULT_TIMEOUT_MS;

        timeoutRunnable = () -> {
            Context ctx = appCtxRef.get();
            if (ctx == null) return;
            // Perform logout on main thread
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() != null) {
                auth.signOut();
            }
            Intent i = new Intent(ctx, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            ctx.startActivity(i);
        };

        // register lifecycle callbacks to observe activities and attach touch listeners
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, android.os.Bundle savedInstanceState) { }

            @Override
            public void onActivityStarted(@NonNull Activity activity) { }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                // attach a touch listener on the decorView to reset the timer on user interaction
                View decor = activity.getWindow().getDecorView();
                View.OnTouchListener l = (v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_UP) {
                        reset();
                    }
                    return false; // do not consume
                };
                // remove any previous listener we added
                try {
                    if (listeners.containsKey(activity)) {
                        decor.setOnTouchListener(null);
                        listeners.remove(activity);
                    }
                } catch (Exception ignored) { }

                try {
                    decor.setOnTouchListener(l);
                    listeners.put(activity, l);
                } catch (Exception ignored) { }

                // also reset timer when activity comes to foreground
                reset();
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                // remove listener to avoid leaks
                try {
                    View decor = activity.getWindow().getDecorView();
                    if (listeners.containsKey(activity)) {
                        decor.setOnTouchListener(null);
                        listeners.remove(activity);
                    }
                } catch (Exception ignored) { }
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) { }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull android.os.Bundle outState) { }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                try {
                    if (listeners.containsKey(activity)) listeners.remove(activity);
                } catch (Exception ignored) { }
            }
        });

        // start the timer initially
        reset();
    }

    public static void reset() {
        handler.removeCallbacks(timeoutRunnable);
        handler.postDelayed(timeoutRunnable, timeoutMs);
    }

    public static void stop() {
        handler.removeCallbacks(timeoutRunnable);
    }

    public static void setTimeoutMs(long ms) {
        timeoutMs = ms > 0 ? ms : DEFAULT_TIMEOUT_MS;
        reset();
    }
}

