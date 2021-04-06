package edu.utexas.tacc.tapis.systems.dao;

import java.sql.Connection;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import edu.utexas.tacc.tapis.shared.threadlocal.OrderBy;
import edu.utexas.tacc.tapis.systems.model.JobRuntime;
import org.flywaydb.core.Flyway;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.apache.commons.lang3.StringUtils;

import edu.utexas.tacc.tapis.search.parser.ASTBinaryExpression;
import edu.utexas.tacc.tapis.search.parser.ASTLeaf;
import edu.utexas.tacc.tapis.search.parser.ASTNode;
import edu.utexas.tacc.tapis.search.parser.ASTUnaryExpression;
import edu.utexas.tacc.tapis.shared.i18n.MsgUtils;
import edu.utexas.tacc.tapis.systems.model.LogicalQueue;
import edu.utexas.tacc.tapis.systems.model.SystemBasic;

import edu.utexas.tacc.tapis.systems.gen.jooq.tables.records.SystemsRecord;
import static edu.utexas.tacc.tapis.systems.gen.jooq.Tables.*;
import static edu.utexas.tacc.tapis.systems.gen.jooq.Tables.SYSTEMS;

import edu.utexas.tacc.tapis.sharedapi.security.AuthenticatedUser;
import edu.utexas.tacc.tapis.systems.model.PatchSystem;
import edu.utexas.tacc.tapis.search.SearchUtils;
import edu.utexas.tacc.tapis.search.SearchUtils.SearchOperator;
import edu.utexas.tacc.tapis.shared.utils.TapisGsonUtils;
import edu.utexas.tacc.tapis.systems.model.Capability;
import edu.utexas.tacc.tapis.systems.model.TSystem;
import edu.utexas.tacc.tapis.systems.model.TSystem.SystemOperation;
import edu.utexas.tacc.tapis.systems.utils.LibUtils;
import edu.utexas.tacc.tapis.shared.exceptions.TapisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Class to handle persistence and queries for Tapis System objects.
 */
public class SystemsDaoImpl extends AbstractDao implements SystemsDao
{
  /* ********************************************************************** */
  /*                               Constants                                */
  /* ********************************************************************** */
  // Local logger.
  private static final Logger _log = LoggerFactory.getLogger(SystemsDaoImpl.class);

  private static final String EMPTY_JSON = "{}";
  private static final int INVALID_SEQ_ID = -1;


  /* ********************************************************************** */
  /*                             Public Methods                             */
  /* ********************************************************************** */

  /**
   * Create a new system.
   *
   * @return true if created
   * @throws TapisException - on error
   * @throws IllegalStateException - if system already exists
   */
  @Override
  public boolean createSystem(AuthenticatedUser authenticatedUser, TSystem system, String createJsonStr, String scrubbedText)
          throws TapisException, IllegalStateException {
    String opName = "createSystem";
    // ------------------------- Check Input -------------------------
    if (system == null) LibUtils.logAndThrowNullParmException(opName, "system");
    if (authenticatedUser == null) LibUtils.logAndThrowNullParmException(opName, "authenticatedUser");
    if (StringUtils.isBlank(createJsonStr)) LibUtils.logAndThrowNullParmException(opName, "createJson");
    if (StringUtils.isBlank(system.getTenant())) LibUtils.logAndThrowNullParmException(opName, "tenant");
    if (StringUtils.isBlank(system.getId())) LibUtils.logAndThrowNullParmException(opName, "systemId");
    if (system.getSystemType() == null) LibUtils.logAndThrowNullParmException(opName, "systemType");
    if (system.getDefaultAuthnMethod() == null) LibUtils.logAndThrowNullParmException(opName, "defaultAuthnMethod");

    // Convert transferMethods into array of strings
    String[] transferMethodsStrArray = LibUtils.getTransferMethodsAsStringArray(system.getTransferMethods());

    // Convert nulls to default values. Postgres adheres to sql standard of <col> = null is not the same as <col> is null
    String proxyHost = TSystem.DEFAULT_PROXYHOST;
    if (system.getProxyHost() != null) proxyHost = system.getProxyHost();

    // Make sure owner, effectiveUserId, notes and tags are all set
    String owner = TSystem.DEFAULT_OWNER;
    if (StringUtils.isNotBlank(system.getOwner())) owner = system.getOwner();
    String effectiveUserId = TSystem.DEFAULT_EFFECTIVEUSERID;
    if (StringUtils.isNotBlank(system.getEffectiveUserId())) effectiveUserId = system.getEffectiveUserId();
    String[] tagsStrArray = TSystem.EMPTY_STR_ARRAY;
    if (system.getTags() != null) tagsStrArray = system.getTags();
    JsonObject notesObj = TSystem.DEFAULT_NOTES;
    if (system.getNotes() != null) notesObj = (JsonObject) system.getNotes();

    // ------------------------- Call SQL ----------------------------
    Connection conn = null;
    try
    {
      // Get a database connection.
      conn = getConnection();
      DSLContext db = DSL.using(conn);

      // Check to see if system exists or has been soft deleted. If yes then throw IllegalStateException
      boolean doesExist = checkForSystem(db, system.getTenant(), system.getId(), true);
      if (doesExist) throw new IllegalStateException(LibUtils.getMsgAuth("SYSLIB_SYS_EXISTS", authenticatedUser, system.getId()));

      Record record = db.insertInto(SYSTEMS)
              .set(SYSTEMS.TENANT, system.getTenant())
              .set(SYSTEMS.ID, system.getId())
              .set(SYSTEMS.DESCRIPTION, system.getDescription())
              .set(SYSTEMS.SYSTEM_TYPE, system.getSystemType())
              .set(SYSTEMS.OWNER, owner)
              .set(SYSTEMS.HOST, system.getHost())
              .set(SYSTEMS.ENABLED, system.isEnabled())
              .set(SYSTEMS.EFFECTIVE_USER_ID, effectiveUserId)
              .set(SYSTEMS.DEFAULT_AUTHN_METHOD, system.getDefaultAuthnMethod())
              .set(SYSTEMS.BUCKET_NAME, system.getBucketName())
              .set(SYSTEMS.ROOT_DIR, system.getRootDir())
              .set(SYSTEMS.TRANSFER_METHODS, transferMethodsStrArray)
              .set(SYSTEMS.PORT, system.getPort())
              .set(SYSTEMS.USE_PROXY, system.isUseProxy())
              .set(SYSTEMS.PROXY_HOST, proxyHost)
              .set(SYSTEMS.PROXY_PORT, system.getProxyPort())
              .set(SYSTEMS.DTN_SYSTEM_ID, system.getDtnSystemId())
              .set(SYSTEMS.DTN_MOUNT_SOURCE_PATH, system.getDtnMountSourcePath())
              .set(SYSTEMS.DTN_MOUNT_POINT, system.getDtnMountPoint())
              .set(SYSTEMS.IS_DTN, system.isDtn())
              .set(SYSTEMS.CAN_EXEC, system.getCanExec())
              .set(SYSTEMS.JOB_WORKING_DIR, system.getJobWorkingDir())
              .set(SYSTEMS.JOB_ENV_VARIABLES, system.getJobEnvVariables())
              .set(SYSTEMS.JOB_MAX_JOBS, system.getJobMaxJobs())
              .set(SYSTEMS.JOB_MAX_JOBS_PER_USER, system.getJobMaxJobsPerUser())
              .set(SYSTEMS.JOB_IS_BATCH, system.getJobIsBatch())
              .set(SYSTEMS.BATCH_SCHEDULER, system.getBatchScheduler())
              .set(SYSTEMS.BATCH_DEFAULT_LOGICAL_QUEUE, system.getBatchDefaultLogicalQueue())
              .set(SYSTEMS.TAGS, tagsStrArray)
              .set(SYSTEMS.NOTES, notesObj)
              .set(SYSTEMS.UUID, system.getUuid())
              .returningResult(SYSTEMS.SEQ_ID)
              .fetchOne();
      // Generated sequence id
      int seqId = record.getValue(SYSTEMS.SEQ_ID);

      if (seqId < 1) return false;

      // Persist job runtimes
      persistJobRuntimes(db, system, seqId);

      // Persist batch logical queues
      persistLogicalQueues(db, system, seqId);

      // Persist job capabilities
      persistJobCapabilities(db, system, seqId);

      // Persist update record
      addUpdate(db, authenticatedUser, system.getTenant(), system.getId(), seqId, SystemOperation.create,
                createJsonStr, scrubbedText, system.getUuid());

      // Close out and commit
      LibUtils.closeAndCommitDB(conn, null, null);
    }
    catch (Exception e)
    {
      // Rollback transaction and throw an exception
      LibUtils.rollbackDB(conn, e,"DB_INSERT_FAILURE", "systems");
    }
    finally
    {
      // Always return the connection back to the connection pool.
      LibUtils.finalCloseDB(conn);
    }
    return true;
  }

  /**
   * Update an existing system.
   * Following columns will be updated:
   *   description, host, effectiveUserId, defaultAuthnMethod, transferMethods,
   *   port, useProxy, proxyHost, proxyPort, dtnSystemId, dtnMountPoint, dtnMountSourcePath,
   *   jobRuntimes, jobWorkingDir, jobEnvVariables, jobMaxJobs, jobMaxJobsPerUers, jobIsBatch,
   *   batchScheduler, batchLogicalQueues, batchDefaultLogicalQueue, jobCapabilities, tags, notes.
   * @throws TapisException - on error
   * @throws IllegalStateException - if system already exists
   */
  @Override
  public void updateSystem(AuthenticatedUser authenticatedUser, TSystem patchedSystem, PatchSystem patchSystem,
                           String updateJsonStr, String scrubbedText)
          throws TapisException, IllegalStateException {
    String opName = "updateSystem";
    // ------------------------- Check Input -------------------------
    if (patchedSystem == null) LibUtils.logAndThrowNullParmException(opName, "patchedSystem");
    if (patchSystem == null) LibUtils.logAndThrowNullParmException(opName, "patchSystem");
    if (authenticatedUser == null) LibUtils.logAndThrowNullParmException(opName, "authenticatedUser");
    // Pull out some values for convenience
    String tenant = patchedSystem.getTenant();
    String systemId = patchedSystem.getId();
    if (StringUtils.isBlank(updateJsonStr)) LibUtils.logAndThrowNullParmException(opName, "updateJson");
    if (StringUtils.isBlank(tenant)) LibUtils.logAndThrowNullParmException(opName, "tenant");
    if (StringUtils.isBlank(systemId)) LibUtils.logAndThrowNullParmException(opName, "systemId");
    if (patchedSystem.getSystemType() == null) LibUtils.logAndThrowNullParmException(opName, "systemType");

    // Convert transferMethods into array of strings
    String[] transferMethodsStrArray = LibUtils.getTransferMethodsAsStringArray(patchedSystem.getTransferMethods());

    // Convert nulls to default values. Postgres adheres to sql standard of <col> = null is not the same as <col> is null
    String proxyHost = TSystem.DEFAULT_PROXYHOST;
    if (patchedSystem.getProxyHost() != null) proxyHost = patchedSystem.getProxyHost();

    // Make sure effectiveUserId, jobEnvVariables, notes and tags are all set
    String effectiveUserId = TSystem.DEFAULT_EFFECTIVEUSERID;
    if (StringUtils.isNotBlank(patchedSystem.getEffectiveUserId())) effectiveUserId = patchedSystem.getEffectiveUserId();

    String[] jobEnvVariablesStrArray = TSystem.EMPTY_STR_ARRAY;
    if (patchedSystem.getJobEnvVariables() != null) jobEnvVariablesStrArray = patchedSystem.getJobEnvVariables();

    String[] tagsStrArray = TSystem.EMPTY_STR_ARRAY;
    if (patchedSystem.getTags() != null) tagsStrArray = patchedSystem.getTags();
    JsonObject notesObj =  TSystem.DEFAULT_NOTES;
    if (patchedSystem.getNotes() != null) notesObj = (JsonObject) patchedSystem.getNotes();

    // ------------------------- Call SQL ----------------------------
    Connection conn = null;
    try
    {
      // Get a database connection.
      conn = getConnection();
      DSLContext db = DSL.using(conn);

      // Make sure system exists and has not been soft deleted.
      boolean doesExist = checkForSystem(db, tenant, systemId, false);
      if (!doesExist) throw new IllegalStateException(LibUtils.getMsgAuth("SYSLIB_NOT_FOUND", authenticatedUser, systemId));


      int seqId = db.update(SYSTEMS)
              .set(SYSTEMS.DESCRIPTION, patchedSystem.getDescription())
              .set(SYSTEMS.HOST, patchedSystem.getHost())
              .set(SYSTEMS.EFFECTIVE_USER_ID, effectiveUserId)
              .set(SYSTEMS.DEFAULT_AUTHN_METHOD, patchedSystem.getDefaultAuthnMethod())
              .set(SYSTEMS.TRANSFER_METHODS, transferMethodsStrArray)
              .set(SYSTEMS.PORT, patchedSystem.getPort())
              .set(SYSTEMS.USE_PROXY, patchedSystem.isUseProxy())
              .set(SYSTEMS.PROXY_HOST, proxyHost)
              .set(SYSTEMS.PROXY_PORT, patchedSystem.getProxyPort())
              .set(SYSTEMS.DTN_SYSTEM_ID, patchedSystem.getDtnSystemId())
              .set(SYSTEMS.DTN_MOUNT_POINT, patchedSystem.getDtnMountPoint())
              .set(SYSTEMS.DTN_MOUNT_SOURCE_PATH, patchedSystem.getDtnMountSourcePath())
              .set(SYSTEMS.JOB_WORKING_DIR, patchedSystem.getJobWorkingDir())
              .set(SYSTEMS.JOB_ENV_VARIABLES, jobEnvVariablesStrArray)
              .set(SYSTEMS.JOB_MAX_JOBS, patchedSystem.getJobMaxJobs())
              .set(SYSTEMS.JOB_MAX_JOBS_PER_USER, patchedSystem.getJobMaxJobsPerUser())
              .set(SYSTEMS.JOB_IS_BATCH, patchedSystem.getJobIsBatch())
              .set(SYSTEMS.BATCH_SCHEDULER, patchedSystem.getBatchScheduler())
              .set(SYSTEMS.BATCH_DEFAULT_LOGICAL_QUEUE, patchedSystem.getBatchDefaultLogicalQueue())
              .set(SYSTEMS.TAGS, tagsStrArray)
              .set(SYSTEMS.NOTES, notesObj)
              .where(SYSTEMS.TENANT.eq(tenant),SYSTEMS.ID.eq(systemId))
              .returningResult(SYSTEMS.SEQ_ID)
              .fetchOne().getValue(SYSTEMS.SEQ_ID);

      // If jobRuntimes updated then replace them
      if (patchSystem.getJobRuntimes() != null) {
        db.deleteFrom(JOB_RUNTIMES).where(JOB_RUNTIMES.SYSTEM_SEQ_ID.eq(seqId)).execute();
        persistJobRuntimes(db, patchedSystem, seqId);
      }

      // If batchLogicalQueues updated then replace them
      if (patchSystem.getBatchLogicalQueues() != null) {
        db.deleteFrom(LOGICAL_QUEUES).where(LOGICAL_QUEUES.SYSTEM_SEQ_ID.eq(seqId)).execute();
        persistLogicalQueues(db, patchedSystem, seqId);
      }

      // If jobCapabilities updated then replace them
      if (patchSystem.getJobCapabilities() != null) {
        db.deleteFrom(CAPABILITIES).where(CAPABILITIES.SYSTEM_SEQ_ID.eq(seqId)).execute();
        persistJobCapabilities(db, patchedSystem, seqId);
      }

      // Persist update record
      addUpdate(db, authenticatedUser, tenant, systemId, seqId, SystemOperation.modify, updateJsonStr, scrubbedText,
                patchedSystem.getUuid());

      // Close out and commit
      LibUtils.closeAndCommitDB(conn, null, null);
    }
    catch (Exception e)
    {
      // Rollback transaction and throw an exception
      LibUtils.rollbackDB(conn, e,"DB_INSERT_FAILURE", "systems");
    }
    finally
    {
      // Always return the connection back to the connection pool.
      LibUtils.finalCloseDB(conn);
    }
  }

  /**
   * Update attribute enabled for a system given system Id and value
   */
  @Override
  public void updateEnabled(AuthenticatedUser authenticatedUser, String id, boolean enabled) throws TapisException
  {
    String opName = "updateEnabled";
    // ------------------------- Check Input -------------------------
    if (StringUtils.isBlank(id)) LibUtils.logAndThrowNullParmException(opName, "systemId");

    String tenant = authenticatedUser.getOboTenantId();

    // AppOperation needed for recording the update
    SystemOperation systemOp = enabled ? SystemOperation.enable : SystemOperation.disable;

    // ------------------------- Call SQL ----------------------------
    Connection conn = null;
    try
    {
      // Get a database connection.
      conn = getConnection();
      DSLContext db = DSL.using(conn);
      db.update(SYSTEMS).set(SYSTEMS.ENABLED, enabled).where(SYSTEMS.TENANT.eq(tenant),SYSTEMS.ID.eq(id)).execute();
      // Persist update record
      String updateJsonStr = "{\"enabled\":" +  enabled + "}";
      addUpdate(db, authenticatedUser, tenant, id, INVALID_SEQ_ID, systemOp, updateJsonStr , null,
                getUUIDUsingDb(db, tenant, id));
      // Close out and commit
      LibUtils.closeAndCommitDB(conn, null, null);
    }
    catch (Exception e)
    {
      // Rollback transaction and throw an exception
      LibUtils.rollbackDB(conn, e,"DB_UPDATE_FAILURE", "apps", id);
    }
    finally
    {
      // Always return the connection back to the connection pool.
      LibUtils.finalCloseDB(conn);
    }
  }

  /**
   * Update owner of a system given system Id and new owner name
   *
   */
  @Override
  public void updateSystemOwner(AuthenticatedUser authenticatedUser, String id, String newOwnerName) throws TapisException
  {
    String opName = "changeOwner";
    // ------------------------- Check Input -------------------------
    if (StringUtils.isBlank(id)) LibUtils.logAndThrowNullParmException(opName, "systemId");
    if (StringUtils.isBlank(newOwnerName)) LibUtils.logAndThrowNullParmException(opName, "newOwnerName");

    String tenant = authenticatedUser.getOboTenantId();

    // ------------------------- Call SQL ----------------------------
    Connection conn = null;
    try
    {
      // Get a database connection.
      conn = getConnection();
      DSLContext db = DSL.using(conn);
      db.update(SYSTEMS).set(SYSTEMS.OWNER, newOwnerName).where(SYSTEMS.TENANT.eq(tenant),SYSTEMS.ID.eq(id)).execute();
      // Persist update record
      String updateJsonStr = TapisGsonUtils.getGson().toJson(newOwnerName);
      addUpdate(db, authenticatedUser, tenant, id, INVALID_SEQ_ID, SystemOperation.changeOwner, updateJsonStr , null,
                getUUIDUsingDb(db, tenant, id));
      // Close out and commit
      LibUtils.closeAndCommitDB(conn, null, null);
    }
    catch (Exception e)
    {
      // Rollback transaction and throw an exception
      LibUtils.rollbackDB(conn, e,"DB_UPDATE_FAILURE", "systems", id);
    }
    finally
    {
      // Always return the connection back to the connection pool.
      LibUtils.finalCloseDB(conn);
    }
  }

  /**
   * Soft delete a system record given the system id.
   *
   */
  @Override
  public int softDeleteSystem(AuthenticatedUser authenticatedUser, String id) throws TapisException
  {
    String opName = "softDeleteSystem";
    int rows = -1;
    // ------------------------- Check Input -------------------------
    if (StringUtils.isBlank(id)) LibUtils.logAndThrowNullParmException(opName, "systemId");

    String tenant = authenticatedUser.getOboTenantId();

    // ------------------------- Call SQL ----------------------------
    Connection conn = null;
    try
    {
      // Get a database connection.
      conn = getConnection();
      DSLContext db = DSL.using(conn);
      // If system does not exist or has been soft deleted return 0
      if (!db.fetchExists(SYSTEMS,SYSTEMS.TENANT.eq(tenant),SYSTEMS.ID.eq(id),SYSTEMS.DELETED.eq(false)))
      {
        return 0;
      }
      rows = db.update(SYSTEMS).set(SYSTEMS.DELETED, true).where(SYSTEMS.TENANT.eq(tenant),SYSTEMS.ID.eq(id)).execute();

      // Persist update record
      addUpdate(db, authenticatedUser, tenant, id, INVALID_SEQ_ID, SystemOperation.softDelete, EMPTY_JSON, null,
                getUUIDUsingDb(db, tenant, id));

      // Close out and commit
      LibUtils.closeAndCommitDB(conn, null, null);
    }
    catch (Exception e)
    {
      // Rollback transaction and throw an exception
      LibUtils.rollbackDB(conn, e,"DB_UPDATE_FAILURE", "systems", id);
    }
    finally
    {
      // Always return the connection back to the connection pool.
      LibUtils.finalCloseDB(conn);
    }
    return rows;
  }

  /**
   * Hard delete a system record given the system name.
   */
  @Override
  public int hardDeleteSystem(String tenant, String id) throws TapisException
  {
    String opName = "hardDeleteSystem";
    int rows = -1;
    // ------------------------- Check Input -------------------------
    if (StringUtils.isBlank(tenant)) LibUtils.logAndThrowNullParmException(opName, "tenant");
    if (StringUtils.isBlank(id)) LibUtils.logAndThrowNullParmException(opName, "name");

    // ------------------------- Call SQL ----------------------------
    Connection conn = null;
    try
    {
      conn = getConnection();
      DSLContext db = DSL.using(conn);
      db.deleteFrom(SYSTEMS).where(SYSTEMS.TENANT.eq(tenant),SYSTEMS.ID.eq(id)).execute();
      LibUtils.closeAndCommitDB(conn, null, null);
    }
    catch (Exception e)
    {
      LibUtils.rollbackDB(conn, e,"DB_DELETE_FAILURE", "systems");
    }
    finally
    {
      LibUtils.finalCloseDB(conn);
    }
    return rows;
  }

  /**
   * checkDB
   * Check that we can connect with DB and that the main table of the service exists.
   * @return null if all OK else return an exception
   */
  @Override
  public Exception checkDB()
  {
    Exception result = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      DSLContext db = DSL.using(conn);
      // execute SELECT to_regclass('tapis_sys.systems');
      // Build and execute a simple postgresql statement to check for the table
      String sql = "SELECT to_regclass('" + SYSTEMS.getName() + "')";
      Result<Record> ret = db.resultQuery(sql).fetch();
      if (ret == null || ret.isEmpty() || ret.getValue(0,0) == null)
      {
        result = new TapisException(LibUtils.getMsg("SYSLIB_CHECKDB_NO_TABLE", SYSTEMS.getName()));
      }
      LibUtils.closeAndCommitDB(conn, null, null);
    }
    catch (Exception e)
    {
      result = e;
      // Rollback always logs msg and throws exception.
      // In this case of a simple check we ignore the exception, we just want the log msg
      try { LibUtils.rollbackDB(conn, e,"DB_QUERY_ERROR", "systems"); }
      catch (Exception e1) { _log.error(LibUtils.getMsg("SYSLIB_DB_ROLLBACK_ERROR", "checkDB"), e1); }
    }
    finally
    {
      LibUtils.finalCloseDB(conn);
    }
    return result;
  }

  /**
   * migrateDB
   * Use Flyway to make sure DB schema is at the latest version
   */
  @Override
  public void migrateDB() throws TapisException
  {
    Flyway flyway = Flyway.configure().dataSource(getDataSource()).load();
    // TODO remove workaround if possible. Figure out how to deploy X.Y.Z-SNAPSHOT repeatedly.
    // Use repair as workaround to avoid checksum error during develop/deploy of SNAPSHOT versions when it is not
    // a true migration.
    flyway.repair();
    flyway.migrate();
  }

  /**
   * checkForSystem
   * @param id - system name
   * @return true if found else false
   * @throws TapisException - on error
   */
  @Override
  public boolean checkForSystem(String tenant, String id, boolean includeDeleted) throws TapisException {
    // Initialize result.
    boolean result = false;

    // ------------------------- Call SQL ----------------------------
    Connection conn = null;
    try
    {
      // Get a database connection.
      conn = getConnection();
      DSLContext db = DSL.using(conn);
      // Run the sql
      result = checkForSystem(db, tenant, id, includeDeleted);
      // Close out and commit
      LibUtils.closeAndCommitDB(conn, null, null);
    }
    catch (Exception e)
    {
      // Rollback transaction and throw an exception
      LibUtils.rollbackDB(conn, e,"DB_SELECT_NAME_ERROR", "System", tenant, id, e.getMessage());
    }
    finally
    {
      // Always return the connection back to the connection pool.
      LibUtils.finalCloseDB(conn);
    }
    return result;
  }

  /**
   * getSystem
   * @param id - system name
   * @return System object if found, null if not found
   * @throws TapisException - on error
   */
  @Override
  public TSystem getSystem(String tenant, String id) throws TapisException
  {
    return getSystem(tenant, id, false);
  }

  /**
   * getSystem
   * @param id - system name
   * @return System object if found, null if not found
   * @throws TapisException - on error
   */
  @Override
  public TSystem getSystem(String tenant, String id, boolean includeDeleted) throws TapisException {
    // Initialize result.
    TSystem result = null;

    // ------------------------- Call SQL ----------------------------
    Connection conn = null;
    try
    {
      // Get a database connection.
      conn = getConnection();
      DSLContext db = DSL.using(conn);
      SystemsRecord r;
      if (includeDeleted)
        r = db.selectFrom(SYSTEMS).where(SYSTEMS.TENANT.eq(tenant),SYSTEMS.ID.eq(id)).fetchOne();
      else
        r = db.selectFrom(SYSTEMS).where(SYSTEMS.TENANT.eq(tenant),SYSTEMS.ID.eq(id),SYSTEMS.DELETED.eq(false)).fetchOne();
      if (r == null) return null;
      else result = r.into(TSystem.class);

      // TODO: Looks like jOOQ has fetchGroups() which should allow us to retrieve LogicalQueues and Capabilities
      //       in one call which might improve performance.

      // Retrieve and set jobRuntimes
      result.setJobRuntimes(retrieveJobRuntimes(db, result.getSeqId()));

      // Retrieve and set batch logical queues
      result.setBatchLogicalQueues(retrieveLogicalQueues(db, result.getSeqId()));

      // Retrieve and set job capabilities
      result.setJobCapabilities(retrieveJobCaps(db, result.getSeqId()));

      // Close out and commit
      LibUtils.closeAndCommitDB(conn, null, null);
    }
    catch (Exception e)
    {
      // Rollback transaction and throw an exception
      LibUtils.rollbackDB(conn, e,"DB_SELECT_NAME_ERROR", "System", tenant, id, e.getMessage());
    }
    finally
    {
      // Always return the connection back to the connection pool.
      LibUtils.finalCloseDB(conn);
    }
    return result;
  }

  /**
   * getTSystemsCount
   * Count all TSystems matching various search and sort criteria.
   *     Search conditions given as a list of strings or an abstract syntax tree (AST).
   * Conditions in searchList must be processed by SearchUtils.validateAndExtractSearchCondition(cond)
   *   prior to this call for proper validation and treatment of special characters.
   * WARNING: If both searchList and searchAST provided only searchList is used.
   * @param tenant - tenant name
   * @param searchList - optional list of conditions used for searching
   * @param searchAST - AST containing search conditions
   * @param setOfIDs - list of system IDs to consider. null indicates no restriction.
   * @param orderByList - orderBy entries for sorting, e.g. orderBy=created(desc).
   * @param startAfter - where to start when sorting, e.g. orderBy=id(asc)&startAfter=101 (may not be used with skip)
   * @return - count of TSystem objects
   * @throws TapisException - on error
   */
  @Override
  public int getSystemsCount(String tenant, List<String> searchList, ASTNode searchAST, Set<String> setOfIDs,
                             List<OrderBy> orderByList, String startAfter)
          throws TapisException
  {
    // TODO - for now just use the major (i.e. first in list) orderBy item.
    String majorOrderBy = null;
    String majorSortDirection = "ASC";
    if (orderByList != null && !orderByList.isEmpty())
    {
      majorOrderBy = orderByList.get(0).getOrderByAttr();
      majorSortDirection = orderByList.get(0).getOrderByDir().name();
    }

    // NOTE: Sort matters for the count even though we will not actually need to sort.
    boolean sortAsc = true;
    if (OrderBy.OrderByDir.DESC.name().equalsIgnoreCase(majorSortDirection)) sortAsc = false;

    // If startAfter is given then orderBy is required
    if (!StringUtils.isBlank(startAfter) && StringUtils.isBlank(majorOrderBy))
    {
      String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_SORT_START", SYSTEMS.getName());
      throw new TapisException(msg);
    }

    // If no IDs in list then we are done.
    if (setOfIDs != null && setOfIDs.isEmpty()) return 0;

    // Determine and check orderBy column
    Field<?> colOrderBy = SYSTEMS.field(DSL.name(SearchUtils.camelCaseToSnakeCase(majorOrderBy)));
    if (!StringUtils.isBlank(majorOrderBy) && colOrderBy == null)
    {
      String msg = LibUtils.getMsg("SYSLIB_DB_NO_COLUMN_SORT", SYSTEMS.getName(), DSL.name(majorOrderBy));
      throw new TapisException(msg);
    }

    // Begin where condition for the query
    Condition whereCondition = (SYSTEMS.TENANT.eq(tenant)).and(SYSTEMS.DELETED.eq(false));

    // Add searchList or searchAST to where condition
    if (searchList != null)
    {
      whereCondition = addSearchListToWhere(whereCondition, searchList);
    }
    else if (searchAST != null)
    {
      Condition astCondition = createConditionFromAst(searchAST);
      if (astCondition != null) whereCondition = whereCondition.and(astCondition);
    }

    // Add startAfter.
    if (!StringUtils.isBlank(startAfter))
    {
      // Build search string so we can re-use code for checking and adding a condition
      String searchStr;
      if (sortAsc) searchStr = majorOrderBy + ".gt." + startAfter;
      else searchStr = majorOrderBy + ".lt." + startAfter;
      whereCondition = addSearchCondStrToWhere(whereCondition, searchStr, "AND");
    }

    // Add IN condition for list of IDs
    if (setOfIDs != null && !setOfIDs.isEmpty()) whereCondition = whereCondition.and(SYSTEMS.ID.in(setOfIDs));

    // ------------------------- Build and execute SQL ----------------------------
    int count = 0;
    Connection conn = null;
    try
    {
      // Get a database connection.
      conn = getConnection();
      DSLContext db = DSL.using(conn);

      // Execute the select including orderByAttrList, startAfter
      count = db.selectCount().from(SYSTEMS).where(whereCondition).fetchOne(0,int.class);

      // Close out and commit
      LibUtils.closeAndCommitDB(conn, null, null);
    }
    catch (Exception e)
    {
      // Rollback transaction and throw an exception
      LibUtils.rollbackDB(conn, e,"DB_QUERY_ERROR", "systems", e.getMessage());
    }
    finally
    {
      // Always return the connection back to the connection pool.
      LibUtils.finalCloseDB(conn);
    }
    return count;
  }

  /**
   * getTSystems
   * Retrieve all TSystems matching various search and sort criteria.
   *     Search conditions given as a list of strings or an abstract syntax tree (AST).
   * Conditions in searchList must be processed by SearchUtils.validateAndExtractSearchCondition(cond)
   *   prior to this call for proper validation and treatment of special characters.
   * WARNING: If both searchList and searchAST provided only searchList is used.
   * @param tenant - tenant name
   * @param searchList - optional list of conditions used for searching
   * @param searchAST - AST containing search conditions
   * @param setOfIDs - list of system IDs to consider. null indicates no restriction.
   * @param limit - indicates maximum number of results to be included, -1 for unlimited
   * @param orderByList - orderBy entries for sorting, e.g. orderBy=created(desc).
   * @param skip - number of results to skip (may not be used with startAfter)
   * @param startAfter - where to start when sorting, e.g. limit=10&orderBy=id(asc)&startAfter=101 (may not be used with skip)
   * @return - list of TSystem objects
   * @throws TapisException - on error
   */
  @Override
  public List<TSystem> getSystems(String tenant, List<String> searchList, ASTNode searchAST, Set<String> setOfIDs,
                                  int limit, List<OrderBy> orderByList, int skip, String startAfter)
          throws TapisException
  {
    // TODO - for now just use the major (i.e. first in list) orderBy item.
    String majorOrderBy = null;
    String majorSortDirection = "ASC";
    if (orderByList != null && !orderByList.isEmpty())
    {
      majorOrderBy = orderByList.get(0).getOrderByAttr();
      majorSortDirection = orderByList.get(0).getOrderByDir().name();
    }

    // The result list should always be non-null.
    var retList = new ArrayList<TSystem>();

    // Negative skip indicates no skip
    if (skip < 0) skip = 0;

    boolean sortAsc = true;
    if (OrderBy.OrderByDir.DESC.name().equalsIgnoreCase(majorSortDirection)) sortAsc = false;

    // If startAfter is given then orderBy is required
    if (!StringUtils.isBlank(startAfter) && StringUtils.isBlank(majorOrderBy))
    {
      String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_SORT_START", SYSTEMS.getName());
      throw new TapisException(msg);
    }

    // If no IDs in list then we are done.
    if (setOfIDs != null && setOfIDs.isEmpty()) return retList;

// DEBUG Iterate over all columns and show the type
//      Field<?>[] cols = SYSTEMS.fields();
//      for (Field<?> col : cols) {
//        var dataType = col.getDataType();
//        int sqlType = dataType.getSQLType();
//        String sqlTypeName = dataType.getTypeName();
//        _log.debug("Column name: " + col.getName() + " type: " + sqlTypeName);
//      }
// DEBUG

    // Determine and check orderBy column
    Field<?> colOrderBy = SYSTEMS.field(DSL.name(SearchUtils.camelCaseToSnakeCase(majorOrderBy)));
    if (!StringUtils.isBlank(majorOrderBy) && colOrderBy == null)
    {
      String msg = LibUtils.getMsg("SYSLIB_DB_NO_COLUMN_SORT", SYSTEMS.getName(), DSL.name(majorOrderBy));
      throw new TapisException(msg);
    }

    // Begin where condition for the query
    Condition whereCondition = (SYSTEMS.TENANT.eq(tenant)).and(SYSTEMS.DELETED.eq(false));

    // Add searchList or searchAST to where condition
    if (searchList != null)
    {
      whereCondition = addSearchListToWhere(whereCondition, searchList);
    }
    else if (searchAST != null)
    {
      Condition astCondition = createConditionFromAst(searchAST);
      if (astCondition != null) whereCondition = whereCondition.and(astCondition);
    }

    // Add startAfter
    if (!StringUtils.isBlank(startAfter))
    {
      // Build search string so we can re-use code for checking and adding a condition
      String searchStr;
      if (sortAsc) searchStr = majorOrderBy + ".gt." + startAfter;
      else searchStr = majorOrderBy + ".lt." + startAfter;
      whereCondition = addSearchCondStrToWhere(whereCondition, searchStr, "AND");
    }

    // Add IN condition for list of IDs
    if (setOfIDs != null && !setOfIDs.isEmpty()) whereCondition = whereCondition.and(SYSTEMS.ID.in(setOfIDs));

    // ------------------------- Build and execute SQL ----------------------------
    Connection conn = null;
    try
    {
      // Get a database connection.
      conn = getConnection();
      DSLContext db = DSL.using(conn);

      // Execute the select including limit, orderByAttrList, skip and startAfter
      // NOTE: LIMIT + OFFSET is not standard among DBs and often very difficult to get right.
      //       Jooq claims to handle it well.
      Result<SystemsRecord> results;
      org.jooq.SelectConditionStep<SystemsRecord> condStep = db.selectFrom(SYSTEMS).where(whereCondition);
      if (!StringUtils.isBlank(majorOrderBy) &&  limit >= 0)
      {
        // We are ordering and limiting
        if (sortAsc) results = condStep.orderBy(colOrderBy.asc()).limit(limit).offset(skip).fetch();
        else results = condStep.orderBy(colOrderBy.desc()).limit(limit).offset(skip).fetch();
      }
      else if (!StringUtils.isBlank(majorOrderBy))
      {
        // We are ordering but not limiting
        if (sortAsc) results = condStep.orderBy(colOrderBy.asc()).fetch();
        else results = condStep.orderBy(colOrderBy.desc()).fetch();
      }
      else if (limit >= 0)
      {
        // We are limiting but not ordering
        results = condStep.limit(limit).offset(skip).fetch();
      }
      else
      {
        // We are not limiting and not ordering
        results = condStep.fetch();
      }

      if (results == null || results.isEmpty()) return retList;

      // Fill in batch logical queues and job capabilities list from aux tables
      // TODO: Looks like jOOQ has fetchGroups() which should allow us to retrieve LogicalQueues and Capabilities
      //       in one call which might improve performance.
      for (SystemsRecord r : results)
      {
        TSystem s = r.into(TSystem.class);
        s.setJobRuntimes(retrieveJobRuntimes(db, s.getSeqId()));
        s.setBatchLogicalQueues(retrieveLogicalQueues(db, s.getSeqId()));
        s.setJobCapabilities(retrieveJobCaps(db, s.getSeqId()));
        retList.add(s);
      }

      // Close out and commit
      LibUtils.closeAndCommitDB(conn, null, null);
    }
    catch (Exception e)
    {
      // Rollback transaction and throw an exception
      LibUtils.rollbackDB(conn, e,"DB_QUERY_ERROR", "systems", e.getMessage());
    }
    finally
    {
      // Always return the connection back to the connection pool.
      LibUtils.finalCloseDB(conn);
    }
    return retList;
  }

  /**
   * getTSystemsSatisfyingConstraints
   * Retrieve all TSystems satisfying capability constraint criteria.
   *     Constraint criteria conditions provided as an abstract syntax tree (AST).
   * @param tenant - tenant name
   * @param matchAST - AST containing match conditions. If null then nothing matches.
   * @param setOfIDs - list of system IDs to consider. If null all allowed. If empty none allowed.
   * @return - list of TSystem objects
   * @throws TapisException - on error
   */
  @Override
  public List<TSystem> getSystemsSatisfyingConstraints(String tenant, ASTNode matchAST, Set<String> setOfIDs)
          throws TapisException
  {
    // TODO: might be possible to optimize this method with a join between systems and capabilities tables.
    // The result list should always be non-null.
    var retList = new ArrayList<TSystem>();

    // If no match criteria or IDs list is empty then we are done.
    if (matchAST == null || (setOfIDs != null && setOfIDs.isEmpty())) return retList;

    // TODO/TBD: For now return all allowed systems. Once a shared util method is available for matching
    //       as a first pass we can simply iterate through all systems to find matches.
    //       For performance might need to later do matching with DB queries.

    // Get all desired capabilities (category, name) from AST
    List<Capability> capabilitiesInAST = new ArrayList<>();
    getCapabilitiesFromAST(matchAST, capabilitiesInAST);

    List<TSystem> systemsList = null;
    // ------------------------- Build and execute SQL ----------------------------
    Connection conn = null;
    try
    {
      // Get a database connection.
      conn = getConnection();
      DSLContext db = DSL.using(conn);

      Set<String> allowedIDs = setOfIDs;
      // If IDs is null then all allowed. Use tenant to get all system IDs
      // TODO: might be able to optimize with a join somewhere
      if (setOfIDs == null) allowedIDs = null; //TODO getAllSystemSeqIdsInTenant(db, tenant); still needed?

      // Get all Systems that specify they support the desired Capabilities
      systemsList = getSystemsHavingCapabilities(db, tenant, capabilitiesInAST, allowedIDs);

      // Close out and commit
      LibUtils.closeAndCommitDB(conn, null, null);
    }
    catch (Exception e)
    {
      // Rollback transaction and throw an exception
      LibUtils.rollbackDB(conn, e,"DB_QUERY_ERROR", "systems", e.getMessage());
    }
    finally
    {
      // Always return the connection back to the connection pool.
      LibUtils.finalCloseDB(conn);
    }

    // If there was a problem the list to match against might be null
    if (systemsList == null) return retList;

    // TODO Select only those systems satisfying the constraints
    for (TSystem sys : systemsList)
    {
// TODO      if (systemMatchesConstraints(sys, matchAST)) retList.add(sys);
      retList.add(sys);
    }
    return retList;
  }

  /**
   * getSystemBasic
   * @param id - system name
   * @return SystemBasic object if found, null if not found
   * @throws TapisException - on error
   */
  @Override
  public SystemBasic getSystemBasic(String tenant, String id) throws TapisException {
    // Initialize result.
    SystemBasic systemBasic = null;
    TSystem result = null;

    // Build list of attributes we will be returning.
    List<TableField> fieldList = new ArrayList<>();
    fieldList.add(SYSTEMS.SEQ_ID);
    fieldList.add(SYSTEMS.TENANT);
    fieldList.add(SYSTEMS.ID);
    fieldList.add(SYSTEMS.SYSTEM_TYPE);
    fieldList.add(SYSTEMS.OWNER);
    fieldList.add(SYSTEMS.HOST);
    fieldList.add(SYSTEMS.DEFAULT_AUTHN_METHOD);
    fieldList.add(SYSTEMS.CAN_EXEC);

    // ------------------------- Call SQL ----------------------------
    Connection conn = null;
    try
    {
      // Get a database connection.
      conn = getConnection();
      DSLContext db = DSL.using(conn);
      SystemsRecord r = db.select(fieldList).from(SYSTEMS)
              .where(SYSTEMS.TENANT.eq(tenant),SYSTEMS.ID.eq(id),SYSTEMS.DELETED.eq(false))
              .fetchOne().into(SYSTEMS);
      if (r == null) return null;
      else result = r.into(TSystem.class);

      // Close out and commit
      LibUtils.closeAndCommitDB(conn, null, null);
    }
    catch (Exception e)
    {
      // Rollback transaction and throw an exception
      LibUtils.rollbackDB(conn, e,"DB_SELECT_NAME_ERROR", "System", tenant, id, e.getMessage());
    }
    finally
    {
      // Always return the connection back to the connection pool.
      LibUtils.finalCloseDB(conn);
    }
    if (result != null)
    {
      systemBasic = new SystemBasic(result);
    }
    return systemBasic;
  }

  /**
   * getSystemsBasic
   * TODO: This method and getTSystems are very similar. Factor out some helper methods?
   * Retrieve all System matching various search and sort criteria.
   *     Search conditions given as a list of strings or an abstract syntax tree (AST).
   * Conditions in searchList must be processed by SearchUtils.validateAndExtractSearchCondition(cond)
   *   prior to this call for proper validation and treatment of special characters.
   * WARNING: If both searchList and searchAST provided only searchList is used.
   * @param tenant - tenant name
   * @param searchList - optional list of conditions used for searching
   * @param searchAST - AST containing search conditions
   * @param setOfIDs - list of system IDs to consider. null indicates no restriction.
   * @param limit - indicates maximum number of results to be included, -1 for unlimited
   * @param orderByList - orderBy entries for sorting, e.g. orderBy=created(desc).
   * @param skip - number of results to skip (may not be used with startAfter)
   * @param startAfter - where to start when sorting, e.g. limit=10&orderBy=id(asc)&startAfter=101 (may not be used with skip)
   * @return - list of SystemBasic objects
   * @throws TapisException - on error
   */
  @Override
  public List<SystemBasic> getSystemsBasic(String tenant, List<String> searchList, ASTNode searchAST, Set<String> setOfIDs,
                                           int limit, List<OrderBy> orderByList, int skip, String startAfter)
          throws TapisException
  {
    // TODO - for now just use the major (i.e. first in list) orderBy item.
    String majorOrderBy = null;
    String majorSortDirection = "ASC";
    if (orderByList != null && !orderByList.isEmpty())
    {
      majorOrderBy = orderByList.get(0).getOrderByAttr();
      majorSortDirection = orderByList.get(0).getOrderByDir().name();
    }

    // The result list should always be non-null.
    var retList = new ArrayList<SystemBasic>();

    // Negative skip indicates no skip
    if (skip < 0) skip = 0;

    boolean sortAsc = true;
    if (OrderBy.OrderByDir.DESC.name().equalsIgnoreCase(majorSortDirection)) sortAsc = false;

    // If startAfter is given then orderBy is required
    if (!StringUtils.isBlank(startAfter) && StringUtils.isBlank(majorOrderBy))
    {
      String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_SORT_START", SYSTEMS.getName());
      throw new TapisException(msg);
    }

    // If no IDs in list then we are done.
    if (setOfIDs != null && setOfIDs.isEmpty()) return retList;

    // Determine and check orderBy column
    Field<?> colOrderBy = SYSTEMS.field(DSL.name(SearchUtils.camelCaseToSnakeCase(majorOrderBy)));
    if (!StringUtils.isBlank(majorOrderBy) && colOrderBy == null)
    {
      String msg = LibUtils.getMsg("SYSLIB_DB_NO_COLUMN_SORT", SYSTEMS.getName(), DSL.name(majorOrderBy));
      throw new TapisException(msg);
    }

    // Begin where condition for the query
    Condition whereCondition = (SYSTEMS.TENANT.eq(tenant)).and(SYSTEMS.DELETED.eq(false));

    // Add searchList or searchAST to where condition
    if (searchList != null)
    {
      whereCondition = addSearchListToWhere(whereCondition, searchList);
    }
    else if (searchAST != null)
    {
      Condition astCondition = createConditionFromAst(searchAST);
      if (astCondition != null) whereCondition = whereCondition.and(astCondition);
    }

    // Add startAfter
    if (!StringUtils.isBlank(startAfter))
    {
      // Build search string so we can re-use code for checking and adding a condition
      String searchStr;
      if (sortAsc) searchStr = majorOrderBy + ".gt." + startAfter;
      else searchStr = majorOrderBy + ".lt." + startAfter;
      whereCondition = addSearchCondStrToWhere(whereCondition, searchStr, "AND");
    }

    // Add IN condition for list of IDs
    if (setOfIDs != null && !setOfIDs.isEmpty()) whereCondition = whereCondition.and(SYSTEMS.ID.in(setOfIDs));

    // Build list of attributes we will be returning.
    List<TableField> fieldList = new ArrayList<>();
    fieldList.add(SYSTEMS.SEQ_ID);
    fieldList.add(SYSTEMS.TENANT);
    fieldList.add(SYSTEMS.ID);
    fieldList.add(SYSTEMS.SYSTEM_TYPE);
    fieldList.add(SYSTEMS.OWNER);
    fieldList.add(SYSTEMS.HOST);
    fieldList.add(SYSTEMS.DEFAULT_AUTHN_METHOD);
    fieldList.add(SYSTEMS.CAN_EXEC);

    // ------------------------- Build and execute SQL ----------------------------
    Connection conn = null;
    try
    {
      // Get a database connection.
      conn = getConnection();
      DSLContext db = DSL.using(conn);

      // Execute the select including limit, orderByAttrList, skip and startAfter
      // NOTE: LIMIT + OFFSET is not standard among DBs and often very difficult to get right.
      //       Jooq claims to handle it well.
      Result<SystemsRecord> results;
      org.jooq.SelectConditionStep condStep = db.select(fieldList).from(SYSTEMS).where(whereCondition);
      if (!StringUtils.isBlank(majorOrderBy) &&  limit >= 0)
      {
        // We are ordering and limiting
        if (sortAsc) results = condStep.orderBy(colOrderBy.asc()).limit(limit).offset(skip).fetchInto(SYSTEMS);
        else results = condStep.orderBy(colOrderBy.desc()).limit(limit).offset(skip).fetchInto(SYSTEMS);
      }
      else if (!StringUtils.isBlank(majorOrderBy))
      {
        // We are ordering but not limiting
        if (sortAsc) results = condStep.orderBy(colOrderBy.asc()).fetchInto(SYSTEMS);
        else results = condStep.orderBy(colOrderBy.desc()).fetchInto(SYSTEMS);
      }
      else if (limit >= 0)
      {
        // We are limiting but not ordering
        results = condStep.limit(limit).offset(skip).fetchInto(SYSTEMS);
      }
      else
      {
        // We are not limiting and not ordering
        results = condStep.fetchInto(SYSTEMS);
      }

      if (results == null || results.isEmpty()) return retList;

      // Create SystemBasic objects from TSystem objects.
      for (SystemsRecord r : results)
      {
        TSystem s = r.into(TSystem.class);
        retList.add(new SystemBasic(s));
      }

      // Close out and commit
      LibUtils.closeAndCommitDB(conn, null, null);
    }
    catch (Exception e)
    {
      // Rollback transaction and throw an exception
      LibUtils.rollbackDB(conn, e,"DB_QUERY_ERROR", "systems", e.getMessage());
    }
    finally
    {
      // Always return the connection back to the connection pool.
      LibUtils.finalCloseDB(conn);
    }
    return retList;
  }

  /**
   * getSystemNames
   * @param tenant - tenant name
   * @return - List of system names
   * @throws TapisException - on error
   */
  @Override
  public Set<String> getSystemNames(String tenant) throws TapisException
  {
    // The result list is always non-null.
    var names = new HashSet<String>();

    Connection conn = null;
    try
    {
      // Get a database connection.
      conn = getConnection();
      // ------------------------- Call SQL ----------------------------
      // Use jOOQ to build query string
      DSLContext db = DSL.using(conn);
      Result<?> result = db.select(SYSTEMS.ID).from(SYSTEMS).where(SYSTEMS.TENANT.eq(tenant)).fetch();
      // Iterate over result
      for (Record r : result) { names.add(r.get(SYSTEMS.ID)); }
    }
    catch (Exception e)
    {
      // Rollback transaction and throw an exception
      LibUtils.rollbackDB(conn, e,"DB_QUERY_ERROR", "systems", e.getMessage());
    }
    finally
    {
      // Always return the connection back to the connection pool.
      LibUtils.finalCloseDB(conn);
    }
    return names;
  }

  /**
   * getSystemOwner
   * @param tenant - name of tenant
   * @param id - name of system
   * @return Owner or null if no system found
   * @throws TapisException - on error
   */
  @Override
  public String getSystemOwner(String tenant, String id) throws TapisException
  {
    String owner = null;
    // ------------------------- Call SQL ----------------------------
    Connection conn = null;
    try
    {
      // Get a database connection.
      conn = getConnection();
      DSLContext db = DSL.using(conn);
      owner = db.selectFrom(SYSTEMS).where(SYSTEMS.TENANT.eq(tenant),SYSTEMS.ID.eq(id)).fetchOne(SYSTEMS.OWNER);

      // Close out and commit
      LibUtils.closeAndCommitDB(conn, null, null);
    }
    catch (Exception e)
    {
      // Rollback transaction and throw an exception
      LibUtils.rollbackDB(conn, e,"DB_QUERY_ERROR", "systems", e.getMessage());
    }
    finally
    {
      // Always return the connection back to the connection pool.
      LibUtils.finalCloseDB(conn);
    }
    return owner;
  }

  /**
   * getSystemEffectiveUserId
   * @param tenant - name of tenant
   * @param id - name of system
   * @return EffectiveUserId or null if no system found
   * @throws TapisException - on error
   */
  @Override
  public String getSystemEffectiveUserId(String tenant, String id) throws TapisException
  {
    String effectiveUserId = null;

    // ------------------------- Call SQL ----------------------------
    Connection conn = null;
    try
    {
      // Get a database connection.
      conn = getConnection();
      DSLContext db = DSL.using(conn);
      effectiveUserId = db.selectFrom(SYSTEMS).where(SYSTEMS.TENANT.eq(tenant),SYSTEMS.ID.eq(id)).fetchOne(SYSTEMS.EFFECTIVE_USER_ID);

      // Close out and commit
      LibUtils.closeAndCommitDB(conn, null, null);
    }
    catch (Exception e)
    {
      // Rollback transaction and throw an exception
      LibUtils.rollbackDB(conn, e,"DB_QUERY_ERROR", "systems", e.getMessage());
    }
    finally
    {
      // Always return the connection back to the connection pool.
      LibUtils.finalCloseDB(conn);
    }
    return effectiveUserId;
  }

  /**
   * Add an update record given the system Id and operation type
   *
   */
  @Override
  public void addUpdateRecord(AuthenticatedUser authenticatedUser, String tenant, String id, SystemOperation op,
                              String upd_json, String upd_text) throws TapisException
  {
    // ------------------------- Call SQL ----------------------------
    Connection conn = null;
    try
    {
      // Get a database connection.
      conn = getConnection();
      DSLContext db = DSL.using(conn);
      addUpdate(db, authenticatedUser, tenant, id, INVALID_SEQ_ID, op, upd_json, upd_text,
                getUUIDUsingDb(db, tenant, id));

      // Close out and commit
      LibUtils.closeAndCommitDB(conn, null, null);
    }
    catch (Exception e)
    {
      // Rollback transaction and throw an exception
      LibUtils.rollbackDB(conn, e,"DB_INSERT_FAILURE", "systems");
    }
    finally
    {
      // Always return the connection back to the connection pool.
      LibUtils.finalCloseDB(conn);
    }
  }

  /* ********************************************************************** */
  /*                             Private Methods                            */
  /* ********************************************************************** */

  /**
   * Given an sql connection and basic info add an update record
   * If seqId <= 0 then seqId is fetched.
   * NOTE: Both system tenant and user tenant are recorded. If a service makes an update on behalf of itself
   *       the tenants will differ.
   *
   * @param db - Database connection
   * @param authenticatedUser - User who requested the update
   * @param tenant - Tenant of the system being updated
   * @param id - Id of the system being updated
   * @param seqId - Sequence Id of system being updated
   * @param op - Operation, such as create, modify, etc.
   * @param upd_json - JSON representing the update - with secrets scrubbed
   * @param upd_text - Text data supplied by client - secrets should be scrubbed
   */
  private void addUpdate(DSLContext db, AuthenticatedUser authenticatedUser, String tenant, String id, int seqId,
                         SystemOperation op, String upd_json, String upd_text, UUID uuid)
  {
    String updJsonStr = (StringUtils.isBlank(upd_json)) ? EMPTY_JSON : upd_json;
    if (seqId < 1)
    {
      seqId = db.selectFrom(SYSTEMS).where(SYSTEMS.TENANT.eq(tenant),SYSTEMS.ID.eq(id)).fetchOne(SYSTEMS.SEQ_ID);
    }
    // Persist update record
    db.insertInto(SYSTEM_UPDATES)
            .set(SYSTEM_UPDATES.SYSTEM_SEQ_ID, seqId)
            .set(SYSTEM_UPDATES.SYSTEM_TENANT, tenant)
            .set(SYSTEM_UPDATES.SYSTEM_ID, id)
            .set(SYSTEM_UPDATES.USER_TENANT, authenticatedUser.getOboTenantId())
            .set(SYSTEM_UPDATES.USER_NAME, authenticatedUser.getOboUser())
            .set(SYSTEM_UPDATES.OPERATION, op)
            .set(SYSTEM_UPDATES.UPD_JSON, TapisGsonUtils.getGson().fromJson(updJsonStr, JsonElement.class))
            .set(SYSTEM_UPDATES.UPD_TEXT, upd_text)
            .set(SYSTEM_UPDATES.UUID, uuid)
            .execute();
  }

  /**
   * Given an sql connection check to see if specified system exists and has/has not been soft deleted
   * @param db - jooq context
   * @param tenant - name of tenant
   * @param id - name of system
   * @param includeDeleted -if soft deleted systems should be included
   * @return - true if system exists, else false
   */
  private static boolean checkForSystem(DSLContext db, String tenant, String id, boolean includeDeleted)
  {
    if (includeDeleted) return db.fetchExists(SYSTEMS,SYSTEMS.TENANT.eq(tenant),SYSTEMS.ID.eq(id));
    else return db.fetchExists(SYSTEMS,SYSTEMS.TENANT.eq(tenant),SYSTEMS.ID.eq(id),SYSTEMS.DELETED.eq(false));
  }

  /**
   * Persist batch logical queues given an sql connection and a system
   */
  private static void persistLogicalQueues(DSLContext db, TSystem tSystem, int seqId)
  {
    var logicalQueues = tSystem.getBatchLogicalQueues();
    if (logicalQueues == null || logicalQueues.isEmpty()) return;
    for (LogicalQueue queue : logicalQueues) {
      db.insertInto(LOGICAL_QUEUES).set(LOGICAL_QUEUES.SYSTEM_SEQ_ID, seqId)
              .set(LOGICAL_QUEUES.NAME, queue.getName())
              .set(LOGICAL_QUEUES.HPC_QUEUE_NAME, queue.getHpcQueueName())
              .set(LOGICAL_QUEUES.MAX_JOBS, queue.getMaxJobs())
              .set(LOGICAL_QUEUES.MAX_JOBS_PER_USER, queue.getMaxJobsPerUser())
              .set(LOGICAL_QUEUES.MAX_NODE_COUNT, queue.getMaxNodeCount())
              .set(LOGICAL_QUEUES.MAX_CORES_PER_NODE, queue.getMaxCoresPerNode())
              .set(LOGICAL_QUEUES.MAX_MEMORY_MB, queue.getMaxMemoryMB())
              .set(LOGICAL_QUEUES.MAX_MINUTES, queue.getMaxMinutes())
              .execute();
    }
  }

  /**
   * Persist job capabilities given an sql connection and a system
   */
  private static void persistJobCapabilities(DSLContext db, TSystem tSystem, int seqId)
  {
    var jobCapabilities = tSystem.getJobCapabilities();
    if (jobCapabilities == null || jobCapabilities.isEmpty()) return;

    for (Capability cap : jobCapabilities) {
      int precedence = Capability.DEFAULT_PRECEDENCE;
      String valStr = Capability.DEFAULT_VALUE;
      if (cap.getPrecedence() > 0) precedence = cap.getPrecedence();
      if (cap.getValue() != null ) valStr = cap.getValue();
      db.insertInto(CAPABILITIES).set(CAPABILITIES.SYSTEM_SEQ_ID, seqId)
              .set(CAPABILITIES.CATEGORY, cap.getCategory())
              .set(CAPABILITIES.NAME, cap.getName())
              .set(CAPABILITIES.DATATYPE, cap.getDatatype())
              .set(CAPABILITIES.PRECEDENCE, precedence)
              .set(CAPABILITIES.VALUE, valStr)
              .execute();
    }
  }

  /**
   * Persist job runtimes given an sql connection and a system
   */
  private static void persistJobRuntimes(DSLContext db, TSystem tSystem, int seqId)
  {
    var jobRuntimes = tSystem.getJobRuntimes();
    if (jobRuntimes == null || jobRuntimes.isEmpty()) return;
    for (JobRuntime runtime : jobRuntimes) {
      db.insertInto(JOB_RUNTIMES).set(JOB_RUNTIMES.SYSTEM_SEQ_ID, seqId)
            .set(JOB_RUNTIMES.RUNTIME_TYPE, runtime.getRuntimeType())
            .set(JOB_RUNTIMES.VERSION, runtime.getVersion())
              .execute();
    }
  }

  /**
   * Get batch logical queues for a system from an auxiliary table
   * @param db - DB connection
   * @param seqId - system
   * @return list of logical queues
   */
  private static List<LogicalQueue> retrieveLogicalQueues(DSLContext db, int seqId)
  {
    List<LogicalQueue> qRecords = db.selectFrom(LOGICAL_QUEUES).where(LOGICAL_QUEUES.SYSTEM_SEQ_ID.eq(seqId)).fetchInto(LogicalQueue.class);
    return qRecords;
  }

  /**
   * Get capabilities for a system from an auxiliary table
   * @param db - DB connection
   * @param seqId - system
   * @return list of capabilities
   */
  private static List<Capability> retrieveJobCaps(DSLContext db, int seqId)
  {
    List<Capability> capRecords = db.selectFrom(CAPABILITIES).where(CAPABILITIES.SYSTEM_SEQ_ID.eq(seqId)).fetchInto(Capability.class);
    return capRecords;
  }

  /**
   * Get jobRuntimes for a system from an auxiliary table
   * @param db - DB connection
   * @param seqId - system
   * @return list of runtimes
   */
  private static List<JobRuntime> retrieveJobRuntimes(DSLContext db, int seqId)
  {
    List<JobRuntime> jobRuntimes = db.selectFrom(JOB_RUNTIMES).where(JOB_RUNTIMES.SYSTEM_SEQ_ID.eq(seqId)).fetchInto(JobRuntime.class);
    return jobRuntimes;
  }

  /**
   * Get all system sequence IDs for specified tenant
   * @param db - DB connection
   * @param tenant - tenant name
   * @return list of sequence IDs
   */
  private static List<Integer> getAllSystemSeqIdsInTenant(DSLContext db, String tenant)
  {
    List<Integer> retList = new ArrayList<>();
    if (db == null || StringUtils.isBlank(tenant)) return retList;
    retList = db.select(SYSTEMS.SEQ_ID).from(SYSTEMS).where(SYSTEMS.TENANT.eq(tenant)).fetchInto(Integer.class);
    return retList;
  }

  /**
   * Add searchList to where condition. All conditions are joined using AND
   * Validate column name, search comparison operator
   *   and compatibility of column type + search operator + column value
   * @param whereCondition base where condition
   * @param searchList List of conditions to add to the base condition
   * @return resulting where condition
   * @throws TapisException on error
   */
  private static Condition addSearchListToWhere(Condition whereCondition, List<String> searchList)
          throws TapisException
  {
    if (searchList == null || searchList.isEmpty()) return whereCondition;
    // Parse searchList and add conditions to the WHERE clause
    for (String condStr : searchList)
    {
      whereCondition = addSearchCondStrToWhere(whereCondition, condStr, "AND");
    }
    return whereCondition;
  }

  /**
   * Create a condition for abstract syntax tree nodes by recursively walking the tree
   * @param astNode Abstract syntax tree node to add to the base condition
   * @return resulting condition
   * @throws TapisException on error
   */
  private static Condition createConditionFromAst(ASTNode astNode) throws TapisException
  {
    if (astNode == null || astNode instanceof ASTLeaf)
    {
      // A leaf node is a column name or value. Nothing to process since we only process a complete condition
      //   having the form column_name.op.value. We should never make it to here
      String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_SEARCH_AST1", (astNode == null ? "null" : astNode.toString()));
      throw new TapisException(msg);
    }
    else if (astNode instanceof ASTUnaryExpression)
    {
      // A unary node should have no operator and contain a binary node with two leaf nodes.
      // NOTE: Currently unary operators not supported. If support is provided for unary operators (such as NOT) then
      //   changes will be needed here.
      ASTUnaryExpression unaryNode = (ASTUnaryExpression) astNode;
      if (!StringUtils.isBlank(unaryNode.getOp()))
      {
        String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_SEARCH_UNARY_OP", unaryNode.getOp(), unaryNode.toString());
        throw new TapisException(msg);
      }
      // Recursive call
      return createConditionFromAst(unaryNode.getNode());
    }
    else if (astNode instanceof ASTBinaryExpression)
    {
      // It is a binary node
      ASTBinaryExpression binaryNode = (ASTBinaryExpression) astNode;
      // Recursive call
      return createConditionFromBinaryExpression(binaryNode);
    }
    return null;
  }

  /**
   * Create a condition from an abstract syntax tree binary node
   * @param binaryNode Abstract syntax tree binary node to add to the base condition
   * @return resulting condition
   * @throws TapisException on error
   */
  private static Condition createConditionFromBinaryExpression(ASTBinaryExpression binaryNode) throws TapisException
  {
    // If we are given a null then something went very wrong.
    if (binaryNode == null)
    {
      String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_SEARCH_AST2");
      throw new TapisException(msg);
    }
    // If operator is AND or OR then make recursive call for each side and join together
    // For other operators build the condition left.op.right and add it
    String op = binaryNode.getOp();
    ASTNode leftNode = binaryNode.getLeft();
    ASTNode rightNode = binaryNode.getRight();
    if (StringUtils.isBlank(op))
    {
      String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_SEARCH_AST3", binaryNode.toString());
      throw new TapisException(msg);
    }
    else if (op.equalsIgnoreCase("AND"))
    {
      // Recursive calls
      Condition cond1 = createConditionFromAst(leftNode);
      Condition cond2 = createConditionFromAst(rightNode);
      if (cond1 == null || cond2 == null)
      {
        String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_SEARCH_AST4", binaryNode.toString());
        throw new TapisException(msg);
      }
      return cond1.and(cond2);

    }
    else if (op.equalsIgnoreCase("OR"))
    {
      // Recursive calls
      Condition cond1 = createConditionFromAst(leftNode);
      Condition cond2 = createConditionFromAst(rightNode);
      if (cond1 == null || cond2 == null)
      {
        String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_SEARCH_AST4", binaryNode.toString());
        throw new TapisException(msg);
      }
      return cond1.or(cond2);

    }
    else
    {
      // End of recursion. Create a single condition.
      // Since operator is not an AND or an OR we should have 2 unary nodes or a unary and leaf node
      String lValue;
      String rValue;
      if (leftNode instanceof ASTLeaf) lValue = ((ASTLeaf) leftNode).getValue();
      else if (leftNode instanceof ASTUnaryExpression) lValue =  ((ASTLeaf) ((ASTUnaryExpression) leftNode).getNode()).getValue();
      else
      {
        String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_SEARCH_AST5", binaryNode.toString());
        throw new TapisException(msg);
      }
      if (rightNode instanceof ASTLeaf) rValue = ((ASTLeaf) rightNode).getValue();
      else if (rightNode instanceof ASTUnaryExpression) rValue =  ((ASTLeaf) ((ASTUnaryExpression) rightNode).getNode()).getValue();
      else
      {
        String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_SEARCH_AST6", binaryNode.toString());
        throw new TapisException(msg);
      }
      // Build the string for the search condition, left.op.right
      String condStr = lValue + "." + binaryNode.getOp() + "." + rValue;
      // Validate and create a condition from the string
      return addSearchCondStrToWhere(null, condStr, null);
    }
  }

  /**
   * Take a string containing a single condition and create a new condition or join it to an existing condition.
   * Validate column name, search comparison operator and compatibility of column type + search operator + column value
   * @param whereCondition existing condition. If null a new condition is returned.
   * @param searchStr Single search condition in the form column_name.op.value
   * @param joinOp If whereCondition is not null use AND or OR to join the condition with the whereCondition
   * @return resulting where condition
   * @throws TapisException on error
   */
  private static Condition addSearchCondStrToWhere(Condition whereCondition, String searchStr, String joinOp)
          throws TapisException
  {
    // If we have no search string then return what we were given
    if (StringUtils.isBlank(searchStr)) return whereCondition;
    // If we are given a condition but no indication of how to join new condition to it then return what we were given
    if (whereCondition != null && StringUtils.isBlank(joinOp)) return whereCondition;
    if (whereCondition != null && joinOp != null && !joinOp.equalsIgnoreCase("AND") && !joinOp.equalsIgnoreCase("OR"))
    {
      return whereCondition;
    }

    // Parse search value into column name, operator and value
    // Format must be column_name.op.value
    String[] parsedStrArray = searchStr.split("\\.", 3);
    // Validate column name
    String column = parsedStrArray[0];
    Field<?> col = SYSTEMS.field(DSL.name(column));
    // Check for column name passed in as camelcase
    if (col == null)
    {
      col = SYSTEMS.field(DSL.name(SearchUtils.camelCaseToSnakeCase(column)));
    }
    // If column not found then it is an error
    if (col == null)
    {
      String msg = LibUtils.getMsg("SYSLIB_DB_NO_COLUMN", SYSTEMS.getName(), DSL.name(column));
      throw new TapisException(msg);
    }
    // Validate and convert operator string
    String opStr = parsedStrArray[1].toUpperCase();
    SearchOperator op = SearchUtils.getSearchOperator(opStr);
    if (op == null)
    {
      String msg = MsgUtils.getMsg("SYSLIB_DB_INVALID_SEARCH_OP", opStr, SYSTEMS.getName(), DSL.name(column));
      throw new TapisException(msg);
    }

    // Check that column value is compatible for column type and search operator
    String val = parsedStrArray[2];
    checkConditionValidity(col, op, val);

     // If val is a timestamp then convert the string(s) to a form suitable for SQL
    // Use a utility method since val may be a single item or a list of items, e.g. for the BETWEEN operator
    if (col.getDataType().getSQLType() == Types.TIMESTAMP)
    {
      val = SearchUtils.convertValuesToTimestamps(op, val);
    }

    // Create the condition
    Condition newCondition = createCondition(col, op, val);
    // If specified add the condition to the WHERE clause
    if (StringUtils.isBlank(joinOp) || whereCondition == null) return newCondition;
    else if (joinOp.equalsIgnoreCase("AND")) return whereCondition.and(newCondition);
    else if (joinOp.equalsIgnoreCase("OR")) return whereCondition.or(newCondition);
    return newCondition;
  }

  /**
   * Validate condition expression based on column type, search operator and column string value.
   * Use java.sql.Types for validation.
   * @param col jOOQ column
   * @param op Operator
   * @param valStr Column value as string
   * @throws TapisException on error
   */
  private static void checkConditionValidity(Field<?> col, SearchOperator op, String valStr) throws TapisException
  {
    var dataType = col.getDataType();
    int sqlType = dataType.getSQLType();
    String sqlTypeName = dataType.getTypeName();
//    var t2 = dataType.getSQLDataType();
//    var t3 = dataType.getCastTypeName();
//    var t4 = dataType.getSQLType();
//    var t5 = dataType.getType();

    // Make sure we support the sqlType
    if (SearchUtils.ALLOWED_OPS_BY_TYPE.get(sqlType) == null)
    {
      String msg = LibUtils.getMsg("SYSLIB_DB_UNSUPPORTED_SQLTYPE", SYSTEMS.getName(), col.getName(), op.name(), sqlTypeName);
      throw new TapisException(msg);
    }
    // Check that operation is allowed for column data type
    if (!SearchUtils.ALLOWED_OPS_BY_TYPE.get(sqlType).contains(op))
    {
      String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_SEARCH_TYPE", SYSTEMS.getName(), col.getName(), op.name(), sqlTypeName);
      throw new TapisException(msg);
    }

    // Check that value (or values for op that takes a list) are compatible with sqlType
    if (!SearchUtils.validateTypeAndValueList(sqlType, op, valStr, sqlTypeName, SYSTEMS.getName(), col.getName()))
    {
      String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_SEARCH_VALUE", op.name(), sqlTypeName, valStr, SYSTEMS.getName(), col.getName());
      throw new TapisException(msg);
    }
  }

  /**
   * Add condition to SQL where clause given column, operator, value info
   * @param col jOOQ column
   * @param op Operator
   * @param val Column value
   * @return Resulting where clause
   */
  private static Condition createCondition(Field col, SearchOperator op, String val)
  {
    List<String> valList = Collections.emptyList();
    if (SearchUtils.listOpSet.contains(op)) valList = SearchUtils.getValueList(val);
    switch (op) {
      case EQ:
        return col.eq(val);
      case NEQ:
        return col.ne(val);
      case LT:
        return col.lt(val);
      case LTE:
        return col.le(val);
      case GT:
        return col.gt(val);
      case GTE:
        return col.ge(val);
      case LIKE:
        return col.like(val);
      case NLIKE:
        return col.notLike(val);
      case IN:
        return col.in(valList);
      case NIN:
        return col.notIn(valList);
      case BETWEEN:
        return col.between(valList.get(0), valList.get(1));
      case NBETWEEN:
        return col.notBetween(valList.get(0), valList.get(1));
    }
    return null;
  }

  /**
   * Get all capabilities contained in an abstract syntax tree by recursively walking the tree
   * @param astNode Abstract syntax tree node containing constraint matching conditions
   * @throws TapisException on error
   */
  private static void getCapabilitiesFromAST(ASTNode astNode, List<Capability> capList) throws TapisException
  {
    if (astNode == null || astNode instanceof ASTLeaf)
    {
      // A leaf node is "category$name" or value. Nothing to process since we only process a complete condition
      //   having the form category$name op value. We should never make it to here
      String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_MATCH_AST1", (astNode == null ? "null" : astNode.toString()));
      throw new TapisException(msg);
    }
    else if (astNode instanceof ASTUnaryExpression)
    {
      // A unary node should have no operator and contain a binary node with two leaf nodes.
      // NOTE: Currently unary operators not supported. If support is provided for unary operators (such as NOT) then
      //   changes will be needed here.
      ASTUnaryExpression unaryNode = (ASTUnaryExpression) astNode;
      if (!StringUtils.isBlank(unaryNode.getOp()))
      {
        String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_SEARCH_UNARY_OP", unaryNode.getOp(), unaryNode.toString());
        throw new TapisException(msg);
      }
      // Recursive call
      getCapabilitiesFromAST(unaryNode.getNode(), capList);
    }
    else if (astNode instanceof ASTBinaryExpression)
    {
      // It is a binary node
      ASTBinaryExpression binaryNode = (ASTBinaryExpression) astNode;
      // Recursive call
      getCapabilitiesFromBinaryExpression(binaryNode, capList);
    }
  }

  /**
   * Add capabilities from an abstract syntax tree binary node
   * @param binaryNode Abstract syntax tree binary node to add
   * @throws TapisException on error
   */
  private static void getCapabilitiesFromBinaryExpression(ASTBinaryExpression binaryNode, List<Capability> capList)
          throws TapisException
  {
    // If we are given a null then something went very wrong.
    if (binaryNode == null)
    {
      String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_MATCH_AST2");
      throw new TapisException(msg);
    }
    // If operator is AND or OR then make recursive call for each side
    // Since we are just collecting capabilities we do not distinguish between AND, OR
    // For other operators extract the capability and return
    String op = binaryNode.getOp();
    ASTNode leftNode = binaryNode.getLeft();
    ASTNode rightNode = binaryNode.getRight();
    if (StringUtils.isBlank(op))
    {
      String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_MATCH_AST3", binaryNode.toString());
      throw new TapisException(msg);
    }
    else if (op.equalsIgnoreCase("AND") || op.equalsIgnoreCase("OR"))
    {
      // Recursive calls
      getCapabilitiesFromAST(leftNode, capList);
      getCapabilitiesFromAST(rightNode, capList);
    }
    else
    {
      // End of recursion. Extract the capability and return
      // Since operator is not an AND or an OR we should have 2 unary nodes or a unary and leaf node
      // lValue should be in the form category-name or category$name
      // rValue should be the Capability value.
      String lValue;
      String rValue;
      if (leftNode instanceof ASTLeaf) lValue = ((ASTLeaf) leftNode).getValue();
      else if (leftNode instanceof ASTUnaryExpression) lValue =  ((ASTLeaf) ((ASTUnaryExpression) leftNode).getNode()).getValue();
      else
      {
        String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_MATCH_AST5", binaryNode.toString());
        throw new TapisException(msg);
      }
      if (rightNode instanceof ASTLeaf) rValue = ((ASTLeaf) rightNode).getValue();
      else if (rightNode instanceof ASTUnaryExpression) rValue =  ((ASTLeaf) ((ASTUnaryExpression) rightNode).getNode()).getValue();
      else
      {
        String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_MATCH_AST6", binaryNode.toString());
        throw new TapisException(msg);
      }
      // Validate and create a capability using lValue, rValue from node
      Capability cap = getCapabilityFromNode(lValue, rValue, binaryNode);
      capList.add(cap);
    }
  }

  /**
   * Construct a Capability based on lValue, rValue from a binary ASTNode containing a constraint matching condition
   * Validate and extract capability attributes: category, name and value.
   *   lValue must be in the form category$name or category$name
   * @param lValue - left string value from the condition in the form category-name or category-name
   * @param rValue - right string value from the condition
   * @return - capability
   * @throws TapisException on error
   */
  private static Capability getCapabilityFromNode(String lValue, String rValue, ASTBinaryExpression binaryNode)
          throws TapisException
  {
    // If lValue is empty it is an error
    if (StringUtils.isBlank(lValue))
    {
      String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_MATCH_AST7", binaryNode);
      throw new TapisException(msg);
    }
    // Validate and extract components from lValue
    // Parse lValue into category, and name
    // Format must be column_name.op.value
    String[] parsedStrArray = lValue.split("\\$", 2);
    // Must have at least two items
    if (parsedStrArray.length < 2)
    {
      String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_MATCH_AST7", binaryNode);
      throw new TapisException(msg);
    }
    String categoryStr = parsedStrArray[0];
    Capability.Category category = null;
    try { category = Capability.Category.valueOf(categoryStr.toUpperCase()); }
    catch (IllegalArgumentException e)
    {
      String msg = LibUtils.getMsg("SYSLIB_DB_INVALID_MATCH_AST7", binaryNode);
      throw new TapisException(msg);
    }
    String name = parsedStrArray[1];
    Capability.Datatype datatype = null;
    int precedence = -1;
    Capability cap = new Capability(category, name, datatype, precedence, rValue);
    return cap;
  }

  /**
   * Given an sql connection retrieve the system uuid.
   * @param db - jooq context
   * @param tenant - name of tenant
   * @param id - Id of system
   * @return - uuid
   */
  private static UUID getUUIDUsingDb(DSLContext db, String tenant, String id)
  {
    return db.selectFrom(SYSTEMS).where(SYSTEMS.TENANT.eq(tenant),SYSTEMS.ID.eq(id)).fetchOne(SYSTEMS.UUID);
  }


  /**
   * Given an sql connection, a tenant, a list of Category names and a list of system IDs to consider,
   *   fetch all systems that have a Capability matching a category, name.
   * @param db - jooq context
   * @param tenant - name of tenant
   * @param capabilityList - list of Capabilities from AST (category, name)
   * @param allowedIDs - list of system IDs to consider.
   * @return - true if system exists, else false
   */
  private static List<TSystem> getSystemsHavingCapabilities(DSLContext db, String tenant, List<Capability> capabilityList,
                                                            Set<String> allowedIDs)
  {
    List<TSystem> retList = new ArrayList<>();
    if (allowedIDs == null || allowedIDs.isEmpty()) return retList;

    // Begin where condition for the query
    Condition whereCondition = (SYSTEMS.TENANT.eq(tenant)).and(SYSTEMS.DELETED.eq(false));

    Field catCol = CAPABILITIES.CATEGORY;
    Field nameCol = CAPABILITIES.NAME;

    // For each capability add a condition joined by OR
    Condition newCondition1 = null;
    for (Capability cap : capabilityList)
    {
      Condition newCondition2 = catCol.eq(cap.getCategory().name());
      newCondition2 = newCondition2.and(nameCol.eq(cap.getName()));
      if (newCondition1 == null) newCondition1 = newCondition2;
      else newCondition1 = newCondition1.or(newCondition2);
    }
    whereCondition = whereCondition.and(newCondition1);

    // TODO: Work out raw SQL, copy it here and translate it into jOOQ.
    /*
     * --  select S.id,S.name as s_name, C.id as c_id, C.category,C.name,C.value from systems as S
     * select S.* from systems as S
     *   join capabilities as C on (S.id = C.system_id)
     *   where c.category = 'SCHEDULER' and c.name = 'Type'
     *   and S.id in (222, 230, 245);
     *
     * select S.* from systems as S
     *   inner join capabilities as C on (S.id = C.system_id)
     *   where (c.category = 'SCHEDULER' and c.name = 'Type') OR
     *   (c.category = 'SCHEDULER' and c.name = 'Type')
     *   AND S.id in (222, 230, 245);
     */

    // Add IN condition for list of IDs
    whereCondition = whereCondition.and(SYSTEMS.ID.in(allowedIDs));

    // Inner join on capabilities table
    // Execute the select

    Result<SystemsRecord> results = db.selectFrom(SYSTEMS.join(CAPABILITIES).on(SYSTEMS.SEQ_ID.eq(CAPABILITIES.SYSTEM_SEQ_ID)))
                                      .where(whereCondition).fetchInto(SYSTEMS);
//    Result<SystemsRecord> results = db.select(SYSTEMS.fields()).from(SYSTEMS)
//            .innerJoin(CAPABILITIES).on(SYSTEMS.SEQ_ID.eq(CAPABILITIES.SYSTEM_ID))
//            .where(whereCondition).fetchInto(SYSTEMS);

    if (results == null || results.isEmpty()) return retList;

    // Fill in batch logical queues and job capabilities list from aux tables
    // TODO might be able to use fetchGroups to populate these.
    for (SystemsRecord r : results)
    {
      TSystem s = r.into(TSystem.class);
      s.setJobRuntimes(retrieveJobRuntimes(db, s.getSeqId()));
      s.setBatchLogicalQueues(retrieveLogicalQueues(db, s.getSeqId()));
      s.setJobCapabilities(retrieveJobCaps(db, s.getSeqId()));
      retList.add(s);
    }
    return retList;
  }
}
