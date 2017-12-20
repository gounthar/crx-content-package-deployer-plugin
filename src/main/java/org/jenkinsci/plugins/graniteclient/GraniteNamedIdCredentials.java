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
import java.security.KeyPair;
import java.security.UnrecoverableKeyException;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.Secret;
import jenkins.bouncycastle.api.PEMEncodable;
import jenkins.model.Jenkins;
import net.adamcin.httpsig.api.DefaultKeychain;
import net.adamcin.httpsig.api.Key;
import net.adamcin.httpsig.api.KeyId;
import net.adamcin.httpsig.api.Keychain;
import net.adamcin.httpsig.ssh.jce.KeyFormat;
import net.adamcin.httpsig.ssh.jce.SSHKey;
import net.adamcin.httpsig.ssh.jce.UserKeysFingerprintKeyId;
import org.apache.commons.lang.StringUtils;

/**
 * Wrapper for {@link SSHUserPrivateKey} credentials implementing {@link IdCredentials} for selection widgets
 */
abstract class GraniteNamedIdCredentials implements IdCredentials {
    private static final Logger LOGGER = Logger.getLogger(GraniteNamedIdCredentials.class.getName());

    private static final long serialVersionUID = -7611025520557823267L;

    @CheckForNull
    public static Credentials getCredentialsById(String credentialsId) {
        if (sanityCheck()) {
            if (StringUtils.isNotBlank(credentialsId)) {
                CredentialsMatcher matcher = new CredentialsIdMatcher(credentialsId);
                List<Credentials> credentialsList =
                        DomainCredentials.getCredentials(
                                SystemCredentialsProvider.getInstance().getDomainCredentialsMap(),
                                Credentials.class, Collections.<DomainRequirement>emptyList(), matcher
                        );

                if (!credentialsList.isEmpty()) {
                    return credentialsList.iterator().next();
                }
            }
        }

        return null;
    }

    public static Credentials getCredentialsFromURIUserInfo(String userInfo, Credentials toOverride) {
        if (sanityCheck() && userInfo != null && !userInfo.isEmpty()) {
            if (userInfo.indexOf(':') >= 0) {
                String[] parts = userInfo.split(":", 2);
                return new URIUserInfoCredentials(parts[0], parts[1], null);
            } else if (toOverride instanceof SSHUserPrivateKey) {
                return new URIUserInfoCredentialsWithSSHKey(userInfo, (SSHUserPrivateKey) toOverride);
            } else if (toOverride instanceof StandardUsernamePasswordCredentials) {
                return new URIUserInfoCredentials(userInfo, null, (StandardUsernamePasswordCredentials) toOverride);
            } else {
                return new URIUserInfoCredentials(userInfo, null, null);
            }
        }
        return null;
    }

    private static boolean sanityCheck() {
        return Jenkins.getInstance() != null;
    }

    public CredentialsScope getScope() {
        return getWrappedCredentials().getScope();
    }

    protected abstract Credentials getWrappedCredentials();

    public abstract String getName();

    @NonNull
    public CredentialsDescriptor getDescriptor() {
        return getWrappedCredentials().getDescriptor();
    }

    static class SSHPrivateKeyNamedIdCredentials extends GraniteNamedIdCredentials {

        private static final long serialVersionUID = -8908675817671402062L;

        private final SSHUserPrivateKey wrapped;

        SSHPrivateKeyNamedIdCredentials(SSHUserPrivateKey wrapped) {
            this.wrapped = wrapped;
        }

        @NonNull
        public String getId() {
            return wrapped.getId();
        }

        public String getName() {
            Keychain keychain = getKeychainFromCredentials(wrapped);

            if (keychain.isEmpty()) {
                return "[Signature] <failed to read SSH key> " + getId();
            }

            KeyId keyId = new UserKeysFingerprintKeyId(wrapped.getUsername());
            StringBuilder nameBuilder = new StringBuilder("[Signature] ").append(keyId.getId(keychain.currentKey()));
            if (!wrapped.getDescription().trim().isEmpty()) {
                nameBuilder.append(" (").append(wrapped.getDescription()).append(")");
            }

            return nameBuilder.toString();
        }

        @Override
        protected Credentials getWrappedCredentials() {
            return wrapped;
        }
    }

    static class UserPassNamedIdCredentials extends GraniteNamedIdCredentials {

        private static final long serialVersionUID = -7566342113168803477L;

        private final StandardUsernamePasswordCredentials wrapped;

        UserPassNamedIdCredentials(StandardUsernamePasswordCredentials wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public String getName() {
            return CredentialsNameProvider.name(wrapped);
        }

        @Override
        protected Credentials getWrappedCredentials() {
            return wrapped;
        }

        @NonNull
        public String getId() {
            return wrapped.getId();
        }
    }

    static class URIUserInfoCredentials extends BaseStandardCredentials implements StandardUsernamePasswordCredentials {
        private String username;
        private Secret password;
        private StandardUsernamePasswordCredentials wrapped;

        URIUserInfoCredentials(String username, String password, StandardUsernamePasswordCredentials wrapped) {
            super("URIUserInfoCredentials_" + username, "");
            if (username == null) {
                throw new NullPointerException("username");
            }
            this.username = username;

            this.password = password == null || password.isEmpty() ? null : Secret.fromString(password);
            this.wrapped = wrapped;
        }

        @NonNull
        public String getUsername() {
            return username;
        }

        @NonNull
        public Secret getPassword() {
            if (this.password != null) {
                return this.password;
            }

            if (this.wrapped != null) {
                return this.wrapped.getPassword();
            }

            return Secret.fromString("admin");
        }
    }

    static class URIUserInfoCredentialsWithSSHKey extends BaseStandardCredentials implements SSHUserPrivateKey {
        private String username;
        private SSHUserPrivateKey wrapped;

        URIUserInfoCredentialsWithSSHKey(String username, SSHUserPrivateKey wrapped) {
            super("URIUserInfoCredentialsWithSSHKey_" + username, "");
            if (username == null) {
                throw new NullPointerException("username");
            }
            if (wrapped == null) {
                throw new NullPointerException("wrapped");
            }
            this.username = username;
            this.wrapped = wrapped;
        }

        @NonNull
        @Override
        public List<String> getPrivateKeys() {
            return wrapped.getPrivateKeys();
        }

        @NonNull
        public String getPrivateKey() {
            return wrapped.getPrivateKey();
        }

        public Secret getPassphrase() {
            return wrapped.getPassphrase();
        }

        @NonNull
        public String getUsername() {
            return this.username;
        }
    }

    @NonNull
    public static GraniteNamedIdCredentials wrap(final SSHUserPrivateKey creds) {
        return new SSHPrivateKeyNamedIdCredentials(creds);
    }

    @NonNull
    public static GraniteNamedIdCredentials wrap(final StandardUsernamePasswordCredentials creds) {
        return new UserPassNamedIdCredentials(creds);
    }

    @CheckForNull
    public static GraniteNamedIdCredentials maybeWrap(@CheckForNull final Credentials creds) {
        if (creds instanceof StandardUsernamePasswordCredentials) {
            return wrap((StandardUsernamePasswordCredentials) creds);
        } else if (creds instanceof SSHUserPrivateKey) {
            return wrap((SSHUserPrivateKey) creds);
        }
        return null;
    }

    private static class CredentialsIdMatcher implements CredentialsMatcher {
        final String credentialsId;

        private CredentialsIdMatcher(String credentialsId) {
            this.credentialsId = credentialsId;
        }

        public boolean matches(@NonNull Credentials item) {
            if (credentialsId != null && !credentialsId.isEmpty()) {
                if (item instanceof SSHUserPrivateKey) {
                    return credentialsId.equals(((SSHUserPrivateKey) item).getId());
                } else if (item instanceof IdCredentials) {
                    return credentialsId.equals(((IdCredentials) item).getId());
                }
            }
            return false;
        }
    }

    public static Keychain getKeychainFromCredentials(@CheckForNull SSHUserPrivateKey creds) {
        List<Key> keys = new ArrayList<>();
        if (creds == null) {
            return new DefaultKeychain(keys);
        }

        char[] passphrase = null;

        Secret cPPhrase = creds.getPassphrase();
        if (cPPhrase != null) {
            passphrase = cPPhrase.getEncryptedValue().toCharArray();
        }

        for (String pk : creds.getPrivateKeys()) {
            try {
                PEMEncodable enc = PEMEncodable.decode(pk, passphrase);
                KeyPair keyPair = enc.toKeyPair();

                if (keyPair != null) {
                    if (keyPair.getPrivate() instanceof RSAPrivateKey
                            || keyPair.getPublic() instanceof RSAPublicKey) {
                        keys.add(new SSHKey(KeyFormat.SSH_RSA, keyPair));
                    } else if (keyPair.getPrivate() instanceof DSAPrivateKey
                            || keyPair.getPublic() instanceof DSAPublicKey) {
                        keys.add(new SSHKey(KeyFormat.SSH_DSS, keyPair));
                    }
                }
            } catch (UnrecoverableKeyException e) {
                LOGGER.severe("[getKeyFromCredentials] failed to decode key from SSHUserPrivateKey: " + e.getMessage());
            } catch (IOException e) {
                LOGGER.severe("[getKeyFromCredentials] failed to read key from SSHUserPrivateKey: " + e.getMessage());
            }
        }

        return new DefaultKeychain(keys);
    }

    @CheckForNull
    public static Key getKeyFromCredentials(@CheckForNull SSHUserPrivateKey creds) {
        if (creds != null) {
            Keychain keys = getKeychainFromCredentials(creds);
            if (!keys.isEmpty()) {
                return keys.currentKey();
            }
        }

        return null;
    }
}
