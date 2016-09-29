/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <http://unlicense.org/>
 */

package org.jenkinsci.plugins.graniteclient;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUser;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.UsernameCredentials;
import jenkins.model.Jenkins;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pojo for capturing the group of configuration values for a single Granite Client connection
 */
public final class GraniteClientConfig implements Serializable {

    private static final long serialVersionUID = 2713710297119924270L;
    private static final List<String> ignorableSuffixes =
            Arrays.asList("/", "/crx", "/crx/packmgr", "/crx/packmgr/service.jsp");

    static String trimUrl(String value) {
        String trimmed = value.trim();
        for (String ignorableSuffix : ignorableSuffixes) {
            if (trimmed.endsWith(ignorableSuffix)) {
                trimmed = trimmed.substring(0, trimmed.length() - ignorableSuffix.length());
            }
        }
        return trimmed;
    }

    private final String baseUrl;
    private final String credentialsId;
    private final long requestTimeout;
    private final long serviceTimeout;
    private final long waitDelay;
    private final Credentials credentials;

    public GraniteClientConfig(String baseUrl, String credentialsId) {
        this(baseUrl, credentialsId, 0L, 0L, 0L);
    }

    public GraniteClientConfig(String baseUrl, String credentialsId, long requestTimeout, long serviceTimeout) {
        this(baseUrl, credentialsId, requestTimeout, serviceTimeout, 0L);
    }

    public GraniteClientConfig(String baseUrl, String credentialsId, long requestTimeout, long serviceTimeout, long waitDelay) {
        this.credentialsId = credentialsId;
        this.requestTimeout = requestTimeout > 0L ? requestTimeout : -1L;
        this.serviceTimeout = serviceTimeout > 0L ? serviceTimeout : -1L;
        this.waitDelay = waitDelay > 0L ? waitDelay : -1L;

        String _baseUrl = sanitizeUrl(baseUrl);

        Credentials _credentials = GraniteNamedIdCredentials.getCredentialsById(credentialsId);

        if (_credentials == null) {
            _credentials = GraniteAHCFactory.getFactoryInstance().getDefaultCredentials();
        }

        try {
            URI baseUri = new URI(_baseUrl);
            if (baseUri.getUserInfo() != null) {

                _credentials = GraniteNamedIdCredentials
                        .getCredentialsFromURIUserInfo(baseUri.getUserInfo(), _credentials);

                URI changed = new URI(
                        baseUri.getScheme(),
                        null,
                        baseUri.getHost(),
                        baseUri.getPort(),
                        baseUri.getPath(),
                        baseUri.getQuery(),
                        baseUri.getFragment());

                _baseUrl = changed.toString();
            }
        } catch (URISyntaxException e) {
            // do nothing at the moment;
        }

        this.baseUrl = _baseUrl;
        this.credentials = _credentials;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public boolean isSignatureLogin() {
        return credentials instanceof SSHUserPrivateKey;
    }

    public String getUsername() {
        if (this.credentials instanceof SSHUser) {
            return ((SSHUser) this.credentials).getUsername();
        } else if (this.credentials instanceof UsernameCredentials) {
            return ((UsernameCredentials) this.credentials).getUsername();
        } else {
            return "admin";
        }
    }

    public long getRequestTimeout() {
        return requestTimeout;
    }

    public long getServiceTimeout() {
        return serviceTimeout;
    }

    public long getWaitDelay() {
        return waitDelay;
    }

    public Credentials getCredentials() {
        return credentials;
    }

    private static final Pattern HTTP_URL_PATTERN = Pattern.compile("(https?://)([^/]+)($|/.*)");

    public static String sanitizeUrl(final String url) {
        // remove tokens with extreme prejudice
        String _url = url.replaceAll("\\$\\{\\w*\\}?", "");

        // identify http/s URLs, since that's really all we support
        Matcher urlMatcher = HTTP_URL_PATTERN.matcher(_url);
        if (urlMatcher.find()) {

            StringBuilder sb = new StringBuilder(urlMatcher.group(1));

            final String authority = urlMatcher.group(2);

            int lastAt = authority.lastIndexOf('@');
            if (lastAt >= 0) {
                final String host = authority.substring(lastAt);
                final String rawUserInfo = authority.substring(0, lastAt);
                final int firstColon = rawUserInfo.indexOf(':');
                if (firstColon >= 0) {
                    String rawUsername = rawUserInfo.substring(0, firstColon);
                    String rawPassword = rawUserInfo.substring(firstColon + 1);
                    sb.append(urlEscape(rawUsername)).append(":").append(urlEscape(rawPassword));
                } else {
                    sb.append(urlEscape(rawUserInfo));
                }

                sb.append(host);
            } else {
                sb.append(authority);
            }

            _url = sb.append(urlMatcher.group(3)).toString();
        }

        return _url;
    }

    private static String urlEscape(final String raw) {
        return raw.replaceAll("%(?![A-Fa-f0-9]{2})", "%25")
                .replace(" ", "%20")
                .replace("!", "%21")
                .replace("#", "%23")
                .replace("$", "%24")
                .replace("&", "%26")
                .replace("'", "%27")
                .replace("(", "%28")
                .replace(")", "%29")
                .replace("*", "%2A")
                .replace("+", "%2B")
                .replace(",", "%2C")
                .replace("/", "%2F")
                .replace(":", "%3A")
                .replace(";", "%3B")
                .replace("=", "%3D")
                .replace("?", "%3F")
                .replace("@", "%40")
                .replace("[", "%5B")
                .replace("]", "%5D");
    }
}
