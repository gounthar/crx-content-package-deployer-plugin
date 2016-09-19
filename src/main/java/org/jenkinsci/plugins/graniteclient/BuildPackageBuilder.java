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

import com.cloudbees.plugins.credentials.common.AbstractIdCredentialsListBoxModel;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.security.AccessControlled;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import net.adamcin.granite.client.packman.PackId;
import net.adamcin.granite.client.packman.WspFilter;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import javax.annotation.Nonnull;

/**
 * Implementation of the "Build a Content Package on CRX" build step
 */
public class BuildPackageBuilder extends AbstractBuildStep {
    private String packageId;
    private String baseUrl;
    private String credentialsId;
    private long requestTimeout;
    private long serviceTimeout;
    private long waitDelay;
    private String wspFilter;
    private String localDirectory;
    private boolean download;

    @DataBoundConstructor
    public BuildPackageBuilder(String packageId, String baseUrl, String credentialsId,
                               long requestTimeout, long serviceTimeout, long waitDelay,
                               String wspFilter, String localDirectory, boolean download) {
        this.packageId = packageId;
        this.baseUrl = baseUrl;
        this.credentialsId = credentialsId;
        this.requestTimeout = requestTimeout;
        this.serviceTimeout = serviceTimeout;
        this.waitDelay = waitDelay;
        this.wspFilter = wspFilter;
        this.localDirectory = localDirectory;
        this.download = download;
    }

    @Override
    boolean perform(@Nonnull AbstractBuild<?, ?> build, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
                    @Nonnull BuildListener listener) throws InterruptedException, IOException {

        Result result = Result.SUCCESS;
        Result buildResult = build.getResult();
        if (buildResult != null) {
            result = buildResult;
        }

        String packIdString = getPackageId(build, listener);
        PackId packId = PackId.parsePid(packIdString);
        if (packId == null) {
            listener.fatalError("Failed to parse Package ID: %s%n", packIdString);
            return false;
        }

        String wspFilterString = getWspFilter(build, listener);
        WspFilter filter = WspFilter.parseSimpleSpec(wspFilterString);

        GraniteClientConfig clientConfig = new GraniteClientConfig(
                getBaseUrl(build, listener), credentialsId, requestTimeout, serviceTimeout, waitDelay);

        BuildPackageCallable callable =
                new BuildPackageCallable(clientConfig, listener, packId, filter, download);

        final String fLocalDirectory = getLocalDirectory(build, listener);

        Result actResult = workspace.child(fLocalDirectory).act(callable);
        if (actResult != null) {
            result = result.combine(actResult);
        }

        return result.isBetterOrEqualTo(Result.UNSTABLE);
    }

    public String getPackageId() {
        if (this.packageId != null) {
            return this.packageId.trim();
        } else {
            return "";
        }
    }

    public String getPackageId(AbstractBuild<?, ?> build, TaskListener listener) throws IOException, InterruptedException {
        try {
            return TokenMacro.expandAll(build, listener, getPackageId());
        } catch (MacroEvaluationException e) {
            listener.error("Failed to expand macros in Package ID: %s", getPackageId());
            return getPackageId();
        }
    }

    private String getBaseUrl(AbstractBuild<?, ?> build, TaskListener listener) {
        try {
            return TokenMacro.expandAll(build, listener, getBaseUrl());
        } catch (Exception e) {
            listener.error("failed to expand tokens in: %s%n", getBaseUrl());
        }
        return getBaseUrl();
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    private String getLocalDirectory(AbstractBuild<?, ?> build, TaskListener listener) {
        try {
            return TokenMacro.expandAll(build, listener, getLocalDirectory());
        } catch (Exception e) {
            listener.error("failed to expand tokens in: %s%n", getLocalDirectory());
        }
        return getLocalDirectory();
    }

    private String getWspFilter(AbstractBuild<?, ?> build, TaskListener listener) {
        try {
            return TokenMacro.expandAll(build, listener, getWspFilter());
        } catch (Exception e) {
            listener.error("failed to expand tokens in: %s%n", getWspFilter());
        }
        return getWspFilter();
    }

    public String getBaseUrl() {
        if (this.baseUrl != null) {
            return this.baseUrl.trim();
        } else {
            return "";
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

    public void setWaitDelay(long waitDelay) {
        this.waitDelay = waitDelay;
    }

    public String getWspFilter() {
        return wspFilter;
    }

    public boolean isDownload() {
        return download;
    }

    public String getLocalDirectory() {
        if (localDirectory == null || localDirectory.trim().isEmpty()) {
            return ".";
        } else {
            return localDirectory;
        }
    }

    public void setLocalDirectory(String localDirectory) {
        this.localDirectory = localDirectory;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setWspFilter(String wspFilter) {
        this.wspFilter = wspFilter;
    }

    public void setDownload(boolean download) {
        this.download = download;
    }

    public void setRequestTimeout(long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public void setServiceTimeout(long serviceTimeout) {
        this.serviceTimeout = serviceTimeout;
    }

    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public AbstractIdCredentialsListBoxModel doFillCredentialsIdItems(@AncestorInPath AccessControlled context, @QueryParameter String baseUrl) {
            return GraniteCredentialsListBoxModel.fillItems(context, baseUrl);
        }

        public FormValidation doCheckBaseUrl(@QueryParameter String value, @QueryParameter String credentialsId,
                                             @QueryParameter long requestTimeout, @QueryParameter long serviceTimeout) {
            try {
                GraniteClientConfig config =
                        new GraniteClientConfig(value, credentialsId, requestTimeout, serviceTimeout);
                if (!GraniteClientExecutor.validateBaseUrl(config)) {
                    return FormValidation.error("Failed to login to " + config.getBaseUrl() + " as " + config.getUsername());
                }
                return FormValidation.ok();
            } catch (IOException e) {
                return FormValidation.error(e.getCause(), e.getMessage());
            }
        }

        public FormValidation doCheckWspFilter(@QueryParameter String value) {
            try {
                WspFilter.parseSimpleSpec(value);
                return FormValidation.ok();
            } catch (RuntimeException e) {
                return FormValidation.error(e.getMessage());
            }
        }

        @Override
        public String getDisplayName() {
            return "Build a Content Package on CRX";
        }
    }

}
