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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.servlet.ServletException;

import com.cloudbees.plugins.credentials.common.AbstractIdCredentialsListBoxModel;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.util.FormValidation;
import net.adamcin.granite.client.packman.ListResponse;
import net.adamcin.granite.client.packman.ListResult;
import net.adamcin.granite.client.packman.PackId;
import net.adamcin.granite.client.packman.PackIdFilter;
import net.adamcin.granite.client.packman.PackageManagerClient;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Implementation of the "CRX Content Package Choice Parameter" type
 */
public class PackageChoiceParameterDefinition extends ParameterDefinition {

    private static final long serialVersionUID = 2160382112619250912L;

    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {

        @Override
        public String getDisplayName() {
            return "CRX Content Package Choice Parameter";
        }

        public FormValidation doTestConnection(@QueryParameter("baseUrl") final String baseUrl,
                                               @QueryParameter("credentialsId") final String credentialsId,
                                               @QueryParameter("requestTimeout") final long requestTimeout,
                                               @QueryParameter("serviceTimeout") final long serviceTimeout)
                throws IOException, ServletException {

            return BaseUrlUtil.testOneConnection(baseUrl, credentialsId, requestTimeout, serviceTimeout);
        }

        public AbstractIdCredentialsListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context,
                                                                          @QueryParameter("baseUrl") String baseUrl,
                                                                          @QueryParameter("value") String value) {
            return GraniteCredentialsListBoxModel.fillItems(value, context, baseUrl);
        }

    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        Object value = jo.get("value");
        if (value instanceof String) {
            return constructValue((String) value);
        } else if (value instanceof JSONArray) {
            return constructValue((JSONArray) value);
        } else {
            return constructDefaultValue();
        }
    }

    @Override
    public ParameterValue createValue(StaplerRequest req) {
        String[] requestValues = req.getParameterValues(getName());
        if (requestValues == null || requestValues.length == 0) {
            return constructDefaultValue();
        } else {
            return constructValue(requestValues);
        }
    }

    protected PackageChoiceParameterValue constructDefaultValue() {
        return new PackageChoiceParameterValue(getName(), "");
    }

    protected PackageChoiceParameterValue constructValue(String value) {
        return new PackageChoiceParameterValue(getName(), value);
    }

    protected PackageChoiceParameterValue constructValue(JSONArray values) {
        return constructValue(values.iterator());
    }

    protected PackageChoiceParameterValue constructValue(Iterator values) {
        return new PackageChoiceParameterValue(getName(), StringUtils.join(values, "\n"));
    }

    protected PackageChoiceParameterValue constructValue(String[] values) {
        return new PackageChoiceParameterValue(getName(), StringUtils.join(values, "\n"));
    }

    private static class Execution implements PackageManagerClientCallable<ListResponse> {
        private final String query;

        public Execution(String query) {
            this.query = query;
        }

        @Override
        public ListResponse doExecute(PackageManagerClient client) throws Exception {
            return client.list(this.query);
        }
    }

    public List<PackId> getPackageList() {
        GraniteClientConfig config = getGraniteClientConfig();

        try {
            config.resolveCredentials();

            ListResponse response = GraniteClientExecutor.execute(new Execution(this.query), config);

            List<PackId> packIds = new ArrayList<PackId>();
            List<ListResult> results = response.getResults();
            if (results != null) {
                for (ListResult result : results) {
                    if ((!excludeNotInstalled || result.isHasSnapshot())
                            && (!excludeModified || !result.isNeedsRewrap())
                            && getPackIdFilter().includes(result.getPackId())) {
                        packIds.add(result.getPackId());
                    }
                }
            }
            return Collections.unmodifiableList(packIds);
        } catch (Exception e) {
            return getSelectedPackIds();
        }
    }

    public List<PackId> getSelectedPackIds() {
        List<PackId> packIds = new ArrayList<PackId>();

        String effectiveValue = getEffectiveValue();
        for (String pid : BaseUrlUtil.splitByNewline(effectiveValue)) {
            PackId packId = PackId.parsePid(pid);
            if (packId != null) {
                packIds.add(packId);
            }
        }

        return Collections.unmodifiableList(packIds);
    }

    private String baseUrl;
    private String credentialsId;
    private long requestTimeout;
    private long serviceTimeout;

    private boolean multiselect;
    private long visibleItemCount;
    private boolean excludeNotInstalled;
    private boolean excludeModified;
    private String query;
    private String packageIdFilter;
    private String value;

    @DataBoundConstructor
    public PackageChoiceParameterDefinition(String name, String description, String baseUrl, String credentialsId,
                                            long requestTimeout, long serviceTimeout, boolean multiselect,
                                            boolean excludeNotInstalled, boolean excludeModified, long visibleItemCount,
                                            String query, String packageIdFilter, String value) {
        super(name, description);
        this.baseUrl = baseUrl;
        this.credentialsId = credentialsId;
        this.requestTimeout = requestTimeout;
        this.serviceTimeout = serviceTimeout;
        this.multiselect = multiselect;
        this.visibleItemCount = visibleItemCount;
        this.excludeNotInstalled = excludeNotInstalled;
        this.excludeModified = excludeModified;
        this.query = query;
        this.packageIdFilter = packageIdFilter;
        this.value = value;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public long getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public long getServiceTimeout() {
        return serviceTimeout;
    }

    public void setServiceTimeout(long serviceTimeout) {
        this.serviceTimeout = serviceTimeout;
    }

    public boolean isMultiselect() {
        return multiselect;
    }

    public void setMultiselect(boolean multiselect) {
        this.multiselect = multiselect;
    }

    public long getVisibleItemCount() {
        if (visibleItemCount <= 0) {
            return 10;
        } else {
            return this.visibleItemCount;
        }
    }

    public void setVisibleItemCount(long visibleItemCount) {
        this.visibleItemCount = visibleItemCount;
    }

    public boolean isExcludeNotInstalled() {
        return excludeNotInstalled;
    }

    public void setExcludeNotInstalled(boolean excludeNotInstalled) {
        this.excludeNotInstalled = excludeNotInstalled;
    }

    public boolean isExcludeModified() {
        return excludeModified;
    }

    public void setExcludeModified(boolean excludeModified) {
        this.excludeModified = excludeModified;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getPackageIdFilter() {
        return packageIdFilter;
    }

    public void setPackageIdFilter(String packageIdFilter) {
        this.packageIdFilter = packageIdFilter;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getEffectiveValue() {
        if (this.value == null) {
            return "";
        } else {
            return this.value.trim();
        }
    }

    public PackIdFilter getPackIdFilter() {
        return PathOrPackIdFilter.parse(getPackageIdFilter());
    }

    public GraniteClientConfig getGraniteClientConfig() {
        return new GraniteClientConfig(GraniteAHCFactory.getGlobalConfig(), getBaseUrl(),
                getCredentialsId(), requestTimeout, serviceTimeout);

    }

}
