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
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.security.AccessControlled;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import net.adamcin.granite.client.packman.PackId;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Implementation of the "Replicate Content Packages from CRX" build step
 */
public class ReplicatePackagesBuilder extends AbstractBuildStep {
    private String packageIds;
    private String baseUrls;
    private String credentialsId;
    private long requestTimeout;
    private long serviceTimeout;
    private long waitDelay;
    private boolean ignoreErrors;

    @DataBoundConstructor
    public ReplicatePackagesBuilder(String packageIds, String baseUrls, String credentialsId,
                                   long requestTimeout, long serviceTimeout, long waitDelay,
                                   boolean ignoreErrors) {
        this.packageIds = packageIds;
        this.baseUrls = baseUrls;
        this.credentialsId = credentialsId;
        this.requestTimeout = requestTimeout;
        this.serviceTimeout = serviceTimeout;
        this.waitDelay = waitDelay;
        this.ignoreErrors = ignoreErrors;
    }

    @Override
    boolean perform(@Nonnull AbstractBuild<?, ?> build, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
                    @Nonnull BuildListener listener) throws InterruptedException, IOException {

        Result result = build.getResult();
        if (result == null) {
            result = Result.SUCCESS;
        }

		for (String baseUrl : listBaseUrls(build, listener)) {
			if (result.isBetterOrEqualTo(Result.UNSTABLE)) {
				GraniteClientConfig clientConfig = new GraniteClientConfig(
						baseUrl, credentialsId, requestTimeout, serviceTimeout, waitDelay);

				ReplicatePackagesClientCallable callable = new ReplicatePackagesClientCallable(
						listener, listPackIds(build, listener), ignoreErrors);

				try {
					result = result.combine(GraniteClientExecutor.execute(
							callable, clientConfig, listener));
				} catch (Exception e) {
					e.printStackTrace(listener.fatalError(
							"Failed to replicate packages.", e.getMessage()));
					if (ignoreErrors) {
						result = result.combine(Result.UNSTABLE);
					} else {
						result = result.combine(Result.FAILURE);
					}
				}
			}
		}
        
        return result.isBetterOrEqualTo(Result.UNSTABLE);
    }

    public String getPackageIds() {
        if (this.packageIds != null) {
            return this.packageIds.trim();
        } else {
            return "";
        }
    }

    public String getPackageIds(AbstractBuild<?, ?> build, TaskListener listener) throws IOException, InterruptedException {
        try {
            return TokenMacro.expandAll(build, listener, getPackageIds());
        } catch (MacroEvaluationException e) {
            listener.error("Failed to expand macros in Package ID: %s", getPackageIds());
            return getPackageIds();
        }
    }
    
    public String getBaseUrls() {
        //return baseUrls;
        if (baseUrls != null) {
            return baseUrls.trim();
        } else {
            return "";
        }
    }

    public void setBaseUrls(String baseUrls) {
        this.baseUrls = baseUrls;
    }
    
    private List<String> listBaseUrls(AbstractBuild<?, ?> build, TaskListener listener) {
        try {
            return parseBaseUrls(TokenMacro.expandAll(build, listener, getBaseUrls()));
        } catch (Exception e) {
            listener.error("failed to expand tokens in: %s%n", getBaseUrls());
        }
        return parseBaseUrls(getBaseUrls());
    }

    private static List<String> parseBaseUrls(String value) {
         List<String> _baseUrls = new ArrayList<String>();
        for (String url : value.split("(\\r)?\\n")) {
            if (url.trim().length() > 0) {
                _baseUrls.add(url);
            }
        }
        return Collections.unmodifiableList(_baseUrls);
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public List<PackId> listPackIds(AbstractBuild<?, ?> build, TaskListener listener) throws IOException, InterruptedException {
        List<PackId> packIds = new ArrayList<PackId>();

        for (String packageId : getPackageIds(build, listener).split("\\r?\\n")) {
            PackId packId = PackId.parsePid(packageId);
            if (packId != null) {
                packIds.add(packId);
            }
        }

        return Collections.unmodifiableList(packIds);
    }

    public long getRequestTimeout() {
        return requestTimeout;
    }

    public long getServiceTimeout() {
        return serviceTimeout;
    }

    public boolean isIgnoreErrors() {
        return ignoreErrors;
    }

    public void setPackageIds(String packageIds) {
        this.packageIds = packageIds;
    }

    public void setIgnoreErrors(boolean ignoreErrors) {
        this.ignoreErrors = ignoreErrors;
    }

    public void setRequestTimeout(long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public void setServiceTimeout(long serviceTimeout) {
        this.serviceTimeout = serviceTimeout;
    }

    public long getWaitDelay() {
        return waitDelay;
    }

    public void setWaitDelay(long waitDelay) {
        this.waitDelay = waitDelay;
    }

    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public AbstractIdCredentialsListBoxModel doFillCredentialsIdItems(@AncestorInPath AccessControlled context, @QueryParameter String baseUrls) {
            List<String> _baseUrls = parseBaseUrls(baseUrls);

            if (_baseUrls != null && !_baseUrls.isEmpty()) {
                return GraniteCredentialsListBoxModel.fillItems(context, _baseUrls.iterator().next());
            } else {
                return GraniteCredentialsListBoxModel.fillItems(context);
            }
        }

		public FormValidation doCheckBaseUrls(@QueryParameter String value,
				@QueryParameter String credentialsId,
				@QueryParameter long requestTimeout,
				@QueryParameter long serviceTimeout) {
			for (String baseUrl : parseBaseUrls(value)) {
				try {
                    GraniteClientConfig config =
                            new GraniteClientConfig(baseUrl, credentialsId, requestTimeout, serviceTimeout);
					if (!GraniteClientExecutor.validateBaseUrl(config)) {
                        return FormValidation.error("Failed to login to " + config.getBaseUrl() + " as " + config.getUsername());
					}
				} catch (IOException e) {
					return FormValidation.error(e.getCause(), e.getMessage());
				}
			}
			return FormValidation.ok();
		}

        @Override
        public String getDisplayName() {
            return "Replicate Content Packages from CRX";
        }
    }

}
