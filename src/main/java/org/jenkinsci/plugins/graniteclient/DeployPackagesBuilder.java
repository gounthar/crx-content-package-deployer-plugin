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

import static org.jenkinsci.plugins.graniteclient.BaseUrlUtil.splitByNewline;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import com.cloudbees.plugins.credentials.common.AbstractIdCredentialsListBoxModel;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.MasterToSlaveFileCallable;
import net.adamcin.granite.client.packman.ACHandling;
import net.adamcin.granite.client.packman.PackId;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Implementation of the "Deploy Content Packages to CRX" build step
 */
public class DeployPackagesBuilder extends AbstractBuildStep {

    private String packageIdFilters;
    private String baseUrls;
    private String credentialsId = null;
    private String localDirectory = null;
    private String behavior = null;
    private boolean recursive = false;
    private boolean replicate = false;
    private int autosave = 1024;
    private String acHandling = null;
    private boolean disableForJobTesting = false;
    private long requestTimeout = 0L;
    private long serviceTimeout = 0L;
    private long waitDelay = 0L;

    @DataBoundConstructor
    public DeployPackagesBuilder(@Nonnull String packageIdFilters, @Nonnull String baseUrls) {
        this.packageIdFilters = packageIdFilters;
        this.baseUrls = baseUrls;
    }

    public DeployPackagesBuilder(String packageIdFilters, String baseUrls, String credentialsId,
                                 String localDirectory, String behavior, boolean recursive, boolean replicate,
                                 int autosave, String acHandling, boolean disableForJobTesting, long requestTimeout,
                                 long serviceTimeout, long waitDelay) {
        this.packageIdFilters = packageIdFilters;
        this.baseUrls = baseUrls;
        this.credentialsId = credentialsId;
        this.localDirectory = localDirectory;
        this.behavior = behavior;
        this.recursive = recursive;
        this.replicate = replicate;
        this.autosave = autosave;
        this.acHandling = acHandling;
        this.disableForJobTesting = disableForJobTesting;
        this.requestTimeout = requestTimeout;
        this.serviceTimeout = serviceTimeout;
        this.waitDelay = waitDelay;
    }

    public String getPackageIdFilters() {
        //return packageIdFilters;
        if (packageIdFilters != null) {
            return packageIdFilters.trim();
        } else {
            return "";
        }
    }

    @DataBoundSetter
    public void setPackageIdFilters(@Nonnull String packageIdFilters) {
        this.packageIdFilters = packageIdFilters;
    }

    public String getBaseUrls() {
        //return baseUrls;
        if (baseUrls != null) {
            return baseUrls.trim();
        } else {
            return "";
        }
    }

    @DataBoundSetter
    public void setBaseUrls(String baseUrls) {
        this.baseUrls = baseUrls;
    }

    public String getCredentialsId() {
        return credentialsId == null ? "" : credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        if (StringUtils.isBlank(credentialsId)) {
            this.credentialsId = null;
        } else {
            this.credentialsId = credentialsId;
        }
    }

    public String getLocalDirectory() {
        if (StringUtils.isBlank(localDirectory)) {
            return ".";
        } else {
            return localDirectory.trim();
        }
    }

    @DataBoundSetter
    public void setLocalDirectory(String localDirectory) {
        this.localDirectory = localDirectory;
    }

    public String getBehavior() {
        return behavior;
    }

    @DataBoundSetter
    public void setBehavior(String behavior) {
        this.behavior = behavior;
    }

    public boolean isReplicate() {
        return replicate;
    }

    @DataBoundSetter
    public void setReplicate(boolean replicate) {
        this.replicate = replicate;
    }

    public boolean isRecursive() {
        return recursive;
    }

    @DataBoundSetter
    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public int getAutosave() {
        return autosave;
    }

    @DataBoundSetter
    public void setAutosave(int autosave) {
        this.autosave = autosave;
    }

    public String getAcHandling() {
        if (acHandling == null) {
            return DescriptorImpl.ACHANDLING_DEFER_VALUE;
        } else {
            return acHandling.toUpperCase();
        }
    }

    @DataBoundSetter
    public void setAcHandling(String acHandling) {
        if (DescriptorImpl.ACHANDLING_DEFER_VALUE.equals(acHandling)) {
            this.acHandling = null;
        } else {
            this.acHandling = acHandling;
        }
    }

    public boolean isDisableForJobTesting() {
        return disableForJobTesting;
    }

    @DataBoundSetter
    public void setDisableForJobTesting(boolean disableForJobTesting) {
        this.disableForJobTesting = disableForJobTesting;
    }

    public long getRequestTimeout() {
        return requestTimeout;
    }

    @DataBoundSetter
    public void setRequestTimeout(long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public long getServiceTimeout() {
        return serviceTimeout;
    }

    @DataBoundSetter
    public void setServiceTimeout(long serviceTimeout) {
        this.serviceTimeout = serviceTimeout;
    }

    public long getWaitDelay() {
        return waitDelay;
    }

    @DataBoundSetter
    public void setWaitDelay(long waitDelay) {
        this.waitDelay = waitDelay;
    }

    public PackageInstallOptions getPackageInstallOptions() {
        ACHandling _acHandling = null;
        if (getAcHandling() != null && !DescriptorImpl.ACHANDLING_DEFER_VALUE.equals(getAcHandling())) {
            if (ACHandling.IGNORE.name().equals(getAcHandling())) {
                _acHandling = ACHandling.IGNORE;
            } else if (ACHandling.MERGE.name().equals(getAcHandling())) {
                _acHandling = ACHandling.MERGE;
            } else if (ACHandling.MERGE_PRESERVE.name().equals(getAcHandling())) {
                _acHandling = ACHandling.MERGE_PRESERVE;
            } else if (ACHandling.OVERWRITE.name().equals(getAcHandling())) {
                _acHandling = ACHandling.OVERWRITE;
            } else if (ACHandling.CLEAR.name().equals(getAcHandling())) {
                _acHandling = ACHandling.CLEAR;
            }
        }

        return new PackageInstallOptions(isRecursive(), getAutosave(), _acHandling, isReplicate());
    }

    public ExistingPackageBehavior getExistingPackageBehavior() {
        ExistingPackageBehavior _behavior = ExistingPackageBehavior.IGNORE;
        if (getBehavior() != null) {
            if ("uninstall".equalsIgnoreCase(getBehavior())) {
                _behavior = ExistingPackageBehavior.UNINSTALL;
            } else if ("delete".equalsIgnoreCase(getBehavior())) {
                _behavior = ExistingPackageBehavior.DELETE;
            } else if ("overwrite".equalsIgnoreCase(getBehavior())) {
                _behavior = ExistingPackageBehavior.OVERWRITE;
            } else if ("skip".equalsIgnoreCase(getBehavior())) {
                _behavior = ExistingPackageBehavior.SKIP;
            }
        }
        return _behavior;
    }

    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
                        @Nonnull TaskListener listener) throws InterruptedException, IOException {

        Result result = build.getResult();
        if (result == null) {
            result = Result.SUCCESS;
        }

        if (disableForJobTesting) {
            listener.getLogger().println("DEBUG: *** package deployment disabled for testing ***");
        }

        for (String baseUrl : listBaseUrls(build, workspace, listener)) {
            if (result.isBetterOrEqualTo(Result.UNSTABLE)) {
                GraniteClientConfig clientConfig =
                        new GraniteClientConfig(GraniteAHCFactory.getGlobalConfig(),
                                baseUrl, credentialsId, requestTimeout, serviceTimeout, waitDelay);

                clientConfig.resolveCredentials();

                listener.getLogger().printf("Deploying packages to %s%n", clientConfig.getBaseUrl());
                for (Map.Entry<PackId, FilePath> selectedPackage : selectPackages(build, workspace, listener).entrySet()) {
                    if (!result.isBetterOrEqualTo(Result.UNSTABLE)) {
                        return;
                    }
                    FilePath.FileCallable<Result> callable = null;
                    if (disableForJobTesting) {
                        callable = new DebugPackageCallable(selectedPackage.getKey(), listener);
                    } else {

                        callable = new DeployPackageCallable(clientConfig, listener,
                                selectedPackage.getKey(), getPackageInstallOptions(), getExistingPackageBehavior());
                    }

                    Result actResult = selectedPackage.getValue().act(callable);
                    if (actResult != null) {
                        result = result.combine(actResult);
                    }
                    build.setResult(result);
                }
            }
        }
    }

    private Map<PackId, FilePath> selectPackages(@Nonnull final Run<?, ?> build,
                                                 @Nonnull final FilePath workspace,
                                                 @Nonnull final TaskListener listener)
            throws IOException, InterruptedException {
        Map<PackId, FilePath> found = new HashMap<PackId, FilePath>();

        final String fLocalDirectory = getLocalDirectory(build, workspace, listener);
        FilePath dir = workspace.child(fLocalDirectory);

        try {
            List<FilePath> listed = new ArrayList<FilePath>();
            //listed.addAll(build.getWorkspace().list());
            listed.addAll(Arrays.asList(dir.list("**/*.jar")));
            listed.addAll(Arrays.asList(dir.list("**/*.zip")));

            Collections.sort(
                    listed, Collections.reverseOrder(
                    new Comparator<FilePath>() {
                        public int compare(FilePath left, FilePath right) {
                            try {
                                return Long.compare(left.lastModified(), right.lastModified());
                            } catch (Exception e) {
                                listener.error("Failed to compare a couple files: %s", e.getMessage());
                            }
                            return 0;
                        }
                    }
            ));

            for (FilePath path : listed) {
                PackId packId = path.act(new IdentifyPackageCallable());

                if (packId != null && !found.containsKey(packId)) {
                    found.put(packId, path);
                }
            }
        } catch (Exception e) {
            listener.error("Failed to list package files: %s", e.getMessage());
        }

        Map<PackId, FilePath> selected = new LinkedHashMap<PackId, FilePath>();
        for (Map.Entry<String, PathOrPackIdFilter> filterEntry : listPackageFilters(build, workspace, listener).entrySet()) {
            boolean matched = false;
            for (Map.Entry<PackId, FilePath> entry : found.entrySet()) {
                if (filterEntry.getValue().includes(entry.getKey()) ||
                        filterEntry.getValue().includes(dir, entry.getValue())) {
                    matched = true;

                    if (!selected.containsKey(entry.getKey())) {
                        selected.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            if (!matched) {
                throw new IOException("No package found matching filter " + filterEntry.getKey());
            }
        }

        Map<String, List<PackId>> groupings = new HashMap<String, List<PackId>>();
        for (PackId packId : found.keySet()) {
            String groupKey = packId.getGroup() + ":" + packId.getName();
            if (!groupings.containsKey(groupKey)) {
                groupings.put(groupKey, new ArrayList<PackId>());
            }
            groupings.get(groupKey).add(packId);
        }

        Set<PackId> maxes = new HashSet<PackId>();
        for (List<PackId> grouping : groupings.values()) {
            Collections.sort(grouping, Collections.reverseOrder());
            maxes.add(grouping.get(0));
        }

        selected.keySet().retainAll(maxes);

        return Collections.unmodifiableMap(selected);
    }

    public String getPackageIdFilters(Run<?, ?> build, FilePath workspace, TaskListener listener) throws Exception {
        return TokenMacro.expandAll(build, workspace, listener, getPackageIdFilters());
    }

    private Map<String, PathOrPackIdFilter> listPackageFilters(Run<?, ?> build, FilePath workspace, TaskListener listener) {
        Map<String, PathOrPackIdFilter> filters = new LinkedHashMap<String, PathOrPackIdFilter>();
        try {
            for (String filter : splitByNewline(getPackageIdFilters(build, workspace, listener))) {
                if (filter.trim().length() > 0) {
                    filters.put(filter, PathOrPackIdFilter.parse(filter));
                }
            }
        } catch (Exception e) {
            listener.error("failed to expand tokens in: %n%s", getPackageIdFilters());
        }
        return Collections.unmodifiableMap(filters);
    }

    private List<String> listBaseUrls(Run<?, ?> build, FilePath workspace, TaskListener listener) {
        try {
            return splitByNewline(TokenMacro.expandAll(build, workspace, listener, getBaseUrls()));
        } catch (Exception e) {
            listener.error("failed to expand tokens in: %s%n", getBaseUrls());
        }
        return splitByNewline(getBaseUrls());
    }


    private String getLocalDirectory(Run<?, ?> build, FilePath workspace, TaskListener listener) {
        try {
            return TokenMacro.expandAll(build, workspace, listener, getLocalDirectory());
        } catch (Exception e) {
            listener.error("failed to expand tokens in: %s%n", getLocalDirectory());
        }
        return getLocalDirectory();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Symbol("crxDeploy")
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public static final String ACHANDLING_DEFER_VALUE = "_DEFER"; // must be upper case

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Deploy Content Packages to CRX";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            req.bindJSON(this, json.getJSONObject("graniteDeployPackages"));
            save();
            return true;
        }

        public AbstractIdCredentialsListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context,
                                                                          @QueryParameter("baseUrls") String baseUrls,
                                                                          @QueryParameter("value") String value) {
            List<String> _baseUrls = splitByNewline(baseUrls);

            if (!_baseUrls.isEmpty()) {
                return GraniteCredentialsListBoxModel.fillItems(value, context, _baseUrls.iterator().next());
            } else {
                return GraniteCredentialsListBoxModel.fillItems(value, context);
            }
        }

        public FormValidation doTestConnection(@QueryParameter("baseUrls") final String baseUrls,
                                               @QueryParameter("credentialsId") final String credentialsId,
                                               @QueryParameter("requestTimeout") final long requestTimeout,
                                               @QueryParameter("serviceTimeout") final long serviceTimeout)
                throws IOException, ServletException {

            return BaseUrlUtil.testManyConnections(baseUrls, credentialsId, requestTimeout, serviceTimeout);
        }

        public ListBoxModel doFillAcHandlingItems() {
            ListBoxModel model = new ListBoxModel();
            model.add("Defer to Package", ACHANDLING_DEFER_VALUE);
            for (ACHandling mode : Arrays.asList(
                    ACHandling.IGNORE,
                    ACHandling.MERGE_PRESERVE,
                    ACHandling.MERGE,
                    ACHandling.OVERWRITE,
                    ACHandling.CLEAR)
                    ) {
                model.add(mode.getLabel(), mode.name());
            }
            return model;
        }

        public ListBoxModel doFillBehaviorItems() {
            ListBoxModel model = new ListBoxModel();
            model.add("Overwrite existing", "Overwrite");
            model.add("Uninstall and delete", "Uninstall");
            model.add("Skip package", "Skip");
            model.add("Delete package", "Delete");
            model.add("Ignore", "Ignore");
            return model;
        }

    }

    static class DebugPackageCallable extends MasterToSlaveFileCallable<Result> {
        final PackId packId;
        final TaskListener listener;

        DebugPackageCallable(PackId packId, TaskListener listener) {
            this.packId = packId;
            this.listener = listener;
        }

        public Result invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            listener.getLogger().printf("DEBUG: %s identified as %s.%n", f.getPath(), packId.toString());
            return Result.SUCCESS;
        }
    }

}
