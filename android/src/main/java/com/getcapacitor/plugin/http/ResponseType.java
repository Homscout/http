package com.getcapacitor.plugin.http;

/**
 * An enum specifying conventional HTTP Response Types
 * See <a href="https://developer.mozilla.org/en-US/docs/Web/API/XMLHttpRequest/responseType">docs</a>
 */
public enum ResponseType {
    ARRAY_BUFFER("arraybuffer"),
    BLOB("blob"),
    DOCUMENT("document"),
    JSON("json"),
    TEXT("text");

    private final String name;

    ResponseType(String name) {
        this.name = name;
    }

    static final ResponseType DEFAULT = TEXT;

    static ResponseType parse(String value) {
        for (ResponseType responseType : values()) {
            if (responseType.name.equalsIgnoreCase(value)) {
                return responseType;
            }
        }
        return DEFAULT;
    }
}