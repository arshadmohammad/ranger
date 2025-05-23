/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ranger.rest;

import com.sun.jersey.core.header.FormDataContentDisposition;
import org.apache.ranger.admin.client.datatype.RESTResponse;
import org.apache.ranger.biz.AssetMgr;
import org.apache.ranger.biz.RangerBizUtil;
import org.apache.ranger.biz.RoleDBStore;
import org.apache.ranger.biz.RoleRefUpdater;
import org.apache.ranger.biz.ServiceDBStore;
import org.apache.ranger.biz.ServiceDBStore.JSON_FILE_NAME_TYPE;
import org.apache.ranger.biz.XUserMgr;
import org.apache.ranger.common.ContextUtil;
import org.apache.ranger.common.RESTErrorUtil;
import org.apache.ranger.common.RangerSearchUtil;
import org.apache.ranger.common.RangerValidatorFactory;
import org.apache.ranger.common.ServiceUtil;
import org.apache.ranger.common.UserSessionBase;
import org.apache.ranger.db.RangerDaoManager;
import org.apache.ranger.db.XXRoleDao;
import org.apache.ranger.db.XXRoleRefGroupDao;
import org.apache.ranger.db.XXRoleRefRoleDao;
import org.apache.ranger.db.XXRoleRefUserDao;
import org.apache.ranger.entity.XXPortalUser;
import org.apache.ranger.entity.XXRoleRefGroup;
import org.apache.ranger.entity.XXRoleRefUser;
import org.apache.ranger.entity.XXService;
import org.apache.ranger.plugin.model.RangerPolicy;
import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyItem;
import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyResource;
import org.apache.ranger.plugin.model.RangerRole;
import org.apache.ranger.plugin.model.validation.RangerRoleValidator;
import org.apache.ranger.plugin.util.GrantRevokeRoleRequest;
import org.apache.ranger.plugin.util.RangerRoles;
import org.apache.ranger.plugin.util.SearchFilter;
import org.apache.ranger.security.context.RangerContextHolder;
import org.apache.ranger.security.context.RangerSecurityContext;
import org.apache.ranger.service.RangerRoleService;
import org.apache.ranger.service.XUserService;
import org.apache.ranger.view.RangerRoleList;
import org.apache.ranger.view.VXUser;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.AdditionalAnswers;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;

@RunWith(MockitoJUnitRunner.class)
public class TestRoleREST {
    private static final Long                userId       = 8L;
    private static final Long                roleId       = 9L;
    private static final Long                Id           = 7L;
    private static final String              adminLoginID = "admin";
    private static final JSON_FILE_NAME_TYPE ROLE         = JSON_FILE_NAME_TYPE.ROLE;

    String importRoleTestFilePath = "./src/test/java/org/apache/ranger/rest/importRole/import_role_test_file.json";
    @Mock
    RangerRole role;

    @Mock
    RESTErrorUtil restErrorUtil;
    @Mock
    AssetMgr      assetMgr;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    RangerDaoManager  daoMgr;
    @Mock
    RoleDBStore       roleStore;
    @Mock
    RangerRoleService roleService;

    @Mock
    XUserService xUserService;

    @Mock
    ServiceDBStore svcStore;

    @Mock
    RangerSearchUtil searchUtil;

    @Mock
    ServiceUtil serviceUtil;

    @Mock
    RangerValidatorFactory validatorFactory;

    @Mock
    RangerBizUtil bizUtil;

    @Mock
    XUserMgr userMgr;

    @Mock
    XXRoleRefUserDao xRoleUserDao;

    @Mock
    XXRoleRefGroupDao xRoleGroupDao;

    @Mock
    XXRoleRefRoleDao xRoleRoleDao;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    RoleRefUpdater roleRefUpdater;

    @InjectMocks private RoleREST roleRest = new RoleREST();

    @Before
    public void setup() {
        RangerSecurityContext context = new RangerSecurityContext();
        context.setUserSession(new UserSessionBase());
        RangerContextHolder.setSecurityContext(context);
        UserSessionBase currentUserSession = ContextUtil.getCurrentUserSession();
        currentUserSession.setUserAdmin(true);
        XXPortalUser xXPortalUser = new XXPortalUser();
        xXPortalUser.setLoginId(adminLoginID);
        xXPortalUser.setId(userId);
        currentUserSession.setXXPortalUser(xXPortalUser);
    }

    @After
    public void destroySession() {
        RangerSecurityContext context = new RangerSecurityContext();
        context.setUserSession(null);
        RangerContextHolder.setSecurityContext(context);
    }

    @Test
    public void test1CreateRole() {
        boolean createNonExistUserGroup = true;
        Mockito.when(validatorFactory.getRangerRoleValidator(roleStore)).thenReturn(Mockito.mock(RangerRoleValidator.class));
        Mockito.when(bizUtil.isUserRangerAdmin(Mockito.anyString())).thenReturn(true);
        RangerRole rangerRole = createRole();
        try {
            Mockito.when(roleStore.createRole(Mockito.any(RangerRole.class), eq(createNonExistUserGroup))).thenReturn(rangerRole);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        RangerRole createdRole = roleRest.createRole("admin", rangerRole, createNonExistUserGroup);
        Assert.assertNotNull(createdRole);
        Assert.assertEquals(createdRole.getName(), rangerRole.getName());
        Assert.assertEquals(createdRole.getDescription(), rangerRole.getDescription());
        Assert.assertEquals(createdRole.getCreatedByUser(), rangerRole.getCreatedByUser());
    }

    @Test
    public void test2UpdateRole() {
        Boolean    createNonExistUserGroup = Boolean.TRUE;
        RangerRole rangerRole              = createRole();
        RangerRole rangerRoleOld           = createRoleOld();
        Mockito.when(validatorFactory.getRangerRoleValidator(roleStore)).thenReturn(Mockito.mock(RangerRoleValidator.class));
        XXRoleDao xxRoleDao = Mockito.mock(XXRoleDao.class);
        Mockito.when(daoMgr.getXXRole()).thenReturn(xxRoleDao);
        Mockito.when(daoMgr.getXXPolicyRefRole().findRoleRefPolicyCount(Mockito.anyString())).thenReturn(0L);
        try {
            Mockito.when(roleStore.getRole(Mockito.anyLong())).thenReturn(rangerRoleOld);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            Mockito.when(roleStore.updateRole(Mockito.any(RangerRole.class), Mockito.anyBoolean())).thenReturn(rangerRole);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        RangerRole updatedRole = roleRest.updateRole(roleId, rangerRole, eq(createNonExistUserGroup));
        Assert.assertNotNull(updatedRole);
        Assert.assertEquals(updatedRole.getName(), rangerRole.getName());
        Assert.assertEquals(updatedRole.getUsers(), rangerRole.getUsers());
    }

    @Test
    public void test3DeleteRoleByName() {
        RangerRole rangerRole = createRole();
        Mockito.doReturn(true).when(bizUtil).isUserRangerAdmin(Mockito.anyString());
        Mockito.when(validatorFactory.getRangerRoleValidator(roleStore)).thenReturn(Mockito.mock(RangerRoleValidator.class));
        roleRest.deleteRole("admin", adminLoginID, rangerRole.getName());
        try {
            Mockito.verify(roleStore, Mockito.times(1)).deleteRole(Mockito.anyString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void test4DeleteRoleById() {
        RangerRole rangerRole = createRole();
        Mockito.doReturn(true).when(bizUtil).isUserRangerAdmin(Mockito.anyString());
        Mockito.when(validatorFactory.getRangerRoleValidator(roleStore)).thenReturn(Mockito.mock(RangerRoleValidator.class));
        roleRest.deleteRole(rangerRole.getId());
        try {
            Mockito.verify(roleStore, Mockito.times(1)).deleteRole(Mockito.anyLong());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void test5GetRoleByName() {
        RangerRole rangerRole = createRole();
        Mockito.doReturn(true).when(bizUtil).isUserRangerAdmin(Mockito.anyString());
        try {
            Mockito.when(roleStore.getRole(Mockito.anyString())).thenReturn(rangerRole);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        RangerRole returnedRole = roleRest.getRole("admin", adminLoginID, rangerRole.getName());
        Assert.assertNotNull(returnedRole);
        Assert.assertEquals(returnedRole.getName(), rangerRole.getName());
    }

    @Test
    public void test6GetRoleById() {
        RangerRole rangerRole = createRole();
        try {
            Mockito.when(roleStore.getRole(Mockito.anyLong())).thenReturn(rangerRole);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        RangerRole returnedRole = roleRest.getRole(eq(rangerRole.getId()));
        Assert.assertNotNull(returnedRole);
        Assert.assertEquals(returnedRole.getName(), rangerRole.getName());
        Assert.assertEquals(returnedRole.getId(), rangerRole.getId());
    }

    @Test
    public void test7GetAllRoles() {
        RangerRoleList rangerRoleList = new RangerRoleList();
        Mockito.when(searchUtil.getSearchFilter(Mockito.any(HttpServletRequest.class), eq(roleService.sortFields))).thenReturn(Mockito.mock(SearchFilter.class));
        Mockito.when(bizUtil.isUserRangerAdmin(Mockito.anyString())).thenReturn(true);
        RangerRoleList returnedRangerRoleList = roleRest.getAllRoles(Mockito.mock(HttpServletRequest.class));
        Assert.assertNotNull(returnedRangerRoleList);
        Assert.assertEquals(returnedRangerRoleList.getListSize(), rangerRoleList.getListSize());
    }

    @Test
    public void test8GetAllRolesForUser() {
        RangerRoleList rangerRoleList = new RangerRoleList();
        SearchFilter   searchFilter   = new SearchFilter();
        Mockito.when(searchUtil.getSearchFilter(Mockito.any(HttpServletRequest.class), eq(roleService.sortFields))).thenReturn(searchFilter);
        RangerRoleList returnedRangerRoleList = roleRest.getAllRolesForUser(Mockito.mock(HttpServletRequest.class));
        Assert.assertNotNull(returnedRangerRoleList);
        Assert.assertEquals(returnedRangerRoleList.getListSize(), rangerRoleList.getListSize());
    }

    @Test
    public void test9GetAllRoleNames() {
        List<String> roleList = createRoleList();
        Mockito.when(searchUtil.getSearchFilter(Mockito.any(HttpServletRequest.class), eq(roleService.sortFields))).thenReturn(Mockito.mock(SearchFilter.class));
        Mockito.when(bizUtil.isUserRangerAdmin(Mockito.anyString())).thenReturn(true);
        try {
            Mockito.when(roleStore.getRoleNames(Mockito.any(SearchFilter.class))).thenReturn(roleList);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        List<String> returnedRoleList = roleRest.getAllRoleNames(adminLoginID, adminLoginID, Mockito.mock(HttpServletRequest.class));
        Assert.assertNotNull(returnedRoleList);
        Assert.assertEquals(returnedRoleList.size(), roleList.size());
    }

    @Test
    public void test10AddUsersAndGroups() {
        RangerRole   rangerRole = createRole();
        List<String> users      = new ArrayList<>(Arrays.asList("test-role", "admin"));
        List<String> groups     = new ArrayList<>(Arrays.asList("group1", "group2"));
        Boolean      isAdmin    = true;
        Mockito.when(bizUtil.isUserRangerAdmin(Mockito.anyString())).thenReturn(true);
        try {
            Mockito.when(roleStore.getRole(Mockito.anyLong())).thenReturn(rangerRole);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            Mockito.when(roleStore.updateRole(Mockito.any(RangerRole.class), Mockito.anyBoolean())).then(AdditionalAnswers.returnsFirstArg());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        RangerRole returnedRole = roleRest.addUsersAndGroups(roleId, users, groups, isAdmin);
        Assert.assertNotNull(returnedRole);
        Assert.assertEquals(returnedRole.getUsers().size(), users.size());
        Assert.assertEquals(returnedRole.getGroups().size(), groups.size());
    }

    @Test
    public void test11RemoveUsersAndGroups() {
        RangerRole   rangerRole       = createRoleWithUsersAndGroups();
        List<String> users            = new ArrayList<>(Arrays.asList("test-role", "admin"));
        List<String> groups           = new ArrayList<>(Arrays.asList("test-group", "admin"));
        List<String> createdRoleUsers = new ArrayList<>();
        for (RangerRole.RoleMember roleMember : rangerRole.getUsers()) {
            createdRoleUsers.add(roleMember.getName());
        }
        List<String> createdRoleGroups = new ArrayList<>();
        for (RangerRole.RoleMember groupMember : rangerRole.getGroups()) {
            createdRoleGroups.add(groupMember.getName());
        }
        Mockito.when(bizUtil.isUserRangerAdmin(Mockito.anyString())).thenReturn(true);
        try {
            Mockito.when(roleStore.getRole(Mockito.anyLong())).thenReturn(rangerRole);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            Mockito.when(roleStore.updateRole(Mockito.any(RangerRole.class), Mockito.anyBoolean())).then(AdditionalAnswers.returnsFirstArg());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        RangerRole returnedRole = roleRest.removeUsersAndGroups(roleId, users, groups);
        Assert.assertNotNull(returnedRole);
        Assert.assertEquals(createdRoleUsers, users);
        Assert.assertEquals(createdRoleGroups, groups);
        Assert.assertEquals(0, returnedRole.getUsers().size());
        Assert.assertEquals(0, returnedRole.getGroups().size());
    }

    @Test
    public void test12RemoveAdminFromUsersAndGroups() {
        RangerRole rangerRole = createRoleWithUsersAndGroups();
        for (RangerRole.RoleMember role : rangerRole.getUsers()) {
            Assert.assertTrue(role.getIsAdmin());
        }
        for (RangerRole.RoleMember group : rangerRole.getGroups()) {
            Assert.assertTrue(group.getIsAdmin());
        }
        List<String> users            = new ArrayList<>(Arrays.asList("test-role", "admin"));
        List<String> groups           = new ArrayList<>(Arrays.asList("test-group", "admin"));
        List<String> createdRoleUsers = new ArrayList<>();
        for (RangerRole.RoleMember roleMember : rangerRole.getUsers()) {
            createdRoleUsers.add(roleMember.getName());
        }
        List<String> createdRoleGroups = new ArrayList<>();
        for (RangerRole.RoleMember groupMember : rangerRole.getGroups()) {
            createdRoleGroups.add(groupMember.getName());
        }
        Mockito.when(bizUtil.isUserRangerAdmin(Mockito.anyString())).thenReturn(true);
        try {
            Mockito.when(roleStore.getRole(Mockito.anyLong())).thenReturn(rangerRole);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            Mockito.when(roleStore.updateRole(Mockito.any(RangerRole.class), Mockito.anyBoolean())).then(AdditionalAnswers.returnsFirstArg());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        RangerRole returnedRole = roleRest.removeAdminFromUsersAndGroups(roleId, users, groups);
        Assert.assertNotNull(returnedRole);
        Assert.assertEquals(createdRoleUsers, users);
        Assert.assertEquals(createdRoleGroups, groups);
        for (RangerRole.RoleMember role : returnedRole.getUsers()) {
            Assert.assertFalse(role.getIsAdmin());
        }
        for (RangerRole.RoleMember group : returnedRole.getGroups()) {
            Assert.assertFalse(group.getIsAdmin());
        }
    }

    @Test
    public void test13GrantRole() {
        RangerRole             rangerRole             = createRole();
        String                 serviceName            = "serviceName";
        GrantRevokeRoleRequest grantRevokeRoleRequest = createGrantRevokeRoleRequest();
        Mockito.when(bizUtil.isUserRangerAdmin(Mockito.anyString())).thenReturn(true, true, true);
        try {
            Mockito.when(roleStore.updateRole(Mockito.any(RangerRole.class), Mockito.anyBoolean())).then(AdditionalAnswers.returnsFirstArg());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            Mockito.when(roleStore.getRole(Mockito.anyString())).thenReturn(rangerRole, rangerRole, rangerRole, rangerRole);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        RESTResponse resp = roleRest.grantRole(serviceName, grantRevokeRoleRequest, Mockito.mock(HttpServletRequest.class));
        Assert.assertNotNull(resp);
        Assert.assertEquals(RESTResponse.STATUS_SUCCESS, resp.getStatusCode());
    }

    @Test
    public void test14RevokeRole() {
        RangerRole             rangerRole             = createRole();
        String                 serviceName            = "serviceName";
        GrantRevokeRoleRequest grantRevokeRoleRequest = createGrantRevokeRoleRequest();
        Mockito.when(bizUtil.isUserRangerAdmin(Mockito.anyString())).thenReturn(true, true, true);
        try {
            Mockito.when(roleStore.getRole(Mockito.anyString())).thenReturn(rangerRole, rangerRole, rangerRole, rangerRole);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            Mockito.when(roleStore.updateRole(Mockito.any(RangerRole.class), Mockito.anyBoolean())).then(AdditionalAnswers.returnsFirstArg());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        RESTResponse resp = roleRest.revokeRole(serviceName, grantRevokeRoleRequest, Mockito.mock(HttpServletRequest.class));
        Assert.assertNotNull(resp);
        Assert.assertEquals(RESTResponse.STATUS_SUCCESS, resp.getStatusCode());
    }

    @Test
    public void test15GetUserRoles() {
        Set<RangerRole> rangerRoles = new HashSet<>();
        RangerRole      rangerRole  = createRole();
        rangerRoles.add(rangerRole);
        List<XXRoleRefGroup> xxRoleRefGroupList = createXXRoleRefGroupList();
        List<XXRoleRefUser>  xxRoleRefRoleList  = createXXRoleRefUserList();
        Set<String>          groups             = new HashSet<>(Arrays.asList("group1", "group2"));
        Mockito.when(xUserService.getXUserByUserName(Mockito.anyString())).thenReturn(createVXUser());
        Mockito.when(userMgr.getGroupsForUser(Mockito.anyString())).thenReturn(groups);
        Mockito.when(roleRefUpdater.getRangerDaoManager().getXXRoleRefUser().findByUserName(adminLoginID)).thenReturn(xxRoleRefRoleList);
        Mockito.when(roleRefUpdater.getRangerDaoManager().getXXRoleRefGroup().findByGroupName(adminLoginID)).thenReturn(xxRoleRefGroupList);
        try {
            Mockito.when(roleStore.getRoleNames(Mockito.anyString(), eq(groups))).thenReturn(rangerRoles);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        List<String> returnedRoles = roleRest.getUserRoles(adminLoginID, Mockito.mock(HttpServletRequest.class));
        Assert.assertNotNull(returnedRoles);
        Assert.assertEquals(returnedRoles.size(), rangerRoles.size());
    }

    @Test
    public void test16GetRangerRolesIfUpdated() {
        RangerRoles rangerRoles        = createRangerRoles();
        String      serviceName        = "serviceName";
        String      pluginId           = "pluginId";
        String      clusterName        = "";
        String      pluginCapabilities = "";
        RangerRoles returnedRangeRoles;
        Mockito.when(serviceUtil.isValidService(Mockito.anyString(), Mockito.any(HttpServletRequest.class))).thenReturn(true);
        try {
            Mockito.when(roleStore.getRoles(Mockito.anyString(), Mockito.anyLong())).thenReturn(rangerRoles);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            returnedRangeRoles = roleRest.getRangerRolesIfUpdated(serviceName, -1L, 0L, pluginId, clusterName, pluginCapabilities, Mockito.mock(HttpServletRequest.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Assert.assertNotNull(returnedRangeRoles);
        Assert.assertEquals(returnedRangeRoles.getRangerRoles().size(), rangerRoles.getRangerRoles().size());
    }

    @Test
    public void test17GetSecureRangerRolesIfUpdated() {
        RangerRoles rangerRoles        = createRangerRoles();
        String      serviceName        = "serviceName";
        String      pluginId           = "pluginId";
        String      clusterName        = "";
        String      pluginCapabilities = "";
        RangerRoles returnedRangeRoles;
        Mockito.when(serviceUtil.isValidService(Mockito.anyString(), Mockito.any(HttpServletRequest.class))).thenReturn(true);
        try {
            Mockito.when(roleStore.getRoles(Mockito.anyString(), Mockito.anyLong())).thenReturn(rangerRoles);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Mockito.when(daoMgr.getXXService().findByName(Mockito.anyString())).thenReturn(createXXService());
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        try {
            returnedRangeRoles = roleRest.getSecureRangerRolesIfUpdated(serviceName, -1L, 0L, pluginId, clusterName, pluginCapabilities, Mockito.mock(HttpServletRequest.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Assert.assertNotNull(returnedRangeRoles);
        Assert.assertEquals(returnedRangeRoles.getRangerRoles().size(), rangerRoles.getRangerRoles().size());
    }

    @Test(expected = Throwable.class)
    public void test1bCreateRole() {
        boolean createNonExistUserGroup = true;
        Mockito.when(validatorFactory.getRangerRoleValidator(roleStore)).thenReturn(Mockito.mock(RangerRoleValidator.class));
        Mockito.when(bizUtil.isUserRangerAdmin(Mockito.anyString())).thenReturn(true);
        RangerRole rangerRole = createRoleInvalidMember();
        roleRest.createRole("admin", rangerRole, createNonExistUserGroup);
    }

    @Test(expected = Throwable.class)
    public void test2bUpdateRole() {
        Boolean    createNonExistUserGroup = Boolean.TRUE;
        RangerRole rangerRole              = createRoleInvalidMember();
        RangerRole rangerRoleOld           = createRoleOld();
        Mockito.when(validatorFactory.getRangerRoleValidator(roleStore)).thenReturn(Mockito.mock(RangerRoleValidator.class));
        XXRoleDao xxRoleDao = Mockito.mock(XXRoleDao.class);
        Mockito.when(daoMgr.getXXRole()).thenReturn(xxRoleDao);
        Mockito.when(daoMgr.getXXPolicyRefRole().findRoleRefPolicyCount(Mockito.anyString())).thenReturn(0L);
        try {
            Mockito.when(roleStore.getRole(Mockito.anyLong())).thenReturn(rangerRoleOld);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        roleRest.updateRole(roleId, rangerRole, eq(createNonExistUserGroup));
    }

    @Test(expected = Throwable.class)
    public void test3bDeleteRoleByName() {
        RangerRole rangerRole = createRole();
        Mockito.doReturn(false).when(bizUtil).isUserRangerAdmin(Mockito.anyString());
        Mockito.when(validatorFactory.getRangerRoleValidator(roleStore)).thenReturn(Mockito.mock(RangerRoleValidator.class));
        roleRest.deleteRole("admin", adminLoginID, rangerRole.getName());
        try {
            Mockito.verify(roleStore, Mockito.times(1)).deleteRole(Mockito.anyString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test(expected = Throwable.class)
    public void test4bDeleteRoleById() {
        RangerRole rangerRole = createRole();
        Mockito.when(validatorFactory.getRangerRoleValidator(roleStore)).thenReturn(Mockito.mock(RangerRoleValidator.class));
        roleRest.deleteRole(rangerRole.getId());
        try {
            Mockito.verify(roleStore, Mockito.times(1)).deleteRole(Mockito.anyLong());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test(expected = Throwable.class)
    public void test5bGetRoleByName() {
        RangerRole rangerRole = createRole();
        roleRest.getRole("admin", adminLoginID, rangerRole.getName());
    }

    @Test(expected = Throwable.class)
    public void test6bGetRoleById() {
        RangerRole rangerRole = createRole();
        try {
            Mockito.when(roleStore.getRole(Mockito.anyLong())).thenThrow(new Exception("test"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        roleRest.getRole(eq(rangerRole.getId()));
    }

    @Test(expected = Throwable.class)
    public void test7bGetAllRoles() {
        SearchFilter searchFilter = new SearchFilter();
        try {
            Mockito.when(roleStore.getRoles(searchFilter, Mockito.any(RangerRoleList.class))).thenThrow(new Exception("test"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Mockito.when(searchUtil.getSearchFilter(Mockito.any(HttpServletRequest.class), eq(roleService.sortFields))).thenReturn(Mockito.mock(SearchFilter.class));
        roleRest.getAllRoles(Mockito.mock(HttpServletRequest.class));
    }

    @Test
    public void test8bGetAllRolesForUser() {
        RangerRoleList rangerRoleList = new RangerRoleList();
        SearchFilter   searchFilter   = new SearchFilter();
        Mockito.when(searchUtil.getSearchFilter(Mockito.any(HttpServletRequest.class), eq(roleService.sortFields))).thenReturn(searchFilter);
        RangerRoleList returnedRangerRoleList = roleRest.getAllRolesForUser(Mockito.mock(HttpServletRequest.class));
        Assert.assertNotNull(returnedRangerRoleList);
        Assert.assertEquals(returnedRangerRoleList.getListSize(), rangerRoleList.getListSize());
    }

    @Test(expected = Throwable.class)
    public void test9bGetAllRoleNames() {
        List<String> roleList = createRoleList();
        Mockito.when(searchUtil.getSearchFilter(Mockito.any(HttpServletRequest.class), eq(roleService.sortFields))).thenReturn(Mockito.mock(SearchFilter.class));
        List<String> returnedRoleList = roleRest.getAllRoleNames(adminLoginID, adminLoginID, Mockito.mock(HttpServletRequest.class));
        Assert.assertNotNull(returnedRoleList);
        Assert.assertEquals(returnedRoleList.size(), roleList.size());
    }

    @Test
    public void test10bAddUsersAndGroups() {
        RangerRole   rangerRole         = createRoleWithUsersAndGroups();
        int          currentUsersCount  = rangerRole.getUsers().size();
        int          currentGroupsCount = rangerRole.getGroups().size();
        List<String> users              = new ArrayList<>(Arrays.asList("test-role2", "test-role3"));
        List<String> groups             = new ArrayList<>(Arrays.asList("test-group2", "test-group3"));
        Mockito.when(bizUtil.isUserRangerAdmin(Mockito.anyString())).thenReturn(true);
        try {
            Mockito.when(roleStore.getRole(Mockito.anyLong())).thenReturn(rangerRole);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            Mockito.when(roleStore.updateRole(Mockito.any(RangerRole.class), Mockito.anyBoolean())).then(AdditionalAnswers.returnsFirstArg());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        RangerRole returnedRole = roleRest.addUsersAndGroups(roleId, users, groups, true);
        Assert.assertNotNull(returnedRole);
        Assert.assertEquals(returnedRole.getGroups().size(), groups.size() + currentGroupsCount);
    }

    @Test(expected = Throwable.class)
    public void test10cAddUsersAndGroups() {
        RangerRole   rangerRole = createRole();
        List<String> users      = new ArrayList<>(Arrays.asList("{OWNER}", "test-role3"));
        List<String> groups     = new ArrayList<>(Arrays.asList("test-group2", "test-group3"));
        roleRest.addUsersAndGroups(roleId, users, groups, true);
    }

    @Test(expected = Throwable.class)
    public void test11bRemoveUsersAndGroups() {
        RangerRole   rangerRole       = createRole();
        List<String> users            = new ArrayList<>(Arrays.asList("test-role", "admin"));
        List<String> groups           = new ArrayList<>();
        List<String> createdRoleUsers = new ArrayList<>();
        for (RangerRole.RoleMember roleMember : rangerRole.getUsers()) {
            createdRoleUsers.add(roleMember.getName());
        }
        roleRest.removeUsersAndGroups(roleId, users, groups);
    }

    @Test(expected = Throwable.class)
    public void test12bRemoveAdminFromUsersAndGroups() {
        RangerRole rangerRole = createRole();
        for (RangerRole.RoleMember role : rangerRole.getUsers()) {
            Assert.assertTrue(role.getIsAdmin());
        }
        List<String> users            = new ArrayList<>(Arrays.asList("test-role", "admin"));
        List<String> groups           = new ArrayList<>();
        List<String> createdRoleUsers = new ArrayList<>();
        for (RangerRole.RoleMember roleMember : rangerRole.getUsers()) {
            createdRoleUsers.add(roleMember.getName());
        }
        roleRest.removeAdminFromUsersAndGroups(roleId, users, groups);
    }

    @Test(expected = Throwable.class)
    public void test13bGrantRole() {
        RangerRole             rangerRole             = createRole();
        String                 serviceName            = "serviceName";
        GrantRevokeRoleRequest grantRevokeRoleRequest = createGrantRevokeRoleRequest();
        roleRest.grantRole(serviceName, grantRevokeRoleRequest, Mockito.mock(HttpServletRequest.class));
    }

    @Test
    public void test14bRevokeRole() {
        RangerRole             rangerRole             = createRole();
        String                 serviceName            = "serviceName";
        GrantRevokeRoleRequest grantRevokeRoleRequest = createGrantRevokeRoleRequest();
        grantRevokeRoleRequest.setGrantOption(Boolean.TRUE);
        Mockito.when(bizUtil.isUserRangerAdmin(Mockito.anyString())).thenReturn(true, true, true);
        try {
            Mockito.when(roleStore.getRole(Mockito.anyString())).thenReturn(rangerRole, rangerRole, rangerRole, rangerRole);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            Mockito.when(roleStore.updateRole(Mockito.any(RangerRole.class), Mockito.anyBoolean())).then(AdditionalAnswers.returnsFirstArg());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        RESTResponse resp = roleRest.revokeRole(serviceName, grantRevokeRoleRequest, Mockito.mock(HttpServletRequest.class));
        Assert.assertNotNull(resp);
        Assert.assertEquals(RESTResponse.STATUS_SUCCESS, resp.getStatusCode());
    }

    @Test(expected = Throwable.class)
    public void test14cRevokeRole() {
        RangerRole             rangerRole             = createRole();
        String                 serviceName            = "serviceName";
        GrantRevokeRoleRequest grantRevokeRoleRequest = createGrantRevokeRoleRequest();
        grantRevokeRoleRequest.setGrantOption(Boolean.TRUE);
        grantRevokeRoleRequest.setGrantorGroups(new HashSet<>(Arrays.asList("group1", "group2")));
        roleRest.revokeRole(serviceName, grantRevokeRoleRequest, Mockito.mock(HttpServletRequest.class));
    }

    @Test(expected = Throwable.class)
    public void test15bGetUserRoles() {
        Set<RangerRole> rangerRoles = new HashSet<>();
        RangerRole      rangerRole  = createRole();
        rangerRoles.add(rangerRole);
        List<XXRoleRefGroup> xxRoleRefGroupList = createXXRoleRefGroupList();
        List<XXRoleRefUser>  xxRoleRefRoleList  = createXXRoleRefUserList();
        Set<String>          groups             = new HashSet<>(Arrays.asList("group1", "group2"));
        Mockito.when(xUserService.getXUserByUserName(Mockito.anyString())).thenReturn(null);
        Mockito.when(roleRefUpdater.getRangerDaoManager().getXXRoleRefUser().findByUserName(adminLoginID)).thenReturn(xxRoleRefRoleList);
        Mockito.when(roleRefUpdater.getRangerDaoManager().getXXRoleRefGroup().findByGroupName(adminLoginID)).thenReturn(xxRoleRefGroupList);
        roleRest.getUserRoles(adminLoginID, Mockito.mock(HttpServletRequest.class));
    }

    @Test(expected = Throwable.class)
    public void test16bGetRangerRolesIfUpdated() {
        RangerRoles rangerRoles        = createRangerRoles();
        String      serviceName        = "serviceName";
        String      pluginId           = "pluginId";
        String      clusterName        = "";
        String      pluginCapabilities = "";
        try {
            Mockito.doThrow(new Exception()).when(bizUtil).failUnauthenticatedDownloadIfNotAllowed();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            roleRest.getRangerRolesIfUpdated(serviceName, -1L, 0L, pluginId, clusterName, pluginCapabilities, Mockito.mock(HttpServletRequest.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test(expected = Throwable.class)
    public void test16cGetRangerRolesIfUpdated() {
        String serviceName        = "serviceName";
        String pluginId           = "pluginId";
        String clusterName        = "";
        String pluginCapabilities = "";
        Mockito.when(serviceUtil.isValidService(Mockito.anyString(), Mockito.any(HttpServletRequest.class))).thenReturn(true);
        try {
            Mockito.when(roleStore.getRoles(Mockito.anyString(), Mockito.anyLong())).thenReturn(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            roleRest.getRangerRolesIfUpdated(serviceName, -1L, 0L, pluginId, clusterName, pluginCapabilities, Mockito.mock(HttpServletRequest.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test(expected = Throwable.class)
    public void test17bGetSecureRangerRolesIfUpdated() {
        RangerRoles rangerRoles        = createRangerRoles();
        String      serviceName        = "serviceName";
        String      pluginId           = "pluginId";
        String      clusterName        = "";
        String      pluginCapabilities = "";
        Mockito.when(serviceUtil.isValidService(eq(null), Mockito.any(HttpServletRequest.class))).thenThrow(new Exception());
        try {
            Mockito.when(roleStore.getRoles(Mockito.anyString(), Mockito.anyLong())).thenReturn(rangerRoles);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Mockito.when(daoMgr.getXXService().findByName(Mockito.anyString())).thenReturn(createXXService());
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        try {
            roleRest.getSecureRangerRolesIfUpdated(serviceName, -1L, 0L, pluginId, clusterName, pluginCapabilities, Mockito.mock(HttpServletRequest.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test(expected = Throwable.class)
    public void test17cGetSecureRangerRolesIfUpdated() {
        String serviceName        = "serviceName";
        String pluginId           = "pluginId";
        String clusterName        = "";
        String pluginCapabilities = "";
        Mockito.when(serviceUtil.isValidService(Mockito.anyString(), Mockito.any(HttpServletRequest.class))).thenReturn(true);
        Mockito.when(daoMgr.getXXService().findByName(Mockito.anyString())).thenReturn(null);
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        try {
            roleRest.getSecureRangerRolesIfUpdated(serviceName, -1L, 0L, pluginId, clusterName, pluginCapabilities, Mockito.mock(HttpServletRequest.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test(expected = Throwable.class)
    public void test17dGetSecureRangerRolesIfUpdated() {
        String serviceName        = "serviceName";
        String pluginId           = "pluginId";
        String clusterName        = "";
        String pluginCapabilities = "";
        Mockito.when(serviceUtil.isValidService(Mockito.anyString(), Mockito.any(HttpServletRequest.class))).thenReturn(true);
        try {
            Mockito.when(roleStore.getRoles(Mockito.anyString(), Mockito.anyLong())).thenReturn(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Mockito.when(daoMgr.getXXService().findByName(Mockito.anyString())).thenReturn(createXXService());
        Mockito.when(bizUtil.isAdmin()).thenReturn(true);
        try {
            roleRest.getSecureRangerRolesIfUpdated(serviceName, -1L, 0L, pluginId, clusterName, pluginCapabilities, Mockito.mock(HttpServletRequest.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // empty request roles (requestParamRoles = 0, dbRoles = 5, return = all dbRoles)
    @Test
    public void test18GetRolesInJson() throws Exception {
        // pre-requisites
        List<RangerRole> rangerRolesProcessed = new ArrayList<>();

        rangerRolesProcessed.add(createRangerRole("role1", true));
        rangerRolesProcessed.add(createRangerRole("role2", false));
        rangerRolesProcessed.add(createRangerRole("role3", false));
        rangerRolesProcessed.add(createRangerRole("adm", true));
        rangerRolesProcessed.add(createRangerRole("user", false));

        // mock
        HttpServletRequest  requestMock  = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse responseMock = Mockito.mock(HttpServletResponse.class);

        // stubs
        Mockito.when(roleRest.getAllFilteredRoleList(requestMock)).thenReturn(rangerRolesProcessed);

        // test
        roleRest.getRolesInJson(requestMock, responseMock);
        Mockito.verify(svcStore).getObjectInJson(rangerRolesProcessed, responseMock, ROLE);
    }

    // non-empty request roles (requestParamRoles = 2, dbRoles = 5, return = 2 requestParamRoles)
    @Test
    public void test18bGetRolesInJson() throws Exception {
        // pre-requisites
        List<RangerRole> rangerRolesProcessed = new ArrayList<>();

        RangerRole admRole  = createRangerRole("adm", true);
        RangerRole userRole = createRangerRole("user", false);

        rangerRolesProcessed.add(admRole);
        rangerRolesProcessed.add(userRole);

        // mock
        HttpServletRequest  requestMock  = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse responseMock = Mockito.mock(HttpServletResponse.class);

        // stubs
        Mockito.when(roleRest.getAllFilteredRoleList(requestMock)).thenReturn(rangerRolesProcessed);

        // test
        roleRest.getRolesInJson(requestMock, responseMock);
        Mockito.verify(svcStore).getObjectInJson(rangerRolesProcessed, responseMock, ROLE);
    }

    // non-empty request roles (requestParamRoles = 3, dbRoles = 0, return = 0 dbRoles)
    @Test
    public void test18cGetRolesInJson() throws Exception {
        // pre-requisites
        List<RangerRole> rangerRolesProcessed = new ArrayList<>();

        // mock
        HttpServletRequest  requestMock  = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse responseMock = Mockito.mock(HttpServletResponse.class);

        // stubs
        Mockito.when(roleRest.getAllFilteredRoleList(requestMock)).thenReturn(rangerRolesProcessed);

        // test
        roleRest.getRolesInJson(requestMock, responseMock);
        Mockito.verify(svcStore, Mockito.never()).getObjectInJson(rangerRolesProcessed, responseMock, ROLE);
    }

    // getAllFilteredRoleList throws Exception
    @Test(expected = Throwable.class)
    public void test18dGetRolesInJson() throws Exception {
        // pre-requisites
        List<RangerRole> rangerRolesProcessed = new ArrayList<>();

        // mock
        HttpServletRequest  requestMock  = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse responseMock = Mockito.mock(HttpServletResponse.class);

        // stubs
        Mockito.when(roleRest.getAllFilteredRoleList(requestMock)).thenThrow(new Throwable());

        // test
        Assert.assertThrows(Throwable.class, () -> roleRest.getRolesInJson(requestMock, responseMock));
        Mockito.verify(svcStore, Mockito.never()).getObjectInJson(rangerRolesProcessed, responseMock, ROLE);
        Mockito.verify(restErrorUtil).createRESTException(Mockito.anyString());
    }

    // full match: requestParamRoles = 0, dbRoles = 5, return = all dbRoles
    @Test
    public void test19GetAllFilteredRoleList() throws Exception {
        // pre-requisites
        String           requestParamRoles            = "";
        List<RangerRole> rangerRolesDb                = new ArrayList<>();
        List<RangerRole> rangerRolesProcessedExpected = new ArrayList<>();

        rangerRolesDb.add(createRangerRole("role1", true));
        rangerRolesDb.add(createRangerRole("role2", false));
        rangerRolesDb.add(createRangerRole("role3", false));
        rangerRolesDb.add(createRangerRole("adm", true));
        rangerRolesDb.add(createRangerRole("user", false));

        rangerRolesProcessedExpected.addAll(rangerRolesDb);

        // mock
        HttpServletRequest requestMock = Mockito.mock(HttpServletRequest.class);

        // stubs
        Mockito.when(requestMock.getParameter(RoleREST.PARAM_ROLE_NAME)).thenReturn(requestParamRoles);
        Mockito.when(roleStore.getRoles(Mockito.any(SearchFilter.class))).thenReturn(rangerRolesDb);

        // test
        List<RangerRole> rangerRolesProcessedActual = roleRest.getAllFilteredRoleList(requestMock);
        Assert.assertNotNull(rangerRolesProcessedActual);
        Assert.assertEquals(rangerRolesProcessedActual.size(), rangerRolesProcessedExpected.size());
        Assert.assertEquals(rangerRolesProcessedActual, rangerRolesProcessedExpected);
    }

    // partial match: requestParamRoles = 2, dbRoles = 5, match = 2
    @Test
    public void test19bGetAllFilteredRoleList() throws Exception {
        // pre-requisites
        String           requestParamRoles            = "adm,user";
        List<RangerRole> rangerRolesDb                = new ArrayList<>();
        List<RangerRole> rangerRolesProcessedExpected = new ArrayList<>();

        rangerRolesDb.add(createRangerRole("role1", true));
        rangerRolesDb.add(createRangerRole("role2", false));
        rangerRolesDb.add(createRangerRole("role3", false));

        RangerRole admRole  = createRangerRole("adm", true);
        RangerRole userRole = createRangerRole("user", false);

        rangerRolesDb.add(admRole);
        rangerRolesDb.add(userRole);

        rangerRolesProcessedExpected.add(admRole);
        rangerRolesProcessedExpected.add(userRole);

        // mock
        HttpServletRequest requestMock = Mockito.mock(HttpServletRequest.class);

        // stubs
        Mockito.when(requestMock.getParameter(RoleREST.PARAM_ROLE_NAME)).thenReturn(requestParamRoles);
        Mockito.when(roleStore.getRoles(Mockito.any(SearchFilter.class))).thenReturn(rangerRolesDb);

        // test
        List<RangerRole> rangerRolesProcessedActual = roleRest.getAllFilteredRoleList(requestMock);
        Assert.assertNotNull(rangerRolesProcessedActual);
        Assert.assertEquals(rangerRolesProcessedActual.size(), rangerRolesProcessedExpected.size());
        Assert.assertEquals(rangerRolesProcessedActual, rangerRolesProcessedExpected);
    }

    // partial match: requestParamRoles = 4, dbRoles = 5, match = 2
    @Test
    public void test19cGetAllFilteredRoleList() throws Exception {
        // pre-requisites
        String           requestParamRoles            = "adm,key-adm,delegate-adm,user";
        List<RangerRole> rangerRolesDb                = new ArrayList<>();
        List<RangerRole> rangerRolesProcessedExpected = new ArrayList<>();

        rangerRolesDb.add(createRangerRole("role1", true));
        rangerRolesDb.add(createRangerRole("role2", false));
        rangerRolesDb.add(createRangerRole("role3", false));

        RangerRole admRole  = createRangerRole("adm", true);
        RangerRole userRole = createRangerRole("user", false);

        rangerRolesDb.add(admRole);
        rangerRolesDb.add(userRole);

        rangerRolesProcessedExpected.add(admRole);
        rangerRolesProcessedExpected.add(userRole);

        // mock
        HttpServletRequest requestMock = Mockito.mock(HttpServletRequest.class);

        // stubs
        Mockito.when(requestMock.getParameter(RoleREST.PARAM_ROLE_NAME)).thenReturn(requestParamRoles);
        Mockito.when(roleStore.getRoles(Mockito.any(SearchFilter.class))).thenReturn(rangerRolesDb);

        // test
        List<RangerRole> rangerRolesProcessedActual = roleRest.getAllFilteredRoleList(requestMock);
        Assert.assertNotNull(rangerRolesProcessedActual);
        Assert.assertEquals(rangerRolesProcessedActual.size(), rangerRolesProcessedExpected.size());
        Assert.assertEquals(rangerRolesProcessedActual, rangerRolesProcessedExpected);
    }

    // no match: requestParamRoles = 3, dbRoles = 5, match = 0
    @Test
    public void test19dGetAllFilteredRoleList() throws Exception {
        // pre-requisites
        String           requestParamRoles            = "sys-adm,key-adm,delegate-adm";
        List<RangerRole> rangerRolesDb                = new ArrayList<>();
        List<RangerRole> rangerRolesProcessedExpected = new ArrayList<>();

        rangerRolesDb.add(createRangerRole("role1", true));
        rangerRolesDb.add(createRangerRole("role2", false));
        rangerRolesDb.add(createRangerRole("role3", false));

        RangerRole admRole  = createRangerRole("adm", true);
        RangerRole userRole = createRangerRole("user", false);

        rangerRolesDb.add(admRole);
        rangerRolesDb.add(userRole);

        // mock
        HttpServletRequest requestMock = Mockito.mock(HttpServletRequest.class);

        // stubs
        Mockito.when(requestMock.getParameter(RoleREST.PARAM_ROLE_NAME)).thenReturn(requestParamRoles);
        Mockito.when(roleStore.getRoles(Mockito.any(SearchFilter.class))).thenReturn(rangerRolesDb);

        // test
        List<RangerRole> rangerRolesProcessedActual = roleRest.getAllFilteredRoleList(requestMock);
        Assert.assertNotNull(rangerRolesProcessedActual);
        Assert.assertEquals(rangerRolesProcessedActual.size(), rangerRolesProcessedExpected.size());
        Assert.assertEquals(rangerRolesProcessedActual, rangerRolesProcessedExpected);
    }

    // no match: requestParamRoles = 3, dbRoles = 0, match = 0
    @Test
    public void test19eGetAllFilteredRoleList() throws Exception {
        // pre-requisites
        String           requestParamRoles            = "sys-adm,key-adm,delegate-adm";
        List<RangerRole> rangerRolesDb                = new ArrayList<>();
        List<RangerRole> rangerRolesProcessedExpected = Collections.emptyList();

        // mock
        HttpServletRequest requestMock = Mockito.mock(HttpServletRequest.class);

        // stubs
        Mockito.when(requestMock.getParameter(RoleREST.PARAM_ROLE_NAME)).thenReturn(requestParamRoles);
        Mockito.when(roleStore.getRoles(Mockito.any(SearchFilter.class))).thenReturn(rangerRolesDb);

        // test
        List<RangerRole> rangerRolesProcessedActual = roleRest.getAllFilteredRoleList(requestMock);
        Assert.assertNotNull(rangerRolesProcessedActual);
        Assert.assertEquals(rangerRolesProcessedActual.size(), rangerRolesProcessedExpected.size());
        Assert.assertEquals(rangerRolesProcessedActual, rangerRolesProcessedExpected);
    }

    // full match: requestParamRoles = null, dbRoles = 5, return = all dbRoles
    @Test
    public void test19fGetAllFilteredRoleList() throws Exception {
        // pre-requisites
        String           requestParamRoles            = null;
        List<RangerRole> rangerRolesDb                = new ArrayList<>();
        List<RangerRole> rangerRolesProcessedExpected = new ArrayList<>();

        rangerRolesDb.add(createRangerRole("role1", true));
        rangerRolesDb.add(createRangerRole("role2", false));
        rangerRolesDb.add(createRangerRole("role3", false));
        rangerRolesDb.add(createRangerRole("adm", true));
        rangerRolesDb.add(createRangerRole("user", false));

        rangerRolesProcessedExpected.addAll(rangerRolesDb);

        // mock
        HttpServletRequest requestMock = Mockito.mock(HttpServletRequest.class);

        // stubs
        Mockito.when(requestMock.getParameter(RoleREST.PARAM_ROLE_NAME)).thenReturn(requestParamRoles);
        Mockito.when(roleStore.getRoles(Mockito.any(SearchFilter.class))).thenReturn(rangerRolesDb);

        // test
        List<RangerRole> rangerRolesProcessedActual = roleRest.getAllFilteredRoleList(requestMock);
        Assert.assertNotNull(rangerRolesProcessedActual);
        Assert.assertEquals(rangerRolesProcessedActual.size(), rangerRolesProcessedExpected.size());
        Assert.assertEquals(rangerRolesProcessedActual, rangerRolesProcessedExpected);
    }

    // import role with updateIfExists=false and createNonExistUserGroupRole=false
    @SuppressWarnings("unchecked")
    @Test
    public void test20importRolesFromFile() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        List<String> roleList   = createRoleList();
        RangerRole   rangerRole = createRole();

        SearchFilter filter = new SearchFilter();

        File        jsonRoleFile        = new File(importRoleTestFilePath);
        InputStream uploadedInputStream = new FileInputStream(jsonRoleFile);
        FormDataContentDisposition fileDetail = FormDataContentDisposition.name("file").fileName(jsonRoleFile.getName()).size(uploadedInputStream.toString().length()).build();
        boolean updateIfExists              = false;
        boolean createNonExistUserGroupRole = false;

        Mockito.when(searchUtil.getSearchFilter(request, roleService.sortFields)).thenReturn(filter);
        Mockito.when(roleStore.getRoleNames(Mockito.any(SearchFilter.class))).thenReturn(roleList);
        Mockito.when(roleStore.createRole(Mockito.any(RangerRole.class), eq(createNonExistUserGroupRole))).thenReturn(rangerRole);

        RESTResponse resp = roleRest.importRolesFromFile(request, uploadedInputStream, fileDetail, updateIfExists, createNonExistUserGroupRole);
        Assert.assertNotNull(resp);
        Assert.assertEquals(RESTResponse.STATUS_SUCCESS, resp.getStatusCode());
        Assert.assertEquals("Total Role Created = 6 , Total Role Unchanged = 1", resp.getMsgDesc());
    }

    // import role with updateIfExists=false and createNonExistUserGroupRole=true
    @SuppressWarnings("unchecked")
    @Test
    public void test20bimportRolesFromFile() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        List<String> roleList   = createRoleList();
        RangerRole   rangerRole = createRoleWithUsersAndGroups();

        SearchFilter filter = new SearchFilter();

        File        jsonRoleFile        = new File(importRoleTestFilePath);
        InputStream uploadedInputStream = new FileInputStream(jsonRoleFile);
        FormDataContentDisposition fileDetail = FormDataContentDisposition.name("file").fileName(jsonRoleFile.getName()).size(uploadedInputStream.toString().length()).build();
        boolean updateIfExists              = false;
        boolean createNonExistUserGroupRole = true;

        Mockito.when(searchUtil.getSearchFilter(request, roleService.sortFields)).thenReturn(filter);
        Mockito.when(roleStore.getRoleNames(Mockito.any(SearchFilter.class))).thenReturn(roleList);
        Mockito.when(roleStore.createRole(Mockito.any(RangerRole.class), eq(createNonExistUserGroupRole))).thenReturn(rangerRole);

        RESTResponse resp = roleRest.importRolesFromFile(request, uploadedInputStream, fileDetail, updateIfExists, createNonExistUserGroupRole);
        Assert.assertNotNull(resp);
        Assert.assertEquals(RESTResponse.STATUS_SUCCESS, resp.getStatusCode());
        Assert.assertEquals("Total Role Created = 6 , Total Role Unchanged = 1", resp.getMsgDesc());
    }

    // import role with updateIfExists=true and createNonExistUserGroupRole=true
    @SuppressWarnings("unchecked")
    @Test
    public void test20cimportRolesFromFileWithUpdate() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        List<String> roleList   = createRoleList();
        RangerRole   rangerRole = createRole();

        SearchFilter filter = new SearchFilter();

        File        jsonRoleFile        = new File(importRoleTestFilePath);
        InputStream uploadedInputStream = new FileInputStream(jsonRoleFile);
        FormDataContentDisposition fileDetail = FormDataContentDisposition.name("file").fileName(jsonRoleFile.getName()).size(uploadedInputStream.toString().length()).build();
        boolean updateIfExists              = true;
        boolean createNonExistUserGroupRole = true;

        Mockito.when(searchUtil.getSearchFilter(request, roleService.sortFields)).thenReturn(filter);
        Mockito.when(roleStore.getRoleNames(Mockito.any(SearchFilter.class))).thenReturn(roleList);
        Mockito.when(roleStore.getRole(Mockito.anyString())).thenReturn(rangerRole);
        Mockito.when(roleStore.createRole(Mockito.any(RangerRole.class), eq(createNonExistUserGroupRole))).thenReturn(rangerRole);

        RESTResponse resp = roleRest.importRolesFromFile(request, uploadedInputStream, fileDetail, updateIfExists, createNonExistUserGroupRole);
        Assert.assertNotNull(resp);
        Assert.assertEquals(RESTResponse.STATUS_SUCCESS, resp.getStatusCode());
        Assert.assertEquals("Total Role Created = 6 , Total Role Updated = 1 , Total Role Unchanged = 0", resp.getMsgDesc());
    }

    // import role throws exceptions
    @SuppressWarnings("unchecked")
    @Test(expected = Throwable.class)
    public void test20dimportRolesFromFileWithUpdate() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        File        jsonRoleFile        = new File(importRoleTestFilePath);
        InputStream uploadedInputStream = new FileInputStream(jsonRoleFile);
        FormDataContentDisposition fileDetail = FormDataContentDisposition.name("file").fileName(jsonRoleFile.getName()).size(uploadedInputStream.toString().length()).build();
        boolean updateIfExists              = false;
        boolean createNonExistUserGroupRole = false;

        Mockito.when(roleStore.getRoleNames(Mockito.any(SearchFilter.class))).thenThrow(new Throwable());

        Assert.assertThrows(Throwable.class, () -> roleRest.importRolesFromFile(request, uploadedInputStream, fileDetail, updateIfExists, createNonExistUserGroupRole));
        Mockito.verify(restErrorUtil).createRESTException(Mockito.anyString());
    }

    @Test(expected = Throwable.class)
    public void test21deleteRoleWithinPolicy() {
        RangerRole rangerRole = createRole();
        rangerPolicy(rangerRole);
        try {
            Mockito.doThrow(new Throwable()).when(roleStore).deleteRole(Mockito.anyLong());
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        try {
            Assert.assertThrows(Throwable.class, () -> roleRest.deleteRole(rangerRole.getId()));
            Mockito.verify(restErrorUtil, Mockito.times(1)).createRESTException(Mockito.anyString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test(expected = Throwable.class)
    public void test22deleteRoleWithValidationError() {
        RangerRole rangerRole = createRole();
        try {
            Mockito.when(validatorFactory.getRangerRoleValidator(roleStore)).thenThrow(new Exception());
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        try {
            Assert.assertThrows(Throwable.class, () -> roleRest.deleteRole(rangerRole.getId()));
            Mockito.verify(restErrorUtil, Mockito.times(1)).createRESTException(Mockito.anyString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private RangerRole createRole() {
        String                      name       = "test-role";
        String                      name2      = "admin";
        RangerRole.RoleMember       rm1        = new RangerRole.RoleMember(name, true);
        RangerRole.RoleMember       rm2        = new RangerRole.RoleMember(name2, true);
        List<RangerRole.RoleMember> usersList  = new ArrayList<>(Arrays.asList(rm1, rm2));
        RangerRole                  rangerRole = new RangerRole(name, name, null, usersList, null);
        rangerRole.setCreatedByUser(name);
        rangerRole.setId(roleId);
        return rangerRole;
    }

    private RangerRole createRoleWithUsersAndGroups() {
        String                      userName1  = "test-role";
        String                      userName2  = "admin";
        String                      groupName1 = "test-group";
        String                      groupName2 = "admin";
        RangerRole.RoleMember       rm1        = new RangerRole.RoleMember(userName1, true);
        RangerRole.RoleMember       rm2        = new RangerRole.RoleMember(userName2, true);
        List<RangerRole.RoleMember> usersList  = new ArrayList<>(Arrays.asList(rm1, rm2));
        RangerRole.RoleMember       rm3        = new RangerRole.RoleMember(groupName1, true);
        RangerRole.RoleMember       rm4        = new RangerRole.RoleMember(groupName2, true);
        List<RangerRole.RoleMember> groupList  = new ArrayList<>(Arrays.asList(rm3, rm4));

        RangerRole rangerRole = new RangerRole(userName1, userName1, null, usersList, groupList);
        rangerRole.setCreatedByUser(userName1);
        rangerRole.setId(roleId);
        return rangerRole;
    }

    private RangerRole createRoleInvalidMember() {
        String                      name       = "{OWNER}";
        String                      name2      = "admin";
        RangerRole.RoleMember       rm1        = new RangerRole.RoleMember(name, true);
        RangerRole.RoleMember       rm2        = new RangerRole.RoleMember(name2, true);
        List<RangerRole.RoleMember> usersList  = new ArrayList<>(Arrays.asList(rm1, rm2));
        RangerRole                  rangerRole = new RangerRole(name, name, null, usersList, null);
        rangerRole.setCreatedByUser(name);
        rangerRole.setId(roleId);
        return rangerRole;
    }

    private RangerRole createRoleOld() {
        String                      name       = "test-role2";
        String                      name2      = "admin";
        RangerRole.RoleMember       rm1        = new RangerRole.RoleMember(name, true);
        RangerRole.RoleMember       rm2        = new RangerRole.RoleMember(name2, true);
        List<RangerRole.RoleMember> usersList  = Arrays.asList(rm1, rm2);
        RangerRole                  rangerRole = new RangerRole(name, name, null, usersList, null);
        rangerRole.setCreatedByUser(name);
        rangerRole.setId(roleId);
        return rangerRole;
    }

    private VXUser createVXUser() {
        VXUser             testVXUser = new VXUser();
        Collection<String> c          = new ArrayList<>();
        testVXUser.setId(userId);
        testVXUser.setCreateDate(new Date());
        testVXUser.setUpdateDate(new Date());
        testVXUser.setOwner("admin");
        testVXUser.setUpdatedBy("admin");
        testVXUser.setName("User1");
        testVXUser.setFirstName("FnameUser1");
        testVXUser.setLastName("LnameUser1");
        testVXUser.setPassword("User1");
        testVXUser.setGroupIdList(null);
        testVXUser.setGroupNameList(null);
        testVXUser.setStatus(1);
        testVXUser.setIsVisible(1);
        testVXUser.setUserSource(0);
        c.add("ROLE_SYS_ADMIN");
        testVXUser.setUserRoleList(c);
        return testVXUser;
    }

    private List<XXRoleRefGroup> createXXRoleRefGroupList() {
        List<XXRoleRefGroup> xxRoleRefGroupList = new ArrayList<>();
        XXRoleRefGroup       xxRoleRefGroup1    = new XXRoleRefGroup();
        xxRoleRefGroup1.setRoleId(roleId);
        xxRoleRefGroupList.add(xxRoleRefGroup1);
        return xxRoleRefGroupList;
    }

    private List<XXRoleRefUser> createXXRoleRefUserList() {
        List<XXRoleRefUser> xxRoleRefUserList = new ArrayList<>();
        XXRoleRefUser       xxRoleRefUser1    = new XXRoleRefUser();
        xxRoleRefUser1.setRoleId(roleId);
        xxRoleRefUserList.add(xxRoleRefUser1);
        return xxRoleRefUserList;
    }

    private List<String> createRoleList() {
        List<String> roleList = new ArrayList<>();
        roleList.add("admin");
        roleList.add("user");
        return roleList;
    }

    private RangerRoles createRangerRoles() {
        Set<RangerRole> rangerRolesSet = new HashSet<>(Collections.singletonList(createRole()));
        RangerRoles     rangerRoles    = new RangerRoles();
        rangerRoles.setRangerRoles(rangerRolesSet);
        return rangerRoles;
    }

    private XXService createXXService() {
        XXService xxService = new XXService();
        xxService.setId(1L);
        xxService.setName("test-service");
        xxService.setDescription("test-service");
        xxService.setIsEnabled(true);
        xxService.setCreateTime(new Date());
        xxService.setUpdateTime(new Date());
        return xxService;
    }

    private GrantRevokeRoleRequest createGrantRevokeRoleRequest() {
        Set<String>            users       = new HashSet<>(Arrays.asList("test-role", "admin"));
        Set<String>            groups      = new HashSet<>(Arrays.asList("test-group", "admin"));
        GrantRevokeRoleRequest roleRequest = new GrantRevokeRoleRequest();
        roleRequest.setUsers(users);
        roleRequest.setGroups(groups);
        roleRequest.setGrantor("admin");
        roleRequest.setTargetRoles(new HashSet<>(Arrays.asList("role1", "role2")));
        return roleRequest;
    }

    private RangerRole createRangerRole(String name, boolean isAdmin) {
        RangerRole.RoleMember       roleMember = new RangerRole.RoleMember(name, isAdmin);
        List<RangerRole.RoleMember> usersList  = Collections.singletonList(roleMember);
        RangerRole                  rangerRole = new RangerRole(name, name, null, usersList, null);
        rangerRole.setCreatedByUser(name);
        rangerRole.setId(roleId);
        return rangerRole;
    }

    private RangerPolicy rangerPolicy(RangerRole rangerRole) {
        List<String> roles = new ArrayList<>();
        roles.add(rangerRole.getName());

        List<RangerPolicyItem> policyItems = new ArrayList<>();

        policyItems.add(new RangerPolicyItem(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), roles, new ArrayList<>(), false));

        Map<String, RangerPolicyResource> policyResource = new HashMap<>();

        policyResource.put("resource", new RangerPolicyResource("1", true, true));

        return getRangerPolicy(policyItems, policyResource);
    }

    private static RangerPolicy getRangerPolicy(List<RangerPolicyItem> policyItems, Map<String, RangerPolicyResource> policyResource) {
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
        policy.setService("HDFS_1");
        return policy;
    }
}
