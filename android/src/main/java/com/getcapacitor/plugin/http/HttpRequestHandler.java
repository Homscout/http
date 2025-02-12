package com.getcapacitor.plugin.http;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;
import org.json.JSONException;

public class HttpRequestHandler {
    /**
     * Makes an Http Request based on the PluginCall parameters
     * @param call The Capacitor PluginCall that contains the options need for an Http request
     * @param httpMethod The HTTP method that overrides the PluginCall HTTP method
     * @throws IOException throws an IO request when a connection can't be made
     * @throws URISyntaxException thrown when the URI is malformed
     * @throws JSONException thrown when the incoming JSON is malformed
     */
    public static JSObject request(PluginCall call, String httpMethod) throws IOException, URISyntaxException, JSONException {
        String urlString = call.getString("url", "");
        JSObject headers = call.getObject("headers");
        JSObject params = call.getObject("params");
        Integer connectTimeout = call.getInt("connectTimeout");
        Integer readTimeout = call.getInt("readTimeout");
        Boolean disableRedirects = call.getBoolean("disableRedirects");
        Boolean shouldEncode = call.getBoolean("shouldEncodeUrlParams", true);
        ResponseType responseType = ResponseType.parse(call.getString("responseType"));

        String method = httpMethod != null ? httpMethod.toUpperCase() : Objects.requireNonNull(call.getString("method", "")).toUpperCase();

        boolean isHttpMutate = method.equals("DELETE") || method.equals("PATCH") || method.equals("POST") || method.equals("PUT");

        URL url = new URL(urlString);
        HttpURLConnectionBuilder connectionBuilder = new HttpURLConnectionBuilder()
            .setUrl(url)
            .setMethod(method)
            .setHeaders(headers)
            .setUrlParams(params, Boolean.TRUE.equals(shouldEncode))
            .setConnectTimeout(connectTimeout)
            .setReadTimeout(readTimeout)
            .setDisableRedirects(disableRedirects)
            .openConnection();

        CapacitorHttpUrlConnection connection = connectionBuilder.build();

        // Set HTTP body on a non GET or HEAD request
        if (isHttpMutate) {
            JSValue data = new JSValue(call, "data");
            if (data.getValue() != null) {
                connection.setDoOutput(true);
                connection.setRequestBody(call, data);
            }
        }

        connection.connect();

        return HttpResponseBuilder.buildResponse(connection, responseType);
    }

    /**
     * Makes an Http Request to download a file based on the PluginCall parameters
     * @param call The Capacitor PluginCall that contains the options need for an Http request
     * @param context The Android Context required for writing to the filesystem
     * @param progress The emitter which notifies listeners on downloading progression
     * @throws IOException throws an IO request when a connection can't be made
     * @throws URISyntaxException thrown when the URI is malformed
     */
    public static JSObject downloadFile(PluginCall call, Context context, ProgressEmitter progress)
        throws IOException, URISyntaxException, JSONException {
        String urlString = call.getString("url");
        String method = Objects.requireNonNull(call.getString("method", "GET")).toUpperCase();
        String filePath = call.getString("filePath");
        String fileDirectory = call.getString("fileDirectory", FilesystemUtils.DIRECTORY_DOCUMENTS);
        JSObject headers = call.getObject("headers");
        JSObject params = call.getObject("params");
        Integer connectTimeout = call.getInt("connectTimeout");
        Integer readTimeout = call.getInt("readTimeout");

        final URL url = new URL(urlString);
        final File file = FilesystemUtils.getFileObject(context, filePath, fileDirectory);

        HttpURLConnectionBuilder connectionBuilder = new HttpURLConnectionBuilder()
            .setUrl(url)
            .setMethod(method)
            .setHeaders(headers)
            .setUrlParams(params)
            .setConnectTimeout(connectTimeout)
            .setReadTimeout(readTimeout)
            .openConnection();

        ICapacitorHttpUrlConnection connection = connectionBuilder.build();
        InputStream connectionInputStream = connection.getInputStream();

        FileOutputStream fileOutputStream = new FileOutputStream(file, false);

        String contentLength = connection.getHeaderField("content-length");
        int bytes = 0;
        int maxBytes = 0;

        try {
            maxBytes = contentLength != null ? Integer.parseInt(contentLength) : 0;
        } catch (NumberFormatException ignored) {
        }

        byte[] buffer = new byte[1024];
        int len;

        while ((len = connectionInputStream.read(buffer)) > 0) {
            fileOutputStream.write(buffer, 0, len);

            bytes += len;
            progress.emit(bytes, maxBytes);
        }

        connectionInputStream.close();
        fileOutputStream.close();

        return new JSObject() {
            {
                assert file != null;
                put("path", file.getAbsolutePath());
            }
        };
    }

    /**
     * Makes an Http Request to upload a file based on the PluginCall parameters
     * @param call The Capacitor PluginCall that contains the options need for an Http request
     * @param data The data for the multipart upload
     */
    private static void performUpload(PluginCall call, File file, JSObject data) {
        String uploadId = file.getName();
        Log.d("UploadQueue", "🆕 Preparing upload " + uploadId + " for file: " + file.getName());

        // Create upload task
        UploadTask task = new UploadTask(call, file, data, uploadId);

        // Add to queue
        UploadQueue.getInstance().addUpload(task);
    }

    public static void uploadFile(PluginCall call, Context context) {
        String filePath = call.getString("filePath");
        String fileDirectory = call.getString("fileDirectory", FilesystemUtils.DIRECTORY_DOCUMENTS);
        File file = FilesystemUtils.getFileObject(context, filePath, fileDirectory);
        assert file != null;
        performUpload(call, file, call.getObject("data"));
    }

    public static void uploadImage(PluginCall call, Context context) {
        String filePath = call.getString("filePath");
        String fileDirectory = call.getString("fileDirectory", FilesystemUtils.DIRECTORY_DOCUMENTS);
        File file = FilesystemUtils.getFileObject(context, filePath, fileDirectory);
        assert file != null;

        // Get header names from options or use defaults
        String widthHeader = call.getString("widthHeader", "X-Image-Width");
        String heightHeader = call.getString("heightHeader", "X-Image-Height");
        String sizeHeader = call.getString("sizeHeader", "X-File-Size");

        // Get existing data or create new
        JSObject data = call.getObject("data", new JSObject());
        assert data != null;

        File uploadFile;

        JSObject resizeOptions = call.getObject("resize");
        if (resizeOptions != null) {
            ImageUtils.ImageResult result = ImageUtils.resizeImage(context, file, resizeOptions);
            uploadFile = result.file;

            data.put(widthHeader, String.valueOf(result.width));
            data.put(heightHeader, String.valueOf(result.height));
            data.put(sizeHeader, String.valueOf(result.fileSize));
        } else {
            uploadFile = file;
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bmOptions);
            data.put(widthHeader, String.valueOf(bmOptions.outWidth));
            data.put(heightHeader, String.valueOf(bmOptions.outHeight));
            data.put(sizeHeader, String.valueOf(file.length()));
        }

        performUpload(call, uploadFile, data);
    }

    @FunctionalInterface
    public interface ProgressEmitter {
        void emit(Integer bytes, Integer contentLength);
    }
}
