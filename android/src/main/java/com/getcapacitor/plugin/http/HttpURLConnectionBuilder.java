package com.getcapacitor.plugin.http;

import com.getcapacitor.JSObject;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Iterator;

/**
 * Internal builder class for building a CapacitorHttpUrlConnection
 */
public class HttpURLConnectionBuilder {
    private Integer connectTimeout;
    private Integer readTimeout;
    private Boolean disableRedirects;
    private JSObject headers;
    private String method;
    private URL url;

    private CapacitorHttpUrlConnection connection;

    public HttpURLConnectionBuilder setConnectTimeout(Integer connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    public HttpURLConnectionBuilder setReadTimeout(Integer readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    public HttpURLConnectionBuilder setDisableRedirects(Boolean disableRedirects) {
        this.disableRedirects = disableRedirects;
        return this;
    }

    public HttpURLConnectionBuilder setHeaders(JSObject headers) {
        this.headers = headers;
        return this;
    }

    public HttpURLConnectionBuilder setMethod(String method) {
        this.method = method;
        return this;
    }

    public HttpURLConnectionBuilder setUrl(URL url) {
        this.url = url;
        return this;
    }

    public HttpURLConnectionBuilder openConnection() throws IOException {
        connection = new CapacitorHttpUrlConnection((HttpURLConnection) url.openConnection());

        connection.setAllowUserInteraction(false);
        connection.setRequestMethod(method);

        if (connectTimeout != null) connection.setConnectTimeout(connectTimeout);
        if (readTimeout != null) connection.setReadTimeout(readTimeout);
        if (disableRedirects != null) connection.setDisableRedirects(disableRedirects);
        if (headers != null) connection.setRequestHeaders(headers);
        return this;
    }

    public HttpURLConnectionBuilder setUrlParams(JSObject params) throws MalformedURLException, URISyntaxException {
        return this.setUrlParams(params, true);
    }

    public HttpURLConnectionBuilder setUrlParams(JSObject params, boolean shouldEncode)
            throws URISyntaxException, MalformedURLException {
        if (params == null) {
            return this;
        }

        String initialQuery = url.getQuery();
        String initialQueryBuilderStr = initialQuery == null ? "" : initialQuery;

        Iterator<String> keys = params.keys();

        if (!keys.hasNext()) {
            return this;
        }

        StringBuilder urlQueryBuilder = new StringBuilder(initialQueryBuilderStr);

        // Build the new query string
        while (keys.hasNext()) {
            String key = keys.next();

            // Attempt as JSONArray and fallback to string if it fails
            try {
                StringBuilder value = new StringBuilder();
                JSONArray arr = params.getJSONArray(key);
                for (int x = 0; x < arr.length(); x++) {
                    value.append(key).append("=").append(arr.getString(x));
                    if (x != arr.length() - 1) {
                        value.append("&");
                    }
                }
                if (urlQueryBuilder.length() > 0) {
                    urlQueryBuilder.append("&");
                }
                urlQueryBuilder.append(value);
            } catch (JSONException e) {
                if (urlQueryBuilder.length() > 0) {
                    urlQueryBuilder.append("&");
                }
                urlQueryBuilder.append(key).append("=").append(params.getString(key));
            }
        }

        String urlQuery = urlQueryBuilder.toString();

        URI uri = url.toURI();
        if (shouldEncode) {
            URI encodedUri = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), urlQuery, uri.getFragment());
            this.url = encodedUri.toURL();
        } else {
            String unEncodedUrlString =
                    uri.getScheme() +
                            "://" +
                            uri.getAuthority() +
                            uri.getPath() +
                            ((!urlQuery.equals("")) ? "?" + urlQuery : "") +
                            ((uri.getFragment() != null) ? uri.getFragment() : "");
            this.url = new URL(unEncodedUrlString);
        }

        return this;
    }

    public CapacitorHttpUrlConnection build() {
        return connection;
    }
}
