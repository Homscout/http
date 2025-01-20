package com.getcapacitor.plugin.http;

import android.content.Context;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;
import java.util.UUID;
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
    public static JSObject downloadFile(PluginCall call, File file, Context context, ProgressEmitter progress)
        throws IOException, URISyntaxException, JSONException {
        String urlString = call.getString("url");
        String method = Objects.requireNonNull(call.getString("method", "GET")).toUpperCase();
        JSObject headers = call.getObject("headers");
        JSObject params = call.getObject("params");
        Integer connectTimeout = call.getInt("connectTimeout");
        Integer readTimeout = call.getInt("readTimeout");

        final URL url = new URL(urlString);

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
     * @param context Application environment context
     */
    private static void performUpload(PluginCall call, File file, Context context) {
        try {
            CapacitorHttpUrlConnection connection = new HttpURLConnectionBuilder()
                    .setUrl(new URL(call.getString("url")))
                    .setMethod(Objects.requireNonNull(call.getString("method", "POST")).toUpperCase())
                    .setHeaders(call.getObject("headers"))
                    .setUrlParams(call.getObject("params"))
                    .setConnectTimeout(call.getInt("connectTimeout"))
                    .setReadTimeout(call.getInt("readTimeout"))
                    .openConnection()
                    .build();

            connection.setDoOutput(true);

            // Create upload task
            UploadTask task = new UploadTask(
                connection,
                file,
                call.getObject("data", new JSObject()),
                call.getObject("resize"),
                call.getString("id", UUID.randomUUID().toString()),
                call.getString("name", "file"),
                call.getString("widthHeader", "X-Image-Width"),
                call.getString("heightHeader", "X-Image-Height"),
                call.getString("sizeHeader", "X-File-Size"),
                ResponseType.parse(call.getString("responseType")),
                context,
                new UploadTaskCallback() {
                    @Override
                    public void onSuccess(JSObject response) {
                        call.resolve(response);
                    }

                    @Override
                    public void onError(String message, String code, Exception error) {
                        call.reject(message, code, error);
                    }
                }
            );

            // Add to queue
            UploadQueue.getInstance().addUpload(task);
        } catch (Exception e) {
            call.reject("Error", e);
        }
    }

    public static void uploadFile(PluginCall call, File file, Context context) {
        performUpload(call, file, context);
    }

    public static void uploadImage(PluginCall call, File file, Context context) {
        performUpload(call, file, context);
    }

    @FunctionalInterface
    public interface ProgressEmitter {
        void emit(Integer bytes, Integer contentLength);
    }
}
