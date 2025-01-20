package com.getcapacitor.plugin.http;

import android.content.Context;
import android.util.Log;

import com.getcapacitor.JSObject;

import java.io.File;

public class UploadTask implements Runnable {
    private final CapacitorHttpUrlConnection connection;
    private final File file;
    private final JSObject data;
    private final JSObject resizeOptions;
    private final String uploadId;
    private final String paramName;
    private final String widthHeader;
    private final String heightHeader;
    private final String sizeHeader;
    private final ResponseType responseType;
    private final UploadTaskCallback callback;
    private static final int DEFAULT_CONNECTION_TIMEOUT = 60 * 1000;  // 1 minute
    private static final int DEFAULT_READ_TIMEOUT = 300 * 1000;       // 5 minutes
    private final Context context;  // Need context for image operations

    public UploadTask(
            CapacitorHttpUrlConnection connection,
            File file,
            JSObject data,
            JSObject resizeOptions,
            String uploadId,
            String paramName,
            String widthHeader,
            String heightHeader,
            String sizeHeader,
            ResponseType responseType,
            Context context,
            UploadTaskCallback callback
    ) {
        this.connection = connection;
        this.file = file;
        this.data = data;
        this.resizeOptions = resizeOptions;
        this.uploadId = uploadId;
        this.paramName = paramName;
        this.widthHeader = widthHeader;
        this.heightHeader = heightHeader;
        this.sizeHeader = sizeHeader;
        this.responseType = responseType;
        this.callback = callback;
        this.context = context;
    }

    @Override
    public void run() {
        Log.d("UploadQueue", "▶️ Starting upload " + uploadId);

        try {
            ImageUtils.ImageResult result = resizeOptions != null
                ? ImageUtils.resizeImage(context, file, resizeOptions, uploadId)
                : ImageUtils.getImageResult(file);

            data.put(widthHeader, String.valueOf(result.width));
            data.put(heightHeader, String.valueOf(result.height));
            data.put(sizeHeader, String.valueOf(result.fileSize));

            FormUploader builder = new FormUploader(connection.getHttpConnection());
            builder.addFilePart(paramName, result.file, data);
            builder.finish();

            JSObject response = HttpResponseBuilder.buildResponse(connection, responseType);
            Integer statusCode = response.getInteger("status");

            Log.d("UploadQueue", "✅ Upload " + uploadId + " completed with status: " + statusCode);
            callback.onSuccess(response);
        } catch (Exception e) {
            Log.e("UploadQueue", "❌ Upload " + uploadId + " failed: " + e.getMessage());
            callback.onError(e.getMessage(), "UPLOAD", e);
        }
    }
}
