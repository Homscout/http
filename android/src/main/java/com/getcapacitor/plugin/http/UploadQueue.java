package com.getcapacitor.plugin.http;

import android.util.Log;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class UploadQueue {
    private static final UploadQueue INSTANCE = new UploadQueue();
    private final ThreadPoolExecutor executor;

    private UploadQueue() {
        int optimalConcurrency = calculateOptimalConcurrency();

        executor = new ThreadPoolExecutor(
                optimalConcurrency,  // Core pool size
                optimalConcurrency,  // Max pool size (same as core to maintain fixed size)
                600,  // Keep alive time (10 mins)
                TimeUnit.SECONDS,       // Time unit
                new LinkedBlockingQueue<>()  // Queue for pending uploads
        );

        Log.d("UploadQueue", "📤 UploadQueue initialized with max " + optimalConcurrency + " concurrent uploads");
    }

    public static UploadQueue getInstance() {
        return INSTANCE;
    }

    public void addUpload(Runnable uploadTask) {
        Log.d("UploadQueue", "📥 Queueing new upload. Current queue size: " + executor.getQueue().size());
        executor.execute(uploadTask);
        Log.d("UploadQueue", "📊 Queue status: " +
                (executor.getQueue().size() + executor.getActiveCount()) + " total, " +
                executor.getActiveCount() + " executing");
    }

    private int calculateOptimalConcurrency() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024); // in MB

        // This is a bit arbitrary but photo resizing takes a lot of memory,
        // especially in Android. This helps prevent OOM issues on resource-
        // constrained devices
        if (maxMemory <= 256) {
            return 1;
        } else if (maxMemory <= 512) {
            return 2;
        } else {
            return 3;
        }
    }
}

