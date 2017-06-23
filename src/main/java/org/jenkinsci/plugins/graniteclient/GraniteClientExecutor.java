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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Response;
import hudson.model.TaskListener;
import hudson.util.LogTaskListener;
import net.adamcin.granite.client.packman.async.AsyncPackageManagerClient;
import net.adamcin.httpsig.api.KeyId;
import net.adamcin.httpsig.api.Keychain;
import net.adamcin.httpsig.api.Signer;
import net.adamcin.httpsig.http.ning.AsyncUtil;
import net.adamcin.httpsig.ssh.jce.UserKeysFingerprintKeyId;

/**
 * Executes {@link PackageManagerClientCallable} instances by injecting an {@link AsyncPackageManagerClient}
 * as the implementation for {@link net.adamcin.granite.client.packman.PackageManagerClient}
 */
public final class GraniteClientExecutor {

    private static final Logger LOGGER = Logger.getLogger(GraniteClientExecutor.class.getName());

    private static final TaskListener DEFAULT_LISTENER = new LogTaskListener(LOGGER, Level.INFO);

    private static final AsyncCompletionHandler<Boolean> LOGIN_HANDLER = new AsyncCompletionHandler<Boolean>() {
        @Override
        public Boolean onCompleted(Response response) throws Exception {
            return response.getStatusCode() == 405 || response.getStatusCode() == 200;
        }
    };

    public static <T> T execute(PackageManagerClientCallable<T> callable, GraniteClientConfig config) throws Exception {
        return execute(callable, config, null);
    }

    public static <T> T execute(PackageManagerClientCallable<T> callable, GraniteClientConfig config,
                                TaskListener _listener) throws Exception {
        final TaskListener listener = _listener != null ? _listener : DEFAULT_LISTENER;

        final GraniteClientGlobalConfig globalConfig = config.getGlobalConfig();
        final AsyncHttpClient ahcClient = new AsyncHttpClient(new AsyncHttpClientConfig.Builder().setProxyServer(globalConfig.getProxyServer())
                .setConnectTimeout(globalConfig.getConnectionTimeoutInMs())
                .setReadTimeout(config.getServiceTimeout() > 0 ? (int) config.getServiceTimeout() : globalConfig.getIdleConnectionTimeoutInMs())
                .setRequestTimeout(config.getRequestTimeout() > 0 ? (int) config.getRequestTimeout() : globalConfig.getRequestTimeoutInMs())
                .build()
        );
        
        try {
            AsyncPackageManagerClient client = new AsyncPackageManagerClient(ahcClient);

            boolean preemptLogin = isPreemptLogin(config, listener);

            client.setBaseUrl(config.getBaseUrl());
            client.setRequestTimeout(config.getRequestTimeout());
            client.setServiceTimeout(config.getServiceTimeout());
            client.setWaitDelay(config.getWaitDelay());

            if (doLogin(client, config.getCredentials(), preemptLogin, listener)) {
                return callable.doExecute(client);
            } else {
                throw new IOException("Failed to login to " + config.getBaseUrl());
            }
        } finally {
            ahcClient.close();
        }
    }

    private static boolean doLogin(AsyncPackageManagerClient client, Credentials credentials, boolean preemptLogin,
                                   final TaskListener listener) throws IOException {
        final Credentials _creds = credentials != null ? credentials :
                GraniteAHCFactory.getFactoryInstance().getDefaultCredentials();

        if (_creds instanceof SSHUserPrivateKey) {
            if (preemptLogin) {
                listener.getLogger()
                        .printf("[ALERT] Ignoring preemptive auth preference for HTTP Signature scheme.%n").flush();
            }
            return doLoginSignature(client, (SSHUserPrivateKey) _creds, listener);
        } else if (_creds instanceof UsernamePasswordCredentials) {
            String username = ((UsernamePasswordCredentials) _creds).getUsername();
            String password = ((UsernamePasswordCredentials) _creds).getPassword().getPlainText();
            if (preemptLogin) {
                client.preemptLogin(username, password);
                listener.getLogger()
                        .printf("[ALERT] Preemptive basic auth enabled for URL %s%n", client.getBaseUrl()).flush();
                try {
                    return client.waitForService();
                } catch (IOException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IOException("Unexpected exception during preemptive auth check.", e);
                }
            } else {
                return doLoginPOST(client, username, password, listener);
            }
        } else {
            if (preemptLogin) {
                client.preemptLogin("admin", "admin");
                listener.getLogger()
                        .printf("[ALERT] Preemptive basic auth enabled for URL %s%n", client.getBaseUrl()).flush();
                try {
                    return client.waitForService();
                } catch (IOException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IOException("Unexpected exception during preemptive auth check.", e);
                }
            } else {
                return doLoginPOST(client, "admin", "admin", listener);
            }
        }
    }

    private static boolean doLoginSignature(AsyncPackageManagerClient client, SSHUserPrivateKey key,
                                            final TaskListener listener) throws IOException {

        Keychain sshkeys = GraniteNamedIdCredentials.getKeychainFromCredentials(key);
        if (sshkeys.isEmpty()) {
            return false;
        }

        KeyId keyId = new UserKeysFingerprintKeyId(key.getUsername());
        Signer signer = new Signer(sshkeys, keyId);
        Future<Boolean> fResponse = AsyncUtil.login(
                client.getClient(),
                signer, client.getClient().prepareGet(
                        client.getBaseUrl() + "?sling:authRequestLogin=Signature&j_validate=true"
                ).build(), LOGIN_HANDLER);

        try {
            if (client.getServiceTimeout() > 0) {
                return fResponse.get(client.getServiceTimeout(), TimeUnit.MILLISECONDS);
            } else {
                return fResponse.get();
            }
        } catch (Exception e) {
            throw new IOException("Failed to login using HTTP Signature authentication.", e);
        }
    }

    private static boolean doLoginPOST(AsyncPackageManagerClient client, String username, String password,
                                       final TaskListener listener) throws IOException {
        return client.login(username, password);
    }

    public static boolean validateBaseUrl(final GraniteClientConfig config) throws IOException {
        final TaskListener listener = DEFAULT_LISTENER;
        final AsyncHttpClient asyncHttpClient = config.getGlobalConfig().getInstance();
        try {

            AsyncPackageManagerClient client = new AsyncPackageManagerClient(asyncHttpClient);

            client.setBaseUrl(config.getBaseUrl());
            client.setRequestTimeout(config.getRequestTimeout());
            client.setServiceTimeout(config.getServiceTimeout());
            client.setWaitDelay(config.getWaitDelay());

            return doLogin(client, config.getCredentials(),
                    isPreemptLogin(config, listener), listener);
        } finally {
            asyncHttpClient.close();
        }

    }

    static boolean isPreemptLogin(final GraniteClientConfig config, final TaskListener _listener) {
        final TaskListener listener = _listener != null ? _listener : DEFAULT_LISTENER;
        return config.getGlobalConfig().shouldPreemptLoginForBaseUrl(config.getBaseUrl(), listener);
    }

}
