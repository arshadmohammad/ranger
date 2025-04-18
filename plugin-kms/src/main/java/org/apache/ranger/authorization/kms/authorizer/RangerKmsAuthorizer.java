/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ranger.authorization.kms.authorizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.crypto.key.kms.server.KMS.KMSOp;
import org.apache.hadoop.crypto.key.kms.server.KMSACLsType;
import org.apache.hadoop.crypto.key.kms.server.KMSACLsType.Type;
import org.apache.hadoop.crypto.key.kms.server.KMSConfiguration;
import org.apache.hadoop.crypto.key.kms.server.KMSWebApp;
import org.apache.hadoop.crypto.key.kms.server.KeyAuthorizationKeyProvider.KeyACLs;
import org.apache.hadoop.crypto.key.kms.server.KeyAuthorizationKeyProvider.KeyOpType;
import org.apache.hadoop.security.AccessControlException;
import org.apache.hadoop.security.SecureClientLogin;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.authorize.AccessControlList;
import org.apache.hadoop.security.authorize.AuthorizationException;
import org.apache.ranger.audit.provider.MiscUtil;
import org.apache.ranger.plugin.policyengine.RangerAccessResult;
import org.apache.ranger.plugin.util.RangerPerfTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RangerKmsAuthorizer implements Runnable, KeyACLs {
    private static final Logger LOG                      = LoggerFactory.getLogger(RangerKmsAuthorizer.class);
    private static final Logger PERF_KMSAUTH_REQUEST_LOG = RangerPerfTracer.getPerfLogger("kmsauth.request");

    public static final int    RELOADER_SLEEP_MILLIS        = 1000;
    public static final String ACCESS_TYPE_DECRYPT_EEK      = "decrypteek";
    public static final String ACCESS_TYPE_GENERATE_EEK     = "generateeek";
    public static final String ACCESS_TYPE_GET_METADATA     = "getmetadata";
    public static final String ACCESS_TYPE_GET_KEYS         = "getkeys";
    public static final String ACCESS_TYPE_GET              = "get";
    public static final String ACCESS_TYPE_SET_KEY_MATERIAL = "setkeymaterial";
    public static final String ACCESS_TYPE_ROLLOVER         = "rollover";
    public static final String ACCESS_TYPE_CREATE           = "create";
    public static final String ACCESS_TYPE_DELETE           = "delete";
    /**
     * Constant that identifies the authentication mechanism.
     */
    public static final String TYPE = "kerberos";
    /**
     * Constant for the configuration property that indicates the kerberos principal.
     */
    public static final String PRINCIPAL = TYPE + ".principal";
    /**
     * Constant for the configuration property that indicates the keytab file path.
     */
    public  static final String KEYTAB                       = TYPE + ".keytab";
    private static final String KMS_USER_PRINCIPAL           = "ranger.ks.kerberos.principal";
    private static final String KMS_USER_KEYTAB              = "ranger.ks.kerberos.keytab";
    private static final String KMS_NAME_RULES               = "hadoop.security.auth_to_local";
    private static final String UNAUTHORIZED_MSG_WITH_KEY    = "User:%s not allowed to do '%s' on '%s'";
    private static final String UNAUTHORIZED_MSG_WITHOUT_KEY = "User:%s not allowed to do '%s'";

    private static final    Map<KMSACLsType.Type, String> ACCESS_TYPE_MAP = new HashMap<>();
    private static volatile RangerKMSPlugin               kmsPlugin;

    private volatile Map<Type, AccessControlList> blacklistedAcls;

    private long                     lastReload;
    private ScheduledExecutorService executorService;

    RangerKmsAuthorizer(Configuration conf) {
        LOG.info("RangerKmsAuthorizer(conf)...");

        if (conf == null) {
            conf = loadACLs();
        }

        authWithKerberos(conf);
        setKMSACLs(conf);
        init(conf);
    }

    public RangerKmsAuthorizer() {
        this(null);
    }

    @Override
    public void run() {
        try {
            if (KMSConfiguration.isACLsFileNewer(lastReload)) {
                setKMSACLs(loadACLs());
            }
        } catch (Exception ex) {
            LOG.warn("Could not reload ACLs file: '{}'", ex, ex);
        }
    }

    public boolean hasAccess(Type type, UserGroupInformation ugi, String keyName, String clientIp) {
        LOG.debug("==> RangerKmsAuthorizer.hasAccess( {}, {}, {} )", type, ugi, keyName);

        RangerKMSPlugin   plugin           = kmsPlugin;
        String            rangerAccessType = getRangerAccessType(type);
        AccessControlList blacklist        = blacklistedAcls.get(type);
        boolean           ret              = (blacklist == null) || !blacklist.isUserInList(ugi);

        if (!ret) {
            LOG.debug("Operation {} blocked in the blacklist for user {}", rangerAccessType, ugi.getUserName());
        }

        if (plugin != null && ret) {
            RangerKMSAccessRequest request = new RangerKMSAccessRequest(keyName, rangerAccessType, ugi, clientIp);
            RangerAccessResult     result  = plugin.isAccessAllowed(request);

            ret = result != null && result.getIsAllowed();
        }

        LOG.debug("<== RangerkmsAuthorizer.hasAccess( {}, {}, {} ): {}", type, ugi, keyName, ret);

        return ret;
    }

    @Override
    public boolean hasAccessToKey(String keyName, UserGroupInformation ugi, KeyOpType opType) {
        LOG.debug("<== RangerKmsAuthorizer.hasAccessToKey({}, {}, {} )", keyName, ugi, opType);

        return true;
    }

    @Override
    public boolean isACLPresent(String keyName, KeyOpType opType) {
        return true;
    }

    @Override
    public synchronized void startReloader() {
        if (executorService == null) {
            executorService = Executors.newScheduledThreadPool(1);

            executorService.scheduleAtFixedRate(this, RELOADER_SLEEP_MILLIS, RELOADER_SLEEP_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public synchronized void stopReloader() {
        if (executorService != null) {
            executorService.shutdownNow();

            executorService = null;
        }
    }

    /**
     * First Check if user is in ACL for the KMS operation, if yes, then
     * return true if user is not present in any configured blacklist for
     * the operation
     *
     * @param type KMS Operation
     * @param ugi UserGroupInformation of user
     * @return true is user has access
     */
    @Override
    public boolean hasAccess(Type type, UserGroupInformation ugi, String clientIp) {
        LOG.debug("==> RangerKmsAuthorizer.hasAccess({}, {} )", type, ugi);

        RangerPerfTracer perf = null;

        if (RangerPerfTracer.isPerfTraceEnabled(PERF_KMSAUTH_REQUEST_LOG)) {
            perf = RangerPerfTracer.getPerfTracer(PERF_KMSAUTH_REQUEST_LOG, "RangerKmsAuthorizer.hasAccess(type=" + type + ")");
        }

        RangerKMSPlugin   plugin           = kmsPlugin;
        String            rangerAccessType = getRangerAccessType(type);
        AccessControlList blacklist        = blacklistedAcls.get(type);
        boolean           ret              = (blacklist == null) || !blacklist.isUserInList(ugi);

        if (!ret) {
            LOG.debug("Operation {} blocked in the blacklist for user {}", rangerAccessType, ugi.getUserName());
        }

        if (plugin != null && ret) {
            RangerKMSAccessRequest request = new RangerKMSAccessRequest("", rangerAccessType, ugi, clientIp);
            RangerAccessResult     result  = plugin.isAccessAllowed(request);

            ret = result != null && result.getIsAllowed();
        }

        RangerPerfTracer.log(perf);

        LOG.debug("<== RangerkmsAuthorizer.hasAccess({}, {} ): {}", type, ugi, ret);

        return ret;
    }

    @Override
    public void assertAccess(Type aclType, UserGroupInformation ugi, KMSOp operation, String key, String clientIp) throws AccessControlException {
        LOG.debug("==> RangerKmsAuthorizer.assertAccess({}, {}, {} )", key, ugi, aclType);

        key = (key == null) ? "" : key;

        if (!hasAccess(aclType, ugi, key, clientIp)) {
            KMSWebApp.getUnauthorizedCallsMeter().mark();
            KMSWebApp.getKMSAudit().unauthorized(ugi, operation, key);

            throw new AuthorizationException(String.format((!key.equals("")) ? UNAUTHORIZED_MSG_WITH_KEY : UNAUTHORIZED_MSG_WITHOUT_KEY, ugi.getShortUserName(), operation, key));
        }
    }

    public void init(Configuration conf) {
        LOG.debug("==> RangerKmsAuthorizer.init()");

        RangerKMSPlugin plugin = kmsPlugin;

        if (plugin == null) {
            synchronized (RangerKmsAuthorizer.class) {
                plugin = kmsPlugin;

                if (plugin == null) {
                    plugin = new RangerKMSPlugin();

                    plugin.init();

                    kmsPlugin = plugin;
                }
            }
        }

        LOG.debug("<== RangerkmsAuthorizer.init()");
    }

    private void authWithKerberos(Configuration conf) {
        String localHostName = null;

        try {
            localHostName = java.net.InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e1) {
            LOG.warn("Error getting local host name : {}", e1.getMessage());
        }

        String principal = null;

        try {
            principal = SecureClientLogin.getPrincipal(conf.get(KMS_USER_PRINCIPAL), localHostName);
        } catch (IOException e1) {
            LOG.warn("Error getting {} : {}", KMS_USER_PRINCIPAL, e1.getMessage());
        }

        String keytab    = conf.get(KMS_USER_KEYTAB);
        String nameRules = conf.get(KMS_NAME_RULES);

        LOG.debug("Ranger KMS Principal : {}, Keytab : {}, NameRule {}", principal, keytab, nameRules);

        MiscUtil.authWithKerberos(keytab, principal, nameRules);
    }

    private Configuration loadACLs() {
        LOG.debug("Loading ACLs file");

        lastReload = System.currentTimeMillis();

        Configuration conf = KMSConfiguration.getACLsConf();

        // triggering the resource loading.
        conf.get(Type.CREATE.getAclConfigKey());

        return conf;
    }

    private void setKMSACLs(Configuration conf) {
        Map<Type, AccessControlList> tempBlacklist = new HashMap<>();

        for (Type aclType : Type.values()) {
            String blacklistStr = conf.get(aclType.getBlacklistConfigKey());

            if (blacklistStr != null) {
                // Only add if blacklist is present
                tempBlacklist.put(aclType, new AccessControlList(blacklistStr));

                LOG.info("'{}' Blacklist '{}'", aclType, blacklistStr);
            }
        }

        blacklistedAcls = tempBlacklist;
    }

    private static String getRangerAccessType(KMSACLsType.Type accessType) {
        if (ACCESS_TYPE_MAP.containsKey(accessType)) {
            return ACCESS_TYPE_MAP.get(accessType);
        }

        return null;
    }

    static {
        ACCESS_TYPE_MAP.put(KMSACLsType.Type.CREATE, RangerKmsAuthorizer.ACCESS_TYPE_CREATE);
        ACCESS_TYPE_MAP.put(KMSACLsType.Type.DELETE, RangerKmsAuthorizer.ACCESS_TYPE_DELETE);
        ACCESS_TYPE_MAP.put(KMSACLsType.Type.ROLLOVER, RangerKmsAuthorizer.ACCESS_TYPE_ROLLOVER);
        ACCESS_TYPE_MAP.put(KMSACLsType.Type.GET, RangerKmsAuthorizer.ACCESS_TYPE_GET);
        ACCESS_TYPE_MAP.put(KMSACLsType.Type.GET_KEYS, RangerKmsAuthorizer.ACCESS_TYPE_GET_KEYS);
        ACCESS_TYPE_MAP.put(KMSACLsType.Type.GET_METADATA, RangerKmsAuthorizer.ACCESS_TYPE_GET_METADATA);
        ACCESS_TYPE_MAP.put(KMSACLsType.Type.SET_KEY_MATERIAL, RangerKmsAuthorizer.ACCESS_TYPE_SET_KEY_MATERIAL);
        ACCESS_TYPE_MAP.put(KMSACLsType.Type.GENERATE_EEK, RangerKmsAuthorizer.ACCESS_TYPE_GENERATE_EEK);
        ACCESS_TYPE_MAP.put(KMSACLsType.Type.DECRYPT_EEK, RangerKmsAuthorizer.ACCESS_TYPE_DECRYPT_EEK);
    }
}
