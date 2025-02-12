package com.getcapacitor.plugin.http;

import static com.getcapacitor.plugin.http.MimeType.APPLICATION_JSON;
import static com.getcapacitor.plugin.http.MimeType.APPLICATION_VND_API_JSON;

import android.text.TextUtils;
import android.util.Base64;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

public class HttpResponseBuilder {
    /**
     * Builds an HTTP Response given CapacitorHttpUrlConnection and ResponseType objects
     * @param connection The CapacitorHttpUrlConnection to respond with
     * @param responseType The requested ResponseType
     * @return A JSObject that contains the HTTPResponse to return to the browser
     * @throws IOException Thrown if the InputStream is unable to be parsed correctly
     * @throws JSONException Thrown if the JSON is unable to be parsed
     */
    public static JSObject buildResponse(CapacitorHttpUrlConnection connection, ResponseType responseType)
            throws IOException, JSONException {
        int statusCode = connection.getResponseCode();

        JSObject output = new JSObject();
        output.put("status", statusCode);
        output.put("headers", buildResponseHeaders(connection));
        output.put("url", connection.getURL());
        output.put("data", readData(connection, responseType));

        InputStream errorStream = connection.getErrorStream();
        if (errorStream != null) {
            output.put("error", true);
        }

        return output;
    }

    /**
     * Build the JSObject response headers based on the connection header map
     * @param connection The CapacitorHttpUrlConnection connection
     * @return A JSObject of the header values from the CapacitorHttpUrlConnection
     */
    private static JSObject buildResponseHeaders(CapacitorHttpUrlConnection connection) {
        JSObject output = new JSObject();

        for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
            String valuesString = TextUtils.join(", ", entry.getValue());
            output.put(entry.getKey(), valuesString);
        }

        return output;
    }

    /**
     * Read the existing ICapacitorHttpUrlConnection data
     * @param connection The ICapacitorHttpUrlConnection object to read in
     * @param responseType The type of HTTP response to return to the API
     * @return The parsed data from the connection
     * @throws IOException Thrown if the InputStreams cannot be properly parsed
     * @throws JSONException Thrown if the JSON is malformed when parsing as JSON
     */
    static Object readData(ICapacitorHttpUrlConnection connection, ResponseType responseType) throws IOException, JSONException {
        InputStream errorStream = connection.getErrorStream();
        String contentType = connection.getHeaderField("Content-Type");

        if (errorStream != null) {
            if (isOneOf(contentType, APPLICATION_JSON, APPLICATION_VND_API_JSON)) {
                return parseJSON(readStreamAsString(errorStream));
            } else {
                return readStreamAsString(errorStream);
            }
        } else if (contentType != null && contentType.contains(APPLICATION_JSON.getValue())) {
            // backward compatibility
            return parseJSON(readStreamAsString(connection.getInputStream()));
        } else {
            InputStream stream = connection.getInputStream();
            return switch (responseType) {
                case ARRAY_BUFFER, BLOB -> readStreamAsBase64(stream);
                case JSON -> parseJSON(readStreamAsString(stream));
                default -> readStreamAsString(stream);
            };
        }
    }

    /**
     * Helper function for determining if the Content-Type is a typeof an existing Mime-Type
     * @param contentType The Content-Type string to check for
     * @param mimeTypes The Mime-Type values to check against
     * @return boolean
     */
    private static boolean isOneOf(String contentType, MimeType... mimeTypes) {
        if (contentType != null) {
            for (MimeType mimeType : mimeTypes) {
                if (contentType.contains(mimeType.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns a JSObject or a JSArray based on a string-ified input
     * @param input String-ified JSON that needs parsing
     * @return A JSObject or JSArray
     * @throws JSONException thrown if the JSON is malformed
     */
    private static Object parseJSON(String input) throws JSONException {
        try {
            if ("null".equals(input.trim())) {
                return JSONObject.NULL;
            } else if ("true".equals(input.trim())) {
                return new JSONObject().put("flag", "true");
            } else if ("false".equals(input.trim())) {
                return new JSONObject().put("flag", "false");
            } else {
                try {
                    return new JSObject(input);
                } catch (JSONException e) {
                    return new JSArray(input);
                }
            }
        } catch (JSONException e) {
            return new JSArray(input);
        }
    }

    /**
     * Returns a string based on a base64 InputStream
     * @param in The base64 InputStream to convert to a String
     * @return String value of InputStream
     * @throws IOException thrown if the InputStream is unable to be read as base64
     */
    private static String readStreamAsBase64(InputStream in) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int readBytes;
            while ((readBytes = in.read(buffer)) != -1) {
                out.write(buffer, 0, readBytes);
            }
            byte[] result = out.toByteArray();
            return Base64.encodeToString(result, 0, result.length, Base64.DEFAULT);
        }
    }

    /**
     * Returns a string based on an InputStream
     * @param in The InputStream to convert to a String
     * @return String value of InputStream
     * @throws IOException thrown if the InputStream is unable to be read
     */
    private static String readStreamAsString(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            StringBuilder builder = new StringBuilder();
            String line = reader.readLine();
            while (line != null) {
                builder.append(line);
                line = reader.readLine();
                if (line != null) {
                    builder.append(System.lineSeparator());
                }
            }
            return builder.toString();
        }
    }
}
