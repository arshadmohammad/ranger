/*

 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ranger.rest;

import com.sun.jersey.core.header.FormDataContentDisposition;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.ranger.admin.client.datatype.RESTResponse;
import org.apache.ranger.biz.AssetMgr;
import org.apache.ranger.biz.RangerBizUtil;
import org.apache.ranger.biz.RangerPolicyAdmin;
import org.apache.ranger.biz.SecurityZoneDBStore;
import org.apache.ranger.biz.ServiceDBStore;
import org.apache.ranger.biz.ServiceDBStore.JSON_FILE_NAME_TYPE;
import org.apache.ranger.biz.ServiceMgr;
import org.apache.ranger.biz.TagDBStore;
import org.apache.ranger.biz.XUserMgr;
import org.apache.ranger.common.ContextUtil;
import org.apache.ranger.common.RESTErrorUtil;
import org.apache.ranger.common.RangerConstants;
import org.apache.ranger.common.RangerSearchUtil;
import org.apache.ranger.common.RangerValidatorFactory;
import org.apache.ranger.common.ServiceUtil;
import org.apache.ranger.common.StringUtil;
import org.apache.ranger.common.UserSessionBase;
import org.apache.ranger.common.db.RangerTransactionSynchronizationAdapter;
import org.apache.ranger.db.RangerDaoManager;
import org.apache.ranger.db.XXPolicyDao;
import org.apache.ranger.db.XXSecurityZoneDao;
import org.apache.ranger.db.XXSecurityZoneRefServiceDao;
import org.apache.ranger.db.XXSecurityZoneRefTagServiceDao;
import org.apache.ranger.db.XXServiceDao;
import org.apache.ranger.db.XXServiceDefDao;
import org.apache.ranger.entity.XXPolicy;
import org.apache.ranger.entity.XXPortalUser;
import org.apache.ranger.entity.XXRole;
import org.apache.ranger.entity.XXSecurityZone;
import org.apache.ranger.entity.XXSecurityZoneRefService;
import org.apache.ranger.entity.XXSecurityZoneRefTagService;
import org.apache.ranger.entity.XXService;
import org.apache.ranger.entity.XXServiceDef;
import org.apache.ranger.plugin.model.RangerPluginInfo;
import org.apache.ranger.plugin.model.RangerPolicy;
import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyItem;
import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyItemAccess;
import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyItemCondition;
import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyResource;
import org.apache.ranger.plugin.model.RangerService;
import org.apache.ranger.plugin.model.RangerServiceDef;
import org.apache.ranger.plugin.model.RangerServiceDef.RangerAccessTypeDef;
import org.apache.ranger.plugin.model.RangerServiceDef.RangerContextEnricherDef;
import org.apache.ranger.plugin.model.RangerServiceDef.RangerEnumDef;
import org.apache.ranger.plugin.model.RangerServiceDef.RangerPolicyConditionDef;
import org.apache.ranger.plugin.model.RangerServiceDef.RangerResourceDef;
import org.apache.ranger.plugin.model.RangerServiceDef.RangerServiceConfigDef;
import org.apache.ranger.plugin.model.ServiceDeleteResponse;
import org.apache.ranger.plugin.model.validation.RangerPolicyValidator;
import org.apache.ranger.plugin.model.validation.RangerServiceDefValidator;
import org.apache.ranger.plugin.model.validation.RangerServiceValidator;
import org.apache.ranger.plugin.policyengine.RangerAccessResource;
import org.apache.ranger.plugin.policyengine.RangerAccessResourceImpl;
import org.apache.ranger.plugin.policyengine.RangerPolicyEngineImpl;
import org.apache.ranger.plugin.service.ResourceLookupContext;
import org.apache.ranger.plugin.store.EmbeddedServiceDefsUtil;
import org.apache.ranger.plugin.store.PList;
import org.apache.ranger.plugin.util.GrantRevokeRequest;
import org.apache.ranger.plugin.util.RangerPluginCapability;
import org.apache.ranger.plugin.util.SearchFilter;
import org.apache.ranger.plugin.util.ServicePolicies;
import org.apache.ranger.security.context.RangerContextHolder;
import org.apache.ranger.security.context.RangerSecurityContext;
import org.apache.ranger.service.RangerAuditFields;
import org.apache.ranger.service.RangerDataHistService;
import org.apache.ranger.service.RangerPluginInfoService;
import org.apache.ranger.service.RangerPolicyLabelsService;
import org.apache.ranger.service.RangerPolicyService;
import org.apache.ranger.service.RangerServiceDefService;
import org.apache.ranger.service.RangerServiceService;
import org.apache.ranger.service.XUserService;
import org.apache.ranger.view.RangerExportPolicyList;
import org.apache.ranger.view.RangerPluginInfoList;
import org.apache.ranger.view.RangerPolicyList;
import org.apache.ranger.view.RangerServiceDefList;
import org.apache.ranger.view.RangerServiceList;
import org.apache.ranger.view.VXGroup;
import org.apache.ranger.view.VXResponse;
import org.apache.ranger.view.VXString;
import org.apache.ranger.view.VXUser;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;

@RunWith(MockitoJUnitRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestServiceREST {
    private static final Long   Id  = 8L;

    private final String grantor    = "test-grantor-1";
    private final String ownerUser  = "test-owner-user-1";
    private final String zoneName   = "test-zone-1";
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    String importPoliceTestFilePath = "./src/test/java/org/apache/ranger/rest/importPolicy/import_policy_test_file.json";
    @InjectMocks
    ServiceREST serviceREST = new ServiceREST();
    @Mock
    RangerValidatorFactory validatorFactory;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    RangerDaoManager daoManager;
    @Mock
    ServiceDBStore svcStore;
    @Mock
    SecurityZoneDBStore zoneStore;
    @Mock
    TagDBStore tagStore;
    @Mock
    RangerServiceService svcService;
    @Mock
    RangerDataHistService dataHistService;
    @Mock
    RangerExportPolicyList rangerExportPolicyList;
    @Mock
    RangerServiceDefService serviceDefService;
    @Mock
    RangerPolicyService policyService;
    @Mock
    StringUtil stringUtil;
    @Mock
    XUserService xUserService;
    @Mock
    XUserMgr xUserMgr;
    @Mock
    XUserMgr userMgr;
    @Mock
    RangerAuditFields rangerAuditFields;
    @Mock
    ContextUtil contextUtil;
    @Mock
    RangerBizUtil bizUtil;
    @Mock
    RESTErrorUtil restErrorUtil;
    @Mock
    RangerServiceDefValidator serviceDefValidator;
    @Mock
    RangerServiceValidator serviceValidator;
    @Mock
    RangerPolicyValidator policyValidator;
    @Mock
    ServiceMgr serviceMgr;
    @Mock
    VXResponse vXResponse;
    @Mock
    ServiceUtil serviceUtil;
    @Mock
    RangerSearchUtil searchUtil;
    @Mock
    StringUtils stringUtils;
    @Mock
    AssetMgr assetMgr;
    @Mock
    RangerPolicyLabelsService policyLabelsService;
    @Mock
    RangerPluginInfoService pluginInfoService;
    @Mock
    XXServiceDao xServiceDao;
    @Mock
    RangerPolicyEngineImpl rpImpl;
    @Mock
    RangerPolicyAdmin policyAdmin;
    @Mock
    RangerTransactionSynchronizationAdapter rangerTransactionSynchronizationAdapter;
    private String capabilityVector;

    public void setup() {
        RangerSecurityContext context = new RangerSecurityContext();
        context.setUserSession(new UserSessionBase());
        RangerContextHolder.setSecurityContext(context);
        UserSessionBase currentUserSession = ContextUtil.getCurrentUserSession();
        currentUserSession.setXXPortalUser(new XXPortalUser());
        currentUserSession.setUserAdmin(true);
        capabilityVector = Long.toHexString(new RangerPluginCapability().getPluginCapabilities());
    }

    public RangerServiceDef rangerServiceDef() {
        List<RangerServiceConfigDef>   configs          = new ArrayList<>();
        List<RangerResourceDef>        resources        = new ArrayList<>();
        List<RangerAccessTypeDef>      accessTypes      = new ArrayList<>();
        List<RangerPolicyConditionDef> policyConditions = new ArrayList<>();
        List<RangerContextEnricherDef> contextEnrichers = new ArrayList<>();
        List<RangerEnumDef>            enums            = new ArrayList<>();

        RangerServiceDef rangerServiceDef = new RangerServiceDef();
        rangerServiceDef.setId(Id);
        rangerServiceDef.setImplClass("RangerServiceHdfs");
        rangerServiceDef.setLabel("HDFS Repository");
        rangerServiceDef.setDescription("HDFS Repository");
        rangerServiceDef.setRbKeyDescription(null);
        rangerServiceDef.setUpdatedBy("Admin");
        rangerServiceDef.setUpdateTime(new Date());
        rangerServiceDef.setConfigs(configs);
        rangerServiceDef.setResources(resources);
        rangerServiceDef.setAccessTypes(accessTypes);
        rangerServiceDef.setPolicyConditions(policyConditions);
        rangerServiceDef.setContextEnrichers(contextEnrichers);
        rangerServiceDef.setEnums(enums);

        return rangerServiceDef;
    }

    public RangerService rangerService() {
        Map<String, String> configs = new HashMap<>();
        configs.put("username", "servicemgr");
        configs.put("password", "servicemgr");
        configs.put("namenode", "servicemgr");
        configs.put("hadoop.security.authorization", "No");
        configs.put("hadoop.security.authentication", "Simple");
        configs.put("hadoop.security.auth_to_local", "");
        configs.put("dfs.datanode.kerberos.principal", "");
        configs.put("dfs.namenode.kerberos.principal", "");
        configs.put("dfs.secondary.namenode.kerberos.principal", "");
        configs.put("hadoop.rpc.protection", "Privacy");
        configs.put("commonNameForCertificate", "");

        RangerService rangerService = new RangerService();
        rangerService.setId(Id);
        rangerService.setConfigs(configs);
        rangerService.setCreateTime(new Date());
        rangerService.setDescription("service policy");
        rangerService.setGuid("1427365526516_835_0");
        rangerService.setIsEnabled(true);
        rangerService.setName("HDFS_1");
        rangerService.setDisplayName("HDFS_1");
        rangerService.setPolicyUpdateTime(new Date());
        rangerService.setType("1");
        rangerService.setUpdatedBy("Admin");
        rangerService.setUpdateTime(new Date());

        return rangerService;
    }

    public XXServiceDef serviceDef() {
        XXServiceDef xServiceDef = new XXServiceDef();
        xServiceDef.setAddedByUserId(Id);
        xServiceDef.setCreateTime(new Date());
        xServiceDef.setDescription("HDFS Repository");
        xServiceDef.setGuid("1427365526516_835_0");
        xServiceDef.setId(Id);
        xServiceDef.setUpdateTime(new Date());
        xServiceDef.setUpdatedByUserId(Id);
        xServiceDef.setImplclassname("RangerServiceHdfs");
        xServiceDef.setLabel("HDFS Repository");
        xServiceDef.setRbkeylabel(null);
        xServiceDef.setRbkeydescription(null);
        xServiceDef.setIsEnabled(true);

        return xServiceDef;
    }

    public XXService xService() {
        XXService xService = new XXService();
        xService.setAddedByUserId(Id);
        xService.setCreateTime(new Date());
        xService.setDescription("Hdfs service");
        xService.setGuid("serviceguid");
        xService.setId(Id);
        xService.setIsEnabled(true);
        xService.setName("Hdfs");
        xService.setPolicyUpdateTime(new Date());
        xService.setPolicyVersion(1L);
        xService.setType(1L);
        xService.setUpdatedByUserId(Id);
        xService.setUpdateTime(new Date());

        return xService;
    }

    public ServicePolicies servicePolicies() {
        ServicePolicies sp = new ServicePolicies();
        sp.setAuditMode("auditMode");
        RangerPolicy       rangerPolicy = rangerPolicy();
        List<RangerPolicy> rpolList     = new ArrayList<>();
        rpolList.add(rangerPolicy);
        sp.setPolicies(rpolList);
        sp.setPolicyVersion(1L);
        sp.setServiceName("serviceName");
        sp.setServiceId(1L);
        return sp;
    }

    @Test
    public void test1createServiceDef() throws Exception {
        RangerServiceDef rangerServiceDef = rangerServiceDef();

        Mockito.when(validatorFactory.getServiceDefValidator(svcStore)).thenReturn(serviceDefValidator);
        Mockito.when(svcStore.createServiceDef(Mockito.any())).thenReturn(rangerServiceDef);

        RangerServiceDef dbRangerServiceDef = serviceREST.createServiceDef(rangerServiceDef);
        Assert.assertNotNull(dbRangerServiceDef);
        Assert.assertEquals(dbRangerServiceDef, rangerServiceDef);
        Assert.assertEquals(dbRangerServiceDef.getId(), rangerServiceDef.getId());
        Assert.assertEquals(dbRangerServiceDef.getName(), rangerServiceDef.getName());
        Assert.assertEquals(dbRangerServiceDef.getImplClass(), rangerServiceDef.getImplClass());
        Assert.assertEquals(dbRangerServiceDef.getLabel(), rangerServiceDef.getLabel());
        Assert.assertEquals(dbRangerServiceDef.getDescription(), rangerServiceDef.getDescription());
        Assert.assertEquals(dbRangerServiceDef.getRbKeyDescription(), rangerServiceDef.getRbKeyDescription());
        Assert.assertEquals(dbRangerServiceDef.getUpdatedBy(), rangerServiceDef.getUpdatedBy());
        Assert.assertEquals(dbRangerServiceDef.getUpdateTime(), rangerServiceDef.getUpdateTime());
        Assert.assertEquals(dbRangerServiceDef.getVersion(), rangerServiceDef.getVersion());
        Assert.assertEquals(dbRangerServiceDef.getConfigs(), rangerServiceDef.getConfigs());

        Mockito.verify(validatorFactory).getServiceDefValidator(svcStore);
        Mockito.verify(svcStore).createServiceDef(rangerServiceDef);
    }

    @Test
    public void test2updateServiceDef() throws Exception {
        RangerServiceDef rangerServiceDef = rangerServiceDef();

        Mockito.when(validatorFactory.getServiceDefValidator(svcStore)).thenReturn(serviceDefValidator);
        Mockito.when(svcStore.updateServiceDef(Mockito.any())).thenReturn(rangerServiceDef);

        RangerServiceDef dbRangerServiceDef = serviceREST.updateServiceDef(rangerServiceDef, rangerServiceDef.getId());
        Assert.assertNotNull(dbRangerServiceDef);
        Assert.assertEquals(dbRangerServiceDef, rangerServiceDef);
        Assert.assertEquals(dbRangerServiceDef.getId(), rangerServiceDef.getId());
        Assert.assertEquals(dbRangerServiceDef.getName(), rangerServiceDef.getName());
        Assert.assertEquals(dbRangerServiceDef.getImplClass(), rangerServiceDef.getImplClass());
        Assert.assertEquals(dbRangerServiceDef.getLabel(), rangerServiceDef.getLabel());
        Assert.assertEquals(dbRangerServiceDef.getDescription(), rangerServiceDef.getDescription());
        Assert.assertEquals(dbRangerServiceDef.getRbKeyDescription(), rangerServiceDef.getRbKeyDescription());
        Assert.assertEquals(dbRangerServiceDef.getUpdatedBy(), rangerServiceDef.getUpdatedBy());
        Assert.assertEquals(dbRangerServiceDef.getUpdateTime(), rangerServiceDef.getUpdateTime());
        Assert.assertEquals(dbRangerServiceDef.getVersion(), rangerServiceDef.getVersion());
        Assert.assertEquals(dbRangerServiceDef.getConfigs(), rangerServiceDef.getConfigs());

        Mockito.verify(validatorFactory).getServiceDefValidator(svcStore);
        Mockito.verify(svcStore).updateServiceDef(rangerServiceDef);
    }

    @Test
    public void test3deleteServiceDef() {
        HttpServletRequest request          = Mockito.mock(HttpServletRequest.class);
        RangerServiceDef   rangerServiceDef = rangerServiceDef();
        XXServiceDef       xServiceDef      = serviceDef();
        XXServiceDefDao    xServiceDefDao   = Mockito.mock(XXServiceDefDao.class);
        Mockito.when(validatorFactory.getServiceDefValidator(svcStore))
                .thenReturn(serviceDefValidator);
        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(xServiceDefDao.getById(Id)).thenReturn(xServiceDef);

        serviceREST.deleteServiceDef(rangerServiceDef.getId(), request);
        Mockito.verify(validatorFactory).getServiceDefValidator(svcStore);
        Mockito.verify(daoManager).getXXServiceDef();
    }

    @Test
    public void test4getServiceDefById() throws Exception {
        RangerServiceDef rangerServiceDef = rangerServiceDef();
        XXServiceDef     xServiceDef      = serviceDef();
        XXServiceDefDao  xServiceDefDao   = Mockito.mock(XXServiceDefDao.class);

        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(xServiceDefDao.getById(Id)).thenReturn(xServiceDef);
        Mockito.when(!bizUtil.hasAccess(xServiceDef, null)).thenReturn(true);
        Mockito.when(svcStore.getServiceDef(rangerServiceDef.getId())).thenReturn(rangerServiceDef);
        RangerServiceDef dbRangerServiceDef = serviceREST.getServiceDef(rangerServiceDef.getId());
        Assert.assertNotNull(dbRangerServiceDef);
        Assert.assertEquals(dbRangerServiceDef.getId(), rangerServiceDef.getId());
        Mockito.verify(svcStore).getServiceDef(rangerServiceDef.getId());
        Mockito.verify(daoManager).getXXServiceDef();
        Mockito.verify(bizUtil).hasAccess(xServiceDef, null);
    }

    @Test
    public void test5getServiceDefByName() throws Exception {
        RangerServiceDef rangerServiceDef = rangerServiceDef();
        XXServiceDef     xServiceDef      = serviceDef();
        XXServiceDefDao  xServiceDefDao   = Mockito.mock(XXServiceDefDao.class);

        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(xServiceDefDao.findByName(xServiceDef.getName())).thenReturn(xServiceDef);
        Mockito.when(!bizUtil.hasAccess(xServiceDef, null)).thenReturn(true);
        Mockito.when(svcStore.getServiceDefByName(rangerServiceDef.getName())).thenReturn(rangerServiceDef);
        RangerServiceDef dbRangerServiceDef = serviceREST.getServiceDefByName(rangerServiceDef.getName());
        Assert.assertNotNull(dbRangerServiceDef);
        Assert.assertEquals(dbRangerServiceDef.getName(), rangerServiceDef.getName());
        Mockito.verify(svcStore).getServiceDefByName(rangerServiceDef.getName());
        Mockito.verify(daoManager).getXXServiceDef();
    }

    @Test
    public void test6createService() throws Exception {
        RangerService   rangerService  = rangerService();
        XXServiceDef    xServiceDef    = serviceDef();
        XXServiceDefDao xServiceDefDao = Mockito.mock(XXServiceDefDao.class);
        Mockito.when(validatorFactory.getServiceValidator(svcStore)).thenReturn(serviceValidator);

        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(xServiceDefDao.findByName(rangerService.getType())).thenReturn(xServiceDef);

        Mockito.when(svcStore.createService(Mockito.any())).thenReturn(rangerService);

        RangerService dbRangerService = serviceREST.createService(rangerService);
        Assert.assertNotNull(dbRangerService);
        Assert.assertEquals(rangerService, dbRangerService);
        Assert.assertEquals(rangerService.getId(), dbRangerService.getId());
        Assert.assertEquals(rangerService.getConfigs(), dbRangerService.getConfigs());
        Assert.assertEquals(rangerService.getDescription(), dbRangerService.getDescription());
        Assert.assertEquals(rangerService.getGuid(), dbRangerService.getGuid());
        Assert.assertEquals(rangerService.getName(), dbRangerService.getName());
        Assert.assertEquals(rangerService.getPolicyVersion(), dbRangerService.getPolicyVersion());
        Assert.assertEquals(rangerService.getType(), dbRangerService.getType());
        Assert.assertEquals(rangerService.getVersion(), dbRangerService.getVersion());
        Assert.assertEquals(rangerService.getCreateTime(), dbRangerService.getCreateTime());
        Assert.assertEquals(rangerService.getUpdateTime(), dbRangerService.getUpdateTime());
        Assert.assertEquals(rangerService.getUpdatedBy(), dbRangerService.getUpdatedBy());

        Mockito.verify(validatorFactory).getServiceValidator(svcStore);
        Mockito.verify(svcStore).createService(rangerService);
    }

    @Test
    public void test7getServiceDefs() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        SearchFilter       filter  = new SearchFilter();
        filter.setParam(SearchFilter.POLICY_NAME, "policyName");
        filter.setParam(SearchFilter.SERVICE_NAME, "serviceName");
        Mockito.when(searchUtil.getSearchFilter(request, serviceDefService.sortFields)).thenReturn(filter);

        List<RangerServiceDef> serviceDefsList = new ArrayList<>();
        RangerServiceDef       serviceDef      = rangerServiceDef();
        serviceDefsList.add(serviceDef);
        PList<RangerServiceDef> serviceDefList = new PList<>();
        serviceDefList.setPageSize(0);
        serviceDefList.setResultSize(1);
        serviceDefList.setSortBy("asc");
        serviceDefList.setSortType("1");
        serviceDefList.setStartIndex(0);
        serviceDefList.setTotalCount(10);
        serviceDefList.setList(serviceDefsList);
        Mockito.when(bizUtil.hasModuleAccess(RangerConstants.MODULE_RESOURCE_BASED_POLICIES)).thenReturn(true);
        Mockito.when(svcStore.getPaginatedServiceDefs(filter)).thenReturn(serviceDefList);
        RangerServiceDefList dbRangerServiceDef = serviceREST.getServiceDefs(request);
        Assert.assertNotNull(dbRangerServiceDef);
        Mockito.verify(searchUtil).getSearchFilter(request, serviceDefService.sortFields);
        Mockito.verify(svcStore).getPaginatedServiceDefs(filter);
    }

    @Test
    public void test8updateServiceDef() throws Exception {
        RangerService       rangerService = rangerService();
        XXServiceDef        xServiceDef   = serviceDef();
        HttpServletRequest  request       = null;
        Map<String, Object> options       = null;

        XXServiceDefDao xServiceDefDao = Mockito.mock(XXServiceDefDao.class);
        Mockito.when(validatorFactory.getServiceValidator(svcStore)).thenReturn(serviceValidator);

        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(xServiceDefDao.findByName(rangerService.getType())).thenReturn(xServiceDef);

        Mockito.when(svcStore.updateService(Mockito.any(), Mockito.any())).thenReturn(rangerService);

        RangerService dbRangerService = serviceREST.updateService(rangerService, request);
        Assert.assertNotNull(dbRangerService);
        Assert.assertNotNull(dbRangerService);
        Assert.assertEquals(rangerService, dbRangerService);
        Assert.assertEquals(rangerService.getId(), dbRangerService.getId());
        Assert.assertEquals(rangerService.getConfigs(), dbRangerService.getConfigs());
        Assert.assertEquals(rangerService.getDescription(), dbRangerService.getDescription());
        Assert.assertEquals(rangerService.getGuid(), dbRangerService.getGuid());
        Assert.assertEquals(rangerService.getName(), dbRangerService.getName());
        Assert.assertEquals(rangerService.getPolicyVersion(), dbRangerService.getPolicyVersion());
        Assert.assertEquals(rangerService.getType(), dbRangerService.getType());
        Assert.assertEquals(rangerService.getVersion(), dbRangerService.getVersion());
        Assert.assertEquals(rangerService.getCreateTime(), dbRangerService.getCreateTime());
        Assert.assertEquals(rangerService.getUpdateTime(), dbRangerService.getUpdateTime());
        Assert.assertEquals(rangerService.getUpdatedBy(), dbRangerService.getUpdatedBy());
        Mockito.verify(validatorFactory).getServiceValidator(svcStore);
        Mockito.verify(daoManager).getXXServiceDef();
        Mockito.verify(svcStore).updateService(rangerService, options);
    }

    @Test
    public void test9deleteService() {
        RangerService   rangerService  = rangerService();
        XXServiceDef    xServiceDef    = serviceDef();
        XXService       xService       = xService();
        XXServiceDefDao xServiceDefDao = Mockito.mock(XXServiceDefDao.class);
        XXServiceDao    xServiceDao    = Mockito.mock(XXServiceDao.class);
        Mockito.when(validatorFactory.getServiceValidator(svcStore)).thenReturn(serviceValidator);

        Mockito.when(daoManager.getXXService()).thenReturn(xServiceDao);
        Mockito.when(xServiceDao.getById(Id)).thenReturn(xService);
        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(xServiceDefDao.getById(xService.getType())).thenReturn(xServiceDef);

        serviceREST.deleteService(rangerService.getId());

        Mockito.verify(validatorFactory).getServiceValidator(svcStore);
        Mockito.verify(daoManager).getXXService();
        Mockito.verify(daoManager).getXXServiceDef();
    }

    @Test
    public void test10getServiceById() throws Exception {
        RangerService rangerService = rangerService();
        Mockito.when(svcStore.getService(rangerService.getId())).thenReturn(rangerService);
        RangerService dbRangerService = serviceREST.getService(rangerService.getId());
        Assert.assertNotNull(dbRangerService);
        Mockito.verify(svcStore).getService(dbRangerService.getId());
    }

    @Test
    public void test11getServiceByName() throws Exception {
        RangerService rangerService = rangerService();
        Mockito.when(svcStore.getServiceByName(rangerService.getName())).thenReturn(rangerService);
        RangerService dbRangerService = serviceREST.getServiceByName(rangerService.getName());
        Assert.assertNotNull(dbRangerService);
        Mockito.verify(svcStore).getServiceByName(dbRangerService.getName());
    }

    @Test
    public void test12deleteServiceDef() {
        RangerServiceDef rangerServiceDef = rangerServiceDef();
        XXServiceDef     xServiceDef      = serviceDef();
        XXServiceDefDao  xServiceDefDao   = Mockito.mock(XXServiceDefDao.class);
        Mockito.when(validatorFactory.getServiceDefValidator(svcStore))
                .thenReturn(serviceDefValidator);

        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(xServiceDefDao.getById(Id)).thenReturn(xServiceDef);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        serviceREST.deleteServiceDef(rangerServiceDef.getId(), request);
        Mockito.verify(validatorFactory).getServiceDefValidator(svcStore);
        Mockito.verify(daoManager).getXXServiceDef();
    }

    @Test
    public void test13lookupResource() {
        String                serviceName = "HDFS_1";
        ResourceLookupContext context     = new ResourceLookupContext();
        context.setResourceName(serviceName);
        context.setUserInput("HDFS");
        List<String> list = serviceREST.lookupResource(serviceName, context);
        Assert.assertNotNull(list);
    }

    @Test
    public void test14grantAccess() throws Exception {
        HttpServletRequest request         = Mockito.mock(HttpServletRequest.class);
        String             serviceName     = "HDFS_1";
        GrantRevokeRequest grantRequestObj = new GrantRevokeRequest();
        grantRequestObj.setAccessTypes(null);
        grantRequestObj.setDelegateAdmin(true);
        grantRequestObj.setEnableAudit(true);
        grantRequestObj.setGrantor("read");
        grantRequestObj.setIsRecursive(true);

        Mockito.when(serviceUtil.isValidateHttpsAuthentication(serviceName, request)).thenReturn(false);
        RESTResponse restResponse = serviceREST.grantAccess(serviceName, grantRequestObj, request);
        Assert.assertNotNull(restResponse);
        Mockito.verify(serviceUtil).isValidateHttpsAuthentication(serviceName, request);
    }

    @Test
    public void test14_1_grantAccessWithMultiColumns() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        String      serviceName = "HIVE";
        Set<String> userList    = new HashSet<>();
        userList.add("user1");
        userList.add("user2");
        userList.add("user3");

        Map<String, String> grantResource = new HashMap<>();
        grantResource.put("database", "demo");
        grantResource.put("table", "testtbl");
        grantResource.put("column", "column1,column2,colum3");
        GrantRevokeRequest grantRequestObj = new GrantRevokeRequest();

        grantRequestObj.setResource(grantResource);
        grantRequestObj.setUsers(userList);
        grantRequestObj.setAccessTypes(new HashSet<>(Collections.singletonList("select")));
        grantRequestObj.setDelegateAdmin(true);
        grantRequestObj.setEnableAudit(true);
        grantRequestObj.setGrantor("systest");
        grantRequestObj.setIsRecursive(true);

        RangerAccessResource resource = new RangerAccessResourceImpl(ServiceREST.getAccessResourceObjectMap(grantRequestObj.getResource()), "systest");

        RangerPolicy createPolicy = new RangerPolicy();
        createPolicy.setService(serviceName);
        createPolicy.setName("grant-" + System.currentTimeMillis());
        createPolicy.setDescription("created by grant");
        createPolicy.setIsAuditEnabled(grantRequestObj.getEnableAudit());

        Map<String, RangerPolicyResource> policyResources = new HashMap<>();
        Set<String>                       resourceNames   = resource.getKeys();

        if (!CollectionUtils.isEmpty(resourceNames)) {
            for (String resourceName : resourceNames) {
                policyResources.put(resourceName, serviceREST.getPolicyResource(resource.getValue(resourceName), grantRequestObj));
            }
        }
        createPolicy.setResources(policyResources);

        RangerPolicyItem policyItem = new RangerPolicyItem();
        policyItem.setDelegateAdmin(grantRequestObj.getDelegateAdmin());
        policyItem.addUsers(grantRequestObj.getUsers());
        for (String accessType : grantRequestObj.getAccessTypes()) {
            policyItem.addAccess(new RangerPolicyItemAccess(accessType, Boolean.TRUE));
        }
        createPolicy.addPolicyItem(policyItem);
        createPolicy.setZoneName(null);

        List<String>                      grantColumns         = (List<String>) resource.getValue("column");
        Map<String, RangerPolicyResource> policyResourceMap    = createPolicy.getResources();
        List<String>                      createdPolicyColumns = policyResourceMap.get("column").getValues();

        Assert.assertTrue(createdPolicyColumns.containsAll(grantColumns));

        Mockito.when(serviceUtil.isValidateHttpsAuthentication(serviceName, request)).thenReturn(false);
        RESTResponse restResponse = serviceREST.grantAccess(serviceName, grantRequestObj, request);
        Assert.assertNotNull(restResponse);
        Mockito.verify(serviceUtil).isValidateHttpsAuthentication(serviceName, request);
    }

    @Test
    public void test15revokeAccess() throws Exception {
        HttpServletRequest request     = Mockito.mock(HttpServletRequest.class);
        String             serviceName = "HDFS_1";
        Set<String>        userList    = new HashSet<>();
        userList.add("user1");
        userList.add("user2");
        userList.add("user3");
        Set<String> groupList = new HashSet<>();
        groupList.add("group1");
        groupList.add("group2");
        groupList.add("group3");
        GrantRevokeRequest revokeRequest = new GrantRevokeRequest();
        revokeRequest.setDelegateAdmin(true);
        revokeRequest.setEnableAudit(true);
        revokeRequest.setGrantor("read");
        revokeRequest.setGroups(groupList);
        revokeRequest.setUsers(userList);

        RESTResponse restResponse = serviceREST.revokeAccess(serviceName, revokeRequest, request);
        Assert.assertNotNull(restResponse);
    }

    @Test
    public void test16createPolicyFalse() throws Exception {
        RangerPolicy     rangerPolicy     = rangerPolicy();
        RangerServiceDef rangerServiceDef = rangerServiceDef();

        List<RangerPolicy> policies   = new ArrayList<>();
        RangerPolicy       rangPolicy = new RangerPolicy();
        policies.add(rangPolicy);

        String      userName       = "admin";
        Set<String> userGroupsList = new HashSet<>();
        userGroupsList.add("group1");
        userGroupsList.add("group2");

        ServicePolicies servicePolicies = new ServicePolicies();
        servicePolicies.setServiceId(Id);
        servicePolicies.setServiceName("Hdfs_1");
        servicePolicies.setPolicyVersion(1L);
        servicePolicies.setPolicyUpdateTime(new Date());
        servicePolicies.setServiceDef(rangerServiceDef);
        servicePolicies.setPolicies(policies);

        List<RangerAccessTypeDef> rangerAccessTypeDefList = new ArrayList<>();
        RangerAccessTypeDef       rangerAccessTypeDefObj  = new RangerAccessTypeDef();
        rangerAccessTypeDefObj.setLabel("Read");
        rangerAccessTypeDefObj.setName("read");
        rangerAccessTypeDefObj.setRbKeyLabel(null);
        rangerAccessTypeDefList.add(rangerAccessTypeDefObj);
        XXServiceDef    xServiceDef    = serviceDef();
        XXService       xService       = xService();
        XXServiceDefDao xServiceDefDao = Mockito.mock(XXServiceDefDao.class);
        XXServiceDao    xServiceDao    = Mockito.mock(XXServiceDao.class);

        Mockito.when(validatorFactory.getPolicyValidator(svcStore)).thenReturn(policyValidator);
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        Mockito.when(bizUtil.getCurrentUserLoginId()).thenReturn(userName);
        Mockito.when(daoManager.getXXService()).thenReturn(xServiceDao);
        Mockito.when(xServiceDao.findByName(Mockito.anyString())).thenReturn(xService);
        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(xServiceDefDao.getById(xService.getType())).thenReturn(xServiceDef);
        Mockito.when(svcStore.createPolicy(Mockito.any())).thenReturn(rangPolicy);

        RangerPolicy dbRangerPolicy = serviceREST.createPolicy(rangerPolicy, null);
        Assert.assertNotNull(dbRangerPolicy);
        Mockito.verify(bizUtil, Mockito.times(2)).isAdmin();
        Mockito.verify(validatorFactory).getPolicyValidator(svcStore);

        Mockito.verify(daoManager).getXXService();
        Mockito.verify(daoManager, Mockito.atLeastOnce()).getXXServiceDef();
    }

    @Test
    public void test17updatePolicyFalse() {
        RangerPolicy rangerPolicy = rangerPolicy();
        String       userName     = "admin";

        Set<String> userGroupsList = new HashSet<>();
        userGroupsList.add("group1");
        userGroupsList.add("group2");

        List<RangerAccessTypeDef> rangerAccessTypeDefList = new ArrayList<>();
        RangerAccessTypeDef       rangerAccessTypeDefObj  = new RangerAccessTypeDef();
        rangerAccessTypeDefObj.setLabel("Read");
        rangerAccessTypeDefObj.setName("read");
        rangerAccessTypeDefObj.setRbKeyLabel(null);
        rangerAccessTypeDefList.add(rangerAccessTypeDefObj);
        XXServiceDef    xServiceDef    = serviceDef();
        XXService       xService       = xService();
        XXServiceDefDao xServiceDefDao = Mockito.mock(XXServiceDefDao.class);
        XXServiceDao    xServiceDao    = Mockito.mock(XXServiceDao.class);

        Mockito.when(validatorFactory.getPolicyValidator(svcStore)).thenReturn(
                policyValidator);
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        Mockito.when(bizUtil.getCurrentUserLoginId()).thenReturn(userName);
        Mockito.when(daoManager.getXXService()).thenReturn(xServiceDao);
        Mockito.when(xServiceDao.findByName(Mockito.anyString())).thenReturn(xService);
        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(xServiceDefDao.getById(xService.getType())).thenReturn(xServiceDef);
        RangerPolicy dbRangerPolicy = serviceREST.updatePolicy(rangerPolicy, Id);
        Assert.assertNull(dbRangerPolicy);
        Mockito.verify(validatorFactory).getPolicyValidator(svcStore);
    }

    @Test
    public void test18deletePolicyFalse() throws Exception {
        RangerPolicy rangerPolicy = rangerPolicy();

        Mockito.when(validatorFactory.getPolicyValidator(svcStore)).thenReturn(policyValidator);
        String userName = "admin";

        Set<String> userGroupsList = new HashSet<>();
        userGroupsList.add("group1");
        userGroupsList.add("group2");

        List<RangerAccessTypeDef> rangerAccessTypeDefList = new ArrayList<>();
        RangerAccessTypeDef       rangerAccessTypeDefObj  = new RangerAccessTypeDef();
        rangerAccessTypeDefObj.setLabel("Read");
        rangerAccessTypeDefObj.setName("read");
        rangerAccessTypeDefObj.setRbKeyLabel(null);
        rangerAccessTypeDefList.add(rangerAccessTypeDefObj);
        XXServiceDef    xServiceDef    = serviceDef();
        XXService       xService       = xService();
        XXServiceDefDao xServiceDefDao = Mockito.mock(XXServiceDefDao.class);
        XXServiceDao    xServiceDao    = Mockito.mock(XXServiceDao.class);
        Mockito.when(validatorFactory.getPolicyValidator(svcStore)).thenReturn(policyValidator);
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        Mockito.when(bizUtil.getCurrentUserLoginId()).thenReturn(userName);
        Mockito.when(svcStore.getPolicy(Id)).thenReturn(rangerPolicy);
        Mockito.when(daoManager.getXXService()).thenReturn(xServiceDao);
        Mockito.when(xServiceDao.findByName(Mockito.anyString())).thenReturn(xService);
        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(xServiceDefDao.getById(xService.getType())).thenReturn(xServiceDef);
        serviceREST.deletePolicy(rangerPolicy.getId());
        Mockito.verify(validatorFactory).getPolicyValidator(svcStore);
    }

    @Test
    public void test19getPolicyFalse() throws Exception {
        RangerPolicy rangerPolicy = rangerPolicy();
        Mockito.when(svcStore.getPolicy(rangerPolicy.getId())).thenReturn(rangerPolicy);
        String userName = "admin";

        Set<String> userGroupsList = new HashSet<>();
        userGroupsList.add("group1");
        userGroupsList.add("group2");

        List<RangerAccessTypeDef> rangerAccessTypeDefList = new ArrayList<>();
        RangerAccessTypeDef       rangerAccessTypeDefObj  = new RangerAccessTypeDef();
        rangerAccessTypeDefObj.setLabel("Read");
        rangerAccessTypeDefObj.setName("read");
        rangerAccessTypeDefObj.setRbKeyLabel(null);
        rangerAccessTypeDefList.add(rangerAccessTypeDefObj);
        XXServiceDef    xServiceDef    = serviceDef();
        XXService       xService       = xService();
        XXServiceDefDao xServiceDefDao = Mockito.mock(XXServiceDefDao.class);
        XXServiceDao    xServiceDao    = Mockito.mock(XXServiceDao.class);
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        Mockito.when(bizUtil.getCurrentUserLoginId()).thenReturn(userName);
        Mockito.when(daoManager.getXXService()).thenReturn(xServiceDao);
        Mockito.when(xServiceDao.findByName(Mockito.anyString())).thenReturn(xService);
        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(xServiceDefDao.getById(xService.getType())).thenReturn(xServiceDef);
        RangerPolicy dbRangerPolicy = serviceREST.getPolicy(rangerPolicy.getId());
        Assert.assertNotNull(dbRangerPolicy);
        Assert.assertEquals(dbRangerPolicy.getId(), rangerPolicy.getId());
        Mockito.verify(svcStore).getPolicy(rangerPolicy.getId());
    }

    @Test
    public void test20getPolicies() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        SearchFilter       filter  = new SearchFilter();
        filter.setParam(SearchFilter.POLICY_NAME, "policyName");
        filter.setParam(SearchFilter.SERVICE_NAME, "serviceName");
        Mockito.when(searchUtil.getSearchFilter(request, policyService.sortFields)).thenReturn(filter);
        RangerPolicyList dbRangerPolicy = serviceREST.getPolicies(request);
        Assert.assertNotNull(dbRangerPolicy);
        Assert.assertEquals(0, dbRangerPolicy.getListSize());
        Mockito.verify(searchUtil).getSearchFilter(request, policyService.sortFields);
    }

    @Test
    public void test21countPolicies() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        SearchFilter filter = new SearchFilter();
        filter.setParam(SearchFilter.POLICY_NAME, "policyName");
        filter.setParam(SearchFilter.SERVICE_NAME, "serviceName");
        Mockito.when(searchUtil.getSearchFilter(request, policyService.sortFields)).thenReturn(filter);

        Long data = serviceREST.countPolicies(request);
        Assert.assertNotNull(data);
        Mockito.verify(searchUtil).getSearchFilter(request, policyService.sortFields);
    }

    @Test
    public void test22getServicePoliciesById() throws Exception {
        HttpServletRequest request      = Mockito.mock(HttpServletRequest.class);
        RangerPolicy       rangerPolicy = rangerPolicy();

        SearchFilter filter = new SearchFilter();
        filter.setParam(SearchFilter.POLICY_NAME, "policyName");
        filter.setParam(SearchFilter.SERVICE_NAME, "serviceName");
        Mockito.when(searchUtil.getSearchFilter(request, policyService.sortFields)).thenReturn(filter);

        RangerPolicyList dbRangerPolicy = serviceREST.getServicePolicies(rangerPolicy.getId(), request);
        Assert.assertNotNull(dbRangerPolicy);
        Mockito.verify(searchUtil).getSearchFilter(request, policyService.sortFields);
        Mockito.verify(svcStore).getServicePolicies(Id, filter);
    }

    @Test
    public void test23getServicePoliciesByName() throws Exception {
        HttpServletRequest request      = Mockito.mock(HttpServletRequest.class);
        RangerPolicy       rangerPolicy = rangerPolicy();

        List<RangerPolicy> ret    = Collections.emptyList();
        SearchFilter       filter = new SearchFilter();

        filter.setParam(SearchFilter.POLICY_NAME, "policyName");
        filter.setParam(SearchFilter.SERVICE_NAME, "serviceName");

        Mockito.when(searchUtil.getSearchFilter(request, policyService.sortFields)).thenReturn(filter);
        Mockito.when(svcStore.getServicePolicies(rangerPolicy.getName(), filter)).thenReturn(ret);

        RangerPolicyList dbRangerPolicy = serviceREST.getServicePoliciesByName(rangerPolicy.getName(), request);
        Assert.assertNotNull(dbRangerPolicy);
    }

    @Test
    public void test24getServicePoliciesIfUpdated() throws Exception {
        HttpServletRequest request          = Mockito.mock(HttpServletRequest.class);
        String             serviceName      = "HDFS_1";
        Long               lastKnownVersion = 1L;
        String             pluginId         = "1";

        ServicePolicies dbServicePolicies = serviceREST.getServicePoliciesIfUpdated(serviceName, lastKnownVersion, 0L, pluginId, "", "", false, capabilityVector, request);
        Assert.assertNull(dbServicePolicies);
    }

    @Test
    public void test25getPolicies() throws Exception {
        List<RangerPolicy> ret    = new ArrayList<>();
        SearchFilter       filter = new SearchFilter();
        filter.setParam(SearchFilter.POLICY_NAME, "policyName");
        filter.setParam(SearchFilter.SERVICE_NAME, "serviceName");
        Mockito.when(svcStore.getPolicies(filter)).thenReturn(ret);

        List<RangerPolicy> dbRangerPolicyList = serviceREST.getPolicies(filter);
        Assert.assertNotNull(dbRangerPolicyList);
        Mockito.verify(svcStore).getPolicies(filter);
    }

    @Test
    public void test26getServices() throws Exception {
        List<RangerService> ret    = new ArrayList<>();
        SearchFilter        filter = new SearchFilter();
        filter.setParam(SearchFilter.POLICY_NAME, "policyName");
        filter.setParam(SearchFilter.SERVICE_NAME, "serviceName");
        Mockito.when(svcStore.getServices(filter)).thenReturn(ret);

        List<RangerService> dbRangerService = serviceREST.getServices(filter);
        Assert.assertNotNull(dbRangerService);
        Mockito.verify(svcStore).getServices(filter);
    }

    @Test
    public void test27getPoliciesWithoutServiceAdmin() throws Exception {
        HttpServletRequest request  = Mockito.mock(HttpServletRequest.class);
        SearchFilter       filter   = new SearchFilter();
        List<RangerPolicy> policies = new ArrayList<>();
        policies.add(rangerPolicy());
        filter.setParam(SearchFilter.POLICY_NAME, "policyName");
        filter.setParam(SearchFilter.SERVICE_NAME, "serviceName");
        Mockito.when(searchUtil.getSearchFilter(request, policyService.sortFields)).thenReturn(filter);
        Mockito.when(svcStore.getPolicies(filter)).thenReturn(policies);
        RangerPolicyList dbRangerPolicy = serviceREST.getPolicies(request);
        Assert.assertNotNull(dbRangerPolicy);
        /* here we are not setting service admin role,hence we will not get any policy without the service admin roles */
        Assert.assertEquals(0, dbRangerPolicy.getListSize());
        Mockito.verify(searchUtil).getSearchFilter(request, policyService.sortFields);
    }

    @Test
    public void test28getPoliciesWithServiceAdmin() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        SearchFilter       filter  = new SearchFilter();
        XXService          xs      = Mockito.mock(XXService.class);
        xs.setType(3L);
        ServiceREST        spySVCRest  = Mockito.spy(serviceREST);
        List<RangerPolicy> policies    = new ArrayList<>();
        ServicePolicies    svcPolicies = new ServicePolicies();
        svcPolicies.setPolicies(policies);
        svcPolicies.setServiceName("HDFS_1-1-20150316062453");
        RangerPolicy rPol = rangerPolicy();
        policies.add(rPol);
        filter.setParam(SearchFilter.POLICY_NAME, "policyName");
        filter.setParam(SearchFilter.SERVICE_NAME, "serviceName");
        Mockito.when(searchUtil.getSearchFilter(request, policyService.sortFields)).thenReturn(filter);
        Mockito.when(svcStore.getPolicies(filter)).thenReturn(policies);

        /*here we are setting serviceAdminRole, so we will get the required policy with serviceAdmi role*/
        Mockito.when(svcStore.isServiceAdminUser(rPol.getService(), null)).thenReturn(true);
        RangerPolicyList dbRangerPolicy = spySVCRest.getPolicies(request);
        Assert.assertNotNull(dbRangerPolicy);
        Assert.assertEquals(1, dbRangerPolicy.getListSize());
        Mockito.verify(searchUtil).getSearchFilter(request, policyService.sortFields);
        Mockito.verify(svcStore).getPolicies(filter);
        Mockito.verify(svcStore).isServiceAdminUser(rPol.getService(), null);
    }

    @Test
    public void test30getPolicyFromEventTime() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        String      strdt          = new Date().toString();
        String      userName       = "Admin";
        Set<String> userGroupsList = new HashSet<>();

        userGroupsList.add("group1");
        userGroupsList.add("group2");
        Mockito.when(request.getParameter("eventTime")).thenReturn(strdt);
        Mockito.when(request.getParameter("policyId")).thenReturn("1");
        Mockito.when(request.getParameter("versionNo")).thenReturn("1");
        RangerPolicy                      policy    = new RangerPolicy();
        Map<String, RangerPolicyResource> resources = new HashMap<>();
        policy.setService("services");
        policy.setResources(resources);
        Mockito.when(svcStore.getPolicyFromEventTime(strdt, 1L)).thenReturn(policy);
        Mockito.when(bizUtil.isAdmin()).thenReturn(false);
        Mockito.when(bizUtil.getCurrentUserLoginId()).thenReturn(userName);

        Mockito.when(restErrorUtil.createRESTException(Mockito.anyInt(), Mockito.anyString(), Mockito.anyBoolean())).thenThrow(new WebApplicationException());
        thrown.expect(WebApplicationException.class);

        RangerPolicy dbRangerPolicy = serviceREST.getPolicyFromEventTime(request);
        Assert.assertNull(dbRangerPolicy);
        Mockito.verify(request).getParameter("eventTime");
        Mockito.verify(request).getParameter("policyId");
        Mockito.verify(request).getParameter("versionNo");
    }

    @Test
    public void test31getServices() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        SearchFilter       filter  = new SearchFilter();
        filter.setParam(SearchFilter.POLICY_NAME, "policyName");
        filter.setParam(SearchFilter.SERVICE_NAME, "serviceName");
        RangerServiceList dbRangerService = serviceREST.getServices(request);
        Assert.assertNull(dbRangerService);
    }

    @Test
    public void test32getPolicyVersionList() {
        VXString vXString = new VXString();
        vXString.setValue("1");
        Mockito.when(svcStore.getPolicyVersionList(Id)).thenReturn(vXString);

        VXString dbVXString = serviceREST.getPolicyVersionList(Id);
        Assert.assertNotNull(dbVXString);
        Mockito.verify(svcStore).getPolicyVersionList(Id);
    }

    @Test
    public void test33getPolicyForVersionNumber() {
        RangerPolicy rangerPolicy = rangerPolicy();
        Mockito.when(svcStore.getPolicyForVersionNumber(Id, 1)).thenReturn(rangerPolicy);
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        RangerPolicy dbRangerPolicy = serviceREST.getPolicyForVersionNumber(Id, 1);
        Assert.assertNotNull(dbRangerPolicy);
        Mockito.verify(svcStore).getPolicyForVersionNumber(Id, 1);
    }

    @Test
    public void test34countServices() throws Exception {
        HttpServletRequest   request = Mockito.mock(HttpServletRequest.class);
        PList<RangerService> ret     = Mockito.mock(PList.class);
        SearchFilter         filter  = new SearchFilter();
        filter.setParam(SearchFilter.POLICY_NAME, "policyName");
        filter.setParam(SearchFilter.SERVICE_NAME, "serviceName");
        Mockito.when(searchUtil.getSearchFilter(request, policyService.sortFields)).thenReturn(filter);

        Mockito.when(svcStore.getPaginatedServices(filter)).thenReturn(ret);
        Long data = serviceREST.countServices(request);
        Assert.assertNotNull(data);
        Mockito.verify(searchUtil).getSearchFilter(request, policyService.sortFields);
        Mockito.verify(svcStore).getPaginatedServices(filter);
    }

    @Test
    public void test35validateConfig() throws Exception {
        RangerService rangerService = rangerService();
        Mockito.when(serviceMgr.validateConfig(rangerService, svcStore)).thenReturn(vXResponse);
        VXResponse dbVXResponse = serviceREST.validateConfig(rangerService);
        Assert.assertNotNull(dbVXResponse);
        Mockito.verify(serviceMgr).validateConfig(rangerService, svcStore);
    }

    @Test
    public void test40applyPolicy() {
        RangerPolicy existingPolicy = rangerPolicy();
        RangerPolicy appliedPolicy  = rangerPolicy();

        List<RangerPolicyItem> policyItem = new ArrayList<>();
        existingPolicy.setPolicyItems(policyItem);
        appliedPolicy.setPolicyItems(null);

        Map<String, RangerPolicyResource> policyResources      = new HashMap<>();
        RangerPolicyResource              rangerPolicyResource = new RangerPolicyResource("/tmp");
        rangerPolicyResource.setIsExcludes(true);
        rangerPolicyResource.setIsRecursive(true);
        policyResources.put("path", rangerPolicyResource);

        existingPolicy.setResources(policyResources);
        appliedPolicy.setResources(policyResources);

        RangerPolicyItem rangerPolicyItem = new RangerPolicyItem();
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("read", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("write", true));
        rangerPolicyItem.addGroup("group1");
        rangerPolicyItem.addGroup("group2");
        rangerPolicyItem.addUser("user1");
        rangerPolicyItem.addUser("user2");
        rangerPolicyItem.setDelegateAdmin(true);

        existingPolicy.addPolicyItem(rangerPolicyItem);

        rangerPolicyItem = new RangerPolicyItem();
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("delete", true));
        rangerPolicyItem.addGroup("group1");
        rangerPolicyItem.addGroup("public");
        rangerPolicyItem.addUser("user1");
        rangerPolicyItem.addUser("finance");
        rangerPolicyItem.setDelegateAdmin(false);

        appliedPolicy.addPolicyItem(rangerPolicyItem);

        String existingPolicyStr = existingPolicy.toString();
        System.out.println("existingPolicy = " + existingPolicyStr);

        ServiceRESTUtil.processApplyPolicy(existingPolicy, appliedPolicy);

        String resultPolicyStr = existingPolicy.toString();
        System.out.println("resultPolicy = " + resultPolicyStr);

        Assert.assertTrue(true);
    }

    @Test
    public void test41applyPolicy() {
        RangerPolicy existingPolicy = rangerPolicy();
        RangerPolicy appliedPolicy  = rangerPolicy();

        List<RangerPolicyItem> policyItem = new ArrayList<>();
        existingPolicy.setPolicyItems(policyItem);
        appliedPolicy.setPolicyItems(null);

        Map<String, RangerPolicyResource> policyResources      = new HashMap<>();
        RangerPolicyResource              rangerPolicyResource = new RangerPolicyResource("/tmp");
        rangerPolicyResource.setIsExcludes(true);
        rangerPolicyResource.setIsRecursive(true);
        policyResources.put("path", rangerPolicyResource);

        existingPolicy.setResources(policyResources);
        appliedPolicy.setResources(policyResources);

        RangerPolicyItem rangerPolicyItem = new RangerPolicyItem();
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("read", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("write", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("delete", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("lock", true));
        rangerPolicyItem.addGroup("group1");
        rangerPolicyItem.addGroup("group2");
        rangerPolicyItem.addUser("user1");
        rangerPolicyItem.addUser("user2");
        rangerPolicyItem.setDelegateAdmin(true);

        rangerPolicyItem = new RangerPolicyItem();
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("read", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("write", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("delete", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("lock", true));
        rangerPolicyItem.addGroup("group3");
        rangerPolicyItem.addUser("user3");
        rangerPolicyItem.setDelegateAdmin(true);

        existingPolicy.addPolicyItem(rangerPolicyItem);

        rangerPolicyItem = new RangerPolicyItem();
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("delete", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("lock", true));
        rangerPolicyItem.addGroup("group1");
        rangerPolicyItem.addGroup("group2");
        rangerPolicyItem.addUser("user1");
        rangerPolicyItem.addUser("user2");
        rangerPolicyItem.setDelegateAdmin(false);

        existingPolicy.addAllowException(rangerPolicyItem);

        rangerPolicyItem = new RangerPolicyItem();
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("delete", true));
        rangerPolicyItem.addGroup("group2");
        rangerPolicyItem.addUser("user2");
        rangerPolicyItem.setDelegateAdmin(false);

        existingPolicy.addDenyPolicyItem(rangerPolicyItem);

        rangerPolicyItem = new RangerPolicyItem();
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("index", true));
        rangerPolicyItem.addGroup("public");
        rangerPolicyItem.addUser("user");
        rangerPolicyItem.setDelegateAdmin(false);

        existingPolicy.addDenyPolicyItem(rangerPolicyItem);

        rangerPolicyItem = new RangerPolicyItem();
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("delete", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("index", true));

        rangerPolicyItem.addGroup("group1");
        rangerPolicyItem.addUser("user1");
        rangerPolicyItem.setDelegateAdmin(false);

        appliedPolicy.addPolicyItem(rangerPolicyItem);

        rangerPolicyItem = new RangerPolicyItem();
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("delete", true));

        rangerPolicyItem.addGroup("public");
        rangerPolicyItem.addUser("user1");
        rangerPolicyItem.setDelegateAdmin(false);

        appliedPolicy.addDenyPolicyItem(rangerPolicyItem);

        String existingPolicyStr = existingPolicy.toString();
        System.out.println("existingPolicy=" + existingPolicyStr);

        ServiceRESTUtil.processApplyPolicy(existingPolicy, appliedPolicy);

        String resultPolicyStr = existingPolicy.toString();
        System.out.println("resultPolicy = " + resultPolicyStr);

        Assert.assertTrue(true);
    }

    @Test
    public void test42grant() {
        RangerPolicy           existingPolicy = rangerPolicy();
        List<RangerPolicyItem> policyItem     = new ArrayList<>();
        existingPolicy.setPolicyItems(policyItem);

        Map<String, RangerPolicyResource> policyResources      = new HashMap<>();
        RangerPolicyResource              rangerPolicyResource = new RangerPolicyResource("/tmp");
        rangerPolicyResource.setIsExcludes(true);
        rangerPolicyResource.setIsRecursive(true);
        policyResources.put("path", rangerPolicyResource);

        existingPolicy.setResources(policyResources);

        RangerPolicyItem rangerPolicyItem = new RangerPolicyItem();
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("read", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("write", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("delete", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("lock", true));
        rangerPolicyItem.addGroup("group1");
        rangerPolicyItem.addGroup("group2");
        rangerPolicyItem.addUser("user1");
        rangerPolicyItem.addUser("user2");
        rangerPolicyItem.setDelegateAdmin(true);

        existingPolicy.addPolicyItem(rangerPolicyItem);

        rangerPolicyItem = new RangerPolicyItem();
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("read", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("write", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("delete", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("lock", true));
        rangerPolicyItem.addGroup("group3");
        rangerPolicyItem.addUser("user3");
        rangerPolicyItem.setDelegateAdmin(true);

        existingPolicy.addPolicyItem(rangerPolicyItem);

        rangerPolicyItem = new RangerPolicyItem();
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("delete", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("lock", true));
        rangerPolicyItem.addGroup("group1");
        rangerPolicyItem.addGroup("group2");
        rangerPolicyItem.addUser("user1");
        rangerPolicyItem.addUser("user2");
        rangerPolicyItem.setDelegateAdmin(false);

        existingPolicy.addAllowException(rangerPolicyItem);

        rangerPolicyItem = new RangerPolicyItem();
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("delete", true));
        rangerPolicyItem.addGroup("group2");
        rangerPolicyItem.addUser("user2");
        rangerPolicyItem.setDelegateAdmin(false);

        existingPolicy.addDenyPolicyItem(rangerPolicyItem);

        rangerPolicyItem = new RangerPolicyItem();
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("index", true));
        rangerPolicyItem.addGroup("public");
        rangerPolicyItem.addUser("user");
        rangerPolicyItem.setDelegateAdmin(false);

        existingPolicy.addDenyPolicyItem(rangerPolicyItem);

        GrantRevokeRequest  grantRequestObj = new GrantRevokeRequest();
        Map<String, String> resource        = new HashMap<>();
        resource.put("path", "/tmp");
        grantRequestObj.setResource(resource);

        grantRequestObj.getUsers().add("user1");
        grantRequestObj.getGroups().add("group1");

        grantRequestObj.getAccessTypes().add("delete");
        grantRequestObj.getAccessTypes().add("index");

        grantRequestObj.setDelegateAdmin(true);

        grantRequestObj.setEnableAudit(true);
        grantRequestObj.setIsRecursive(true);

        grantRequestObj.setGrantor("test42Grant");

        String existingPolicyStr = existingPolicy.toString();
        System.out.println("existingPolicy = " + existingPolicyStr);

        ServiceRESTUtil.processGrantRequest(existingPolicy, grantRequestObj);

        String resultPolicyStr = existingPolicy.toString();
        System.out.println("resultPolicy = " + resultPolicyStr);

        Assert.assertTrue(true);
    }

    @Test
    public void test43revoke() {
        RangerPolicy existingPolicy = rangerPolicy();

        List<RangerPolicyItem> policyItem = new ArrayList<>();
        existingPolicy.setPolicyItems(policyItem);

        Map<String, RangerPolicyResource> policyResources      = new HashMap<>();
        RangerPolicyResource              rangerPolicyResource = new RangerPolicyResource("/tmp");
        rangerPolicyResource.setIsExcludes(true);
        rangerPolicyResource.setIsRecursive(true);
        policyResources.put("path", rangerPolicyResource);

        existingPolicy.setResources(policyResources);

        RangerPolicyItem rangerPolicyItem = new RangerPolicyItem();
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("read", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("write", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("delete", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("lock", true));
        rangerPolicyItem.addGroup("group1");
        rangerPolicyItem.addGroup("group2");
        rangerPolicyItem.addUser("user1");
        rangerPolicyItem.addUser("user2");
        rangerPolicyItem.setDelegateAdmin(true);

        existingPolicy.addPolicyItem(rangerPolicyItem);

        rangerPolicyItem = new RangerPolicyItem();
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("read", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("write", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("delete", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("lock", true));
        rangerPolicyItem.addGroup("group3");
        rangerPolicyItem.addUser("user3");
        rangerPolicyItem.setDelegateAdmin(true);

        existingPolicy.addPolicyItem(rangerPolicyItem);

        rangerPolicyItem = new RangerPolicyItem();
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("delete", true));
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("lock", true));
        rangerPolicyItem.addGroup("group1");
        rangerPolicyItem.addGroup("group2");
        rangerPolicyItem.addUser("user1");
        rangerPolicyItem.addUser("user2");
        rangerPolicyItem.setDelegateAdmin(false);

        existingPolicy.addAllowException(rangerPolicyItem);

        rangerPolicyItem = new RangerPolicyItem();
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("delete", true));
        rangerPolicyItem.addGroup("group2");
        rangerPolicyItem.addUser("user2");
        rangerPolicyItem.setDelegateAdmin(false);

        existingPolicy.addDenyPolicyItem(rangerPolicyItem);

        rangerPolicyItem = new RangerPolicyItem();
        rangerPolicyItem.addAccess(new RangerPolicyItemAccess("index", true));
        rangerPolicyItem.addGroup("public");
        rangerPolicyItem.addUser("user");
        rangerPolicyItem.setDelegateAdmin(false);

        existingPolicy.addDenyPolicyItem(rangerPolicyItem);

        GrantRevokeRequest  revokeRequestObj = new GrantRevokeRequest();
        Map<String, String> resource         = new HashMap<>();
        resource.put("path", "/tmp");
        revokeRequestObj.setResource(resource);

        revokeRequestObj.getUsers().add("user1");
        revokeRequestObj.getGroups().add("group1");

        revokeRequestObj.getAccessTypes().add("delete");
        revokeRequestObj.getAccessTypes().add("index");

        revokeRequestObj.setDelegateAdmin(true);

        revokeRequestObj.setEnableAudit(true);
        revokeRequestObj.setIsRecursive(true);

        revokeRequestObj.setGrantor("test43Revoke");

        String existingPolicyStr = existingPolicy.toString();
        System.out.println("existingPolicy=" + existingPolicyStr);

        ServiceRESTUtil.processRevokeRequest(existingPolicy, revokeRequestObj);

        String resultPolicyStr = existingPolicy.toString();
        System.out.println("resultPolicy=" + resultPolicyStr);

        Assert.assertTrue(true);
    }

    @Test
    public void test44getPolicyLabels() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        SearchFilter       filter  = new SearchFilter();
        Mockito.when(searchUtil.getSearchFilter(request, policyLabelsService.sortFields)).thenReturn(filter);
        List<String> ret = new ArrayList<>();
        Mockito.when(svcStore.getPolicyLabels(filter)).thenReturn(ret);
        ret = serviceREST.getPolicyLabels(request);
        Assert.assertNotNull(ret);
        Mockito.verify(searchUtil).getSearchFilter(request, policyLabelsService.sortFields);
    }

    @Test
    public void test45exportPoliciesInJSON() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        List<RangerPolicy> rangerPolicyList = new ArrayList<>();

        RangerPolicy rangerPolicy = rangerPolicy();
        rangerPolicyList.add(rangerPolicy);
        XXService xService = xService();

        XXServiceDao    xServiceDao    = Mockito.mock(XXServiceDao.class);
        XXServiceDef    xServiceDef    = serviceDef();
        XXServiceDefDao xServiceDefDao = Mockito.mock(XXServiceDefDao.class);

        request.setAttribute("serviceType", "hdfs,hbase,hive,yarn,knox,storm,solr,kafka,nifi,atlas,sqoop");
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        SearchFilter        filter   = new SearchFilter();
        filter.setParam("zoneName", "zone1");
        Mockito.when(searchUtil.getSearchFilter(request, policyService.sortFields)).thenReturn(filter);
        Mockito.when(svcStore.getPolicies(filter)).thenReturn(rangerPolicyList);
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        Mockito.when(bizUtil.isKeyAdmin()).thenReturn(false);
        Mockito.when(bizUtil.getCurrentUserLoginId()).thenReturn("admin");
        Mockito.when(bizUtil.isAuditAdmin()).thenReturn(false);
        Mockito.when(bizUtil.isAuditKeyAdmin()).thenReturn(false);
        Mockito.when(daoManager.getXXService()).thenReturn(xServiceDao);

        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(daoManager.getXXService().findByName("HDFS_1-1-20150316062453")).thenReturn(xService);
        Mockito.when(daoManager.getXXServiceDef().getById(xService.getType())).thenReturn(xServiceDef);
        serviceREST.getPoliciesInJson(request, response, false);

        Mockito.verify(svcStore).getObjectInJson(rangerPolicyList, response, JSON_FILE_NAME_TYPE.POLICY);
    }

    @Test
    public void test46exportPoliciesInCSV() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        List<RangerPolicy> rangerPolicyList = new ArrayList<>();

        RangerPolicy rangerPolicy = rangerPolicy();
        rangerPolicyList.add(rangerPolicy);
        XXService xService = xService();

        XXServiceDao    xServiceDao    = Mockito.mock(XXServiceDao.class);
        XXServiceDef    xServiceDef    = serviceDef();
        XXServiceDefDao xServiceDefDao = Mockito.mock(XXServiceDefDao.class);

        request.setAttribute("serviceType", "hdfs,hbase,hive,yarn,knox,storm,solr,kafka,nifi,atlas,sqoop");
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        SearchFilter        filter   = new SearchFilter();

        Mockito.when(searchUtil.getSearchFilter(request, policyService.sortFields)).thenReturn(filter);
        Mockito.when(svcStore.getPolicies(filter)).thenReturn(rangerPolicyList);
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        Mockito.when(bizUtil.isKeyAdmin()).thenReturn(false);
        Mockito.when(bizUtil.getCurrentUserLoginId()).thenReturn("admin");
        Mockito.when(bizUtil.isAuditAdmin()).thenReturn(false);
        Mockito.when(bizUtil.isAuditKeyAdmin()).thenReturn(false);
        Mockito.when(daoManager.getXXService()).thenReturn(xServiceDao);

        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);

        Mockito.when(daoManager.getXXService().findByName("HDFS_1-1-20150316062453")).thenReturn(xService);
        Mockito.when(daoManager.getXXServiceDef().getById(xService.getType())).thenReturn(xServiceDef);
        serviceREST.getPoliciesInCsv(request, response);

        Mockito.verify(svcStore).getPoliciesInCSV(rangerPolicyList, response);
    }

    @Test
    public void test48exportPoliciesInExcel() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        List<RangerPolicy> rangerPolicyList = new ArrayList<>();
        RangerPolicy rangerPolicy = rangerPolicy();

        rangerPolicyList.add(rangerPolicy);

        XXService xService             = xService();
        XXServiceDao    xServiceDao    = Mockito.mock(XXServiceDao.class);
        XXServiceDef    xServiceDef    = serviceDef();
        XXServiceDefDao xServiceDefDao = Mockito.mock(XXServiceDefDao.class);

        request.setAttribute("serviceType", "hdfs,hbase,hive,yarn,knox,storm,solr,kafka,nifi,atlas,sqoop");
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        SearchFilter        filter   = new SearchFilter();

        Mockito.when(searchUtil.getSearchFilter(request, policyService.sortFields)).thenReturn(filter);
        Mockito.when(svcStore.getPolicies(filter)).thenReturn(rangerPolicyList);
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        Mockito.when(bizUtil.isKeyAdmin()).thenReturn(false);
        Mockito.when(bizUtil.getCurrentUserLoginId()).thenReturn("admin");
        Mockito.when(bizUtil.isAuditAdmin()).thenReturn(false);
        Mockito.when(bizUtil.isAuditKeyAdmin()).thenReturn(false);
        Mockito.when(daoManager.getXXService()).thenReturn(xServiceDao);

        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);

        Mockito.when(daoManager.getXXService().findByName("HDFS_1-1-20150316062453")).thenReturn(xService);
        Mockito.when(daoManager.getXXServiceDef().getById(xService.getType())).thenReturn(xServiceDef);
        serviceREST.getPoliciesInExcel(request, response);
        Mockito.verify(svcStore).getPoliciesInExcel(rangerPolicyList, response);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void test49importPoliciesFromFileAllowingOverride() throws Exception {
        HttpServletRequest        request         = Mockito.mock(HttpServletRequest.class);
        RangerPolicyValidator     policyValidator = Mockito.mock(RangerPolicyValidator.class);
        Map<String, RangerPolicy> policiesMap     = new LinkedHashMap<>();
        RangerPolicy              rangerPolicy    = rangerPolicy();
        RangerService             service         = rangerService();
        XXService                 xService        = xService();
        policiesMap.put("Name", rangerPolicy);
        XXServiceDao                      xServiceDao              = Mockito.mock(XXServiceDao.class);
        XXServiceDef                      xServiceDef              = serviceDef();
        XXServiceDefDao                   xServiceDefDao           = Mockito.mock(XXServiceDefDao.class);
        XXSecurityZoneRefServiceDao       xSecZoneRefServiceDao    = Mockito.mock(XXSecurityZoneRefServiceDao.class);
        XXSecurityZoneRefTagServiceDao    xSecZoneRefTagServiceDao = Mockito.mock(XXSecurityZoneRefTagServiceDao.class);
        XXSecurityZoneRefService          xSecZoneRefService       = Mockito.mock(XXSecurityZoneRefService.class);
        XXSecurityZoneRefTagService       xSecZoneRefTagService    = Mockito.mock(XXSecurityZoneRefTagService.class);
        XXSecurityZoneDao                 xSecZoneDao              = Mockito.mock(XXSecurityZoneDao.class);
        XXSecurityZone                    xSecZone                 = Mockito.mock(XXSecurityZone.class);
        List<XXSecurityZoneRefService>    zoneServiceList          = new ArrayList<>();
        List<XXSecurityZoneRefTagService> zoneTagServiceList       = new ArrayList<>();
        zoneServiceList.add(xSecZoneRefService);
        zoneTagServiceList.add(xSecZoneRefTagService);
        Map<String, String> zoneMappingMap = new LinkedHashMap<>();
        zoneMappingMap.put("ZoneSource", "ZoneDestination");

        String paramServiceType = "serviceType";
        String serviceTypeList    = "hdfs,hbase,hive,yarn,knox,storm,solr,kafka,nifi,atlas,sqoop";
        request.setAttribute("serviceType", "hdfs,hbase,hive,yarn,knox,storm,solr,kafka,nifi,atlas,sqoop");
        SearchFilter filter = new SearchFilter();
        filter.setParam("serviceType", "value");

        File        jsonPolicyFile      = getFile(importPoliceTestFilePath);
        InputStream uploadedInputStream = new FileInputStream(jsonPolicyFile);
        FormDataContentDisposition fileDetail = FormDataContentDisposition.name("file").fileName(jsonPolicyFile.getName()).size(uploadedInputStream.toString().length()).build();
        boolean isOverride = true;

        InputStream zoneInputStream = IOUtils.toInputStream("ZoneSource=ZoneDestination", "UTF-8");

        Mockito.when(searchUtil.getSearchFilter(request, policyService.sortFields)).thenReturn(filter);
        Mockito.when(request.getParameter(paramServiceType)).thenReturn(serviceTypeList);
        Mockito.when(svcStore.createPolicyMap(Mockito.any(Map.class), Mockito.any(List.class), Mockito.anyString(), Mockito.any(Map.class), Mockito.any(List.class), Mockito.any(List.class), Mockito.any(RangerPolicy.class), Mockito.any(Map.class))).thenReturn(policiesMap);
        Mockito.when(validatorFactory.getPolicyValidator(svcStore)).thenReturn(policyValidator);
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        Mockito.when(daoManager.getXXService()).thenReturn(xServiceDao);
        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(daoManager.getXXService().findByName("HDFS_1-1-20150316062453")).thenReturn(xService);
        Mockito.when(daoManager.getXXServiceDef().getById(xService.getType())).thenReturn(xServiceDef);
        Mockito.when(validatorFactory.getPolicyValidator(svcStore)).thenReturn(policyValidator);
        Mockito.when(svcStore.getMapFromInputStream(zoneInputStream)).thenReturn(zoneMappingMap);
        Mockito.when(daoManager.getXXSecurityZoneDao()).thenReturn(xSecZoneDao);
        Mockito.when(xSecZoneDao.findByZoneName(Mockito.anyString())).thenReturn(xSecZone);
        Mockito.when(daoManager.getXXSecurityZoneRefService()).thenReturn(xSecZoneRefServiceDao);
        Mockito.when(xSecZoneRefServiceDao.findByServiceNameAndZoneId(Mockito.anyString(), Mockito.anyLong())).thenReturn(zoneServiceList);
        Mockito.when(daoManager.getXXSecurityZoneRefTagService()).thenReturn(xSecZoneRefTagServiceDao);
        Mockito.when(xSecZoneRefTagServiceDao.findByTagServiceNameAndZoneId(Mockito.anyString(), Mockito.anyLong())).thenReturn(zoneTagServiceList);
        Mockito.when(svcStore.getServiceByName(Mockito.anyString())).thenReturn(service);
        serviceREST.importPoliciesFromFile(request, null, zoneInputStream, uploadedInputStream, fileDetail, isOverride, "unzoneToZone");

        Mockito.verify(svcStore).createPolicy(rangerPolicy);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void test50importPoliciesFromFileNotAllowingOverride() throws Exception {
        HttpServletRequest        request      = Mockito.mock(HttpServletRequest.class);
        Map<String, RangerPolicy> policiesMap  = new LinkedHashMap<>();
        RangerPolicy              rangerPolicy = rangerPolicy();
        XXService                 xService     = xService();
        policiesMap.put("Name", rangerPolicy);
        XXServiceDao                      xServiceDao              = Mockito.mock(XXServiceDao.class);
        XXServiceDef                      xServiceDef              = serviceDef();
        XXServiceDefDao                   xServiceDefDao           = Mockito.mock(XXServiceDefDao.class);
        XXSecurityZoneRefServiceDao       xSecZoneRefServiceDao    = Mockito.mock(XXSecurityZoneRefServiceDao.class);
        XXSecurityZoneRefTagServiceDao    xSecZoneRefTagServiceDao = Mockito.mock(XXSecurityZoneRefTagServiceDao.class);
        XXSecurityZoneRefService          xSecZoneRefService       = Mockito.mock(XXSecurityZoneRefService.class);
        XXSecurityZoneRefTagService       xSecZoneRefTagService    = Mockito.mock(XXSecurityZoneRefTagService.class);
        XXSecurityZoneDao                 xSecZoneDao              = Mockito.mock(XXSecurityZoneDao.class);
        XXSecurityZone                    xSecZone                 = Mockito.mock(XXSecurityZone.class);
        List<XXSecurityZoneRefService>    zoneServiceList          = new ArrayList<>();
        List<XXSecurityZoneRefTagService> zoneTagServiceList       = new ArrayList<>();
        zoneServiceList.add(xSecZoneRefService);
        zoneTagServiceList.add(xSecZoneRefTagService);
        Map<String, String> zoneMappingMap = new LinkedHashMap<>();
        zoneMappingMap.put("ZoneSource", "ZoneDestination");

        String paramServiceType = "serviceType";
        String serviceTypeList    = "hdfs,hbase,hive,yarn,knox,storm,solr,kafka,nifi,atlas,sqoop";
        request.setAttribute("serviceType", "hdfs,hbase,hive,yarn,knox,storm,solr,kafka,nifi,atlas,sqoop");
        SearchFilter filter = new SearchFilter();
        filter.setParam("serviceType", "value");

        File        jsonPolicyFile      = getFile(importPoliceTestFilePath);
        InputStream uploadedInputStream = new FileInputStream(jsonPolicyFile);
        FormDataContentDisposition fileDetail = FormDataContentDisposition.name("file").fileName(jsonPolicyFile.getName()).size(uploadedInputStream.toString().length()).build();
        boolean isOverride = false;

        InputStream zoneInputStream = IOUtils.toInputStream("ZoneSource=ZoneDestination", "UTF-8");

        Mockito.when(searchUtil.getSearchFilter(request, policyService.sortFields)).thenReturn(filter);
        Mockito.when(request.getParameter(paramServiceType)).thenReturn(serviceTypeList);
        Mockito.when(svcStore.createPolicyMap(Mockito.any(Map.class), Mockito.any(List.class), Mockito.anyString(), Mockito.any(Map.class), Mockito.any(List.class), Mockito.any(List.class), Mockito.any(RangerPolicy.class), Mockito.any(Map.class))).thenReturn(policiesMap);
        Mockito.when(validatorFactory.getPolicyValidator(svcStore)).thenReturn(policyValidator);
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        Mockito.when(daoManager.getXXService()).thenReturn(xServiceDao);
        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(daoManager.getXXService().findByName("HDFS_1-1-20150316062453")).thenReturn(xService);
        Mockito.when(daoManager.getXXServiceDef().getById(xService.getType())).thenReturn(xServiceDef);
        Mockito.when(svcStore.getMapFromInputStream(zoneInputStream)).thenReturn(zoneMappingMap);
        Mockito.when(daoManager.getXXSecurityZoneDao()).thenReturn(xSecZoneDao);
        Mockito.when(xSecZoneDao.findByZoneName(Mockito.anyString())).thenReturn(xSecZone);
        Mockito.when(daoManager.getXXSecurityZoneRefService()).thenReturn(xSecZoneRefServiceDao);
        Mockito.when(xSecZoneRefServiceDao.findByServiceNameAndZoneId(Mockito.anyString(), Mockito.anyLong())).thenReturn(zoneServiceList);
        Mockito.when(daoManager.getXXSecurityZoneRefTagService()).thenReturn(xSecZoneRefTagServiceDao);
        Mockito.when(xSecZoneRefTagServiceDao.findByTagServiceNameAndZoneId(Mockito.anyString(), Mockito.anyLong())).thenReturn(zoneTagServiceList);
        serviceREST.importPoliciesFromFile(request, null, zoneInputStream, uploadedInputStream, fileDetail, isOverride, "unzoneToUnZone");
        Mockito.verify(svcStore).createPolicy(rangerPolicy);
    }

    @Test
    public void test51getMetricByType() throws Exception {
        String type = "usergroup";
        String ret = "{\"groupCount\":1,\"userCountOfUserRole\":0,\"userCountOfKeyAdminRole\":1,"
                + "\"userCountOfSysAdminRole\":3,\"userCountOfKeyadminAuditorRole\":0,\"userCountOfSysAdminAuditorRole\":0,\"userTotalCount\":4}";
        ServiceDBStore.METRIC_TYPE metricType = ServiceDBStore.METRIC_TYPE.getMetricTypeByName(type);
        Mockito.when(svcStore.getMetricByType(metricType)).thenReturn(ret);
        serviceREST.getMetricByType(type);
        Mockito.verify(svcStore).getMetricByType(metricType);
    }

    @Test
    public void test52deleteService() {
        RangerService   rangerService     = rangerService();
        XXService       xService          = xService();
        List<XXService> referringServices = new ArrayList<>();
        referringServices.add(xService);
        EmbeddedServiceDefsUtil embeddedServiceDefsUtil = EmbeddedServiceDefsUtil.instance();
        xService.setType(embeddedServiceDefsUtil.getTagServiceDefId());
        XXServiceDao xServiceDao = Mockito.mock(XXServiceDao.class);

        String                userLoginID = "testuser";
        Long                  userId      = 8L;
        RangerSecurityContext context     = new RangerSecurityContext();
        context.setUserSession(new UserSessionBase());
        RangerContextHolder.setSecurityContext(context);
        UserSessionBase session = ContextUtil.getCurrentUserSession();
        session.setUserAdmin(true);
        XXPortalUser xXPortalUser = new XXPortalUser();
        xXPortalUser.setLoginId(userLoginID);
        xXPortalUser.setId(userId);
        session.setXXPortalUser(xXPortalUser);

        Mockito.when(validatorFactory.getServiceValidator(svcStore)).thenReturn(serviceValidator);
        Mockito.when(daoManager.getXXService()).thenReturn(xServiceDao);
        Mockito.when(xServiceDao.findByTagServiceId(Mockito.anyLong())).thenReturn(referringServices);
        Mockito.when(xServiceDao.getById(Id)).thenReturn(xService);
        Mockito.when(restErrorUtil.createRESTException(Mockito.anyString(), Mockito.any())).thenThrow(new WebApplicationException());
        thrown.expect(WebApplicationException.class);
        serviceREST.deleteService(rangerService.getId());
    }

    @Test
    public void test53getPoliciesForResource() {
        HttpServletRequest  request = Mockito.mock(HttpServletRequest.class);
        List<RangerService> rsList  = new ArrayList<>();
        RangerService       rs      = rangerService();
        rsList.add(rs);

        Mockito.when(restErrorUtil.createRESTException(Mockito.anyString(), Mockito.any())).thenThrow(new WebApplicationException());
        thrown.expect(WebApplicationException.class);

        serviceREST.getPoliciesForResource("servicedefname", "servicename", request);
    }

    @Test
    public void test54getPluginsInfo() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        SearchFilter       filter  = new SearchFilter();
        filter.setParam(SearchFilter.POLICY_NAME, "policyName");
        filter.setParam(SearchFilter.SERVICE_NAME, "serviceName");
        PList<RangerPluginInfo> paginatedPluginsInfo = new PList<>();
        Mockito.when(searchUtil.getSearchFilter(request, pluginInfoService.getSortFields())).thenReturn(filter);
        Mockito.when(pluginInfoService.searchRangerPluginInfo(filter)).thenReturn(paginatedPluginsInfo);
        RangerPluginInfoList rPluginInfoList = serviceREST.getPluginsInfo(request);
        Assert.assertNotNull(rPluginInfoList);
        Mockito.verify(searchUtil).getSearchFilter(request, pluginInfoService.getSortFields());
        Mockito.verify(pluginInfoService).searchRangerPluginInfo(filter);
    }

    @Test
    public void test55getServicePoliciesIfUpdatedCatch() throws Exception {
        HttpServletRequest request          = Mockito.mock(HttpServletRequest.class);
        String             serviceName      = "HDFS_1";
        Long               lastKnownVersion = 1L;
        String             pluginId         = "1";
        Mockito.when(serviceUtil.isValidateHttpsAuthentication(serviceName, request)).thenReturn(true);
        Mockito.when(restErrorUtil.createRESTException(Mockito.anyInt(), Mockito.anyString(), Mockito.anyBoolean())).thenThrow(new WebApplicationException());
        thrown.expect(WebApplicationException.class);
        serviceREST.getServicePoliciesIfUpdated(serviceName, lastKnownVersion, 0L, pluginId, "", "", false, capabilityVector, request);
    }

    @Test
    public void test56getServicePoliciesIfUpdated() throws Exception {
        HttpServletRequest request          = Mockito.mock(HttpServletRequest.class);
        ServicePolicies    servicePolicies  = servicePolicies();
        String             serviceName      = "HDFS_1";
        Long               lastKnownVersion = 1L;
        String             pluginId         = "1";
        Mockito.when(serviceUtil.isValidateHttpsAuthentication(serviceName, request)).thenReturn(true);
        Mockito.when(svcStore.getServicePoliciesIfUpdated(Mockito.anyString(), Mockito.anyLong(), Mockito.anyBoolean())).thenReturn(servicePolicies);
        ServicePolicies dbServicePolicies = serviceREST.getServicePoliciesIfUpdated(serviceName, lastKnownVersion, 0L, pluginId, "", "", true, capabilityVector, request);
        Assert.assertNotNull(dbServicePolicies);
    }

    @Test
    public void test57getSecureServicePoliciesIfUpdatedFail() throws Exception {
        HttpServletRequest request          = Mockito.mock(HttpServletRequest.class);
        Long               lastKnownVersion = 1L;
        String             pluginId         = "1";
        XXService          xService         = xService();
        XXServiceDef       xServiceDef      = serviceDef();
        String             serviceName      = xService.getName();
        RangerService      rs               = rangerService();
        XXServiceDefDao    xServiceDefDao   = Mockito.mock(XXServiceDefDao.class);
        Mockito.when(serviceUtil.isValidService(serviceName, request)).thenReturn(true);
        Mockito.when(daoManager.getXXService()).thenReturn(xServiceDao);
        Mockito.when(xServiceDao.findByName(serviceName)).thenReturn(xService);
        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(xServiceDefDao.getById(xService.getType())).thenReturn(xServiceDef);
        Mockito.when(svcStore.getServiceByName(serviceName)).thenReturn(rs);
        Mockito.when(restErrorUtil.createRESTException(Mockito.anyInt(), Mockito.anyString(), Mockito.anyBoolean())).thenThrow(new WebApplicationException());
        thrown.expect(WebApplicationException.class);

        serviceREST.getSecureServicePoliciesIfUpdated(serviceName, lastKnownVersion, 0L, pluginId, "", "", false, capabilityVector, request);
    }

    @Test
    public void test58getSecureServicePoliciesIfUpdatedAllowedFail() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        Long         lastKnownVersion = 1L;
        String       pluginId         = "1";
        XXService    xService         = xService();
        XXServiceDef xServiceDef      = serviceDef();
        xServiceDef.setImplclassname("org.apache.ranger.services.kms.RangerServiceKMS");
        String          serviceName    = xService.getName();
        RangerService   rs             = rangerService();
        XXServiceDefDao xServiceDefDao = Mockito.mock(XXServiceDefDao.class);
        Mockito.when(serviceUtil.isValidService(serviceName, request)).thenReturn(true);
        Mockito.when(daoManager.getXXService()).thenReturn(xServiceDao);
        Mockito.when(xServiceDao.findByName(serviceName)).thenReturn(xService);
        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(xServiceDefDao.getById(xService.getType())).thenReturn(xServiceDef);
        Mockito.when(svcStore.getServiceByNameForDP(serviceName)).thenReturn(rs);
        Mockito.when(bizUtil.isUserAllowed(rs, ServiceREST.Allowed_User_List_For_Grant_Revoke)).thenReturn(true);
        Mockito.when(restErrorUtil.createRESTException(Mockito.anyInt(), Mockito.anyString(), Mockito.anyBoolean())).thenThrow(new WebApplicationException());
        thrown.expect(WebApplicationException.class);

        serviceREST.getSecureServicePoliciesIfUpdated(serviceName, lastKnownVersion, 0L, pluginId, "", "", false, capabilityVector, request);
    }

    @Test
    public void test59getSecureServicePoliciesIfUpdatedSuccess() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        Long         lastKnownVersion = 1L;
        String       pluginId         = "1";
        XXService    xService         = xService();
        XXServiceDef xServiceDef      = serviceDef();
        xServiceDef.setImplclassname("org.apache.ranger.services.kms.RangerServiceKMS");
        String          serviceName    = xService.getName();
        RangerService   rs             = rangerService();
        ServicePolicies sp             = servicePolicies();
        XXServiceDefDao xServiceDefDao = Mockito.mock(XXServiceDefDao.class);
        Mockito.when(serviceUtil.isValidService(serviceName, request)).thenReturn(true);
        Mockito.when(daoManager.getXXService()).thenReturn(xServiceDao);
        Mockito.when(xServiceDao.findByName(serviceName)).thenReturn(xService);
        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(xServiceDefDao.getById(xService.getType())).thenReturn(xServiceDef);
        Mockito.when(svcStore.getServiceByNameForDP(serviceName)).thenReturn(rs);
        Mockito.when(bizUtil.isUserAllowed(rs, ServiceREST.Allowed_User_List_For_Grant_Revoke)).thenReturn(true);
        Mockito.when(svcStore.getServicePoliciesIfUpdated(Mockito.anyString(), Mockito.anyLong(), Mockito.anyBoolean())).thenReturn(sp);
        ServicePolicies dbServiceSecurePolicies = serviceREST.getSecureServicePoliciesIfUpdated(serviceName, lastKnownVersion, 0L, pluginId, "", "", true, capabilityVector, request);
        Assert.assertNotNull(dbServiceSecurePolicies);
        Mockito.verify(serviceUtil).isValidService(serviceName, request);
        Mockito.verify(xServiceDao).findByName(serviceName);
        Mockito.verify(xServiceDefDao).getById(xService.getType());
        Mockito.verify(svcStore).getServiceByNameForDP(serviceName);
        Mockito.verify(bizUtil).isUserAllowed(rs, ServiceREST.Allowed_User_List_For_Grant_Revoke);
        Mockito.verify(svcStore).getServicePoliciesIfUpdated(serviceName, lastKnownVersion, false);
    }

    @Test
    public void test60getPolicyFromEventTime() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        String      strdt          = new Date().toString();
        Set<String> userGroupsList = new HashSet<>();
        userGroupsList.add("group1");
        userGroupsList.add("group2");
        Mockito.when(request.getParameter("eventTime")).thenReturn(strdt);
        Mockito.when(request.getParameter("policyId")).thenReturn("1");
        Mockito.when(request.getParameter("versionNo")).thenReturn("1");
        RangerPolicy                      policy    = new RangerPolicy();
        Map<String, RangerPolicyResource> resources = new HashMap<>();
        policy.setService("services");
        policy.setResources(resources);
        Mockito.when(svcStore.getPolicyFromEventTime(strdt, 1L)).thenReturn(null);

        Mockito.when(restErrorUtil.createRESTException(Mockito.anyInt(), Mockito.anyString(), Mockito.anyBoolean())).thenThrow(new WebApplicationException());
        thrown.expect(WebApplicationException.class);

        serviceREST.getPolicyFromEventTime(request);
    }

    @Test
    public void test61getServiceWillOnlyReturnNameIdAndTypeForRoleUser() throws Exception {
        RangerService actualService = rangerService();

        String userLoginID = "testuser";
        Long   userId      = 8L;

        RangerSecurityContext context = new RangerSecurityContext();
        context.setUserSession(new UserSessionBase());
        RangerContextHolder.setSecurityContext(context);
        UserSessionBase currentUserSession = ContextUtil.getCurrentUserSession();
        currentUserSession.setUserAdmin(false);
        XXPortalUser xXPortalUser = new XXPortalUser();
        xXPortalUser.setLoginId(userLoginID);
        xXPortalUser.setId(userId);
        currentUserSession.setXXPortalUser(xXPortalUser);

        VXUser       loggedInUser     = new VXUser();
        List<String> loggedInUserRole = new ArrayList<>();
        loggedInUserRole.add(RangerConstants.ROLE_USER);
        loggedInUser.setId(8L);
        loggedInUser.setName("testuser");
        loggedInUser.setUserRoleList(loggedInUserRole);
        Mockito.when(xUserService.getXUserByUserName("testuser")).thenReturn(loggedInUser);
        Mockito.when(svcStore.getService(Id)).thenReturn(actualService);

        RangerService service = serviceREST.getService(Id);
        Assert.assertNotNull(service);
        Mockito.verify(svcStore).getService(Id);
        Assert.assertNull(service.getDescription());
        Assert.assertTrue(service.getConfigs().isEmpty());
        Assert.assertEquals(Id, service.getId());
        Assert.assertEquals("HDFS_1", service.getName());
        Assert.assertEquals("1", service.getType());
    }

    @Test
    public void test62getServiceByNameWillOnlyReturnNameIdAndTypeForRoleUser() throws Exception {
        RangerService actualService = rangerService();

        String userLoginID = "testuser";
        Long   userId      = 8L;

        RangerSecurityContext context = new RangerSecurityContext();
        context.setUserSession(new UserSessionBase());
        RangerContextHolder.setSecurityContext(context);
        UserSessionBase currentUserSession = ContextUtil.getCurrentUserSession();
        currentUserSession.setUserAdmin(false);
        XXPortalUser xXPortalUser = new XXPortalUser();
        xXPortalUser.setLoginId(userLoginID);
        xXPortalUser.setId(userId);
        currentUserSession.setXXPortalUser(xXPortalUser);

        VXUser       loggedInUser     = new VXUser();
        List<String> loggedInUserRole = new ArrayList<>();
        loggedInUserRole.add(RangerConstants.ROLE_USER);
        loggedInUser.setId(8L);
        loggedInUser.setName("testuser");
        loggedInUser.setUserRoleList(loggedInUserRole);
        Mockito.when(xUserService.getXUserByUserName("testuser")).thenReturn(loggedInUser);
        Mockito.when(svcStore.getServiceByName(actualService.getName())).thenReturn(actualService);

        RangerService service = serviceREST.getServiceByName(actualService.getName());
        Assert.assertNotNull(service);
        Mockito.verify(svcStore).getServiceByName(actualService.getName());
        Assert.assertNull(service.getDescription());
        Assert.assertTrue(service.getConfigs().isEmpty());
        Assert.assertEquals(Id, service.getId());
        Assert.assertEquals("HDFS_1", service.getName());
        Assert.assertEquals("1", service.getType());
    }

    @Test
    public void test63getServices() throws Exception {
        HttpServletRequest   request       = Mockito.mock(HttpServletRequest.class);
        PList<RangerService> paginatedSvcs = new PList<>();
        RangerService        svc1          = rangerService();

        String userLoginID = "testuser";
        Long   userId      = 8L;

        RangerSecurityContext context = new RangerSecurityContext();
        context.setUserSession(new UserSessionBase());
        RangerContextHolder.setSecurityContext(context);
        UserSessionBase currentUserSession = ContextUtil.getCurrentUserSession();
        currentUserSession.setUserAdmin(false);
        XXPortalUser xXPortalUser = new XXPortalUser();
        xXPortalUser.setLoginId(userLoginID);
        xXPortalUser.setId(userId);
        currentUserSession.setXXPortalUser(xXPortalUser);

        VXUser       loggedInUser     = new VXUser();
        List<String> loggedInUserRole = new ArrayList<>();
        loggedInUserRole.add(RangerConstants.ROLE_USER);
        loggedInUser.setId(8L);
        loggedInUser.setName("testuser");
        loggedInUser.setUserRoleList(loggedInUserRole);

        Map<String, String> configs = new HashMap<>();
        configs.put("username", "servicemgr");
        configs.put("password", "servicemgr");
        configs.put("namenode", "servicemgr");
        configs.put("hadoop.security.authorization", "No");
        configs.put("hadoop.security.authentication", "Simple");
        configs.put("hadoop.security.auth_to_local", "");
        configs.put("dfs.datanode.kerberos.principal", "");
        configs.put("dfs.namenode.kerberos.principal", "");
        configs.put("dfs.secondary.namenode.kerberos.principal", "");
        configs.put("hadoop.rpc.protection", "Privacy");
        configs.put("commonNameForCertificate", "");

        RangerService svc2 = new RangerService();
        svc2.setId(9L);
        svc2.setConfigs(configs);
        svc2.setCreateTime(new Date());
        svc2.setDescription("service policy");
        svc2.setGuid("1427365526516_835_1");
        svc2.setIsEnabled(true);
        svc2.setName("YARN_1");
        svc2.setPolicyUpdateTime(new Date());
        svc2.setType("yarn");
        svc2.setUpdatedBy("Admin");
        svc2.setUpdateTime(new Date());

        List<RangerService> rangerServiceList = new ArrayList<>();
        rangerServiceList.add(svc1);
        rangerServiceList.add(svc2);

        paginatedSvcs.setList(rangerServiceList);

        SearchFilter filter = new SearchFilter();
        Mockito.when(searchUtil.getSearchFilter(request, svcService.sortFields)).thenReturn(filter);
        Mockito.when(svcStore.getPaginatedServices(filter)).thenReturn(paginatedSvcs);
        Mockito.when(xUserService.getXUserByUserName("testuser")).thenReturn(loggedInUser);
        RangerServiceList retServiceList = serviceREST.getServices(request);
        Assert.assertNotNull(retServiceList);
        Assert.assertNull(retServiceList.getServices().get(0).getDescription());
        Assert.assertTrue(retServiceList.getServices().get(0).getConfigs().isEmpty());
        Assert.assertNull(retServiceList.getServices().get(1).getDescription());
        Assert.assertTrue(retServiceList.getServices().get(1).getConfigs().isEmpty());
        Assert.assertEquals(Id, retServiceList.getServices().get(0).getId());
        Assert.assertEquals("HDFS_1", retServiceList.getServices().get(0).getName());
        Assert.assertEquals("1", retServiceList.getServices().get(0).getType());

        Assert.assertEquals(retServiceList.getServices().get(1).getId(), svc2.getId());
        Assert.assertEquals("YARN_1", retServiceList.getServices().get(1).getName());
        Assert.assertEquals("yarn", retServiceList.getServices().get(1).getType());
    }

    public void mockValidateGrantRevokeRequest() {
        Mockito.when(xUserService.getXUserByUserName(Mockito.anyString())).thenReturn(Mockito.mock(VXUser.class));
        Mockito.when(userMgr.getGroupByGroupName(Mockito.anyString())).thenReturn(Mockito.mock(VXGroup.class));
        Mockito.when(daoManager.getXXRole().findByRoleName(Mockito.anyString())).thenReturn(Mockito.mock(XXRole.class));
    }

    @Test
    @Ignore
    public void test14bGrantAccess() throws Exception {
        HttpServletRequest request         = Mockito.mock(HttpServletRequest.class);
        String             serviceName     = "HDFS_1";
        GrantRevokeRequest grantRequestObj = createValidGrantRevokeRequest();
        Mockito.when(serviceUtil.isValidateHttpsAuthentication(serviceName, request)).thenReturn(true);
        Mockito.doNothing().when(bizUtil).failUnauthenticatedIfNotAllowed();
        mockValidateGrantRevokeRequest();
        Mockito.when(xUserService.getXUserByUserName(Mockito.anyString())).thenReturn(Mockito.mock(VXUser.class));
        Mockito.when(svcStore.getServiceByName(Mockito.anyString())).thenReturn(Mockito.mock(RangerService.class));
        Mockito.when(bizUtil.isUserRangerAdmin(Mockito.anyString())).thenReturn(true);
        RESTResponse restResponse = serviceREST.grantAccess(serviceName, grantRequestObj, request);
        Mockito.verify(svcStore, Mockito.times(1)).createPolicy(Mockito.any(RangerPolicy.class));

        Assert.assertNotNull(restResponse);
        Assert.assertEquals(RESTResponse.STATUS_SUCCESS, restResponse.getStatusCode());
    }

    @Test
    @Ignore
    public void test64SecureGrantAccess() {
        HttpServletRequest request         = Mockito.mock(HttpServletRequest.class);
        String             serviceName     = "HDFS_1";
        GrantRevokeRequest grantRequestObj = createValidGrantRevokeRequest();
        Mockito.when(serviceUtil.isValidService(serviceName, request)).thenReturn(true);
        Mockito.when(daoManager.getXXService().findByName(Mockito.anyString())).thenReturn(Mockito.mock(XXService.class));
        Mockito.when(daoManager.getXXServiceDef().getById(Mockito.anyLong())).thenReturn(Mockito.mock(XXServiceDef.class));
        try {
            Mockito.when(svcStore.getServiceByName(Mockito.anyString())).thenReturn(Mockito.mock(RangerService.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        mockValidateGrantRevokeRequest();
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        Mockito.when(bizUtil.isUserServiceAdmin(Mockito.any(RangerService.class), Mockito.anyString())).thenReturn(true);
        RESTResponse restResponse;
        try {
            restResponse = serviceREST.secureGrantAccess(serviceName, grantRequestObj, request);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            Mockito.verify(svcStore, Mockito.times(1)).createPolicy(Mockito.any(RangerPolicy.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Assert.assertNotNull(restResponse);
        Assert.assertEquals(RESTResponse.STATUS_SUCCESS, restResponse.getStatusCode());
    }

    @Test
    public void test15bRevokeAccess() throws Exception {
        HttpServletRequest request       = Mockito.mock(HttpServletRequest.class);
        String             serviceName   = "HDFS_1";
        GrantRevokeRequest revokeRequest = createValidGrantRevokeRequest();
        Mockito.when(serviceUtil.isValidateHttpsAuthentication(serviceName, request)).thenReturn(true);
        Mockito.doNothing().when(bizUtil).failUnauthenticatedIfNotAllowed();
        mockValidateGrantRevokeRequest();
        Mockito.when(xUserService.getXUserByUserName(Mockito.anyString())).thenReturn(Mockito.mock(VXUser.class));
        Mockito.when(bizUtil.isUserRangerAdmin(Mockito.anyString())).thenReturn(true);
        RESTResponse restResponse = serviceREST.revokeAccess(serviceName, revokeRequest, request);
        Assert.assertNotNull(restResponse);
        Assert.assertEquals(RESTResponse.STATUS_SUCCESS, restResponse.getStatusCode());
    }

    @Test
    public void test65SecureRevokeAccess() {
        HttpServletRequest request       = Mockito.mock(HttpServletRequest.class);
        String             serviceName   = "HDFS_1";
        GrantRevokeRequest revokeRequest = createValidGrantRevokeRequest();
        Mockito.when(serviceUtil.isValidService(serviceName, request)).thenReturn(true);
        Mockito.when(daoManager.getXXService().findByName(Mockito.anyString())).thenReturn(Mockito.mock(XXService.class));
        Mockito.when(daoManager.getXXServiceDef().getById(Mockito.anyLong())).thenReturn(Mockito.mock(XXServiceDef.class));
        try {
            Mockito.when(svcStore.getServiceByName(Mockito.anyString())).thenReturn(Mockito.mock(RangerService.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        mockValidateGrantRevokeRequest();
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        Mockito.when(bizUtil.isUserRangerAdmin(Mockito.anyString())).thenReturn(true);
        RESTResponse restResponse = null;
        try {
            restResponse = serviceREST.secureRevokeAccess(serviceName,
                    revokeRequest, request);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Assert.assertNotNull(restResponse);
        Assert.assertEquals(RESTResponse.STATUS_SUCCESS, restResponse.getStatusCode());
    }

    @Test
    public void test66ApplyPolicy() {
        ServiceREST        serviceRESTSpy = Mockito.spy(serviceREST);
        HttpServletRequest request        = Mockito.mock(HttpServletRequest.class);
        RangerPolicy       policy         = rangerPolicy();
        Mockito.doReturn(policy).when(serviceRESTSpy).createPolicy(Mockito.any(RangerPolicy.class), eq(null));
        RangerPolicy returnedPolicy = serviceRESTSpy.applyPolicy(policy, request);
        Assert.assertNotNull(returnedPolicy);
        Assert.assertEquals(returnedPolicy.getId(), policy.getId());
        Assert.assertEquals(returnedPolicy.getName(), policy.getName());
    }

    @Test
    public void test67ResetPolicyCacheForAdmin() {
        boolean res         = true;
        String  serviceName = "HDFS_1";
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        RangerService rangerService = rangerService();
        try {
            Mockito.when(svcStore.getServiceByName(serviceName)).thenReturn(rangerService);
        } catch (Exception ignored) {
        }
        Mockito.when(svcStore.resetPolicyCache(serviceName)).thenReturn(res);
        boolean isReset = serviceREST.resetPolicyCache(serviceName);
        Assert.assertEquals(res, isReset);
        try {
            Mockito.verify(svcStore).getServiceByName(serviceName);
        } catch (Exception ignored) {
        }
    }

    @Test
    public void test68ResetPolicyCacheAll() {
        boolean res = true;
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        Mockito.when(svcStore.resetPolicyCache(null)).thenReturn(res);
        boolean isReset = serviceREST.resetPolicyCacheAll();
        Assert.assertEquals(res, isReset);
    }

    @Test
    public void test69DeletePolicyDeltas() {
        int                val     = 1;
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        serviceREST.deletePolicyDeltas(val, request);
        Mockito.verify(svcStore).resetPolicyUpdateLog(Mockito.anyInt(), Mockito.anyInt());
    }

    @Test
    public void test70PurgeEmptyPolicies() {
        ServiceREST        serviceRESTSpy = Mockito.spy(serviceREST);
        HttpServletRequest request        = Mockito.mock(HttpServletRequest.class);
        String             serviceName    = "HDFS_1";
        try {
            Mockito.when(svcStore.getServiceByName(Mockito.anyString())).thenReturn(Mockito.mock(RangerService.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            Mockito.when(svcStore.getServicePolicies(Mockito.anyString(), Mockito.anyLong())).thenReturn(servicePolicies());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        serviceRESTSpy.purgeEmptyPolicies(serviceName, request);
        Mockito.verify(serviceRESTSpy, Mockito.never()).deletePolicy(Mockito.anyLong());
    }

    @Test
    public void test71DeleteClusterServices() {
        String     clusterName = "cluster1";
        List<Long> idsToDelete = createLongList();
        Mockito.when(daoManager.getXXServiceConfigMap().findServiceIdsByClusterName(Mockito.anyString())).thenReturn(idsToDelete);
        XXServiceDef    xServiceDef    = serviceDef();
        XXService       xService       = xService();
        XXServiceDefDao xServiceDefDao = Mockito.mock(XXServiceDefDao.class);
        Mockito.when(validatorFactory.getServiceValidator(svcStore)).thenReturn(serviceValidator);
        Mockito.when(daoManager.getXXService().getById(Mockito.anyLong())).thenReturn(xService);
        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(xServiceDefDao.getById(xService.getType())).thenReturn(xServiceDef);
        ResponseEntity<List<ServiceDeleteResponse>> deletedResponse = serviceREST.deleteClusterServices(clusterName);
        Assert.assertEquals(HttpStatus.OK, deletedResponse.getStatusCode());
        Assert.assertNotEquals(null, deletedResponse.getBody());
        for (ServiceDeleteResponse response : deletedResponse.getBody()) {
            Assert.assertTrue(response.getIsDeleted());
        }
    }

    @Test
    public void test72updatePolicyWithPolicyIdIsNull() {
        RangerPolicy rangerPolicy = rangerPolicy();
        Long         policyId     = rangerPolicy.getId();
        rangerPolicy.setId(null);
        String userName = "admin";

        Set<String> userGroupsList = new HashSet<>();
        userGroupsList.add("group1");
        userGroupsList.add("group2");

        List<RangerAccessTypeDef> rangerAccessTypeDefList = new ArrayList<>();
        RangerAccessTypeDef       rangerAccessTypeDefObj  = new RangerAccessTypeDef();
        rangerAccessTypeDefObj.setLabel("Read");
        rangerAccessTypeDefObj.setName("read");
        rangerAccessTypeDefObj.setRbKeyLabel(null);
        rangerAccessTypeDefList.add(rangerAccessTypeDefObj);
        XXServiceDef    xServiceDef    = serviceDef();
        XXService       xService       = xService();
        XXServiceDefDao xServiceDefDao = Mockito.mock(XXServiceDefDao.class);
        XXServiceDao    xServiceDao    = Mockito.mock(XXServiceDao.class);

        Mockito.when(validatorFactory.getPolicyValidator(svcStore)).thenReturn(policyValidator);
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        Mockito.when(bizUtil.getCurrentUserLoginId()).thenReturn(userName);
        Mockito.when(daoManager.getXXService()).thenReturn(xServiceDao);
        Mockito.when(xServiceDao.findByName(Mockito.anyString())).thenReturn(xService);
        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(xServiceDefDao.getById(xService.getType())).thenReturn(xServiceDef);
        RangerPolicy dbRangerPolicy = serviceREST.updatePolicy(rangerPolicy, policyId);
        Assert.assertNull(dbRangerPolicy);
        Mockito.verify(validatorFactory).getPolicyValidator(svcStore);
    }

    @Test
    public void test72updatePolicyWithInvalidPolicyId() {
        RangerPolicy rangerPolicy = rangerPolicy();

        Set<String> userGroupsList = new HashSet<>();
        userGroupsList.add("group1");
        userGroupsList.add("group2");

        List<RangerAccessTypeDef> rangerAccessTypeDefList = new ArrayList<>();
        RangerAccessTypeDef       rangerAccessTypeDefObj  = new RangerAccessTypeDef();
        rangerAccessTypeDefObj.setLabel("Read");
        rangerAccessTypeDefObj.setName("read");
        rangerAccessTypeDefObj.setRbKeyLabel(null);
        rangerAccessTypeDefList.add(rangerAccessTypeDefObj);
        XXServiceDefDao xServiceDefDao = Mockito.mock(XXServiceDefDao.class);
        XXServiceDao    xServiceDao    = Mockito.mock(XXServiceDao.class);

        Mockito.when(daoManager.getXXService()).thenReturn(xServiceDao);
        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(restErrorUtil.createRESTException(Mockito.anyInt(), Mockito.anyString(), Mockito.anyBoolean())).thenThrow(new WebApplicationException());
        thrown.expect(WebApplicationException.class);
        RangerPolicy dbRangerPolicy = serviceREST.updatePolicy(rangerPolicy, -11L);
        Assert.assertNull(dbRangerPolicy);
        Mockito.verify(validatorFactory).getPolicyValidator(svcStore);
    }

    @Test
    public void test73updateServiceDefWhenIdIsNull() throws Exception {
        RangerServiceDef rangerServiceDef = rangerServiceDef();
        rangerServiceDef.setId(null);

        Mockito.when(validatorFactory.getServiceDefValidator(svcStore)).thenReturn(serviceDefValidator);
        Mockito.when(svcStore.updateServiceDef(Mockito.any())).thenReturn(rangerServiceDef);

        RangerServiceDef dbRangerServiceDef = serviceREST.updateServiceDef(rangerServiceDef, rangerServiceDef.getId());
        Assert.assertNotNull(dbRangerServiceDef);
        Assert.assertEquals(dbRangerServiceDef, rangerServiceDef);
        Assert.assertEquals(dbRangerServiceDef.getId(), rangerServiceDef.getId());
        Assert.assertEquals(dbRangerServiceDef.getName(), rangerServiceDef.getName());
        Assert.assertEquals(dbRangerServiceDef.getImplClass(), rangerServiceDef.getImplClass());
        Assert.assertEquals(dbRangerServiceDef.getLabel(), rangerServiceDef.getLabel());
        Assert.assertEquals(dbRangerServiceDef.getDescription(), rangerServiceDef.getDescription());
        Assert.assertEquals(dbRangerServiceDef.getRbKeyDescription(), rangerServiceDef.getRbKeyDescription());
        Assert.assertEquals(dbRangerServiceDef.getUpdatedBy(), rangerServiceDef.getUpdatedBy());
        Assert.assertEquals(dbRangerServiceDef.getUpdateTime(), rangerServiceDef.getUpdateTime());
        Assert.assertEquals(dbRangerServiceDef.getVersion(), rangerServiceDef.getVersion());
        Assert.assertEquals(dbRangerServiceDef.getConfigs(), rangerServiceDef.getConfigs());

        Mockito.verify(validatorFactory).getServiceDefValidator(svcStore);
        Mockito.verify(svcStore).updateServiceDef(rangerServiceDef);
    }

    @Test
    public void test74updateServiceDefWithInvalidDefId() throws Exception {
        RangerServiceDef rangerServiceDef = rangerServiceDef();

        Mockito.when(restErrorUtil.createRESTException(Mockito.anyInt(), Mockito.anyString(), Mockito.anyBoolean())).thenThrow(new WebApplicationException());
        thrown.expect(WebApplicationException.class);

        RangerServiceDef dbRangerServiceDef = serviceREST.updateServiceDef(rangerServiceDef, -1L);
        Assert.assertNotNull(dbRangerServiceDef);
        Assert.assertEquals(dbRangerServiceDef, rangerServiceDef);
        Assert.assertEquals(dbRangerServiceDef.getId(), rangerServiceDef.getId());
        Assert.assertEquals(dbRangerServiceDef.getName(), rangerServiceDef.getName());
        Assert.assertEquals(dbRangerServiceDef.getImplClass(), rangerServiceDef.getImplClass());
        Assert.assertEquals(dbRangerServiceDef.getLabel(), rangerServiceDef.getLabel());
        Assert.assertEquals(dbRangerServiceDef.getDescription(), rangerServiceDef.getDescription());
        Assert.assertEquals(dbRangerServiceDef.getRbKeyDescription(), rangerServiceDef.getRbKeyDescription());
        Assert.assertEquals(dbRangerServiceDef.getUpdatedBy(), rangerServiceDef.getUpdatedBy());
        Assert.assertEquals(dbRangerServiceDef.getUpdateTime(), rangerServiceDef.getUpdateTime());
        Assert.assertEquals(dbRangerServiceDef.getVersion(), rangerServiceDef.getVersion());
        Assert.assertEquals(dbRangerServiceDef.getConfigs(), rangerServiceDef.getConfigs());

        Mockito.verify(validatorFactory).getServiceDefValidator(svcStore);
        Mockito.verify(svcStore).updateServiceDef(rangerServiceDef);
    }

    @Test
    public void test75GetPolicyByGUIDAndServiceNameAndZoneName() throws Exception {
        RangerPolicy    rangerPolicy   = rangerPolicy();
        RangerService   rangerService  = rangerService();
        String          serviceName    = rangerService.getName();
        String          zoneName       = "zone-1";
        String          userName       = "admin";
        XXServiceDef    xServiceDef    = serviceDef();
        XXService       xService       = xService();
        XXServiceDefDao xServiceDefDao = Mockito.mock(XXServiceDefDao.class);
        XXServiceDao    xServiceDao    = Mockito.mock(XXServiceDao.class);
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        Mockito.when(bizUtil.getCurrentUserLoginId()).thenReturn(userName);
        Mockito.when(daoManager.getXXService()).thenReturn(xServiceDao);
        Mockito.when(xServiceDao.findByName(Mockito.anyString())).thenReturn(xService);
        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(xServiceDefDao.getById(xService.getType())).thenReturn(xServiceDef);
        Mockito.when(svcStore.getPolicy(rangerPolicy.getGuid(), serviceName, zoneName)).thenReturn(rangerPolicy);
        RangerPolicy dbRangerPolicy = serviceREST.getPolicyByGUIDAndServiceNameAndZoneName(rangerPolicy.getGuid(), serviceName, zoneName);
        Assert.assertNotNull(dbRangerPolicy);
        Assert.assertEquals(dbRangerPolicy.getId(), rangerPolicy.getId());
        Mockito.verify(svcStore).getPolicy(rangerPolicy.getGuid(), serviceName, zoneName);
    }

    @Test
    public void test76GetPolicyByGUID() throws Exception {
        RangerPolicy rangerPolicy = rangerPolicy();
        String       userName     = "admin";

        Set<String> userGroupsList = new HashSet<>();
        userGroupsList.add("group1");
        userGroupsList.add("group2");

        List<RangerAccessTypeDef> rangerAccessTypeDefList = new ArrayList<>();
        RangerAccessTypeDef       rangerAccessTypeDefObj  = new RangerAccessTypeDef();
        rangerAccessTypeDefObj.setLabel("Read");
        rangerAccessTypeDefObj.setName("read");
        rangerAccessTypeDefObj.setRbKeyLabel(null);
        rangerAccessTypeDefList.add(rangerAccessTypeDefObj);
        XXServiceDef    xServiceDef    = serviceDef();
        XXService       xService       = xService();
        XXServiceDefDao xServiceDefDao = Mockito.mock(XXServiceDefDao.class);
        XXServiceDao    xServiceDao    = Mockito.mock(XXServiceDao.class);
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        Mockito.when(bizUtil.getCurrentUserLoginId()).thenReturn(userName);
        Mockito.when(daoManager.getXXService()).thenReturn(xServiceDao);
        Mockito.when(xServiceDao.findByName(Mockito.anyString())).thenReturn(xService);
        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(xServiceDefDao.getById(xService.getType())).thenReturn(xServiceDef);
        Mockito.when(svcStore.getPolicy(rangerPolicy.getGuid(), null, null)).thenReturn(rangerPolicy);
        RangerPolicy dbRangerPolicy = serviceREST.getPolicyByGUIDAndServiceNameAndZoneName(rangerPolicy.getGuid(), null, null);
        Assert.assertNotNull(dbRangerPolicy);
        Assert.assertEquals(dbRangerPolicy.getId(), rangerPolicy.getId());
        Mockito.verify(svcStore).getPolicy(rangerPolicy.getGuid(), null, null);
    }

    @Test
    public void test76DeletePolicyByGUIDAndServiceNameAndZoneName() throws Exception {
        RangerPolicy  rangerPolicy  = rangerPolicy();
        RangerService rangerService = rangerService();
        String        serviceName   = rangerService.getName();
        Mockito.when(validatorFactory.getPolicyValidator(svcStore)).thenReturn(policyValidator);
        String          zoneName       = "zone-1";
        String          userName       = "admin";
        XXServiceDef    xServiceDef    = serviceDef();
        XXService       xService       = xService();
        XXServiceDefDao xServiceDefDao = Mockito.mock(XXServiceDefDao.class);
        XXServiceDao    xServiceDao    = Mockito.mock(XXServiceDao.class);
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        Mockito.when(bizUtil.getCurrentUserLoginId()).thenReturn(userName);
        Mockito.when(daoManager.getXXService()).thenReturn(xServiceDao);
        Mockito.when(xServiceDao.findByName(Mockito.anyString())).thenReturn(xService);
        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(xServiceDefDao.getById(xService.getType())).thenReturn(xServiceDef);
        Mockito.when(svcStore.getPolicy(Id)).thenReturn(rangerPolicy);
        Mockito.when(svcStore.getPolicy(rangerPolicy.getGuid(), serviceName, zoneName)).thenReturn(rangerPolicy);
        serviceREST.deletePolicyByGUIDAndServiceNameAndZoneName(rangerPolicy.getGuid(), serviceName, zoneName);
        Mockito.verify(validatorFactory).getPolicyValidator(svcStore);
        Mockito.verify(svcStore).getPolicy(rangerPolicy.getGuid(), serviceName, zoneName);
    }

    @Test
    public void test77DeletePolicyByGUID() throws Exception {
        RangerPolicy rangerPolicy = rangerPolicy();
        Mockito.when(validatorFactory.getPolicyValidator(svcStore)).thenReturn(policyValidator);
        String          userName       = "admin";
        XXServiceDef    xServiceDef    = serviceDef();
        XXService       xService       = xService();
        XXServiceDefDao xServiceDefDao = Mockito.mock(XXServiceDefDao.class);
        XXServiceDao    xServiceDao    = Mockito.mock(XXServiceDao.class);
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        Mockito.when(bizUtil.getCurrentUserLoginId()).thenReturn(userName);
        Mockito.when(daoManager.getXXService()).thenReturn(xServiceDao);
        Mockito.when(xServiceDao.findByName(Mockito.anyString())).thenReturn(xService);
        Mockito.when(daoManager.getXXServiceDef()).thenReturn(xServiceDefDao);
        Mockito.when(xServiceDefDao.getById(xService.getType())).thenReturn(xServiceDef);
        Mockito.when(svcStore.getPolicy(Id)).thenReturn(rangerPolicy);
        Mockito.when(svcStore.getPolicy(rangerPolicy.getGuid(), null, null)).thenReturn(rangerPolicy);
        serviceREST.deletePolicyByGUIDAndServiceNameAndZoneName(rangerPolicy.getGuid(), null, null);
        Mockito.verify(validatorFactory).getPolicyValidator(svcStore);
        Mockito.verify(svcStore).getPolicy(rangerPolicy.getGuid(), null, null);
    }

    @Test
    public void test78ResetPolicyCacheByServiceNameForServiceAdmin() {
        boolean       isAdmin       = false;
        boolean       res           = true;
        RangerService rangerService = rangerService();
        String        serviceName   = rangerService.getName();
        Mockito.when(bizUtil.isAdmin()).thenReturn(isAdmin);
        String userName = "admin";
        Mockito.when(bizUtil.getCurrentUserLoginId()).thenReturn(userName);
        try {
            Mockito.when(svcStore.getServiceByName(serviceName)).thenReturn(rangerService);
        } catch (Exception ignored) {
        }
        Mockito.when(bizUtil.isUserServiceAdmin(Mockito.any(RangerService.class), Mockito.anyString())).thenReturn(true);
        try {
            Mockito.when(svcStore.resetPolicyCache(serviceName)).thenReturn(true);
        } catch (Exception ignored) {
        }
        boolean isReset = serviceREST.resetPolicyCache(serviceName);
        Assert.assertEquals(res, isReset);
        Mockito.verify(bizUtil).isAdmin();
        Mockito.verify(bizUtil).isUserServiceAdmin(Mockito.any(RangerService.class), Mockito.anyString());
        try {
            Mockito.verify(svcStore).getServiceByName(serviceName);
        } catch (Exception ignored) {
        }
        try {
            Mockito.verify(svcStore).resetPolicyCache(serviceName);
        } catch (Exception ignored) {
        }
    }

    @Test
    public void test79ResetPolicyCacheWhenServiceNameIsInvalid() {
        String serviceName = "HDFS_1";
        try {
            Mockito.when(svcStore.getServiceByName(serviceName)).thenReturn(null);
        } catch (Exception ignored) {
        }
        Mockito.when(restErrorUtil.createRESTException(Mockito.anyInt(), Mockito.anyString(), Mockito.anyBoolean())).thenThrow(new WebApplicationException());
        thrown.expect(WebApplicationException.class);
        serviceREST.resetPolicyCache(serviceName);
        Mockito.verify(restErrorUtil).createRESTException(Mockito.anyInt(), Mockito.anyString(), Mockito.anyBoolean());
    }

    @Test
    public void test80GetPolicyByNameAndServiceNameWithZoneName() {
        RangerPolicy  rangerPolicy  = rangerPolicy();
        RangerService rangerService = rangerService();
        XXPolicy      xxPolicy      = new XXPolicy();
        String        serviceName   = rangerService.getName();
        String        policyName    = rangerPolicy.getName();
        String        zoneName      = "zone-1";
        XXPolicyDao   xXPolicyDao   = Mockito.mock(XXPolicyDao.class);
        Mockito.when(daoManager.getXXPolicy()).thenReturn(xXPolicyDao);
        Mockito.when(daoManager.getXXPolicy().findPolicy(policyName, serviceName, zoneName)).thenReturn(xxPolicy);
        Mockito.when(policyService.getPopulatedViewObject(xxPolicy)).thenReturn(rangerPolicy);
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        RangerPolicy dbRangerPolicy = serviceREST.getPolicyByName(serviceName, policyName, zoneName);
        Assert.assertNotNull(dbRangerPolicy);
        Assert.assertEquals(dbRangerPolicy, rangerPolicy);
        Assert.assertEquals(dbRangerPolicy.getId(), rangerPolicy.getId());
        Assert.assertEquals(dbRangerPolicy.getName(), rangerPolicy.getName());
    }

    @Test
    public void test81GetPolicyByNameAndServiceNameWithZoneNameIsNull() {
        RangerPolicy  rangerPolicy  = rangerPolicy();
        RangerService rangerService = rangerService();
        XXPolicy      xxPolicy      = new XXPolicy();
        String        serviceName   = rangerService.getName();
        String        policyName    = rangerPolicy.getName();
        XXPolicyDao   xXPolicyDao   = Mockito.mock(XXPolicyDao.class);
        Mockito.when(daoManager.getXXPolicy()).thenReturn(xXPolicyDao);
        Mockito.when(daoManager.getXXPolicy().findPolicy(policyName, serviceName, null)).thenReturn(xxPolicy);
        Mockito.when(policyService.getPopulatedViewObject(xxPolicy)).thenReturn(rangerPolicy);
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        RangerPolicy dbRangerPolicy = serviceREST.getPolicyByName(serviceName, policyName, null);
        Assert.assertNotNull(dbRangerPolicy);
        Assert.assertEquals(dbRangerPolicy, rangerPolicy);
        Assert.assertEquals(dbRangerPolicy.getId(), rangerPolicy.getId());
        Assert.assertEquals(dbRangerPolicy.getName(), rangerPolicy.getName());
    }

    RangerPolicy rangerPolicy() {
        List<RangerPolicyItemAccess>    accesses         = new ArrayList<>();
        List<String>                    users            = new ArrayList<>();
        List<String>                    groups           = new ArrayList<>();
        List<RangerPolicyItemCondition> conditions       = new ArrayList<>();
        List<RangerPolicyItem>          policyItems      = new ArrayList<>();
        RangerPolicyItem                rangerPolicyItem = new RangerPolicyItem();
        rangerPolicyItem.setAccesses(accesses);
        rangerPolicyItem.setConditions(conditions);
        rangerPolicyItem.setGroups(groups);
        rangerPolicyItem.setUsers(users);
        rangerPolicyItem.setDelegateAdmin(false);

        policyItems.add(rangerPolicyItem);

        Map<String, RangerPolicyResource> policyResource       = new HashMap<>();
        RangerPolicyResource              rangerPolicyResource = new RangerPolicyResource();
        rangerPolicyResource.setIsExcludes(true);
        rangerPolicyResource.setIsRecursive(true);
        rangerPolicyResource.setValue("1");
        rangerPolicyResource.setValues(users);
        policyResource.put("resource", rangerPolicyResource);
        RangerPolicy policy = new RangerPolicy();
        policy.setId(Id);
        policy.setCreateTime(new Date());
        policy.setDescription("policy");
        policy.setGuid("policyguid");
        policy.setIsEnabled(true);
        policy.setName("HDFS_1-1-20150316062453");
        policy.setUpdatedBy("Admin");
        policy.setUpdateTime(new Date());
        policy.setService("HDFS_1-1-20150316062453");
        policy.setIsAuditEnabled(true);
        policy.setPolicyItems(policyItems);
        policy.setResources(policyResource);

        return policy;
    }

    private List<Long> createLongList() {
        List<Long> list = new ArrayList<>();
        list.add(1L);
        list.add(2L);
        list.add(3L);
        return list;
    }

    private ArrayList<String> createUserList() {
        ArrayList<String> userList = new ArrayList<>();
        userList.add("test-user-1");
        return userList;
    }

    private ArrayList<String> createGroupList() {
        ArrayList<String> groupList = new ArrayList<>();
        groupList.add("test-group-1");
        return groupList;
    }

    private ArrayList<String> createRoleList() {
        ArrayList<String> roleList = new ArrayList<>();
        roleList.add("test-role-1");
        return roleList;
    }

    private ArrayList<String> createGrantorGroupList() {
        ArrayList<String> grantorGroupList = new ArrayList<>();
        grantorGroupList.add("test-grantor-group-1");
        return grantorGroupList;
    }

    private HashMap<String, String> createResourceMap() {
        HashMap<String, String> resourceMap = new HashMap<>();
        resourceMap.put("test-resource-1", "test-resource-value-1");
        return resourceMap;
    }

    private ArrayList<String> createAccessTypeList() {
        ArrayList<String> accessTypeList = new ArrayList<>();
        accessTypeList.add("test-access-type-1");
        return accessTypeList;
    }

    private GrantRevokeRequest createValidGrantRevokeRequest() {
        GrantRevokeRequest grantRevokeRequest = new GrantRevokeRequest();
        grantRevokeRequest.setUsers(new HashSet<>(createUserList()));
        grantRevokeRequest.setGroups(new HashSet<>(createGroupList()));
        grantRevokeRequest.setRoles(new HashSet<>(createRoleList()));
        grantRevokeRequest.setGrantor(grantor);
        grantRevokeRequest.setGrantorGroups(new HashSet<>(createGrantorGroupList()));
        grantRevokeRequest.setOwnerUser(ownerUser);
        grantRevokeRequest.setResource(createResourceMap());
        grantRevokeRequest.setAccessTypes(new HashSet<>(createAccessTypeList()));
        grantRevokeRequest.setZoneName(zoneName);
        grantRevokeRequest.setIsRecursive(true);
        return grantRevokeRequest;
    }

    private File getFile(String testFilePath) throws IOException {
        File jsonPolicyFile = new File(testFilePath);
        if (jsonPolicyFile.getCanonicalPath().contains("/target/jstest")) {
            jsonPolicyFile = new File(jsonPolicyFile.getCanonicalPath().replace("/target/jstest", ""));
        }
        return jsonPolicyFile;
    }
}
