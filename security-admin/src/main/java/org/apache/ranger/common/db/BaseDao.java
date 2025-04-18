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

package org.apache.ranger.common.db;

import org.apache.commons.lang3.StringUtils;
import org.apache.ranger.authorization.hadoop.config.RangerAdminConfig;
import org.apache.ranger.biz.RangerBizUtil;
import org.apache.ranger.common.AppConstants;
import org.apache.ranger.db.RangerDaoManager;
import org.apache.ranger.db.RangerDaoManagerBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.TypedQuery;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseDao<T> {
    private static final Logger logger = LoggerFactory.getLogger(BaseDao.class);

    private static final String PROP_BATCH_DELETE_BATCH_SIZE    = "ranger.admin.dao.batch.delete.batch.size";
    private static final int    DEFAULT_BATCH_DELETE_BATCH_SIZE = 1000;
    private static final String NOT_AVAILABLE                   = "Not Available";
    private static final String GDS_TABLES                      = "x_gds_";
    private static       int    BATCH_DELETE_BATCH_SIZE;

    protected RangerDaoManager daoManager;
    protected Class<T>         tClass;
    EntityManager              em;

    public BaseDao(RangerDaoManagerBase daoManager) {
        this.daoManager = (RangerDaoManager) daoManager;

        this.init(daoManager.getEntityManager());
    }

    public BaseDao(RangerDaoManagerBase daoManager, String persistenceContextUnit) {
        this.daoManager = (RangerDaoManager) daoManager;

        EntityManager em = this.daoManager.getEntityManager(persistenceContextUnit);

        this.init(em);
    }

    public EntityManager getEntityManager() {
        return this.em;
    }

    public T create(T obj) {
        T ret;

        em.persist(obj);

        if (!RangerBizUtil.isBulkMode()) {
            em.flush();
        }

        ret = obj;

        return ret;
    }

    public List<T> batchCreate(List<T> obj) {
        List<T> ret;

        for (int n = 0; n < obj.size(); ++n) {
            em.persist(obj.get(n));

            if (!RangerBizUtil.isBulkMode() && (n % RangerBizUtil.BATCH_PERSIST_SIZE == 0)) {
                em.flush();
            }
        }

        if (!RangerBizUtil.isBulkMode()) {
            em.flush();
        }

        ret = obj;

        return ret;
    }

    public void batchDeleteByIds(String namedQuery, List<Long> ids, String paramName) {
        if (BATCH_DELETE_BATCH_SIZE <= 0) {
            getEntityManager().createNamedQuery(namedQuery, tClass).setParameter(paramName, ids).executeUpdate();
        } else {
            for (int fromIndex = 0; fromIndex < ids.size(); fromIndex += BATCH_DELETE_BATCH_SIZE) {
                int toIndex = fromIndex + BATCH_DELETE_BATCH_SIZE;

                if (toIndex > ids.size()) {
                    toIndex = ids.size();
                }

                logger.debug("batchDeleteByIds({}, idCount={}): deleting fromIndex={}, toIndex={}", namedQuery, ids.size(), fromIndex, toIndex);

                List<Long> subList = ids.subList(fromIndex, toIndex);

                getEntityManager().createNamedQuery(namedQuery, tClass).setParameter(paramName, subList).executeUpdate();
            }
        }
    }

    public T update(T obj) {
        em.merge(obj);

        if (!RangerBizUtil.isBulkMode()) {
            em.flush();
        }

        return obj;
    }

    public boolean remove(Long id) {
        return remove(getById(id));
    }

    public boolean remove(T obj) {
        if (obj == null) {
            return true;
        }

        if (!em.contains(obj)) {
            obj = em.merge(obj);
        }

        em.remove(obj);

        if (!RangerBizUtil.isBulkMode()) {
            em.flush();
        }

        return true;
    }

    public void flush() {
        em.flush();
    }

    public void clear() {
        em.clear();
    }

    public T create(T obj, boolean flush) {
        T ret;

        em.persist(obj);

        if (flush) {
            em.flush();
        }

        ret = obj;

        return ret;
    }

    public T update(T obj, boolean flush) {
        em.merge(obj);

        if (flush) {
            em.flush();
        }

        return obj;
    }

    public boolean remove(T obj, boolean flush) {
        if (obj == null) {
            return true;
        }

        em.remove(obj);

        if (flush) {
            em.flush();
        }

        return true;
    }

    public T getById(Long id) {
        if (id == null) {
            return null;
        }

        T ret;

        try {
            ret = em.find(tClass, id);
        } catch (NoResultException e) {
            return null;
        }

        return ret;
    }

    public List<T> findByNamedQuery(String namedQuery, String paramName, Object refId) {
        List<T> ret = new ArrayList<>();

        if (namedQuery == null) {
            return ret;
        }

        try {
            TypedQuery<T> qry = em.createNamedQuery(namedQuery, tClass);

            qry.setParameter(paramName, refId);

            ret = qry.getResultList();
        } catch (NoResultException e) {
            // ignore
        }

        return ret;
    }

    public List<T> findByParentId(Long parentId) {
        String namedQuery = tClass.getSimpleName() + ".findByParentId";

        return findByNamedQuery(namedQuery, "parentId", parentId);
    }

    public List<T> executeQueryInSecurityContext(Class<T> clazz, Query query) {
        return executeQueryInSecurityContext(clazz, query, true);
    }

    @SuppressWarnings("unchecked")
    public List<T> executeQueryInSecurityContext(Class<T> clazz, Query query, boolean userPrefFilter) {
        // boolean filterEnabled = false;
        // filterEnabled = enableVisiblityFilters(clazz, userPrefFilter);

        return (List<T>) query.getResultList();
    }

    public List<Long> getIds(Query query) {
        return (List<Long>) query.getResultList();
    }

    public Long executeCountQueryInSecurityContext(Class<T> clazz, Query query) { //NOPMD
        return (Long) query.getSingleResult();
    }

    public List<T> getAll() {
        TypedQuery<T> qry = em.createQuery("SELECT t FROM " + tClass.getSimpleName() + " t", tClass);

        return qry.getResultList();
    }

    public Long getAllCount() {
        TypedQuery<Long> qry = em.createQuery("SELECT count(t) FROM " + tClass.getSimpleName() + " t", Long.class);

        return qry.getSingleResult();
    }

    public void updateSequence(String seqName, long nextValue) {
        if (RangerBizUtil.getDBFlavor() == AppConstants.DB_FLAVOR_ORACLE) {
            String[] queries = {
                    "ALTER SEQUENCE " + seqName + " INCREMENT BY " + (nextValue - 1),
                    "select " + seqName + ".nextval from dual",
                    "ALTER SEQUENCE " + seqName + " INCREMENT BY 1 NOCACHE NOCYCLE"
            };

            for (String query : queries) {
                getEntityManager().createNativeQuery(query).executeUpdate();
            }
        } else if (RangerBizUtil.getDBFlavor() == AppConstants.DB_FLAVOR_POSTGRES) {
            String query = "SELECT setval('" + seqName + "', " + nextValue + ")";

            getEntityManager().createNativeQuery(query).getSingleResult();
        }
    }

    public void setIdentityInsert(boolean identityInsert) {
        if (RangerBizUtil.getDBFlavor() != AppConstants.DB_FLAVOR_SQLSERVER) {
            logger.debug("Ignoring BaseDao.setIdentityInsert(). This should be executed if DB flavor is sqlserver.");
            return;
        }

        EntityManager entityMgr = getEntityManager();

        String identityInsertStr;
        if (identityInsert) {
            identityInsertStr = "ON";
        } else {
            identityInsertStr = "OFF";
        }

        Table table = tClass.getAnnotation(Table.class);

        if (table == null) {
            throw new NullPointerException("Required annotation `Table` not found");
        }

        String tableName = table.name();

        try (PreparedStatement st = entityMgr.unwrap(Connection.class).prepareStatement("SET IDENTITY_INSERT  ?   ?")) {
            st.setString(1, tableName);
            st.setString(2, identityInsertStr);
            st.execute();
        } catch (SQLException e) {
            logger.error("Error while settion identity_insert {}", identityInsertStr, e);
        }
    }

    public void updateUserIDReference(String paramName, long oldID) {
        Table table = tClass.getAnnotation(Table.class);

        if (table != null) {
            String tableName    = table.name();
            String updatedValue = tableName.contains(GDS_TABLES) ? "1" : "null";
            String query        = "update " + tableName + " set " + paramName + "=" + updatedValue + " where " + paramName + "=" + oldID;
            int    count        = getEntityManager().createNativeQuery(query).executeUpdate();

            if (count > 0) {
                logger.warn("{} records updated in table '{}' with: set {}={} where {}={}", count, tableName, paramName, updatedValue, paramName, oldID);
            }
        } else {
            logger.warn("Required annotation `Table` not found");
        }
    }

    public String getDBVersion() {
        String dbVersion = NOT_AVAILABLE;
        int    dbFlavor  = RangerBizUtil.getDBFlavor();
        String query     = RangerBizUtil.getDBVersionQuery(dbFlavor);

        if (StringUtils.isNotBlank(query)) {
            try {
                dbVersion = (String) getEntityManager().createNativeQuery(query).getSingleResult();
            } catch (Exception ex) {
                logger.error("Error occurred while fetching the DB version.", ex);
            }
        }

        return dbVersion;
    }

    @SuppressWarnings("unchecked")
    private void init(EntityManager em) {
        this.em = em;

        ParameterizedType genericSuperclass = (ParameterizedType) getClass().getGenericSuperclass();
        Type              type              = genericSuperclass.getActualTypeArguments()[0];

        if (type instanceof ParameterizedType) {
            this.tClass = (Class<T>) ((ParameterizedType) type).getRawType();
        } else {
            this.tClass = (Class<T>) type;
        }
    }

    static {
        try {
            BATCH_DELETE_BATCH_SIZE = RangerAdminConfig.getInstance().getInt(PROP_BATCH_DELETE_BATCH_SIZE, DEFAULT_BATCH_DELETE_BATCH_SIZE);

            if (BATCH_DELETE_BATCH_SIZE > DEFAULT_BATCH_DELETE_BATCH_SIZE) {
                logger.warn("Configuration {}={}, which is larger than default value {}", PROP_BATCH_DELETE_BATCH_SIZE, BATCH_DELETE_BATCH_SIZE, DEFAULT_BATCH_DELETE_BATCH_SIZE);
            }
        } catch (Exception e) {
            // When we get the Number format exception due to the invalid value entered into the config file.
            BATCH_DELETE_BATCH_SIZE = DEFAULT_BATCH_DELETE_BATCH_SIZE;
        }

        logger.info("{}={}", PROP_BATCH_DELETE_BATCH_SIZE, BATCH_DELETE_BATCH_SIZE);
    }
}
