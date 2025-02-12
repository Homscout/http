package com.getcapacitor.plugin.http;

import android.util.Log;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class UploadQueue {
    private static final UploadQueue INSTANCE = new UploadQueue();
    private final ThreadPoolExecutor executor;
    private static final int MAX_CONCURRENT_UPLOADS = 3;
    private static final int UPLOAD_TIMEOUT_SECONDS = 600; // 10 minutes

    private UploadQueue() {
        executor = new ThreadPoolExecutor(
                MAX_CONCURRENT_UPLOADS,  // Core pool size
                MAX_CONCURRENT_UPLOADS,  // Max pool size (same as core to maintain fixed size)
                UPLOAD_TIMEOUT_SECONDS,  // Keep alive time
                TimeUnit.SECONDS,       // Time unit
                new LinkedBlockingQueue<>()  // Queue for pending uploads
        );

        Log.d("UploadQueue", "📤 UploadQueue initialized with max " + MAX_CONCURRENT_UPLOADS + " concurrent uploads");
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
}

