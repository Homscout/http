package com.getcapacitor.plugin.http;

import com.getcapacitor.JSObject;

public interface UploadTaskCallback {
    void onSuccess(JSObject response);
    void onError(String message, String code, Exception error);
}
