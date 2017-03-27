package org.jenkinsci.plugins.graniteclient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import hudson.util.FormValidation;

/**
 * Common methods for parsing, sanitizing, and testing Base URL input
 */
class BaseUrlUtil {

    static List<String> splitByNewline(String value) {
        List<String> _baseUrls = new ArrayList<String>();
        // first try to explode on actual newlines
        String[] parts = value.split("\\r?\\n");
        // if there is only 1 part, try splitting on an escaped newline character.
        if (parts.length <= 1) {
            parts = value.split("\\\\n");
        }

        for (String url : parts) {
            if (url.trim().length() > 0) {
                _baseUrls.add(url);
            }
        }
        return Collections.unmodifiableList(_baseUrls);
    }

    static FormValidation testOneConnection(final String baseUrl, String credentialsId, long requestTimeout, long serviceTimeout) {
        GraniteClientConfig config =
                new GraniteClientConfig(GraniteAHCFactory.getGlobalConfig(),
                        baseUrl, credentialsId, requestTimeout, serviceTimeout);

        config.resolveCredentials();

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
        for (String baseUrl : splitByNewline(baseUrls)) {
            GraniteClientConfig config =
                    new GraniteClientConfig(GraniteAHCFactory.getGlobalConfig(),
                            baseUrl, credentialsId, requestTimeout, serviceTimeout);

            config.resolveCredentials();

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
