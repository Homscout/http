package com.getcapacitor.plugin.http;

import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import java.io.File;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class UploadTask implements Runnable {
    private final PluginCall call;
    private final File file;
    private final JSObject data;
    private final String uploadId;
    private static final int DEFAULT_CONNECTION_TIMEOUT = 60 * 1000;  // 1 minute
    private static final int DEFAULT_READ_TIMEOUT = 300 * 1000;       // 5 minutes

    public UploadTask(PluginCall call, File file, JSObject data, String uploadId) {
        this.call = call;
        this.file = file;
        this.data = data;
        this.uploadId = uploadId;
    }

    @Override
    public void run() {
        Log.d("UploadQueue", "▶️ Starting upload " + uploadId);

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<JSObject> resultRef = new AtomicReference<>();
        final AtomicReference<Exception> errorRef = new AtomicReference<>();

        Thread uploadThread = new Thread(() -> {
            try {
                String urlString = call.getString("url");
                String method = Objects.requireNonNull(call.getString("method", "POST")).toUpperCase();
                String name = call.getString("name", "file");
                Integer connectTimeout = call.getInt("connectTimeout", DEFAULT_CONNECTION_TIMEOUT);
                Integer readTimeout = call.getInt("readTimeout", DEFAULT_READ_TIMEOUT);
                JSObject headers = call.getObject("headers");
                JSObject params = call.getObject("params");
                ResponseType responseType = ResponseType.parse(call.getString("responseType"));

                URL url = new URL(urlString);

                HttpURLConnectionBuilder connectionBuilder = new HttpURLConnectionBuilder()
                        .setUrl(url)
                        .setMethod(method)
                        .setHeaders(headers)
                        .setUrlParams(params)
                        .setConnectTimeout(connectTimeout)
                        .setReadTimeout(readTimeout)
                        .openConnection();

                CapacitorHttpUrlConnection connection = connectionBuilder.build();
                connection.setDoOutput(true);

                FormUploader builder = new FormUploader(connection.getHttpConnection());
                builder.addFilePart(name, file, data);
                builder.finish();

                JSObject response = HttpResponseBuilder.buildResponse(connection, responseType);
                Integer statusCode = response.getInteger("status");

                Log.d("UploadQueue", "✅ Upload " + uploadId + " completed with status: " + statusCode);
                resultRef.set(response);
            } catch (Exception e) {
                Log.e("UploadQueue", "❌ Upload " + uploadId + " failed: " + e.getMessage());
                errorRef.set(e);
            } finally {
                latch.countDown();
            }
        });

        uploadThread.start();


        try {
            // Get the upload timeout from the call or use default
            int timeoutMs = Objects.requireNonNull(call.getInt("readTimeout", DEFAULT_READ_TIMEOUT));
            boolean completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS);

            if (!completed) {
                Log.e("UploadQueue", "⚠️ Upload " + uploadId + " timed out after " + timeoutMs + " ms");
                uploadThread.interrupt();
                call.reject("Upload timed out", "TIMEOUT");
                return;
            }

            Exception error = errorRef.get();
            if (error != null) {
                call.reject("Error", "UPLOAD", error);
                return;
            }

            JSObject result = resultRef.get();
            if (result != null) {
                call.resolve(result);
            } else {
                call.reject("No response received", "UPLOAD");
            }

        } catch (InterruptedException e) {
            Log.e("UploadQueue", "❌ Upload " + uploadId + " was interrupted");
            call.reject("Upload interrupted", "INTERRUPTED");
        } finally {
            Log.d("UploadQueue", "🏁 Upload " + uploadId + " operation finished");
        }
    }
}
