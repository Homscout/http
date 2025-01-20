package com.getcapacitor.plugin.http;

import android.Manifest;
import android.os.Build;
import android.util.Log;
import com.getcapacitor.CapConfig;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.File;
import java.io.IOException;
import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.URI;

/**
 * Native HTTP Plugin
 */
@CapacitorPlugin(
    name = "Http",
    permissions = {
        @Permission(strings = { Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE }, alias = "publicStorage"),
    }
)
public class Http extends Plugin {
    static final String PUBLIC_STORAGE = "publicStorage";

    CapConfig capConfig;
    CapacitorCookieManager cookieManager;

    /**
     * Helper function for getting the serverUrl from the Capacitor Config. Returns an empty
     * string if it is invalid and will auto-reject through {@code call}
     * @param call the {@code PluginCall} context
     * @return the string of the server specified in the Capacitor config
     */
    private String getServerUrl(PluginCall call) {
        String url = call.getString("url", "");

        URI uri = getUri(url);
        if (uri == null) {
            call.reject("Invalid URL. Check that \"server\" is passed in correctly");
            return "";
        }

        return url;
    }

    /**
     * Try to parse a url string and if it can't be parsed, return null
     * @param url the url string to try to parse
     * @return a parsed URI
     */
    private URI getUri(String url) {
        try {
            return new URI(url);
        } catch (Exception ex) {
            return null;
        }
    }

    @PermissionCallback
    private void permissionCallback(PluginCall call) {
        if (!isStoragePermissionGranted(call)) {
            Log.w(getLogTag(), "User denied storage permission");
            call.reject("Unable to do file operation, user denied permission request");
            return;
        }

        switch (call.getMethodName()) {
            case "downloadFile":
                downloadFile(call);
                break;
            case "uploadFile":
                uploadFile(call);
                break;
            case "uploadImage":
                uploadImage(call);
                break;
        }
    }

    private boolean isStoragePermissionGranted(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || getPermissionState(PUBLIC_STORAGE) == PermissionState.GRANTED) {
            Log.v(getLogTag(), "Permission '" + PUBLIC_STORAGE + "' is granted");
            return true;
        } else {
            Log.v(getLogTag(), "Permission '" + PUBLIC_STORAGE + "' denied. Asking user for it.");
            requestPermissionForAlias(PUBLIC_STORAGE, call, "permissionCallback");
            return false;
        }
    }

    private void http(final PluginCall call, final String httpMethod) {
        Runnable asyncHttpCall = new Runnable() {
            @Override
            public void run() {
                try {
                    JSObject response = HttpRequestHandler.request(call, httpMethod);
                    call.resolve(response);
                } catch (Exception e) {
                    System.out.println(e.toString());
                    call.reject(e.getClass().getSimpleName(), e);
                }
            }
        };
        Thread httpThread = new Thread(asyncHttpCall);
        httpThread.start();
    }

    @Override
    public void load() {
        this.cookieManager = new CapacitorCookieManager(null, java.net.CookiePolicy.ACCEPT_ALL);
        java.net.CookieHandler.setDefault(cookieManager);
        capConfig = getBridge().getConfig();
    }

    @PluginMethod
    public void request(final PluginCall call) {
        this.http(call, null);
    }

    @PluginMethod
    public void get(final PluginCall call) {
        this.http(call, "GET");
    }

    @PluginMethod
    public void post(final PluginCall call) {
        this.http(call, "POST");
    }

    @PluginMethod
    public void put(final PluginCall call) {
        this.http(call, "PUT");
    }

    @PluginMethod
    public void patch(final PluginCall call) {
        this.http(call, "PATCH");
    }

    @PluginMethod
    public void del(final PluginCall call) {
        this.http(call, "DELETE");
    }

    @PluginMethod
    public void downloadFile(final PluginCall call) {
        try {
            bridge.saveCall(call);
            String path = call.getString("filePath");
            if (path == null) {
                call.reject("filePath not provided");
                return;
            }

            File file = FilesystemUtils.getFileObject(getContext(), path, call.getString("fileDirectory"));

            if (file == null) {
                call.reject("Could not find file" + path);
                return;
            }

            if (
                !FilesystemUtils.isPublicDirectory(file.getParent()) ||
                isStoragePermissionGranted(call)
            ) {
                call.release(bridge);

                HttpRequestHandler.ProgressEmitter emitter = new HttpRequestHandler.ProgressEmitter() {
                    @Override
                    public void emit(Integer bytes, Integer contentLength) {
                        // no-op
                    }
                };
                Boolean progress = call.getBoolean("progress", false);
                if (progress) {
                    emitter =
                        new HttpRequestHandler.ProgressEmitter() {
                            @Override
                            public void emit(final Integer bytes, final Integer contentLength) {
                                JSObject ret = new JSObject();
                                ret.put("type", "DOWNLOAD");
                                ret.put("url", call.getString("url"));
                                ret.put("bytes", bytes);
                                ret.put("contentLength", contentLength);

                                notifyListeners("progress", ret);
                            }
                        };
                }

                JSObject response = HttpRequestHandler.downloadFile(call, file, getContext(), emitter);
                call.resolve(response);
            }
        } catch (MalformedURLException ex) {
            call.reject("Invalid URL", ex);
        } catch (IOException ex) {
            call.reject("IO Error", ex);
        } catch (Exception ex) {
            call.reject("Error", ex);
        }
    }

    @PluginMethod
    public void uploadFile(PluginCall call) {
        try {
            String path = call.getString("filePath");
            if (path == null) {
                call.reject("filePath not provided");
                return;
            }

            File file = FilesystemUtils.getFileObject(getContext(), path, call.getString("fileDirectory"));

            if (file == null) {
                call.reject("Could not find file" + path);
                return;
            }

            bridge.saveCall(call);

            if (
                !FilesystemUtils.isPublicDirectory(file.getParent()) ||
                isStoragePermissionGranted(call)
            ) {
                call.release(bridge);
                HttpRequestHandler.uploadFile(call, file, getContext());
                // Don't release the call here since we need it for the async response
            }
        } catch (Exception ex) {
            call.reject("Error", ex);
        }
    }

    @PluginMethod
    public void uploadImage(PluginCall call) {
        try {
            String path = call.getString("filePath");
            if (path == null) {
                call.reject("filePath not provided");
                return;
            }

            File file = FilesystemUtils.getFileObject(getContext(), path, call.getString("fileDirectory"));

            if (file == null) {
                call.reject("Could not find file" + path);
                return;
            }

            bridge.saveCall(call);

            if (
                !FilesystemUtils.isPublicDirectory(file.getParent()) ||
                isStoragePermissionGranted(call)
            ) {
                call.release(bridge);
                HttpRequestHandler.uploadImage(call, file, getContext());
                // Don't release the call here since we need it for the async response
            }
        } catch (Exception ex) {
            call.reject("Error", ex);
        }
    }

    @PluginMethod
    public void setCookie(PluginCall call) {
        String key = call.getString("key");
        String value = call.getString("value");
        String url = getServerUrl(call);

        if (!url.isEmpty()) {
            cookieManager.setCookie(url, key, value);
            call.resolve();
        }
    }

    @PluginMethod
    public void getCookiesMap(PluginCall call) {
        String url = getServerUrl(call);
        if (!url.isEmpty()) {
            HttpCookie[] cookies = cookieManager.getCookies(url);
            JSObject cookiesJsObject = new JSObject();
            for (HttpCookie cookie : cookies) {
                cookiesJsObject.put(cookie.getName(), cookie.getValue());
            }
            call.resolve(cookiesJsObject);
        }
    }

    @PluginMethod
    public void getCookies(PluginCall call) {
        String url = getServerUrl(call);
        if (!url.isEmpty()) {
            HttpCookie[] cookies = cookieManager.getCookies(url);
            JSArray cookiesJsArray = new JSArray();
            for (HttpCookie cookie : cookies) {
                JSObject cookieJsPair = new JSObject();
                cookieJsPair.put("key", cookie.getName());
                cookieJsPair.put("value", cookie.getValue());
                cookiesJsArray.put(cookieJsPair);
            }
            JSObject cookiesJsObject = new JSObject();
            cookiesJsObject.put("cookies", cookiesJsArray);
            call.resolve(cookiesJsObject);
        }
    }

    @PluginMethod
    public void getCookie(PluginCall call) {
        String key = call.getString("key");
        String url = getServerUrl(call);
        if (!url.isEmpty()) {
            HttpCookie cookie = cookieManager.getCookie(url, key);
            JSObject cookieJsObject = new JSObject();
            cookieJsObject.put("key", key);
            if (cookie != null) {
                cookieJsObject.put("value", cookie.getValue());
            } else {
                cookieJsObject.put("value", "");
            }
            call.resolve(cookieJsObject);
        }
    }

    @PluginMethod
    public void deleteCookie(PluginCall call) {
        String key = call.getString("key");
        String url = getServerUrl(call);
        if (!url.isEmpty()) {
            cookieManager.setCookie(url, key + "=; Expires=Wed, 31 Dec 2000 23:59:59 GMT");
            call.resolve();
        }
    }

    @PluginMethod
    public void clearCookies(PluginCall call) {
        String url = getServerUrl(call);
        if (!url.isEmpty()) {
            HttpCookie[] cookies = cookieManager.getCookies(url);
            for (HttpCookie cookie : cookies) {
                cookieManager.setCookie(url, cookie.getName() + "=; Expires=Wed, 31 Dec 2000 23:59:59 GMT");
            }
            call.resolve();
        }
    }

    @PluginMethod
    public void clearAllCookies(PluginCall call) {
        cookieManager.removeAllCookies();
        call.resolve();
    }
}
