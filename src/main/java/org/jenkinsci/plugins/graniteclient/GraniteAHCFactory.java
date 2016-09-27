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

import java.io.Serializable;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.AbstractIdCredentialsListBoxModel;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.ProxyServer;
import hudson.Extension;
import hudson.ProxyConfiguration;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.security.AccessControlled;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Global extension and configurable factory for {@link AsyncHttpClient} instances
 */
@Extension
public final class GraniteAHCFactory extends Descriptor<GraniteAHCFactory> implements Describable<GraniteAHCFactory>, Serializable {

    private static final long serialVersionUID = 1329103722879551699L;
    private static final int DEFAULT_TIMEOUT = 60000;
    private static final int DEFAULT_TIMEOUT_FOR_VALIDATION = 10000;

    private String credentialsId;
    private int connectionTimeoutInMs = DEFAULT_TIMEOUT;
    private int idleConnectionTimeoutInMs = DEFAULT_TIMEOUT;
    private int requestTimeoutInMs = DEFAULT_TIMEOUT;
    private boolean disableBaseUrlValidation;
    private int connectionTimeoutInMsForValidation = DEFAULT_TIMEOUT_FOR_VALIDATION;
    private int idleConnectionTimeoutInMsForValidation = DEFAULT_TIMEOUT_FOR_VALIDATION;
    private int requestTimeoutInMsForValidation = DEFAULT_TIMEOUT_FOR_VALIDATION;

    private transient AsyncHttpClient instance;
    private transient AsyncHttpClient instanceForValidation;

    public GraniteAHCFactory() {
        this(true);
    }

    public GraniteAHCFactory(boolean loadDescriptor) {
        super(GraniteAHCFactory.class);
        if (loadDescriptor) {
            load();
        }
        this.resetClients();
    }

    private void resetClients() {
        if (this.instance != null) {
            if (!this.instance.isClosed()) {
                this.instance.close();
            }
        }

        this.instance = new AsyncHttpClient(
                new AsyncHttpClientConfig.Builder()
                        .setProxyServer(getProxyServer())
                        .setConnectionTimeoutInMs(this.connectionTimeoutInMs > 0 ?
                                this.connectionTimeoutInMs : DEFAULT_TIMEOUT)
                        .setIdleConnectionTimeoutInMs(this.idleConnectionTimeoutInMs > 0 ?
                                this.idleConnectionTimeoutInMs : DEFAULT_TIMEOUT)
                        .setRequestTimeoutInMs(this.requestTimeoutInMs > 0 ?
                                this.requestTimeoutInMs : DEFAULT_TIMEOUT)
                        .build()
        );

        if (this.instanceForValidation != null) {
            if (!this.instanceForValidation.isClosed()) {
                this.instanceForValidation.close();
            }
        }

        this.instanceForValidation = new AsyncHttpClient(
                new AsyncHttpClientConfig.Builder()
                        .setProxyServer(getProxyServer())
                        .setConnectionTimeoutInMs(this.connectionTimeoutInMsForValidation > 0 ?
                                this.connectionTimeoutInMsForValidation : DEFAULT_TIMEOUT_FOR_VALIDATION)
                        .setIdleConnectionTimeoutInMs(this.idleConnectionTimeoutInMsForValidation > 0 ?
                                this.idleConnectionTimeoutInMsForValidation : DEFAULT_TIMEOUT_FOR_VALIDATION)
                        .setRequestTimeoutInMs(this.requestTimeoutInMsForValidation > 0 ?
                                this.requestTimeoutInMsForValidation : DEFAULT_TIMEOUT_FOR_VALIDATION)
                        .build()
        );
    }

    @SuppressWarnings("unchecked")
    public Descriptor<GraniteAHCFactory> getDescriptor() {
        return getFactoryDescriptor();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json.getJSONObject("GraniteAHCFactory"));
        save();
        this.resetClients();
        return true;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public int getConnectionTimeoutInMs() {
        return connectionTimeoutInMs;
    }

    public void setConnectionTimeoutInMs(int connectionTimeoutInMs) {
        this.connectionTimeoutInMs = connectionTimeoutInMs;
    }

    public int getIdleConnectionTimeoutInMs() {
        return idleConnectionTimeoutInMs;
    }

    public void setIdleConnectionTimeoutInMs(int idleConnectionTimeoutInMs) {
        this.idleConnectionTimeoutInMs = idleConnectionTimeoutInMs;
    }

    public int getRequestTimeoutInMs() {
        return requestTimeoutInMs;
    }

    public void setRequestTimeoutInMs(int requestTimeoutInMs) {
        this.requestTimeoutInMs = requestTimeoutInMs;
    }

    public boolean isDisableBaseUrlValidation() {
        return disableBaseUrlValidation;
    }

    public void setDisableBaseUrlValidation(boolean disableBaseUrlValidation) {
        this.disableBaseUrlValidation = disableBaseUrlValidation;
    }

    public int getConnectionTimeoutInMsForValidation() {
        return connectionTimeoutInMsForValidation;
    }

    public void setConnectionTimeoutInMsForValidation(int connectionTimeoutInMsForValidation) {
        this.connectionTimeoutInMsForValidation = connectionTimeoutInMsForValidation;
    }

    public int getIdleConnectionTimeoutInMsForValidation() {
        return idleConnectionTimeoutInMsForValidation;
    }

    public void setIdleConnectionTimeoutInMsForValidation(int idleConnectionTimeoutInMsForValidation) {
        this.idleConnectionTimeoutInMsForValidation = idleConnectionTimeoutInMsForValidation;
    }

    public int getRequestTimeoutInMsForValidation() {
        return requestTimeoutInMsForValidation;
    }

    public void setRequestTimeoutInMsForValidation(int requestTimeoutInMsForValidation) {
        this.requestTimeoutInMsForValidation = requestTimeoutInMsForValidation;
    }

    @Override
    public String getDisplayName() {
        return "CRX Content Package Deployer - HTTP Client";
    }

    public AbstractIdCredentialsListBoxModel doFillCredentialsIdItems(@AncestorInPath AccessControlled context,
                                                                      @QueryParameter("value") String value) {
        return GraniteCredentialsListBoxModel.fillItems(value, context);
    }

    public Credentials getDefaultCredentials() {
        if (this.credentialsId != null) {
            return GraniteNamedIdCredentials.getCredentialsById(this.credentialsId);
        } else {
            return null;
        }
    }

    public AsyncHttpClient getInstance() {
        return this.instance;
    }

    /**
     * This variation of {@link #getInstance()} returns a client which uses the validation-specific timeout settings.
     *
     * @return a client configured for validation.
     */
    public AsyncHttpClient getInstanceForValidation() {
        return this.instanceForValidation;
    }

    @SuppressWarnings("unchecked")
    private static Descriptor<GraniteAHCFactory> getFactoryDescriptor() {
        Jenkins j = Jenkins.getInstance();
        if (j==null) {
            throw new AssertionError(GraniteAHCFactory.class + " is missing its Jenkins");
        }
        return j.getDescriptorOrDie(GraniteAHCFactory.class);
    }

    public static GraniteAHCFactory getFactoryInstance() {
        if (Jenkins.getInstance() != null) {
            Descriptor descriptor = getFactoryDescriptor();
            if (descriptor instanceof GraniteAHCFactory) {
                return (GraniteAHCFactory) descriptor;
            }
        }

        return new GraniteAHCFactory(false);
    }

    public static ProxyServer getProxyServer() {
        ProxyServer proxyServer;
        Jenkins j = Jenkins.getInstance();
        if (j != null && j.proxy != null) {
            ProxyConfiguration proxy = j.proxy;
            proxyServer = new ProxyServer(proxy.name, proxy.port, proxy.getUserName(), proxy.getPassword());
        } else {
            proxyServer = null;
        }

        return proxyServer;
    }
}
