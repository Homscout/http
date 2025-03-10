package com.getcapacitor.plugin.http;

import com.getcapacitor.JSObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.util.Iterator;
import java.util.UUID;
import org.json.JSONException;

public class FormUploader {

    private final String LINE_FEED = "\r\n";
    private final String boundary;
    private final String charset = "UTF-8";
    private final OutputStream outputStream;
    private final PrintWriter prWriter;

    /**
     * This constructor initializes a new HTTP POST request with content type
     * is set to multipart/form-data
     * @param connection The HttpUrlConnection to use to upload a Form
     * @throws IOException Thrown if unable to parse the OutputStream of the connection
     */
    public FormUploader(HttpURLConnection connection) throws IOException {
        UUID uuid = UUID.randomUUID();
        boundary = uuid.toString();

        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        outputStream = connection.getOutputStream();
        prWriter = new PrintWriter(new OutputStreamWriter(outputStream, charset), true);
    }

    /**
     * Adds a form field to the request
     *
     * @param name  field name
     * @param value field value
     */
    public void addFormField(String name, String value) {
        prWriter
            .append(LINE_FEED)
            .append("--")
            .append(boundary)
            .append(LINE_FEED)
            .append("Content-Disposition: form-data; name=\"")
            .append(name)
            .append("\"")
            .append(LINE_FEED)
            .append("Content-Type: text/plain; charset=")
            .append(charset)
            .append(LINE_FEED)
            .append(LINE_FEED)
            .append(value)
            .append(LINE_FEED)
            .append("--")
            .append(boundary)
            .append("--")
            .append(LINE_FEED);
        prWriter.flush();
    }

    /**
     * Adds a form field to the prWriter
     *
     * @param name  field name
     * @param value field value
     */
    private void appendFieldToWriter(String name, String value) {
        prWriter
            .append(LINE_FEED)
            .append("--")
            .append(boundary)
            .append(LINE_FEED)
            .append("Content-Disposition: form-data; name=\"")
            .append(name)
            .append("\"")
            .append(LINE_FEED)
            .append("Content-Type: text/plain; charset=")
            .append(charset)
            .append(LINE_FEED)
            .append(LINE_FEED)
            .append(value);
    }

    /**
     * Adds a upload file section to the request
     *
     * @param fieldName  name attribute in <input type="file" name="..." />
     * @param uploadFile a File to be uploaded
     * @throws IOException Thrown if unable to parse the OutputStream of the connection
     */
    public void addFilePart(String fieldName, File uploadFile, JSObject data) throws IOException {
        // First, add the 'key' field if it exists
        if (data != null && data.has("key")) {
            try {
                appendFieldToWriter("key", data.getString("key"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Then add all other fields (except 'key' since we already handled it)
        if (data != null) {
            Iterator<String> keyIterator = data.keys();
            while (keyIterator.hasNext()) {
                String key = keyIterator.next();
                if (!key.equals("key")) { // Skip 'key' as we've already handled it
                    try {
                        Object value = data.get(key);

                        if (value instanceof String) {
                            appendFieldToWriter(key, value.toString());
                        } else if (value instanceof String[]) {
                            for (String childValue : (String[]) value) {
                                appendFieldToWriter(key, childValue);
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // Finally add the file part
        String fileName = uploadFile.getName();
        prWriter
            .append(LINE_FEED)
            .append("--")
            .append(boundary)
            .append(LINE_FEED)
            .append("Content-Disposition: form-data; name=\"")
            .append(fieldName)
            .append("\"; filename=\"")
            .append(fileName)
            .append("\"")
            .append(LINE_FEED)
            .append("Content-Type: ")
            .append(URLConnection.guessContentTypeFromName(fileName))
            .append(LINE_FEED)
            .append(LINE_FEED);
        prWriter.flush();

        FileInputStream inputStream = new FileInputStream(uploadFile);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.flush();
        inputStream.close();

        prWriter.append(LINE_FEED).append("--").append(boundary).append("--").append(LINE_FEED);
        prWriter.flush();
    }

    /**
     * Adds a header field to the request.
     *
     * @param name  - name of the header field
     * @param value - value of the header field
     */
    public void addHeaderField(String name, String value) {
        prWriter.append(name).append(": ").append(value).append(LINE_FEED);
        prWriter.flush();
    }

    /**
     * Completes the request and receives response from the server.
     * returns a list of Strings as response in case the server returned
     * status OK, otherwise an exception is thrown.
     */
    public void finish() {
        prWriter.append(LINE_FEED);
        prWriter.flush();
        prWriter.append("--").append(boundary).append("--").append(LINE_FEED);
        prWriter.close();
    }
}
