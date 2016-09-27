package org.jenkinsci.plugins.graniteclient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import hudson.util.FormValidation;

/**
 * Created by madamcin on 9/27/16.
 */
class BaseUrlUtil {

    static List<String> parseBaseUrls(String value) {
        List<String> _baseUrls = new ArrayList<String>();
        for (String url : value.split("(\\r)?\\n")) {
            if (url.trim().length() > 0) {
                _baseUrls.add(url);
            }
        }
        return Collections.unmodifiableList(_baseUrls);
    }

    static FormValidation testOneConnection(final String baseUrl, String credentialsId, long requestTimeout, long serviceTimeout) {
        GraniteClientConfig config =
                new GraniteClientConfig(baseUrl, credentialsId, requestTimeout, serviceTimeout);
        try {
            if (!GraniteClientExecutor.validateBaseUrl(config)) {
                return FormValidation.error("Failed to login to " + config.getBaseUrl() + " as " + config.getUsername());
            }
        } catch (IOException e) {
            return FormValidation.error(e, "Failed to login to " + config.getBaseUrl() + " as " + config.getUsername());
        }

        return FormValidation.ok("Success");
    }

    static FormValidation testManyConnections(final String baseUrls, String credentialsId, long requestTimeout, long serviceTimeout) {
        for (String baseUrl : parseBaseUrls(baseUrls)) {
            GraniteClientConfig config =
                    new GraniteClientConfig(baseUrl, credentialsId, requestTimeout, serviceTimeout);
            try {
                if (!GraniteClientExecutor.validateBaseUrl(config)) {
                    return FormValidation.error("Failed to login to " + config.getBaseUrl() + " as " + config.getUsername());
                }
            } catch (IOException e) {
                return FormValidation.error(e, "Failed to login to " + config.getBaseUrl() + " as " + config.getUsername());
            }
        }

        return FormValidation.ok("Success");
    }
}
