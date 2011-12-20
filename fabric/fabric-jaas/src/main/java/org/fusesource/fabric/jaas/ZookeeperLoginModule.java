/**
 * Copyright (C) 2011, FuseSource Corp.  All rights reserved.
 * http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license a copy of which has been included with this distribution
 * in the license.txt file.
 */
package org.fusesource.fabric.jaas;

import org.apache.karaf.jaas.modules.AbstractKarafLoginModule;
import org.apache.karaf.jaas.modules.Encryption;
import org.apache.karaf.jaas.modules.RolePrincipal;
import org.apache.karaf.jaas.modules.encryption.EncryptionSupport;
import org.fusesource.fabric.internal.ZooKeeperUtils;
import org.linkedin.zookeeper.client.IZKClient;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleReference;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import javax.security.auth.callback.*;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import java.io.IOException;
import java.security.Principal;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

public class ZookeeperLoginModule extends AbstractKarafLoginModule implements LoginModule {
    public static final ThreadLocal<IZKClient> ZOOKEEPER_CONTEXT = new ThreadLocal<IZKClient>();
    private static final Logger LOG = LoggerFactory.getLogger(ZookeeperLoginModule.class);

    private boolean debug = false;
    private static Properties users = new Properties();

    EncryptionSupport encryptionSupport;

    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler, Map sharedState, Map options) {
        debug = "true".equalsIgnoreCase((String)options.get("debug"));
        IZKClient zookeeper = ZOOKEEPER_CONTEXT.get();
        if( zookeeper==null ) {
            // osgi env.
            BundleContext bundleContext = ((BundleReference) getClass().getClassLoader()).getBundle().getBundleContext();
            encryptionSupport = new EncryptionSupport(options);
            ServiceReference serviceReference = bundleContext.getServiceReference(IZKClient.class.getName());
            if (serviceReference != null) {
                try {
                    zookeeper = (IZKClient) bundleContext.getService(serviceReference);
                    users = ZooKeeperUtils.getProperties(zookeeper, ZookeeperBackingEngine.USERS_NODE);
                } catch (Exception e) {
                    LOG.warn("Failed fetching authentication data.", e);
                } finally {
                    if (serviceReference != null) {
                        bundleContext.ungetService(serviceReference);
                    }
                }
            }
        } else {
            // non-osgi env.
            try {
                users = ZooKeeperUtils.getProperties(zookeeper, ZookeeperBackingEngine.USERS_NODE);
            } catch (Exception e) {
                LOG.warn("Failed fetching authentication data.", e);
            }
        }
        if(encryptionSupport==null) {
            encryptionSupport = new BasicEncryptionSupport(options);
        }
        super.initialize(subject, callbackHandler, options);
    }

    @Override
    public boolean login() throws LoginException {
        Callback[] callbacks = new Callback[2];

        callbacks[0] = new NameCallback("Username: ");
        callbacks[1] = new PasswordCallback("Password: ", false);
        try {
            callbackHandler.handle(callbacks);
        } catch (IOException ioe) {
            throw new LoginException(ioe.getMessage());
        } catch (UnsupportedCallbackException uce) {
            throw new LoginException(uce.getMessage() + " not available to obtain information from user");
        }
        user = ((NameCallback)callbacks[0]).getName();
        char[] tmpPassword = ((PasswordCallback)callbacks[1]).getPassword();
        if (tmpPassword == null) {
            tmpPassword = new char[0];
        }
        if (user == null) {
            throw new FailedLoginException("user name is null");
        }
        String userInfos = users.getProperty(user);

        if (userInfos == null) {
            throw new FailedLoginException("User doesn't exist");
        }

        // the password is in the first position
        String[] infos = userInfos.split(",");
        String password = infos[0];

        if (!checkPassword(new String(tmpPassword), password)) {
            throw new FailedLoginException("Password does not match");
        }

        principals = new HashSet<Principal>();
        principals.add(new org.apache.karaf.jaas.modules.UserPrincipal(user));
        for (int i = 1; i < infos.length; i++) {
            principals.add(new RolePrincipal(infos[i]));
        }

        if (debug) {
            LOG.debug("Successfully logged in {}", user);
        }

        return true;
    }

    public boolean abort() throws LoginException {
        clear();
        if (debug) {
            LOG.debug("abort");
        }
        return true;
    }

    public boolean logout() throws LoginException {
        subject.getPrincipals().removeAll(principals);
        principals.clear();
        if (debug) {
            LOG.debug("logout");
        }
        return true;
    }


    public String getEncryptedPassword(String password) {
        Encryption encryption = encryptionSupport.getEncryption();
        String encryptionPrefix = encryptionSupport.getEncryptionPrefix();
        String encryptionSuffix = encryptionSupport.getEncryptionSuffix();

        if (encryption == null) {
            return password;
        } else {
            boolean prefix = encryptionPrefix == null || password.startsWith(encryptionPrefix);
            boolean suffix = encryptionSuffix == null || password.endsWith(encryptionSuffix);
            if (prefix && suffix) {
                return password;
            } else {
                String p = encryption.encryptPassword(password);
                if (encryptionPrefix != null) {
                    p = encryptionPrefix + p;
                }
                if (encryptionSuffix != null) {
                    p = p + encryptionSuffix;
                }
                return p;
            }
        }
    }

    public boolean checkPassword(String plain, String encrypted) {
        Encryption encryption = encryptionSupport.getEncryption();
        String encryptionPrefix = encryptionSupport.getEncryptionPrefix();
        String encryptionSuffix = encryptionSupport.getEncryptionSuffix();
        if (encryption == null) {
            return plain.equals(encrypted);
        } else {
            boolean prefix = encryptionPrefix == null || encrypted.startsWith(encryptionPrefix);
            boolean suffix = encryptionSuffix == null || encrypted.endsWith(encryptionSuffix);
            if (prefix && suffix) {
                encrypted = encrypted.substring(encryptionPrefix != null ? encryptionPrefix.length() : 0,
                        encrypted.length() - (encryptionSuffix != null ? encryptionSuffix.length() : 0));
                return encryption.checkPassword(plain, encrypted);
            } else {
                return plain.equals(encrypted);
            }
        }
    }

}