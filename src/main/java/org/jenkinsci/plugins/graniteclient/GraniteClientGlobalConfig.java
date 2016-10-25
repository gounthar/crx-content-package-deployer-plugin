package org.jenkinsci.plugins.graniteclient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.annotation.CheckForNull;

import com.cloudbees.plugins.credentials.Credentials;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.ProxyServer;
import hudson.ProxyConfiguration;
import hudson.model.TaskListener;
import hudson.util.LogTaskListener;

/**
 * Serializable global config
 */
public class GraniteClientGlobalConfig implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(GraniteClientGlobalConfig.class.getName());
    private static final TaskListener DEFAULT_LISTENER = new LogTaskListener(LOGGER, Level.INFO);

    private static final long serialVersionUID = 2713710297120024271L;

    public static final int DEFAULT_TIMEOUT = 60000;

    private final Credentials defaultCredentials;
    private final String preemptLoginForBaseUrls;
    private final int connectionTimeoutInMs;
    private final int idleConnectionTimeoutInMs;
    private final int requestTimeoutInMs;
    private final ProxyConfiguration proxy;

    public GraniteClientGlobalConfig(Credentials defaultCredentials, String preemptLoginForBaseUrls, int connectionTimeoutInMs, int idleConnectionTimeoutInMs, int requestTimeoutInMs, ProxyConfiguration proxy) {
        this.defaultCredentials = defaultCredentials;
        this.preemptLoginForBaseUrls = preemptLoginForBaseUrls;
        this.connectionTimeoutInMs = connectionTimeoutInMs;
        this.idleConnectionTimeoutInMs = idleConnectionTimeoutInMs;
        this.requestTimeoutInMs = requestTimeoutInMs;
        this.proxy = proxy;
    }

    public Credentials getDefaultCredentials() {
        return defaultCredentials;
    }

    public String getPreemptLoginForBaseUrls() {
        return preemptLoginForBaseUrls;
    }

    public int getConnectionTimeoutInMs() {
        return connectionTimeoutInMs;
    }

    public int getIdleConnectionTimeoutInMs() {
        return idleConnectionTimeoutInMs;
    }

    public int getRequestTimeoutInMs() {
        return requestTimeoutInMs;
    }

    public ProxyConfiguration getProxy() {
        return proxy;
    }

    public AsyncHttpClient getInstance() {
        return new AsyncHttpClient(
                new AsyncHttpClientConfig.Builder()
                        .setProxyServer(getProxyServer())
                        .setConnectTimeout(this.connectionTimeoutInMs > 0 ?
                                this.connectionTimeoutInMs : DEFAULT_TIMEOUT)
                        .setReadTimeout(this.idleConnectionTimeoutInMs > 0 ?
                                this.idleConnectionTimeoutInMs : DEFAULT_TIMEOUT)
                        .setRequestTimeout(this.requestTimeoutInMs > 0 ?
                                this.requestTimeoutInMs : DEFAULT_TIMEOUT)
                        .build()
        );
    }

    public ProxyServer getProxyServer() {
        ProxyServer proxyServer;
        if (this.proxy != null) {
            proxyServer = new ProxyServer(proxy.name, proxy.port, proxy.getUserName(), proxy.getPassword());
        } else {
            proxyServer = null;
        }

        return proxyServer;
    }

    /**
     * compare the baseUrl against the configured list of patterns that should preempt login using basic auth
     *
     * @param baseUrl   the base url to check
     * @param _listener a listener for reporting errors. null uses backend logger
     * @return true if preemptive basic auth should be enabled
     */
    public boolean shouldPreemptLoginForBaseUrl(String baseUrl, @CheckForNull TaskListener _listener) {
        final TaskListener listener = _listener != null ? _listener : DEFAULT_LISTENER;
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            boolean matched = false;
            try {
                List<Pattern> patterns = getPreemptLoginPatterns(getPreemptLoginForBaseUrls());
                for (Pattern pattern : patterns) {
                    if (pattern.matcher(baseUrl).find()) {
                        matched = true;
                    }
                }
            } catch (PatternSyntaxException e) {
                listener.getLogger()
                        .printf("[WARN] exception encountered when attempting to compile Preempt Login patterns: %n%s%n",
                                e.getMessage());
            }
            listener.error("[shouldPreemptLoginForBaseUrl] baseUrl: %s, matched: %s", baseUrl, matched);
            return matched;
        }
        return false;

    }

    public static List<Pattern> getPreemptLoginPatterns(final String _patterns) throws PatternSyntaxException {
        List<Pattern> compiled = new ArrayList<>();
        if (_patterns == null) {
            return compiled;
        }
        String[] patterns = _patterns.split("\\r?\\n");
        for (String pattern : patterns) {
            if (!pattern.trim().isEmpty()) {
                compiled.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
            }
        }

        return compiled;
    }


}
