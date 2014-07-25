package org.jenkinsci.plugins.graniteclient;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import net.adamcin.commons.testing.junit.FailUtil;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
/**
 * Created by mark.j.adamcin on 7/24/14.
 */
public class GraniteClientConfigTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraniteClientConfigTest.class);

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testConstructor() {
        try {
            GraniteClientConfig config = new GraniteClientConfig("http://localhost:4502", "");
            assertEquals("simple base url should be unchanged:", "http://localhost:4502", config.getBaseUrl());

            GraniteClientConfig configWithCreds = new GraniteClientConfig("http://admin:admin@localhost:4502", "");
            assertEquals("base url should have userInfo removed:", "http://localhost:4502", configWithCreds.getBaseUrl());

            GraniteClientConfig configWithBase = new GraniteClientConfig("http://joeTester:Passw%ord@ 123$@localhost:4502/withBase", "");
            assertEquals("base url should have userInfo removed:", "http://localhost:4502/withBase", configWithBase.getBaseUrl());

            assertTrue("credentials should be instance of StandardUsernamePasswordCredentials",
                    configWithBase.getCredentials() instanceof StandardUsernamePasswordCredentials);

            StandardUsernamePasswordCredentials creds = (StandardUsernamePasswordCredentials) configWithBase.getCredentials();
            assertEquals("username should be", "joeTester", creds.getUsername());
            assertEquals("password should be", "Passw%ord@ 123$", creds.getPassword().getPlainText());


        } catch (Exception e) {
            LOGGER.error("Exception: ", e);
            FailUtil.sprintFail(e);
        }
    }

    @Test
    public void testSanitizeUrl() {
        Map<String, String> inputToSanitized = new HashMap<String, String>();

        inputToSanitized.put("", "");
        inputToSanitized.put("http://localhost", "http://localhost");
        inputToSanitized.put("https://www.google.com", "https://www.google.com");
        inputToSanitized.put("https://www.google.com/#lose-fragment", "https://www.google.com/#lose-fragment");
        inputToSanitized.put("http://@localhost", "http://@localhost");
        inputToSanitized.put("http://${PW}@localhost", "http://@localhost");
        inputToSanitized.put("http://user:${PW}@localhost:4502", "http://user:@localhost:4502");
        inputToSanitized.put("http://user:pass%perc@localhost:4502", "http://user:pass%25perc@localhost:4502");
        inputToSanitized.put("http://user:pass%2perc@localhost:4502", "http://user:pass%252perc@localhost:4502");
        inputToSanitized.put("http://user:pass%25perc@localhost:4502", "http://user:pass%25perc@localhost:4502");

        inputToSanitized.put("http://user:pass space@localhost", "http://user:pass%20space@localhost");
        inputToSanitized.put("http://user:pass!bang@localhost", "http://user:pass%21bang@localhost");
        inputToSanitized.put("http://user:pass#hash@localhost", "http://user:pass%23hash@localhost");
        inputToSanitized.put("http://user:pass:colon@localhost:4502", "http://user:pass%3Acolon@localhost:4502");
        inputToSanitized.put("http://user:pass@at@localhost", "http://user:pass%40at@localhost");

        for (Map.Entry<String, String> entry : inputToSanitized.entrySet()) {
            assertEquals("sanitized", entry.getValue(), GraniteClientConfig.sanitizeUrl(entry.getKey()));
        }
    }
}
