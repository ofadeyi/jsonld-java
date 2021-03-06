package com.github.jsonldjava.core;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.RequestAcceptEncoding;
import org.apache.http.client.protocol.ResponseContentEncoding;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.cache.BasicHttpCacheStorage;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.CachingHttpClientBuilder;
import org.junit.Test;

import com.github.jsonldjava.utils.JarCacheStorage;
import com.github.jsonldjava.utils.JsonUtils;

public class MinimalSchemaOrgRegressionTest {

    private static final String ACCEPT_HEADER = "application/ld+json, application/json;q=0.9, application/javascript;q=0.5, text/javascript;q=0.5, text/plain;q=0.2, */*;q=0.1";

    @Test
    public void testHttpURLConnection() throws Exception {
        final URL url = new URL("http://schema.org/");
        final HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
        urlConn.addRequestProperty("Accept", ACCEPT_HEADER);

        final InputStream directStream = urlConn.getInputStream();
        verifyInputStream(directStream);
    }

    private void verifyInputStream(InputStream directStream) throws IOException {
        final StringWriter output = new StringWriter();
        try {
            IOUtils.copy(directStream, output, Charset.forName("UTF-8"));
        } finally {
            directStream.close();
            output.flush();
        }
        final String outputString = output.toString();
        System.out.println(outputString);
        // Test for some basic conditions without including the JSON/JSON-LD
        // parsing code here
        assertTrue(outputString.endsWith("}\n"));
        assertTrue(outputString.length() > 100000);
    }

    @Test
    public void testApacheHttpClient() throws Exception {
        final URL url = new URL("http://schema.org/");
        // Common CacheConfig for both the JarCacheStorage and the underlying
        // BasicHttpCacheStorage
        final CacheConfig cacheConfig = CacheConfig.custom().setMaxCacheEntries(1000)
                .setMaxObjectSize(1024 * 128).build();

        final CloseableHttpClient httpClient = CachingHttpClientBuilder.create()
                // allow caching
                .setCacheConfig(cacheConfig)
                // Wrap the local JarCacheStorage around a BasicHttpCacheStorage
                .setHttpCacheStorage(new JarCacheStorage(null, cacheConfig,
                        new BasicHttpCacheStorage(cacheConfig)))
                // Support compressed data
                // http://hc.apache.org/httpcomponents-client-ga/tutorial/html/httpagent.html#d5e1238
                .addInterceptorFirst(new RequestAcceptEncoding())
                .addInterceptorFirst(new ResponseContentEncoding())
                // use system defaults for proxy etc.
                .useSystemProperties().build();

        final HttpUriRequest request = new HttpGet(url.toExternalForm());
        // We prefer application/ld+json, but fallback to application/json
        // or whatever is available
        request.addHeader("Accept", ACCEPT_HEADER);

        final CloseableHttpResponse response = httpClient.execute(request);
        try {
            final int status = response.getStatusLine().getStatusCode();
            if (status != 200 && status != 203) {
                throw new IOException("Can't retrieve " + url + ", status code: " + status);
            }
            final InputStream content = response.getEntity().getContent();
            verifyInputStream(content);
        } finally {
            if (response != null) {
                response.close();
            }
        }

    }

}
