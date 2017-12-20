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

import static org.jenkinsci.plugins.graniteclient.GraniteClientGlobalConfig.getPreemptLoginPatterns;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.AbstractIdCredentialsListBoxModel;
import com.ning.http.client.AsyncHttpClient;
import hudson.Extension;
import hudson.ProxyConfiguration;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.LogTaskListener;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Global extension and configurable factory for {@link AsyncHttpClient} instances
 */
@Extension
public final class GraniteAHCFactory extends Descriptor<GraniteAHCFactory>
        implements Describable<GraniteAHCFactory>, Serializable {

    private static final Logger LOGGER = Logger.getLogger(GraniteAHCFactory.class.getName());
    private static final TaskListener DEFAULT_LISTENER = new LogTaskListener(LOGGER, Level.INFO);

    private static final long serialVersionUID = 1329103722879551701L;
    private static final int DEFAULT_TIMEOUT = GraniteClientGlobalConfig.DEFAULT_TIMEOUT;

    private String credentialsId;
    private String preemptLoginForBaseUrls;
    private int connectionTimeoutInMs = DEFAULT_TIMEOUT;
    private int idleConnectionTimeoutInMs = DEFAULT_TIMEOUT;
    private int requestTimeoutInMs = DEFAULT_TIMEOUT;

    /**
     * The parent type is not responsible for loading
     */
    public GraniteAHCFactory() {
        super(GraniteAHCFactory.class);
        load();
    }

    // do not remove this!
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json.getJSONObject("GraniteAHCFactory"));
        save();
        return true;
    }

    @SuppressWarnings("unchecked")
    public Descriptor<GraniteAHCFactory> getDescriptor() {
        return getFactoryDescriptor();
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public String getPreemptLoginForBaseUrls() {
        return preemptLoginForBaseUrls;
    }

    public void setPreemptLoginForBaseUrls(String preemptLoginForBaseUrls) {
        this.preemptLoginForBaseUrls = preemptLoginForBaseUrls;
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

    @Override
    public String getDisplayName() {
        return "CRX Content Package Deployer - HTTP Client";
    }

    public AbstractIdCredentialsListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context,
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

    public FormValidation doCheckPreemptLoginForBaseUrls(@QueryParameter String value)
            throws IOException, ServletException {
        try {
            List<Pattern> patterns = getPreemptLoginPatterns(value);
            return FormValidation.ok();
        } catch (PatternSyntaxException e) {
            return FormValidation.error("Invalid regular expression: %n%s%n", e.getMessage());
        }
    }


    @SuppressWarnings("unchecked")
    private static Descriptor<GraniteAHCFactory> getFactoryDescriptor() {
        Jenkins j = Jenkins.getActiveInstance();
        return j.getDescriptorOrDie(GraniteAHCFactory.class);
    }

    public static GraniteAHCFactory getFactoryInstance() {
        return getFactoryInstance(Jenkins.getActiveInstance());
    }

    public static GraniteAHCFactory getFactoryInstance(@Nonnull Jenkins jenkins) {
        Descriptor descriptor = jenkins.getDescriptorOrDie(GraniteAHCFactory.class);
        if (descriptor instanceof GraniteAHCFactory) {
            GraniteAHCFactory factory = (GraniteAHCFactory) descriptor;
            factory.load();
            return factory;
        } else {
            throw new AssertionError("Incompatible descriptor: " + descriptor.getJsonSafeClassName());
        }
    }

    public GraniteClientGlobalConfig createGlobalConfig() {
        GraniteClientGlobalConfig globalConfig =
                new GraniteClientGlobalConfig(
                        this.getDefaultCredentials(),
                        this.getPreemptLoginForBaseUrls(),
                        this.getConnectionTimeoutInMs(),
                        this.getIdleConnectionTimeoutInMs(),
                        this.getRequestTimeoutInMs(),
                        getProxyConfig());
        return globalConfig;
    }

    public static GraniteClientGlobalConfig getGlobalConfig() {
        GraniteAHCFactory factory = getFactoryInstance();
        return factory.createGlobalConfig();
    }

    public static ProxyConfiguration getProxyConfig() {
        Jenkins j = Jenkins.getActiveInstance();
        if (j != null && j.proxy != null) {
            return j.proxy;
        }
        return null;
    }

}
