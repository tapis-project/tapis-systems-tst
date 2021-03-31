package edu.utexas.tacc.tapis.systems.service;

import static edu.utexas.tacc.tapis.shared.TapisConstants.SYSTEMS_SERVICE;
import static edu.utexas.tacc.tapis.systems.model.Credential.SK_KEY_ACCESS_KEY;
import static edu.utexas.tacc.tapis.systems.model.Credential.SK_KEY_ACCESS_SECRET;
import static edu.utexas.tacc.tapis.systems.model.Credential.SK_KEY_PASSWORD;
import static edu.utexas.tacc.tapis.systems.model.Credential.SK_KEY_PRIVATE_KEY;
import static edu.utexas.tacc.tapis.systems.model.Credential.SK_KEY_PUBLIC_KEY;
import static edu.utexas.tacc.tapis.systems.model.Credential.TOP_LEVEL_SECRET_NAME;
import static edu.utexas.tacc.tapis.systems.model.TSystem.APIUSERID_VAR;
import static edu.utexas.tacc.tapis.systems.model.TSystem.DEFAULT_EFFECTIVEUSERID;
import static edu.utexas.tacc.tapis.systems.model.TSystem.OWNER_VAR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;

import edu.utexas.tacc.tapis.shared.TapisConstants;
import edu.utexas.tacc.tapis.shared.i18n.MsgUtils;
import edu.utexas.tacc.tapis.shared.security.ServiceClients;
import edu.utexas.tacc.tapis.shared.security.ServiceContext;
import edu.utexas.tacc.tapis.shared.threadlocal.OrderBy;
import org.apache.commons.lang3.StringUtils;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.utexas.tacc.tapis.client.shared.exceptions.TapisClientException;
import edu.utexas.tacc.tapis.search.parser.ASTParser;
import edu.utexas.tacc.tapis.search.parser.ASTNode;
import edu.utexas.tacc.tapis.search.SearchUtils;
import edu.utexas.tacc.tapis.security.client.SKClient;
import edu.utexas.tacc.tapis.security.client.gen.model.SkSecret;
import edu.utexas.tacc.tapis.security.client.gen.model.SkSecretVersionMetadata;
import edu.utexas.tacc.tapis.security.client.model.KeyType;
import edu.utexas.tacc.tapis.security.client.model.SKSecretDeleteParms;
import edu.utexas.tacc.tapis.security.client.model.SKSecretMetaParms;
import edu.utexas.tacc.tapis.security.client.model.SKSecretReadParms;
import edu.utexas.tacc.tapis.security.client.model.SKSecretWriteParms;
import edu.utexas.tacc.tapis.security.client.model.SecretType;
import edu.utexas.tacc.tapis.shared.exceptions.TapisException;
import edu.utexas.tacc.tapis.shared.threadlocal.TapisThreadContext;
import edu.utexas.tacc.tapis.shared.utils.TapisGsonUtils;
import edu.utexas.tacc.tapis.sharedapi.security.AuthenticatedUser;
import edu.utexas.tacc.tapis.systems.dao.SystemsDao;
import edu.utexas.tacc.tapis.systems.model.Credential;
import edu.utexas.tacc.tapis.systems.model.PatchSystem;
import edu.utexas.tacc.tapis.systems.model.SystemBasic;
import edu.utexas.tacc.tapis.systems.model.TSystem;
import edu.utexas.tacc.tapis.systems.model.TSystem.AuthnMethod;
import edu.utexas.tacc.tapis.systems.model.TSystem.Permission;
import edu.utexas.tacc.tapis.systems.model.TSystem.SystemOperation;
import edu.utexas.tacc.tapis.systems.utils.LibUtils;

/*
 * Service level methods for Systems.
 *   Uses Dao layer and other service library classes to perform all top level service operations.
 * Annotate as an hk2 Service so that default scope for DI is singleton
 */
@Service
public class SystemsServiceImpl implements SystemsService
{
  // ************************************************************************
  // *********************** Constants **************************************
  // ************************************************************************

  // Tracing.
  private static final Logger _log = LoggerFactory.getLogger(SystemsServiceImpl.class);

  private static final Set<Permission> ALL_PERMS = new HashSet<>(Set.of(Permission.READ, Permission.MODIFY, Permission.EXECUTE));
  private static final Set<Permission> READMODIFY_PERMS = new HashSet<>(Set.of(Permission.READ, Permission.MODIFY));
  // Permspec format for systems is "system:<tenant>:<perm_list>:<system_id>"
  private static final String PERM_SPEC_PREFIX = "system";
  private static final String PERM_SPEC_TEMPLATE = "system:%s:%s:%s";

  private static final String SERVICE_NAME = TapisConstants.SERVICE_NAME_SYSTEMS;
  private static final String FILES_SERVICE = TapisConstants.SERVICE_NAME_FILES;
  private static final String APPS_SERVICE = TapisConstants.SERVICE_NAME_APPS;
  private static final String JOBS_SERVICE = TapisConstants.SERVICE_NAME_JOBS;
  private static final Set<String> SVCLIST_GETCRED = new HashSet<>(Set.of(FILES_SERVICE, JOBS_SERVICE));
  private static final Set<String> SVCLIST_READ = new HashSet<>(Set.of(FILES_SERVICE, APPS_SERVICE, JOBS_SERVICE));

  // Message keys
  private static final String ERROR_ROLLBACK = "SYSLIB_ERROR_ROLLBACK";
  private static final String NOT_FOUND = "SYSLIB_NOT_FOUND";

  // ************************************************************************
  // *********************** Enums ******************************************
  // ************************************************************************

  // ************************************************************************
  // *********************** Fields *****************************************
  // ************************************************************************

  // Use HK2 to inject singletons
  @Inject
  private SystemsDao dao;

  @Inject
  private ServiceClients serviceClients;

  @Inject
  private ServiceContext serviceContext;

  // We must be running on a specific site and this will never change
  // These are initialized in method initService()
  private static String siteId;
  public static String getSiteId() {return siteId;}
  private static String siteAdminTenantId;
  public static String getSiteAdminTenantId() {return siteAdminTenantId;}

  // ************************************************************************
  // *********************** Public Methods *********************************
  // ************************************************************************

  // -----------------------------------------------------------------------
  // ------------------------- Systems -------------------------------------
  // -----------------------------------------------------------------------

  /**
   * Create a new system object given a TSystem and the text used to create the TSystem.
   * Secrets in the text should be masked.
   * @param authenticatedUser - principal user containing tenant and user info
   * @param system - Pre-populated TSystem object
   * @param scrubbedText - Text used to create the TSystem object - secrets should be scrubbed. Saved in update record.
   * @throws TapisException - for Tapis related exceptions
   * @throws IllegalStateException - system exists OR TSystem in invalid state
   * @throws IllegalArgumentException - invalid parameter passed in
   * @throws NotAuthorizedException - unauthorized
   */
  @Override
  public void createSystem(AuthenticatedUser authenticatedUser, TSystem system, String scrubbedText)
          throws TapisException, TapisClientException, IllegalStateException, IllegalArgumentException, NotAuthorizedException
  {
    SystemOperation op = SystemOperation.create;
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    if (system == null) throw new IllegalArgumentException(LibUtils.getMsgAuth("SYSLIB_NULL_INPUT_SYSTEM", authenticatedUser));
    _log.trace(LibUtils.getMsgAuth("SYSLIB_CREATE_TRACE", authenticatedUser, scrubbedText));
    // Extract various names for convenience
    String tenantName = authenticatedUser.getTenantId();
    String apiUserId = authenticatedUser.getName();
    String systemId = system.getId();
    String systemTenantName = authenticatedUser.getTenantId();
    // For service request use oboTenant for tenant associated with the system
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType())) systemTenantName = authenticatedUser.getOboTenantId();

    // ---------------------------- Check inputs ------------------------------------
    // Required system attributes: id, type, host, defaultAuthnMethod
    if (StringUtils.isBlank(tenantName) || StringUtils.isBlank(apiUserId) || StringUtils.isBlank(systemId) ||
        system.getSystemType() == null || StringUtils.isBlank(system.getHost()) ||
        system.getDefaultAuthnMethod() == null || StringUtils.isBlank(apiUserId) || StringUtils.isBlank(scrubbedText))
    {
      throw new IllegalArgumentException(LibUtils.getMsgAuth("SYSLIB_CREATE_ERROR_ARG", authenticatedUser, systemId));
    }

    // Check if system already exists
    if (dao.checkForSystem(systemTenantName, systemId, true))
    {
      throw new IllegalStateException(LibUtils.getMsgAuth("SYSLIB_SYS_EXISTS", authenticatedUser, systemId));
    }

    // Make sure owner, effectiveUserId, notes and tags are all set
    // Note that this is done before auth so owner can get resolved and used during auth check.
    system.setTenant(systemTenantName);
    system = TSystem.setDefaults(system);
    String effectiveUserId = system.getEffectiveUserId();

    // ----------------- Resolve variables for any attributes that might contain them --------------------
    system.resolveVariables(authenticatedUser.getOboUser());

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, system.getId(), system.getOwner(), null, null);

    // ---------------- Check for reserved names ------------------------
    checkReservedIds(authenticatedUser, system.getId());

    // ---------------- Check constraints on TSystem attributes ------------------------
    validateTSystem(authenticatedUser, system);

    // Construct Json string representing the TSystem (without credentials) about to be created
    TSystem scrubbedSystem = new TSystem(system);
    scrubbedSystem.setAuthnCredential(null);
    String createJsonStr = TapisGsonUtils.getGson().toJson(scrubbedSystem);

    // ----------------- Create all artifacts --------------------
    // Creation of system, perms and creds not in single DB transaction.
    // Use try/catch to rollback any writes in case of failure.
    boolean itemCreated = false;
    String systemsPermSpecALL = getPermSpecAllStr(systemTenantName, systemId);
    // TODO remove filesPermSpec related code (jira cic-3071)
    String filesPermSpec = "files:" + systemTenantName + ":*:" + systemId;

    // Get SK client now. If we cannot get this rollback not needed.
    var skClient = getSKClient();
    try {
      // ------------------- Make Dao call to persist the system -----------------------------------
      itemCreated = dao.createSystem(authenticatedUser, system, createJsonStr, scrubbedText);

      // ------------------- Add permissions -----------------------------
      // Give owner and possibly effectiveUser full access to the system
      skClient.grantUserPermission(systemTenantName, system.getOwner(), systemsPermSpecALL);
      if (!effectiveUserId.equals(APIUSERID_VAR) && !effectiveUserId.equals(OWNER_VAR)) {
        skClient.grantUserPermission(systemTenantName, effectiveUserId, systemsPermSpecALL);
      }
      // TODO remove filesPermSpec related code (jira cic-3071)
      // Give owner/effectiveUser files service related permission for root directory
      skClient.grantUserPermission(systemTenantName, system.getOwner(), filesPermSpec);
      if (!effectiveUserId.equals(APIUSERID_VAR) && !effectiveUserId.equals(OWNER_VAR))
        skClient.grantUserPermission(systemTenantName, effectiveUserId, filesPermSpec);

      // ------------------- Store credentials -----------------------------------
      // Store credentials in Security Kernel if cred provided and effectiveUser is static
      if (system.getAuthnCredential() != null && !effectiveUserId.equals(APIUSERID_VAR)) {
        String accessUser = effectiveUserId;
        // If effectiveUser is owner resolve to static string.
        if (effectiveUserId.equals(OWNER_VAR)) accessUser = system.getOwner();
        // Use private internal method instead of public API to skip auth and other checks not needed here.
        // Create credential
        createCredential(skClient, system.getAuthnCredential(), tenantName, apiUserId, systemId, systemTenantName, accessUser);
      }
    }
    catch (Exception e0)
    {
      // Something went wrong. Attempt to undo all changes and then re-throw the exception
      // Log error
      String msg = LibUtils.getMsgAuth("SYSLIB_CREATE_ERROR_ROLLBACK", authenticatedUser, systemId, e0.getMessage());
      _log.error(msg);

      // Rollback
      // Remove system from DB
      if (itemCreated) try {dao.hardDeleteSystem(systemTenantName, systemId); }
      catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "hardDelete", e.getMessage()));}
      // Remove perms
      try { skClient.revokeUserPermission(systemTenantName, system.getOwner(), systemsPermSpecALL); }
      catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "revokePermOwner", e.getMessage()));}
      try { skClient.revokeUserPermission(systemTenantName, effectiveUserId, systemsPermSpecALL); }
      catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "revokePermEffUsr", e.getMessage()));}
      // TODO remove filesPermSpec related code (jira cic-3071)
      try { skClient.revokeUserPermission(systemTenantName, system.getOwner(), filesPermSpec);  }
      catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "revokePermF1", e.getMessage()));}
      try { skClient.revokeUserPermission(systemTenantName, effectiveUserId, filesPermSpec);  }
      catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "revokePermF2", e.getMessage()));}
      // Remove creds
      if (system.getAuthnCredential() != null && !effectiveUserId.equals(APIUSERID_VAR)) {
        String accessUser = effectiveUserId;
        if (effectiveUserId.equals(OWNER_VAR)) accessUser = system.getOwner();
        // Use private internal method instead of public API to skip auth and other checks not needed here.
        try { deleteCredential(skClient, tenantName, apiUserId, systemTenantName, systemId, accessUser); }
        catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "deleteCred", e.getMessage()));}
      }
      throw e0;
    }
  }

  /**
   * Update a system object given a PatchSystem and the text used to create the PatchSystem.
   * Secrets in the text should be masked.
   * Attributes that can be updated:
   *   description, host, effectiveUserId, defaultAuthnMethod, transferMethods,
   *   port, useProxy, proxyHost, proxyPort, dtnSystemId, dtnMountPoint, dtnMountSourcePath,
   *   jobRuntimes, jobWorkingDir, jobEnvVariables, jobMaxJobs, jobMaxJobsPerUers, jobIsBatch,
   *   batchScheduler, batchLogicalQueues, batchDefaultLogicalQueue, jobCapabilities, tags, notes.
   * Attributes that cannot be updated:
   *   tenant, id, systemType, owner, authnCredential, bucketName, rootDir, canExec, isDtn
   * @param authenticatedUser - principal user containing tenant and user info
   * @param patchSystem - Pre-populated PatchSystem object
   * @param scrubbedText - Text used to create the PatchSystem object - secrets should be scrubbed. Saved in update record.
   * @throws TapisException - for Tapis related exceptions
   * @throws IllegalStateException - Resulting TSystem would be in an invalid state
   * @throws IllegalArgumentException - invalid parameter passed in
   * @throws NotAuthorizedException - unauthorized
   * @throws NotFoundException - System not found
   */
  @Override
  public void updateSystem(AuthenticatedUser authenticatedUser, PatchSystem patchSystem, String scrubbedText)
          throws TapisException, TapisClientException, IllegalStateException, IllegalArgumentException, NotAuthorizedException, NotFoundException
  {
    SystemOperation op = SystemOperation.modify;
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    if (patchSystem == null) throw new IllegalArgumentException(LibUtils.getMsgAuth("SYSLIB_NULL_INPUT_SYSTEM", authenticatedUser));
    // Extract various names for convenience
    String tenantName = authenticatedUser.getTenantId();
    String apiUserId = authenticatedUser.getName();
    String systemTenantName = patchSystem.getTenant();
    String systemId = patchSystem.getId();
    // For service request use oboTenant for tenant associated with the system
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType())) systemTenantName = authenticatedUser.getOboTenantId();

    // ---------------------------- Check inputs ------------------------------------
    if (StringUtils.isBlank(tenantName) || StringUtils.isBlank(apiUserId) || StringUtils.isBlank(systemId) || StringUtils.isBlank(scrubbedText))
    {
      throw new IllegalArgumentException(LibUtils.getMsgAuth("SYSLIB_CREATE_ERROR_ARG", authenticatedUser, systemId));
    }

    // System must already exist and not be soft deleted
    if (!dao.checkForSystem(systemTenantName, systemId, false))
    {
      throw new NotFoundException(LibUtils.getMsgAuth(NOT_FOUND, authenticatedUser, systemId));
    }

    // Retrieve the system being patched and create fully populated TSystem with changes merged in
    TSystem origTSystem = dao.getSystem(systemTenantName, systemId);
    TSystem patchedTSystem = createPatchedTSystem(origTSystem, patchSystem);

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, origTSystem.getOwner(), null, null);

    // ---------------- Check constraints on TSystem attributes ------------------------
    patchedTSystem = TSystem.setDefaults(patchedTSystem);
    validateTSystem(authenticatedUser, patchedTSystem);

    // Construct Json string representing the PatchSystem about to be used to update the system
    String updateJsonStr = TapisGsonUtils.getGson().toJson(patchSystem);

    // ----------------- Create all artifacts --------------------
    // No distributed transactions so no distributed rollback needed
    // ------------------- Make Dao call to persist the system -----------------------------------
    dao.updateSystem(authenticatedUser, patchedTSystem, patchSystem, updateJsonStr, scrubbedText);
  }

  /**
   * Update enabled to true for a system
   * @param authenticatedUser - principal user containing tenant and user info
   * @param systemId - name of system
   * @return Number of items updated
   *
   * @throws TapisException - for Tapis related exceptions
   * @throws IllegalStateException - Resulting App would be in an invalid state
   * @throws IllegalArgumentException - invalid parameter passed in
   * @throws NotAuthorizedException - unauthorized
   * @throws NotFoundException - App not found
   */
  @Override
  public int enableSystem(AuthenticatedUser authenticatedUser, String systemId)
          throws TapisException, IllegalStateException, IllegalArgumentException, NotAuthorizedException, NotFoundException, TapisClientException
  {
    return updateEnabled(authenticatedUser, systemId, SystemOperation.enable);
  }

  /**
   * Update enabled to false for a system
   * @param authenticatedUser - principal user containing tenant and user info
   * @param systemId - name of system
   * @return Number of items updated
   *
   * @throws TapisException - for Tapis related exceptions
   * @throws IllegalStateException - Resulting App would be in an invalid state
   * @throws IllegalArgumentException - invalid parameter passed in
   * @throws NotAuthorizedException - unauthorized
   * @throws NotFoundException - App not found
   */
  @Override
  public int disableSystem(AuthenticatedUser authenticatedUser, String systemId)
          throws TapisException, IllegalStateException, IllegalArgumentException, NotAuthorizedException, NotFoundException, TapisClientException
  {
    return updateEnabled(authenticatedUser, systemId, SystemOperation.disable);
  }

  /**
   * Change owner of a system
   * @param authenticatedUser - principal user containing tenant and user info
   * @param systemId - name of system
   * @param newOwnerName - User name of new owner
   * @return Number of items updated
   *
   * @throws TapisException - for Tapis related exceptions
   * @throws IllegalStateException - Resulting TSystem would be in an invalid state
   * @throws IllegalArgumentException - invalid parameter passed in
   * @throws NotAuthorizedException - unauthorized
   * @throws NotFoundException - System not found
   */
  @Override
  public int changeSystemOwner(AuthenticatedUser authenticatedUser, String systemId, String newOwnerName)
          throws TapisException, IllegalStateException, IllegalArgumentException, NotAuthorizedException, NotFoundException, TapisClientException
  {
    SystemOperation op = SystemOperation.changeOwner;
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    if (StringUtils.isBlank(systemId) || StringUtils.isBlank(newOwnerName))
         throw new IllegalArgumentException(LibUtils.getMsgAuth("SYSLIB_NULL_INPUT_SYSTEM", authenticatedUser));
    // Extract various names for convenience
    String tenantName = authenticatedUser.getTenantId();
    String apiUserId = authenticatedUser.getName();
    String systemTenantName = tenantName;
    // For service request use oboTenant for tenant associated with the system
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType())) systemTenantName = authenticatedUser.getOboTenantId();

    // ---------------------------- Check inputs ------------------------------------
    if (StringUtils.isBlank(tenantName) || StringUtils.isBlank(apiUserId))
         throw new IllegalArgumentException(LibUtils.getMsgAuth("SYSLIB_CREATE_ERROR_ARG", authenticatedUser, systemId));

    // System must already exist and not be soft deleted
    if (!dao.checkForSystem(systemTenantName, systemId, false))
         throw new NotFoundException(LibUtils.getMsgAuth(NOT_FOUND, authenticatedUser, systemId));

    // Retrieve old owner
    String oldOwnerName = dao.getSystemOwner(systemTenantName, systemId);

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, oldOwnerName, null, null);

    // If new owner same as old owner then this is a no-op
    if (newOwnerName.equals(oldOwnerName)) return 0;

    // ----------------- Make all updates --------------------
    // Changes not in single DB transaction.
    // Use try/catch to rollback any changes in case of failure.
    // Get SK client now. If we cannot get this rollback not needed.
    var skClient = getSKClient();
    String systemsPermSpec = getPermSpecAllStr(systemTenantName, systemId);
    // TODO remove addition of files related permSpec (jira cic-3071)
    String filesPermSpec = "files:" + systemTenantName + ":*:" + systemId;
    try {
      // ------------------- Make Dao call to update the system owner -----------------------------------
      dao.updateSystemOwner(authenticatedUser, systemId, newOwnerName);
      // Add permissions for new owner
      skClient.grantUserPermission(systemTenantName, newOwnerName, systemsPermSpec);
      // TODO remove addition of files related permSpec (jira cic-3071)
      // Give owner files service related permission for root directory
      skClient.grantUserPermission(systemTenantName, newOwnerName, filesPermSpec);
      // Remove permissions from old owner
      skClient.revokeUserPermission(systemTenantName, oldOwnerName, systemsPermSpec);
      // TODO: Notify files service of the change (jira cic-3071)
    }
    catch (Exception e0)
    {
      // Something went wrong. Attempt to undo all changes and then re-throw the exception
      try { dao.updateSystemOwner(authenticatedUser, systemId, oldOwnerName); } catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "updateOwner", e.getMessage()));}
      // TODO remove filesPermSpec related code (jira cic-3071)
      try { skClient.revokeUserPermission(systemTenantName, newOwnerName, filesPermSpec); }
      catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "revokePermNewOwner", e.getMessage()));}
      try { skClient.revokeUserPermission(systemTenantName, newOwnerName, filesPermSpec); }
      catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "revokePermF1", e.getMessage()));}
      try { skClient.grantUserPermission(systemTenantName, oldOwnerName, systemsPermSpec); }
      catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "grantPermOldOwner", e.getMessage()));}
      try { skClient.grantUserPermission(systemTenantName, oldOwnerName, filesPermSpec); }
      catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "grantPermF1", e.getMessage()));}
      throw e0;
    }
    return 1;
  }

  /**
   * Soft delete a system record given the system name.
   * Also remove artifacts from the Security Kernel
   *
   * @param authenticatedUser - principal user containing tenant and user info
   * @param systemId - name of system
   * @return Number of items deleted
   * @throws TapisException - for Tapis related exceptions
   * @throws NotAuthorizedException - unauthorized
   */
  @Override
  public int softDeleteSystem(AuthenticatedUser authenticatedUser, String systemId)
          throws TapisException, NotAuthorizedException, TapisClientException
  {
    SystemOperation op = SystemOperation.softDelete;
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    if (StringUtils.isBlank(systemId)) throw new IllegalArgumentException(LibUtils.getMsgAuth("SYSLIB_NULL_INPUT_SYSTEM", authenticatedUser));
    // For service request use oboTenant for tenant associated with the system
    String systemTenantName = authenticatedUser.getTenantId();
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType())) systemTenantName = authenticatedUser.getOboTenantId();

    // If system does not exist or has already been soft deleted then 0 changes
    if (!dao.checkForSystem(systemTenantName, systemId, false)) return 0;

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, null, null, null);

    // Remove SK artifacts
    removeSKArtifacts(authenticatedUser, systemId, op);

    // Delete the system
    return dao.softDeleteSystem(authenticatedUser, systemId);
  }

  /**
   * Hard delete a system record given the system name.
   * Also remove artifacts from the Security Kernel
   * NOTE: This is package-private. Only test code should ever use it.
   *
   * @param authenticatedUser - principal user containing tenant and user info
   * @param systemId - name of system
   * @return Number of items deleted
   * @throws TapisException - for Tapis related exceptions
   * @throws TapisClientException - for Tapis related exceptions
   * @throws NotAuthorizedException - unauthorized
   */
  int hardDeleteSystem(AuthenticatedUser authenticatedUser, String systemId)
          throws TapisException, TapisClientException, NotAuthorizedException
  {
    SystemOperation op = SystemOperation.hardDelete;
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    if (StringUtils.isBlank(systemId)) throw new IllegalArgumentException(LibUtils.getMsgAuth("SYSLIB_NULL_INPUT_SYSTEM", authenticatedUser));
    // Extract various names for convenience
    String systemTenantName = authenticatedUser.getTenantId();
    // For service request use oboTenant for tenant associated with the system
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType())) systemTenantName = authenticatedUser.getOboTenantId();

    // If system does not exist then 0 changes
    if (!dao.checkForSystem(systemTenantName, systemId, true)) return 0;

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, null, null, null);

    // Remove SK artifacts
    removeSKArtifacts(authenticatedUser, systemId, op);

    // Delete the system
    return dao.hardDeleteSystem(systemTenantName, systemId);
  }

  /**
   * Initialize the service:
   *   init service context
   *   migrate DB
   */
  public void initService(String siteId1, String siteAdminTenantId1, String svcPassword) throws TapisException, TapisClientException
  {
    // Initialize service context and site info
    siteId = siteId1;
    siteAdminTenantId = siteAdminTenantId1;
    serviceContext.initServiceJWT(siteId, SYSTEMS_SERVICE, svcPassword);
    // Make sure DB is present and updated to latest version using flyway
    dao.migrateDB();
  }

  /**
   * Check that we can connect with DB and that the main table of the service exists.
   * @return null if all OK else return an Exception
   */
  public Exception checkDB()
  {
    return dao.checkDB();
  }

  /**
   * checkForSystem
   * @param authenticatedUser - principal user containing tenant and user info
   * @param systemId - Name of the system
   * @return true if system exists and has not been soft deleted, false otherwise
   * @throws TapisException - for Tapis related exceptions
   * @throws NotAuthorizedException - unauthorized
   */
  @Override
  public boolean checkForSystem(AuthenticatedUser authenticatedUser, String systemId) throws TapisException, NotAuthorizedException, TapisClientException
  {
    SystemOperation op = SystemOperation.read;
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    if (StringUtils.isBlank(systemId)) throw new IllegalArgumentException(LibUtils.getMsgAuth("SYSLIB_NULL_INPUT_SYSTEM", authenticatedUser));
    String systemTenantName = authenticatedUser.getTenantId();
    // For service request use oboTenant for tenant associated with the system
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType())) systemTenantName = authenticatedUser.getOboTenantId();

    // We need owner to check auth and if system not there cannot find owner, so cannot do auth check if no system
    if (dao.checkForSystem(systemTenantName, systemId, false)) {
      // ------------------------- Check service level authorization -------------------------
      checkAuth(authenticatedUser, op, systemId, null, null, null);
      return true;
    }
    return false;
  }

  /**
   * getSystem
   * @param authenticatedUser - principal user containing tenant and user info
   * @param systemId - Name of the system
   * @param getCreds - flag indicating if credentials for effectiveUserId should be included
   * @param accMethod - (optional) return credentials for specified authn method instead of default authn method
   * @param requireExecPerm - check for EXECUTE permission as well as READ permission
   * @return populated instance of a TSystem or null if not found or user not authorized.
   * @throws TapisException - for Tapis related exceptions
   * @throws NotAuthorizedException - unauthorized
   */
  @Override
  public TSystem getSystem(AuthenticatedUser authenticatedUser, String systemId, boolean getCreds,
                           AuthnMethod accMethod, boolean requireExecPerm)
          throws TapisException, NotAuthorizedException, TapisClientException
  {
    SystemOperation op = SystemOperation.read;
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    if (StringUtils.isBlank(systemId)) throw new IllegalArgumentException(LibUtils.getMsgAuth("SYSLIB_NULL_INPUT_SYSTEM", authenticatedUser));
    // Extract various names for convenience
    String apiUserId = authenticatedUser.getName();
    String systemTenantName = authenticatedUser.getTenantId();
    // For service request use oboTenant for tenant associated with the system and oboUser as apiUserId
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType()))
    {
      systemTenantName = authenticatedUser.getOboTenantId();
      apiUserId = authenticatedUser.getOboUser();
    }

    // We need owner to check auth and if system not there cannot find owner, so
    // if system does not exist then return null
    if (!dao.checkForSystem(systemTenantName, systemId, false)) return null;

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, null, null, null);
    // If flag is set to also require EXECUTE perm then make a special auth call
    if (requireExecPerm)
    {
      checkAuthUser(authenticatedUser, SystemOperation.execute, systemTenantName, authenticatedUser.getOboUser(),
                    systemId, null, null, null);
    }

    TSystem result = dao.getSystem(systemTenantName, systemId);
    if (result == null) return null;

    // If flag is set to also require EXECUTE perm then system must support execute
    if (requireExecPerm && !result.getCanExec())
    {
      String msg = LibUtils.getMsgAuth("SYSLIB_NOTEXEC", authenticatedUser, systemId, op.name());
      throw new NotAuthorizedException(msg);
    }

    // Resolve effectiveUserId
    String resolvedEffectiveUserId = resolveEffectiveUserId(result.getEffectiveUserId(), result.getOwner(), apiUserId);
    result.setEffectiveUserId(resolvedEffectiveUserId);
    // If requested retrieve credentials from Security Kernel
    if (getCreds)
    {
      AuthnMethod tmpAccMethod = result.getDefaultAuthnMethod();
      // If authnMethod specified then use it instead of default authn method defined for the system.
      if (accMethod != null) tmpAccMethod = accMethod;
      Credential cred = getUserCredential(authenticatedUser, systemId, resolvedEffectiveUserId, tmpAccMethod);
      result.setAuthnCredential(cred);
    }
    return result;
  }

  /**
   * Get count of all systems matching certain criteria and for which user has READ permission
   * @param authenticatedUser - principal user containing tenant and user info
   * @param searchList - optional list of conditions used for searching
   * @param orderByList - orderBy entries for sorting, e.g. orderBy=created(desc).
   * @param startAfter - where to start when sorting, e.g. orderBy=id(asc)&startAfter=101 (may not be used with skip)
   * @return Count of TSystem objects
   * @throws TapisException - for Tapis related exceptions
   */
  @Override
  public int getSystemsTotalCount(AuthenticatedUser authenticatedUser, List<String> searchList,
                                  List<OrderBy> orderByList, String startAfter)
          throws TapisException, TapisClientException
  {
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    // Determine tenant scope for user
    String systemTenantName = authenticatedUser.getTenantId();
    // For service request use oboTenant for tenant associated with the user
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType()))
      systemTenantName = authenticatedUser.getOboTenantId();

    // Build verified list of search conditions
    var verifiedSearchList = new ArrayList<String>();
    if (searchList != null && !searchList.isEmpty())
    {
      try
      {
        for (String cond : searchList)
        {
          // Use SearchUtils to validate condition
          String verifiedCondStr = SearchUtils.validateAndProcessSearchCondition(cond);
          verifiedSearchList.add(verifiedCondStr);
        }
      }
      catch (Exception e)
      {
        String msg = LibUtils.getMsgAuth("SYSLIB_SEARCH_ERROR", authenticatedUser, e.getMessage());
        _log.error(msg, e);
        throw new IllegalArgumentException(msg);
      }
    }

    // Get list of IDs of systems for which requester has view permission.
    // This is either all systems (null) or a list of IDs.
    Set<String> allowedSysIDs = getAllowedSysIDs(authenticatedUser, systemTenantName);

    // If none are allowed we know count is 0
    if (allowedSysIDs != null && allowedSysIDs.isEmpty()) return 0;

    // Count all allowed systems matching the search conditions
    return dao.getSystemsCount(authenticatedUser.getTenantId(), verifiedSearchList, null, allowedSysIDs,
                                orderByList, startAfter);
  }

  /**
   * Get all systems matching certain criteria and for which user has READ permission
   * @param authenticatedUser - principal user containing tenant and user info
   * @param searchList - optional list of conditions used for searching
   * @param limit - indicates maximum number of results to be included, -1 for unlimited
   * @param orderByList - orderBy entries for sorting, e.g. orderBy=created(desc).
   * @param skip - number of results to skip (may not be used with startAfter)
   * @param startAfter - where to start when sorting, e.g. limit=10&orderBy=id(asc)&startAfter=101 (may not be used with skip)
   * @return List of TSystem objects
   * @throws TapisException - for Tapis related exceptions
   */
  @Override
  public List<TSystem> getSystems(AuthenticatedUser authenticatedUser, List<String> searchList, int limit,
                                  List<OrderBy> orderByList, int skip, String startAfter)
          throws TapisException, TapisClientException
  {
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    // Determine tenant scope for user
    String systemTenantName = authenticatedUser.getTenantId();
    // For service request use oboTenant for tenant associated with the user
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType()))
      systemTenantName = authenticatedUser.getOboTenantId();

    // Build verified list of search conditions
    var verifiedSearchList = new ArrayList<String>();
    if (searchList != null && !searchList.isEmpty())
    {
      try
      {
        for (String cond : searchList)
        {
          // Use SearchUtils to validate condition
          String verifiedCondStr = SearchUtils.validateAndProcessSearchCondition(cond);
          verifiedSearchList.add(verifiedCondStr);
        }
      }
      catch (Exception e)
      {
        String msg = LibUtils.getMsgAuth("SYSLIB_SEARCH_ERROR", authenticatedUser, e.getMessage());
        _log.error(msg, e);
        throw new IllegalArgumentException(msg);
      }
    }

    // Get list of IDs of systems for which requester has READ permission.
    // This is either all systems (null) or a list of IDs.
    Set<String> allowedSysIDs = getAllowedSysIDs(authenticatedUser, systemTenantName);

    // Get all allowed systems matching the search conditions
    List<TSystem> systems = dao.getSystems(systemTenantName, verifiedSearchList, null, allowedSysIDs,
                                            limit, orderByList, skip, startAfter);

    for (TSystem system : systems)
    {
      system.setEffectiveUserId(resolveEffectiveUserId(system.getEffectiveUserId(), system.getOwner(),
                 authenticatedUser.getName()));
    }
    return systems;
  }

  /**
   * Get all systems for which user has READ permission.
   * Use provided string containing a valid SQL where clause for the search.
   * @param authenticatedUser - principal user containing tenant and user info
   * @param sqlSearchStr - string containing a valid SQL where clause
   * @param limit - indicates maximum number of results to be included, -1 for unlimited
   * @param orderByList - orderBy entries for sorting, e.g. orderBy=created(desc).
   * @param skip - number of results to skip (may not be used with startAfter)
   * @param startAfter - where to start when sorting, e.g. limit=10&orderBy=id(asc)&startAfter=101 (may not be used with skip)
   * @return List of TSystem objects
   * @throws TapisException - for Tapis related exceptions
   */
  @Override
  public List<TSystem> getSystemsUsingSqlSearchStr(AuthenticatedUser authenticatedUser, String sqlSearchStr, int limit,
                                                   List<OrderBy> orderByList, int skip, String startAfter)
          throws TapisException, TapisClientException
  {
    // If search string is empty delegate to getSystems()
    if (StringUtils.isBlank(sqlSearchStr)) return getSystems(authenticatedUser, null, limit, orderByList, skip, startAfter);

    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    // Determine tenant scope for user
    String systemTenantName = authenticatedUser.getTenantId();
    // For service request use oboTenant for tenant associated with the user
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType()))
      systemTenantName = authenticatedUser.getOboTenantId();

    // Validate and parse the sql string into an abstract syntax tree (AST)
    // NOTE: The activemq parser validates and parses the string into an AST but there does not appear to be a way
    //          to use the resulting BooleanExpression to walk the tree. How to now create a usable AST?
    //   I believe we don't want to simply try to run the where clause for various reasons:
    //      - SQL injection
    //      - we want to verify the validity of each <attr>.<op>.<value>
    //        looks like activemq parser will ensure the leaf nodes all represent <attr>.<op>.<value> and in principle
    //        we should be able to check each one and generate of list of errors for reporting.
    //  Looks like jOOQ can parse an SQL string into a jooq Condition. Do this in the Dao? But still seems like no way
    //    to walk the AST and check each condition so we can report on errors.
    ASTNode searchAST;
    try { searchAST = ASTParser.parse(sqlSearchStr); }
    catch (Exception e)
    {
      String msg = LibUtils.getMsgAuth("SYSLIB_SEARCH_ERROR", authenticatedUser, e.getMessage());
      _log.error(msg, e);
      throw new IllegalArgumentException(msg);
    }

    // Get list of IDs of systems for which requester has READ permission.
    // This is either all systems (null) or a list of IDs.
    Set<String> allowedSysIDs = getAllowedSysIDs(authenticatedUser, systemTenantName);

    // Get all allowed systems matching the search conditions
    List<TSystem> systems = dao.getSystems(authenticatedUser.getTenantId(), null, searchAST, allowedSysIDs,
                                                          limit, orderByList, skip, startAfter);

    for (TSystem system : systems)
    {
      system.setEffectiveUserId(resolveEffectiveUserId(system.getEffectiveUserId(), system.getOwner(),
              authenticatedUser.getName()));
    }
    return systems;
  }

  /**
   * Get all systems for which user has READ permission and matching specified constraint conditions.
   * Use provided string containing a valid SQL where clause for the search.
   * @param authenticatedUser - principal user containing tenant and user info
   * @param matchStr - string containing a valid SQL where clause
   * @return List of TSystem objects
   * @throws TapisException - for Tapis related exceptions
   */
  @Override
  public List<TSystem> getSystemsSatisfyingConstraints(AuthenticatedUser authenticatedUser, String matchStr)
          throws TapisException, TapisClientException
  {
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    // Determine tenant scope for user
    String systemTenantName = authenticatedUser.getTenantId();
    // For service request use oboTenant for tenant associated with the user
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType()))
      systemTenantName = authenticatedUser.getOboTenantId();

    // Get list of IDs of systems for which requester has READ permission.
    // This is either all systems (null) or a list of IDs.
    Set<String> allowedSysIDs = getAllowedSysIDs(authenticatedUser, systemTenantName);

    // Validate and parse the sql string into an abstract syntax tree (AST)
    ASTNode matchAST;
    try { matchAST = ASTParser.parse(matchStr); }
    catch (Exception e)
    {
      String msg = LibUtils.getMsgAuth("SYSLIB_MATCH_ERROR", authenticatedUser, e.getMessage());
      _log.error(msg, e);
      throw new IllegalArgumentException(msg);
    }

    // Get all allowed systems matching the constraint conditions
    List<TSystem> systems = dao.getSystemsSatisfyingConstraints(authenticatedUser.getTenantId(), matchAST,
                                                                 allowedSysIDs);

    for (TSystem system : systems)
    {
      system.setEffectiveUserId(resolveEffectiveUserId(system.getEffectiveUserId(), system.getOwner(),
              authenticatedUser.getName()));
    }
    return systems;
  }

  /**
   * getSystemBasic
   * @param authenticatedUser - principal user containing tenant and user info
   * @param systemId - Name of the system
   * @return SystemBasic - populated instance or null if not found or user not authorized.
   * @throws TapisException - for Tapis related exceptions
   * @throws NotAuthorizedException - unauthorized
   */
  @Override
  public SystemBasic getSystemBasic(AuthenticatedUser authenticatedUser, String systemId)
          throws TapisException, NotAuthorizedException, TapisClientException
  {
    SystemOperation op = SystemOperation.read;
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    if (StringUtils.isBlank(systemId)) throw new IllegalArgumentException(LibUtils.getMsgAuth("SYSLIB_NULL_INPUT_SYSTEM", authenticatedUser));
    // Extract various names for convenience
    String systemTenantName = authenticatedUser.getTenantId();
    // For service request use oboTenant for tenant associated with the system
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType())) systemTenantName = authenticatedUser.getOboTenantId();

    // We need owner to check auth and if system not there cannot find owner, so
    // if system does not exist then return null
    if (!dao.checkForSystem(systemTenantName, systemId, false)) return null;

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, null, null, null);

    // Retrieve the system
    return dao.getSystemBasic(systemTenantName, systemId);
  }

  /**
   * Get all systems matching certain criteria and for which user has READ permission
   * @param authenticatedUser - principal user containing tenant and user info
   * @param searchList - optional list of conditions used for searching
   * @param limit - indicates maximum number of results to be included, -1 for unlimited
   * @param orderByList - orderBy entries for sorting, e.g. orderBy=created(desc).
   * @param skip - number of results to skip (may not be used with startAfter)
   * @param startAfter - where to start when sorting, e.g. limit=10&orderBy=id(asc)&startAfter=101 (may not be used with skip)
   * @return List of TSystem objects
   * @throws TapisException - for Tapis related exceptions
   */
  @Override
  public List<SystemBasic> getSystemsBasic(AuthenticatedUser authenticatedUser, List<String> searchList, int limit,
                                           List<OrderBy> orderByList, int skip, String startAfter)
          throws TapisException, TapisClientException
  {
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    // Determine tenant scope for user
    String systemTenantName = authenticatedUser.getTenantId();
    // For service request use oboTenant for tenant associated with the user
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType()))
      systemTenantName = authenticatedUser.getOboTenantId();

    // Build verified list of search conditions
    var verifiedSearchList = new ArrayList<String>();
    if (searchList != null && !searchList.isEmpty())
    {
      try
      {
        for (String cond : searchList)
        {
          // Use SearchUtils to validate condition
          String verifiedCondStr = SearchUtils.validateAndProcessSearchCondition(cond);
          verifiedSearchList.add(verifiedCondStr);
        }
      }
      catch (Exception e)
      {
        String msg = LibUtils.getMsgAuth("SYSLIB_SEARCH_ERROR", authenticatedUser, e.getMessage());
        _log.error(msg, e);
        throw new IllegalArgumentException(msg);
      }
    }

    // Get list of IDs of systems for which requester has READ permission.
    // This is either all systems (null) or a list of IDs.
    Set<String> allowedSysIDs = getAllowedSysIDs(authenticatedUser, systemTenantName);

    // Get all allowed systems matching the search conditions
    return dao.getSystemsBasic(systemTenantName, verifiedSearchList, null, allowedSysIDs,
                               limit, orderByList, skip, startAfter);
  }

  /**
   * Get all systems for which user has READ permission.
   * Use provided string containing a valid SQL where clause for the search.
   * @param authenticatedUser - principal user containing tenant and user info
   * @param sqlSearchStr - string containing a valid SQL where clause
   * @param limit - indicates maximum number of results to be included, -1 for unlimited
   * @param orderByList - orderBy entries for sorting, e.g. orderBy=created(desc).
   * @param skip - number of results to skip (may not be used with startAfter)
   * @param startAfter - where to start when sorting, e.g. limit=10&orderBy=id(asc)&startAfter=101 (may not be used with skip)
   * @return List of TSystem objects
   * @throws TapisException - for Tapis related exceptions
   */
  @Override
  public List<SystemBasic> getSystemsBasicUsingSqlSearchStr(AuthenticatedUser authenticatedUser, String sqlSearchStr, int limit,
                                                            List<OrderBy> orderByList, int skip, String startAfter)
          throws TapisException, TapisClientException
  {
    // If search string is empty delegate to getSystemsBasic()
    if (StringUtils.isBlank(sqlSearchStr)) return getSystemsBasic(authenticatedUser, null, limit, orderByList, skip, startAfter);

    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    // Determine tenant scope for user
    String systemTenantName = authenticatedUser.getTenantId();
    // For service request use oboTenant for tenant associated with the user
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType()))
      systemTenantName = authenticatedUser.getOboTenantId();

    ASTNode searchAST;
    try { searchAST = ASTParser.parse(sqlSearchStr); }
    catch (Exception e)
    {
      String msg = LibUtils.getMsgAuth("SYSLIB_SEARCH_ERROR", authenticatedUser, e.getMessage());
      _log.error(msg, e);
      throw new IllegalArgumentException(msg);
    }

    // Get list of IDs of systems for which requester has READ permission.
    // This is either all systems (null) or a list of IDs.
    Set<String> allowedSysIDs = getAllowedSysIDs(authenticatedUser, systemTenantName);

    // Get all allowed systems matching the search conditions
    return dao.getSystemsBasic(systemTenantName, null, searchAST, allowedSysIDs,
                                                    limit, orderByList, skip, startAfter);
  }

  /**
   * Get list of system names
   * @param authenticatedUser - principal user containing tenant and user info
   * @return - list of systems
   * @throws TapisException - for Tapis related exceptions
   */
  @Override
  public Set<String> getSystemIDs(AuthenticatedUser authenticatedUser) throws TapisException
  {
    SystemOperation op = SystemOperation.read;
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    // Get all system names
    Set<String> systemIds = dao.getSystemNames(authenticatedUser.getTenantId());
    var allowedNames = new HashSet<String>();
    // Filter based on user authorization
    for (String name: systemIds)
    {
      try {
        checkAuth(authenticatedUser, op, name, null, null, null);
        allowedNames.add(name);
      }
      catch (NotAuthorizedException | TapisClientException e) { }
    }
    return allowedNames;
  }

  /**
   * Get system owner
   * @param authenticatedUser - principal user containing tenant and user info
   * @param systemId - Name of the system
   * @return - Owner or null if system not found or user not authorized
   * @throws TapisException - for Tapis related exceptions
   * @throws NotAuthorizedException - unauthorized
   */
  @Override
  public String getSystemOwner(AuthenticatedUser authenticatedUser, String systemId) throws TapisException, NotAuthorizedException, TapisClientException
  {
    SystemOperation op = SystemOperation.read;
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    if (StringUtils.isBlank(systemId)) throw new IllegalArgumentException(LibUtils.getMsgAuth("SYSLIB_NULL_INPUT_SYSTEM", authenticatedUser));

    String systemTenantName = authenticatedUser.getTenantId();
    // For service request use oboTenant for tenant associated with the system
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType())) systemTenantName = authenticatedUser.getOboTenantId();

    // We need owner to check auth and if system not there cannot find owner, so
    // if system does not exist then return null
    if (!dao.checkForSystem(systemTenantName, systemId, false)) return null;

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, null, null, null);

    return dao.getSystemOwner(authenticatedUser.getTenantId(), systemId);
  }

  // -----------------------------------------------------------------------
  // --------------------------- Permissions -------------------------------
  // -----------------------------------------------------------------------

  /**
   * Grant permissions to a user for a system.
   * Grant of MODIFY implies grant of READ
   * NOTE: Permissions only impact the default user role
   * @param authenticatedUser - principal user containing tenant and user info
   * @param systemId - name of system
   * @param userName - Target user for operation
   * @param permissions - list of permissions to be granted
   * @param updateText - Client provided text used to create the permissions list. Saved in update record.
   * @throws TapisException - for Tapis related exceptions
   * @throws NotAuthorizedException - unauthorized
   */
  @Override
  public void grantUserPermissions(AuthenticatedUser authenticatedUser, String systemId, String userName,
                                   Set<Permission> permissions, String updateText)
          throws TapisException, NotAuthorizedException, TapisClientException
  {
    SystemOperation op = SystemOperation.grantPerms;
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    if (StringUtils.isBlank(systemId) || StringUtils.isBlank(userName))
         throw new IllegalArgumentException(LibUtils.getMsgAuth("SYSLIB_NULL_INPUT_SYSTEM", authenticatedUser));
    String systemTenantName = authenticatedUser.getTenantId();
    // For service request use oboTenant for tenant associated with the system
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType()))
    {
      systemTenantName = authenticatedUser.getOboTenantId();
    }

    // If system does not exist or has been soft deleted then throw an exception
    if (!dao.checkForSystem(systemTenantName, systemId, false))
      throw new TapisException(LibUtils.getMsgAuth(NOT_FOUND, authenticatedUser, systemId));

    // Check to see if owner is trying to update permissions for themselves.
    // If so throw an exception because this would be confusing since owner always has full permissions.
    // For an owner permissions are never checked directly.
    String owner = checkForOwnerPermUpdate(authenticatedUser, systemTenantName, systemId, userName, op.name());

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, owner, null, null);

    // Check inputs. If anything null or empty throw an exception
    if (permissions == null || permissions.isEmpty())
    {
      throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT"));
    }

    // Grant of MODIFY implies grant of READ
    if (permissions.contains(Permission.MODIFY)) permissions.add(Permission.READ);

    // Create a set of individual permSpec entries based on the list passed in
    Set<String> permSpecSet = getPermSpecSet(systemTenantName, systemId, permissions);

    // Get the Security Kernel client
    var skClient = getSKClient();

    // Assign perms to user.
    // Start of updates. Will need to rollback on failure.
    try
    {
      // Assign perms to user. SK creates a default role for the user
      for (String permSpec : permSpecSet)
      {
        skClient.grantUserPermission(systemTenantName, userName, permSpec);
      }
    }
    catch (TapisClientException tce)
    {
      // Rollback
      // Something went wrong. Attempt to undo all changes and then re-throw the exception
      String msg = LibUtils.getMsgAuth("SYSLIB_PERM_ERROR_ROLLBACK", authenticatedUser, systemId, tce.getMessage());
      _log.error(msg);

      // Revoke permissions that may have been granted.
      for (String permSpec : permSpecSet)
      {
        try { skClient.revokeUserPermission(systemTenantName, userName, permSpec); }
        catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "revokePerm", e.getMessage()));}
      }

      // Convert to TapisException and re-throw
      throw new TapisException(LibUtils.getMsgAuth("SYSLIB_PERM_SK_ERROR", authenticatedUser, systemId, op.name()), tce);
    }

    // Construct Json string representing the update
    String updateJsonStr = TapisGsonUtils.getGson().toJson(permissions);
    // Create a record of the update
    dao.addUpdateRecord(authenticatedUser, systemTenantName, systemId, op, updateJsonStr, updateText);
  }

  /**
   * Revoke permissions from a user for a system
   * Revoke of READ implies revoke of MODIFY
   * NOTE: Permissions only impact the default user role
   * @param authenticatedUser - principal user containing tenant and user info
   * @param systemId - name of system
   * @param userName - Target user for operation
   * @param permissions - list of permissions to be revoked
   * @param updateText - Client provided text used to create the permissions list. Saved in update record.
   * @return Number of items revoked
   * @throws TapisException - for Tapis related exceptions
   * @throws NotAuthorizedException - unauthorized
   */
  @Override
  public int revokeUserPermissions(AuthenticatedUser authenticatedUser, String systemId, String userName,
                                   Set<Permission> permissions, String updateText)
          throws TapisException, NotAuthorizedException, TapisClientException
  {
    SystemOperation op = SystemOperation.revokePerms;
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    if (StringUtils.isBlank(systemId) || StringUtils.isBlank(userName))
         throw new IllegalArgumentException(LibUtils.getMsgAuth("SYSLIB_NULL_INPUT_SYSTEM", authenticatedUser));
    String systemTenantName = authenticatedUser.getTenantId();
    // For service request use oboTenant for tenant associated with the system
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType())) systemTenantName = authenticatedUser.getOboTenantId();

    // We need owner to check auth and if system not there cannot find owner, so
    // if system does not exist or has been soft deleted then return 0 changes
    if (!dao.checkForSystem(systemTenantName, systemId, false)) return 0;

    // Check to see if owner is trying to update permissions for themselves.
    // If so throw an exception because this would be confusing since owner always has full permissions.
    // For an owner permissions are never checked directly.
    String owner = checkForOwnerPermUpdate(authenticatedUser, systemTenantName, systemId, userName, op.name());

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, owner, null, null);

    // Check inputs. If anything null or empty throw an exception
    if (permissions == null || permissions.isEmpty())
    {
      throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT"));
    }

    // Revoke of READ implies revoke of MODIFY
    if (permissions.contains(Permission.READ)) permissions.add(Permission.MODIFY);

    var skClient = getSKClient();
    int changeCount;
    // Determine current set of user permissions
    var userPermSet = getUserPermSet(skClient, userName, systemTenantName, systemId);

    try
    {
      // Revoke perms
      changeCount = revokePermissions(skClient, systemTenantName, systemId, userName, permissions);
    }
    catch (TapisClientException tce)
    {
      // Rollback
      // Something went wrong. Attempt to undo all changes and then re-throw the exception
      String msg = LibUtils.getMsgAuth("SYSLIB_PERM_ERROR_ROLLBACK", authenticatedUser, systemId, tce.getMessage());
      _log.error(msg);

      // Grant permissions that may have been revoked and that the user previously held.
      for (Permission perm : permissions)
      {
        if (userPermSet.contains(perm))
        {
          String permSpec = getPermSpecStr(systemTenantName, systemId, perm);
          try { skClient.grantUserPermission(systemTenantName, userName, permSpec); }
          catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "grantPerm", e.getMessage()));}
        }
      }

      // Convert to TapisException and re-throw
      throw new TapisException(LibUtils.getMsgAuth("SYSLIB_PERM_SK_ERROR", authenticatedUser, systemId, op.name()), tce);
    }

    // Construct Json string representing the update
    String updateJsonStr = TapisGsonUtils.getGson().toJson(permissions);
    // Create a record of the update
    dao.addUpdateRecord(authenticatedUser, systemTenantName, systemId, op, updateJsonStr, updateText);
    return changeCount;
  }

  /**
   * Get list of system permissions for a user
   * NOTE: This retrieves permissions from all roles.
   * @param authenticatedUser - principal user containing tenant and user info
   * @param systemId - name of system
   * @param userName - Target user for operation
   * @return List of permissions
   * @throws TapisException - for Tapis related exceptions
   * @throws NotAuthorizedException - unauthorized
   */
  @Override
  public Set<Permission> getUserPermissions(AuthenticatedUser authenticatedUser, String systemId, String userName)
          throws TapisException, NotAuthorizedException, TapisClientException
  {
    SystemOperation op = SystemOperation.getPerms;
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    if (StringUtils.isBlank(systemId) || StringUtils.isBlank(userName))
         throw new IllegalArgumentException(LibUtils.getMsgAuth("SYSLIB_NULL_INPUT_SYSTEM", authenticatedUser));
    String systemTenantName = authenticatedUser.getTenantId();
    // For service request use oboTenant for tenant associated with the system
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType()))
         systemTenantName = authenticatedUser.getOboTenantId();

    // If system does not exist or has been soft deleted then return null
    if (!dao.checkForSystem(systemTenantName, systemId, false)) return null;

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, null, userName, null);

    // Use Security Kernel client to check for each permission in the enum list
    var skClient = getSKClient();
    return getUserPermSet(skClient, userName, systemTenantName, systemId);
  }

  // -----------------------------------------------------------------------
  // ---------------------------- Credentials ------------------------------
  // -----------------------------------------------------------------------

  /**
   * Store or update credential for given system and user.
   * @param authenticatedUser - principal user containing tenant and user info
   * @param systemId - name of system
   * @param userName - Target user for operation
   * @param credential - list of permissions to be granted
   * @param updateText - Client provided text used to create the credential - secrets should be scrubbed. Saved in update record.
   * @throws TapisException - for Tapis related exceptions
   * @throws NotAuthorizedException - unauthorized
   */
  @Override
  public void createUserCredential(AuthenticatedUser authenticatedUser, String systemId, String userName,
                                   Credential credential, String updateText)
          throws TapisException, NotAuthorizedException, IllegalStateException, TapisClientException
  {
    SystemOperation op = SystemOperation.setCred;
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    if (StringUtils.isBlank(systemId) || StringUtils.isBlank(userName))
         throw new IllegalArgumentException(LibUtils.getMsgAuth("SYSLIB_NULL_INPUT_SYSTEM", authenticatedUser));
    String systemTenantName = authenticatedUser.getTenantId();
    // For service request use oboTenant for tenant associated with the system
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType())) systemTenantName = authenticatedUser.getOboTenantId();

    // If system does not exist or has been soft deleted then throw an exception
    if (!dao.checkForSystem(systemTenantName, systemId, false))
      throw new TapisException(LibUtils.getMsgAuth(NOT_FOUND, authenticatedUser, systemId));

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, null, userName, null);

    // Extract various names for convenience
    String tenantName = authenticatedUser.getTenantId();
    String apiUserId = authenticatedUser.getName();

    // Check inputs. If anything null or empty throw an exception
    if (StringUtils.isBlank(tenantName) || credential == null)
    {
      throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT"));
    }

    // If private SSH key is set check that we have a compatible key.
    if (!StringUtils.isBlank(credential.getPrivateKey()) && !credential.isValidPrivateSshKey())
    {
      String msg = LibUtils.getMsgAuth("SYSLIB_CRED_INVALID_PRIVATE_SSHKEY2", authenticatedUser, systemId, userName);
      throw new IllegalArgumentException(msg);
    }

    // Get the Security Kernel client
    var skClient = getSKClient();

    // Create credential
    // If this throws an exception we do not try to rollback. Attempting to track which secrets
    //   have been changed and reverting seems fraught with peril and not a good ROI.
    try
    {
      createCredential(skClient, credential, tenantName, apiUserId, systemId, systemTenantName, userName);
    }
    // If tapis client exception then log error and convert to TapisException
    catch (TapisClientException tce)
    {
      _log.error(tce.toString());
      throw new TapisException(LibUtils.getMsgAuth("SYSLIB_CRED_SK_ERROR", authenticatedUser, systemId, op.name()), tce);
    }

    // Construct Json string representing the update, with actual secrets masked out
    Credential maskedCredential = Credential.createMaskedCredential(credential);
    String updateJsonStr = TapisGsonUtils.getGson().toJson(maskedCredential);

    // Create a record of the update
    dao.addUpdateRecord(authenticatedUser, systemTenantName, systemId, op, updateJsonStr, updateText);
  }

  /**
   * Delete credential for given system and user
   * @param authenticatedUser - principal user containing tenant and user info
   * @param systemId - name of system
   * @param userName - Target user for operation
   * @throws TapisException - for Tapis related exceptions
   * @throws NotAuthorizedException - unauthorized
   */
  @Override
  public int deleteUserCredential(AuthenticatedUser authenticatedUser, String systemId, String userName)
          throws TapisException, NotAuthorizedException, TapisClientException
  {
    SystemOperation op = SystemOperation.removeCred;
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    if (StringUtils.isBlank(systemId) || StringUtils.isBlank(userName))
         throw new IllegalArgumentException(LibUtils.getMsgAuth("SYSLIB_NULL_INPUT_SYSTEM", authenticatedUser));
    String systemTenantName = authenticatedUser.getTenantId();
    // For service request use oboTenant for tenant associated with the system
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType())) systemTenantName = authenticatedUser.getOboTenantId();

    int changeCount = 0;
    // If system does not exist or has been soft deleted then return 0 changes
    if (!dao.checkForSystem(systemTenantName, systemId, false)) return changeCount;

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, null, userName, null);

    // Extract various names for convenience
    String tenantName = authenticatedUser.getTenantId();
    String apiUserId = authenticatedUser.getName();

    // Check inputs. If anything null or empty throw an exception
    if (StringUtils.isBlank(tenantName))
    {
      throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT"));
    }
    // Get the Security Kernel client
    var skClient = getSKClient();

    // Delete credential
    // If this throws an exception we do not try to rollback. Attempting to track which secrets
    //   have been changed and reverting seems fraught with peril and not a good ROI.
    try {
      changeCount = deleteCredential(skClient, tenantName, apiUserId, systemTenantName, systemId, userName);
    }
    // If tapis client exception then log error and convert to TapisException
    catch (TapisClientException tce)
    {
      _log.error(tce.toString());
      throw new TapisException(LibUtils.getMsgAuth("SYSLIB_CRED_SK_ERROR", authenticatedUser, systemId, op.name()), tce);
    }

    // Construct Json string representing the update
    String updateJsonStr = TapisGsonUtils.getGson().toJson(userName);
    // Create a record of the update
    dao.addUpdateRecord(authenticatedUser, systemTenantName, systemId, op, updateJsonStr, null);
    return changeCount;
  }

  /**
   * Get credential for given system, user and authn method
   * @param authenticatedUser - principal user containing tenant and user info
   * @param systemId - name of system
   * @param userName - Target user for operation
   * @param authnMethod - (optional) return credentials for specified authn method instead of default authn method
   * @return Credential - populated instance or null if not found.
   * @throws TapisException - for Tapis related exceptions
   * @throws NotAuthorizedException - unauthorized
   */
  @Override
  public Credential getUserCredential(AuthenticatedUser authenticatedUser, String systemId, String userName, AuthnMethod authnMethod)
          throws TapisException, TapisClientException, NotAuthorizedException
  {
    SystemOperation op = SystemOperation.getCred;
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    if (StringUtils.isBlank(systemId) || StringUtils.isBlank(userName))
         throw new IllegalArgumentException(LibUtils.getMsgAuth("SYSLIB_NULL_INPUT_SYSTEM", authenticatedUser));
    String systemTenantName = authenticatedUser.getTenantId();
    String systemUserName = authenticatedUser.getName();
    // For service request use oboTenant and oboUser for tenant and user
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType()))
    {
      systemTenantName = authenticatedUser.getOboTenantId();
      systemUserName = authenticatedUser.getOboUser();
    }

    // If system does not exist or has been soft deleted then return null
    if (!dao.checkForSystem(systemTenantName, systemId, false)) return null;

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, null, userName, null);

    // If authnMethod not passed in fill in with default from system
    if (authnMethod == null)
    {
      TSystem sys = dao.getSystem(systemTenantName, systemId);
      if (sys == null)  throw new TapisException(LibUtils.getMsgAuth(NOT_FOUND, authenticatedUser, systemId));
      authnMethod = sys.getDefaultAuthnMethod();
    }

    Credential credential = null;
    try
    {
      // Get the Security Kernel client
      var skClient = getSKClient();
      // Construct basic SK secret parameters
      var sParms = new SKSecretReadParms(SecretType.System).setSecretName(TOP_LEVEL_SECRET_NAME);
      sParms.setTenant(systemTenantName).setSysId(systemId).setSysUser(userName);
      sParms.setUser(systemUserName);
      // Set key type based on authn method
      if (authnMethod.equals(AuthnMethod.PASSWORD))sParms.setKeyType(KeyType.password);
      else if (authnMethod.equals(AuthnMethod.PKI_KEYS))sParms.setKeyType(KeyType.sshkey);
      else if (authnMethod.equals(AuthnMethod.ACCESS_KEY))sParms.setKeyType(KeyType.accesskey);
      else if (authnMethod.equals(AuthnMethod.CERT))sParms.setKeyType(KeyType.cert);

      // Retrieve the secrets
      SkSecret skSecret = skClient.readSecret(sParms);
      if (skSecret == null) return null;
      var dataMap = skSecret.getSecretMap();
      if (dataMap == null) return null;

      // Create a credential
      credential = new Credential(dataMap.get(SK_KEY_PASSWORD),
              dataMap.get(SK_KEY_PRIVATE_KEY),
              dataMap.get(SK_KEY_PUBLIC_KEY),
              dataMap.get(SK_KEY_ACCESS_KEY),
              dataMap.get(SK_KEY_ACCESS_SECRET),
              null); //dataMap.get(CERT) TODO: get ssh certificate when supported
    }
    // If tapis client exception then log error but continue so null is returned.
    catch (TapisClientException tce)
    {
      _log.warn(tce.toString());
    }
    return credential;
  }

  // ************************************************************************
  // **************************  Private Methods  ***************************
  // ************************************************************************

  /**
   * Update enabled attribute for a system
   * @param authenticatedUser - principal user containing tenant and user info
   * @param systemId - name of system
   * @param sysOp - operation, enable or disable
   * @return Number of items updated
   *
   * @throws TapisException - for Tapis related exceptions
   * @throws IllegalStateException - Resulting App would be in an invalid state
   * @throws IllegalArgumentException - invalid parameter passed in
   * @throws NotAuthorizedException - unauthorized
   * @throws NotFoundException - App not found
   */
  private int updateEnabled(AuthenticatedUser authenticatedUser, String systemId, SystemOperation sysOp)
          throws TapisException, IllegalStateException, IllegalArgumentException, NotAuthorizedException, NotFoundException, TapisClientException
  {
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    if (StringUtils.isBlank(systemId))
      throw new IllegalArgumentException(LibUtils.getMsgAuth("SYSLIB_NULL_INPUT_SYSTEM", authenticatedUser));
    // Extract various names for convenience
    String tenantName = authenticatedUser.getTenantId();
    String apiUserId = authenticatedUser.getName();
    String appTenantName = tenantName;
    // For service request use oboTenant for tenant associated with the app
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType())) appTenantName = authenticatedUser.getOboTenantId();

    // ---------------------------- Check inputs ------------------------------------
    if (StringUtils.isBlank(tenantName) || StringUtils.isBlank(apiUserId))
      throw new IllegalArgumentException(LibUtils.getMsgAuth("APPLIB_CREATE_ERROR_ARG", authenticatedUser, systemId));

    // App must already exist and not be soft deleted
    if (!dao.checkForSystem(appTenantName, systemId, false))
      throw new NotFoundException(LibUtils.getMsgAuth(NOT_FOUND, authenticatedUser, systemId));

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, sysOp, systemId, null, null, null);

    // ----------------- Make update --------------------
    if (sysOp == SystemOperation.enable)
      dao.updateEnabled(authenticatedUser, systemId, true);
    else
      dao.updateEnabled(authenticatedUser, systemId, false);
    return 1;
  }

  /**
   * Get Security Kernel client
   * Note: The service always calls SK as itself.
   * @return SK client
   * @throws TapisException - for Tapis related exceptions
   */
  private SKClient getSKClient() throws TapisException
  {
    SKClient skClient;
    String tenantName = siteAdminTenantId;
    String userName = SERVICE_NAME;
    try
    {
      skClient = serviceClients.getClient(userName, tenantName, SKClient.class);
    }
    catch (Exception e)
    {
      String msg = MsgUtils.getMsg("TAPIS_CLIENT_NOT_FOUND", TapisConstants.SERVICE_NAME_SECURITY, tenantName, userName);
      throw new TapisException(msg, e);
    }

    return skClient;
  }

  /**
   * Check for reserved names.
   * Endpoints defined lead to certain names that are not valid.
   * Invalid names: healthcheck, readycheck, search
   * @param id - the id to check
   * @throws IllegalStateException - if attempt to create a resource with a reserved name
   */
  private void checkReservedIds(AuthenticatedUser authenticatedUser, String id) throws IllegalStateException
  {
    if (TSystem.RESERVED_ID_SET.contains(id.toUpperCase()))
    {
      String msg = LibUtils.getMsgAuth("SYSLIB_CREATE_RESERVED", authenticatedUser, id);
      throw new IllegalStateException(msg);
    }
  }

  /**
   * Check constraints on TSystem attributes.
   * If DTN is used verify that dtnSystemId exists with isDtn = true
   * Collect and report as many errors as possible so they can all be fixed before next attempt
   * @param tSystem1 - the TSystem to check
   * @throws IllegalStateException - if any constraints are violated
   */
  private void validateTSystem(AuthenticatedUser authenticatedUser, TSystem tSystem1) throws IllegalStateException
  {
    String msg;
    List<String> errMessages = tSystem1.checkAttributeRestrictions();

    // If DTN is used (i.e. dtnSystemId is set) verify that dtnSystemId exists with isDtn = true
    if (!StringUtils.isBlank(tSystem1.getDtnSystemId()))
    {
      TSystem dtnSystem = null;
      try
      {
        dtnSystem = dao.getSystem(tSystem1.getTenant(), tSystem1.getDtnSystemId());
      }
      catch (TapisException e)
      {
        msg = LibUtils.getMsg("SYSLIB_DTN_CHECK_ERROR", tSystem1.getDtnSystemId(), e.getMessage());
        _log.error(msg, e);
        errMessages.add(msg);
      }
      if (dtnSystem == null)
      {
        msg = LibUtils.getMsg("SYSLIB_DTN_NO_SYSTEM", tSystem1.getDtnSystemId());
        errMessages.add(msg);
      }
      else if (!dtnSystem.isDtn())
      {
        msg = LibUtils.getMsg("SYSLIB_DTN_NOT_DTN", tSystem1.getDtnSystemId());
        errMessages.add(msg);
      }
    }

    // If validation failed throw an exception
    if (!errMessages.isEmpty())
    {
      // Construct message reporting all errors
      String allErrors = getListOfErrors(authenticatedUser, tSystem1.getId(), errMessages);
      _log.error(allErrors);
      throw new IllegalStateException(allErrors);
    }
  }

  /**
   * If effectiveUserId is dynamic then resolve it
   * @param userId - effectiveUserId string, static or dynamic
   * @return Resolved value for effective user.
   */
  private static String resolveEffectiveUserId(String userId, String owner, String apiUserId)
  {
    if (StringUtils.isBlank(userId)) return userId;
    else if (userId.equals(OWNER_VAR) && !StringUtils.isBlank(owner)) return owner;
    else if (userId.equals(APIUSERID_VAR) && !StringUtils.isBlank(apiUserId)) return apiUserId;
    else return userId;
  }


  /**
   * Retrieve set of user permissions given sk client, user, tenant, id
   * @param skClient - SK client
   * @param userName - name of user
   * @param tenantName - name of tenant
   * @param resourceId - Id of resource
   * @return - Set of Permissions for the user
   */
  private static Set<Permission> getUserPermSet(SKClient skClient, String userName, String tenantName,
                                                String resourceId)
          throws TapisClientException
  {
    var userPerms = new HashSet<Permission>();
    for (Permission perm : Permission.values())
    {
      String permSpec = String.format(PERM_SPEC_TEMPLATE, tenantName, perm.name(), resourceId);
      if (skClient.isPermitted(tenantName, userName, permSpec)) userPerms.add(perm);
    }
    return userPerms;
  }

  /**
   * Create a set of individual permSpec entries based on the list passed in
   * @param permList - list of individual permissions
   * @return - Set of permSpec entries based on permissions
   */
  private static Set<String> getPermSpecSet(String tenantName, String systemId, Set<Permission> permList)
  {
    var permSet = new HashSet<String>();
    for (Permission perm : permList) { permSet.add(getPermSpecStr(tenantName, systemId, perm)); }
    return permSet;
  }

  /**
   * Create a permSpec given a permission
   * @param perm - permission
   * @return - permSpec entry based on permission
   */
  private static String getPermSpecStr(String tenantName, String systemId, Permission perm)
  {
    return String.format(PERM_SPEC_TEMPLATE, tenantName, perm.name(), systemId);
  }

  /**
   * Create a permSpec for all permissions
   * @return - permSpec entry for all permissions
   */
  private static String getPermSpecAllStr(String tenantName, String systemId)
  {
    return String.format(PERM_SPEC_TEMPLATE, tenantName, "*", systemId);
  }

  /**
   * Construct message containing list of errors
   */
  private static String getListOfErrors(AuthenticatedUser authenticatedUser, String systemId, List<String> msgList) {
    var sb = new StringBuilder(LibUtils.getMsgAuth("SYSLIB_CREATE_INVALID_ERRORLIST", authenticatedUser, systemId));
    sb.append(System.lineSeparator());
    if (msgList == null || msgList.isEmpty()) return sb.toString();
    for (String msg : msgList) { sb.append("  ").append(msg).append(System.lineSeparator()); }
    return sb.toString();
  }

  /**
   * Check to see if owner is trying to update permissions for themselves.
   * If so throw an exception because this would be confusing since owner always has full permissions.
   * For an owner permissions are never checked directly.
   *
   * @param authenticatedUser User making the request
   * @param tenant System tenant
   * @param id System id
   * @param userName user for whom perms are being updated
   * @param opStr Operation in progress, for logging
   * @return name of owner
   */
  private String checkForOwnerPermUpdate(AuthenticatedUser authenticatedUser, String tenant, String id,
                                         String userName, String opStr)
          throws TapisException, NotAuthorizedException
  {
    // Look up owner. If not found then consider not authorized. Very unlikely at this point.
    String owner = dao.getSystemOwner(tenant, id);
    if (StringUtils.isBlank(owner))
        throw new NotAuthorizedException(LibUtils.getMsgAuth("SYSLIB_UNAUTH", authenticatedUser, id, opStr));
    // If owner making the request and owner is the target user for the perm update then reject.
    if (owner.equals(authenticatedUser.getOboUser()) && owner.equals(userName))
    {
      // If it is a svc making request reject with no auth, if user making request reject with special message.
      // Need this check since svc not allowed to update perms but checkAuth happens after checkForOwnerPermUpdate.
      // Without this the op would be denied with a misleading message.
      // Unfortunately this means auth check for svc in 2 places but not clear how to avoid it.
      //   On the bright side it means at worst operation will be denied when maybe it should be allowed which is better
      //   than the other way around.
      if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType()))
        throw new NotAuthorizedException(LibUtils.getMsgAuth("SYSLIB_UNAUTH", authenticatedUser, id, opStr));
      else
        throw new TapisException(LibUtils.getMsgAuth("SYSLIB_PERM_OWNER_UPDATE", authenticatedUser, id, opStr));
    }
    return owner;
  }

  /**
   * Standard service level authorization check. Check is different for service and user requests.
   * A check should be made for system existence before calling this method.
   * If no owner is passed in and one cannot be found then an error is logged and authorization is denied.
   *
   * @param authenticatedUser - principal user containing tenant and user info
   * @param operation - operation name
   * @param systemId - name of the system
   * @param owner - system owner
   * @param perms - List of permissions for the revokePerm case
   * @throws NotAuthorizedException - apiUserId not authorized to perform operation
   */
  private void checkAuth(AuthenticatedUser authenticatedUser, SystemOperation operation, String systemId,
                         String owner, String targetUser, Set<Permission> perms)
      throws TapisException, TapisClientException, NotAuthorizedException, IllegalStateException
  {
    // Check service and user requests separately to avoid confusing a service name with a user name
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType())) {
      // This is a service request. The user name will be the service name. E.g. files, jobs, streams, etc
      switch (operation) {
        case read:
          if (SVCLIST_READ.contains(authenticatedUser.getName())) return;
          break;
        case getCred:
          if (SVCLIST_GETCRED.contains(authenticatedUser.getName())) return;
          break;
      }
    }
    else
    {
      // User check
      checkAuthUser(authenticatedUser, operation, null, null, systemId, owner, targetUser, perms);
      return;
    }
    // Not authorized, throw an exception
    String msg = LibUtils.getMsgAuth("SYSLIB_UNAUTH", authenticatedUser, systemId, operation.name());
    throw new NotAuthorizedException(msg);
  }

  /**
   * User based authorization check.
   * Can be used for OBOUser type checks.
   * By default use tenant and user from authenticatedUser, allow for optional tenant or user.
   * A check should be made for system existence before calling this method.
   * If no owner is passed in and one cannot be found then an error is logged and
   *   authorization is denied.
   * Operations:
   *  Create -      must be owner or have admin role
   *  Delete -      must be owner or have admin role
   *  ChangeOwner - must be owner or have admin role
   *  GrantPerm -   must be owner or have admin role
   *  Read -     must be owner or have admin role or have READ or MODIFY permission or be in list of allowed services
   *  getPerms - must be owner or have admin role or have READ or MODIFY permission or be in list of allowed services
   *  Modify - must be owner or have admin role or have MODIFY permission
   *  Execute - must be owner or have admin role or have EXECUTE permission
   *  RevokePerm -  must be owner or have admin role or apiUserId=targetUser and meet certain criteria (allowUserRevokePerm)
   *  SetCred -     must be owner or have admin role or apiUserId=targetUser and meet certain criteria (allowUserCredOp)
   *  RemoveCred -  must be owner or have admin role or apiUserId=targetUser and meet certain criteria (allowUserCredOp)
   *
   * @param authenticatedUser - principal user containing tenant and user info
   * @param operation - operation name
   * @param tenantToCheck - optional name of the tenant to use. Default is to use authenticatedUser.
   * @param userToCheck - optional name of the user to check. Default is to use authenticatedUser.
   * @param systemId - name of the system
   * @param owner - system owner
   * @param perms - List of permissions for the revokePerm case
   * @throws NotAuthorizedException - apiUserId not authorized to perform operation
   */
  private void checkAuthUser(AuthenticatedUser authenticatedUser, SystemOperation operation,
                             String tenantToCheck, String userToCheck,
                             String systemId, String owner, String targetUser, Set<Permission> perms)
          throws TapisException, TapisClientException, NotAuthorizedException, IllegalStateException
  {
    // Use tenant and user from authenticatedUsr or optional provided values
    String tenantName = (StringUtils.isBlank(tenantToCheck) ? authenticatedUser.getTenantId() : tenantToCheck);
    String userName = (StringUtils.isBlank(userToCheck) ? authenticatedUser.getName() : userToCheck);
    // Requires owner. If no owner specified and owner cannot be determined then log an error and deny.
    if (StringUtils.isBlank(owner)) owner = dao.getSystemOwner(tenantName, systemId);
    if (StringUtils.isBlank(owner)) {
      String msg = LibUtils.getMsgAuth("SYSLIB_AUTH_NO_OWNER", authenticatedUser, systemId, operation.name());
      _log.error(msg);
      throw new NotAuthorizedException(msg);
    }
    switch(operation) {
      case create:
      case enable:
      case disable:
      case softDelete:
      case changeOwner:
      case grantPerms:
        if (owner.equals(userName) || hasAdminRole(authenticatedUser, tenantName, userName))
          return;
        break;
      case hardDelete:
        if (hasAdminRole(authenticatedUser, tenantName, userName))
          return;
        break;
      case read:
      case getPerms:
        if (owner.equals(userName) || hasAdminRole(authenticatedUser, tenantName, userName) ||
              isPermittedAny(authenticatedUser, tenantName, userName, systemId, READMODIFY_PERMS))
          return;
        break;
      case modify:
        if (owner.equals(userName) || hasAdminRole(authenticatedUser, tenantName, userName) ||
                isPermitted(authenticatedUser, tenantName, userName, systemId, Permission.MODIFY))
          return;
        break;
      case execute:
        if (owner.equals(userName) || hasAdminRole(authenticatedUser, tenantName, userName) ||
                isPermitted(authenticatedUser, tenantName, userName, systemId, Permission.EXECUTE))
          return;
        break;
      case revokePerms:
        if (owner.equals(userName) || hasAdminRole(authenticatedUser, tenantName, userName) ||
                (userName.equals(targetUser) &&
                        allowUserRevokePerm(authenticatedUser, tenantName, userName, systemId, perms)))
          return;
        break;
      case setCred:
      case removeCred:
        if (owner.equals(userName) || hasAdminRole(authenticatedUser, tenantName, userName) ||
                (userName.equals(targetUser) &&
                        allowUserCredOp(authenticatedUser, systemId, operation)))
          return;
        break;
    }
    // Not authorized, throw an exception
    String msg = LibUtils.getMsgAuth("SYSLIB_UNAUTH", authenticatedUser, systemId, operation.name());
    throw new NotAuthorizedException(msg);
  }

  /**
   * Determine all systems that a user is allowed to see.
   * If all systems return null else return list of system IDs
   * An empty list indicates no systems allowed.
   */
  private Set<String> getAllowedSysIDs(AuthenticatedUser authenticatedUser, String systemTenantName)
          throws TapisException, TapisClientException
  {
    // If requester is a service calling as itself or an admin then all systems allowed
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType()) &&
            authenticatedUser.getName().equals(authenticatedUser.getOboUser()) ||
        hasAdminRole(authenticatedUser, null, null)) return null;
    var sysIDs = new HashSet<String>();
    var userPerms = getSKClient().getUserPerms(systemTenantName, authenticatedUser.getOboUser());
    // Check each perm to see if it allows user READ access.
    for (String userPerm : userPerms)
    {
      if (StringUtils.isBlank(userPerm)) continue;
      // Split based on :, permSpec has the format system:<tenant>:<perms>:<system_name>
      String[] permFields = userPerm.split(":");
      if (permFields.length < 4) continue;
      if (permFields[0].equalsIgnoreCase(PERM_SPEC_PREFIX) &&
           (permFields[2].contains(Permission.READ.name()) ||
            permFields[2].contains(Permission.MODIFY.name()) ||
            permFields[2].contains(TSystem.PERMISSION_WILDCARD)))
      {
        sysIDs.add(permFields[3]);
      }
    }
    return sysIDs;
  }

  /**
   * Check to see if a user has the service admin role
   * By default use tenant and user from authenticatedUser, allow for optional tenant or user.
   */
  private boolean hasAdminRole(AuthenticatedUser authenticatedUser, String tenantToCheck, String userToCheck)
          throws TapisException, TapisClientException
  {
    // Use tenant and user from authenticatedUsr or optional provided values
    String tenantName = (StringUtils.isBlank(tenantToCheck) ? authenticatedUser.getTenantId() : tenantToCheck);
    String userName = (StringUtils.isBlank(userToCheck) ? authenticatedUser.getName() : userToCheck);
    // TODO: Remove this when admin access is available Jira cic-3964
    if ("testuser9".equalsIgnoreCase(userName)) return true;
//    var skClient = getSKClient();
//    return skClient.isAdmin(tenantName, userName);
    return false;
  }

  /**
   * Check to see if a user has the specified permission
   * By default use tenant and user from authenticatedUser, allow for optional tenant or user.
   */
  private boolean isPermitted(AuthenticatedUser authenticatedUser, String tenantToCheck, String userToCheck,
                              String systemId, Permission perm)
          throws TapisException, TapisClientException
  {
    // Use tenant and user from authenticatedUsr or optional provided values
    String tenantName = (StringUtils.isBlank(tenantToCheck) ? authenticatedUser.getTenantId() : tenantToCheck);
    String userName = (StringUtils.isBlank(userToCheck) ? authenticatedUser.getName() : userToCheck);
    var skClient = getSKClient();
    String permSpecStr = getPermSpecStr(tenantName, systemId, perm);
    return skClient.isPermitted(tenantName, userName, permSpecStr);
  }

  /**
   * Check to see if a user has any of the set of permissions
   * By default use tenant and user from authenticatedUser, allow for optional tenant or user.
   */
  private boolean isPermittedAny(AuthenticatedUser authenticatedUser, String tenantToCheck, String userToCheck,
                                 String systemId, Set<Permission> perms)
          throws TapisException, TapisClientException
  {
    // Use tenant and user from authenticatedUsr or optional provided values
    String tenantName = (StringUtils.isBlank(tenantToCheck) ? authenticatedUser.getTenantId() : tenantToCheck);
    String userName = (StringUtils.isBlank(userToCheck) ? authenticatedUser.getName() : userToCheck);
    var skClient = getSKClient();
    var permSpecs = new ArrayList<String>();
    for (Permission perm : perms) {
      permSpecs.add(getPermSpecStr(tenantName, systemId, perm));
    }
    return skClient.isPermittedAny(tenantName, userName, permSpecs.toArray(TSystem.EMPTY_STR_ARRAY));
  }

  /**
   * Check to see if a user who is not owner or admin is authorized to revoke permissions
   * By default use tenant and user from authenticatedUser, allow for optional tenant or user.
   */
  private boolean allowUserRevokePerm(AuthenticatedUser authenticatedUser, String tenantToCheck, String userToCheck,
                                      String systemId, Set<Permission> perms)
          throws TapisException, TapisClientException
  {
    // Use tenant and user from authenticatedUsr or optional provided values
    String tenantName = (StringUtils.isBlank(tenantToCheck) ? authenticatedUser.getTenantId() : tenantToCheck);
    String userName = (StringUtils.isBlank(userToCheck) ? authenticatedUser.getName() : userToCheck);
    if (perms.contains(Permission.MODIFY)) return isPermitted(authenticatedUser, tenantName, userName, systemId, Permission.MODIFY);
    if (perms.contains(Permission.READ)) return isPermittedAny(authenticatedUser, tenantName, userName, systemId, READMODIFY_PERMS);
    return false;
  }

  /**
   * Check to see if apiUserId who is not owner or admin is authorized to operate on a credential
   * No checks are done for incoming arguments and the system must exist
   */
  private boolean allowUserCredOp(AuthenticatedUser authenticatedUser, String systemId, SystemOperation op)
          throws TapisException, IllegalStateException
  {
    // Get the effectiveUserId. If not ${apiUserId} then considered an error since credential would never be used.
    String effectiveUserId = dao.getSystemEffectiveUserId(authenticatedUser.getTenantId(), systemId);
    if (StringUtils.isBlank(effectiveUserId) || !effectiveUserId.equals(APIUSERID_VAR))
    {
      String msg = LibUtils.getMsgAuth("SYSLIB_CRED_NOTAPIUSER", authenticatedUser, systemId, op.name());
      _log.error(msg);
      throw new IllegalStateException(msg);

    }
    return true;
  }

  /**
   * Create or update a credential
   * No checks are done for incoming arguments and the system must exist
   */
  private static void createCredential(SKClient skClient, Credential credential, String tenantName, String apiUserId,
                                       String systemId, String systemTenantName, String userName)
          throws TapisClientException
  {
    // Construct basic SK secret parameters including tenant, system and user for credential
    var sParms = new SKSecretWriteParms(SecretType.System).setSecretName(TOP_LEVEL_SECRET_NAME);
    sParms.setTenant(systemTenantName).setSysId(systemId).setSysUser(userName);
    Map<String, String> dataMap;
    // Check for each secret type and write values if they are present
    // Note that multiple secrets may be present.
    // Store password if present
    if (!StringUtils.isBlank(credential.getPassword())) {
      dataMap = new HashMap<>();
      sParms.setKeyType(KeyType.password);
      dataMap.put(SK_KEY_PASSWORD, credential.getPassword());
      sParms.setData(dataMap);
      skClient.writeSecret(tenantName, apiUserId, sParms);
    }
    // Store PKI keys if both present
    if (!StringUtils.isBlank(credential.getPublicKey()) && !StringUtils.isBlank(credential.getPublicKey())) {
      dataMap = new HashMap<>();
      sParms.setKeyType(KeyType.sshkey);
      dataMap.put(SK_KEY_PUBLIC_KEY, credential.getPublicKey());
      dataMap.put(SK_KEY_PRIVATE_KEY, credential.getPrivateKey());
      sParms.setData(dataMap);
      skClient.writeSecret(tenantName, apiUserId, sParms);
    }
    // Store Access key and secret if both present
    if (!StringUtils.isBlank(credential.getAccessKey()) && !StringUtils.isBlank(credential.getAccessSecret())) {
      dataMap = new HashMap<>();
      sParms.setKeyType(KeyType.accesskey);
      dataMap.put(SK_KEY_ACCESS_KEY, credential.getAccessKey());
      dataMap.put(SK_KEY_ACCESS_SECRET, credential.getAccessSecret());
      sParms.setData(dataMap);
      skClient.writeSecret(tenantName, apiUserId, sParms);
    }
    // TODO if necessary handle ssh certificate when supported
  }

  /**
   * Delete a credential
   * No checks are done for incoming arguments and the system must exist
   */
  private static int deleteCredential(SKClient skClient, String tenantName, String apiUserId,
                                      String systemTenantName, String systemId, String userName)
          throws TapisClientException
  {
    int changeCount = 0;
    // Return 0 if credential does not exist
    var sMetaParms = new SKSecretMetaParms(SecretType.System).setSecretName(TOP_LEVEL_SECRET_NAME);
    sMetaParms.setTenant(systemTenantName).setSysId(systemId).setSysUser(userName).setUser(apiUserId);
    SkSecretVersionMetadata skMetaSecret;
    try
    {
      skMetaSecret = skClient.readSecretMeta(sMetaParms);
    }
    catch (Exception e)
    {
      _log.warn(e.getMessage());
      skMetaSecret = null;
    }
    if (skMetaSecret == null) return changeCount;

    // Construct basic SK secret parameters
    var sParms = new SKSecretDeleteParms(SecretType.System).setSecretName(TOP_LEVEL_SECRET_NAME);
    sParms.setTenant(systemTenantName).setSysId(systemId).setSysUser(userName);
    sParms.setUser(apiUserId).setVersions(Collections.emptyList());
    sParms.setKeyType(KeyType.password);
    List<Integer> intList = skClient.destroySecret(tenantName, apiUserId, sParms);
    // Return value is a list of destroyed versions. If any destroyed increment changeCount by 1
    if (intList != null && !intList.isEmpty()) changeCount++;
    sParms.setKeyType(KeyType.sshkey);
    intList = skClient.destroySecret(tenantName, apiUserId, sParms);
    if (intList != null && !intList.isEmpty()) changeCount++;
    sParms.setKeyType(KeyType.accesskey);
    intList = skClient.destroySecret(tenantName, apiUserId, sParms);
    if (intList != null && !intList.isEmpty()) changeCount++;
    // If anything destroyed we consider it the removal of a single credential
    if (changeCount > 0) changeCount = 1;
    return changeCount;
  }

  /**
   * Remove all SK artifacts associated with a System: user credentials, user permissions
   * No checks are done for incoming arguments and the system must exist
   */
  private void removeSKArtifacts(AuthenticatedUser authenticatedUser, String systemId, SystemOperation op)
          throws TapisException, TapisClientException
  {
    // Extract various names for convenience
    String tenantName = authenticatedUser.getTenantId();
    String apiUserId = authenticatedUser.getName();
    // For service request use oboTenant for tenant associated with the system
    String systemTenantName = authenticatedUser.getTenantId();
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType())) systemTenantName = authenticatedUser.getOboTenantId();

    var skClient = getSKClient();

    // Use Security Kernel client to find all users with perms associated with the system.
    String permSpec = String.format(PERM_SPEC_TEMPLATE, systemTenantName, "%", systemId);
    var userNames = skClient.getUsersWithPermission(systemTenantName, permSpec);
    // Revoke all perms for all users
    for (String userName : userNames) {
      revokePermissions(skClient, systemTenantName, systemId, userName, ALL_PERMS);
      // Remove wildcard perm
      skClient.revokeUserPermission(systemTenantName, userName, getPermSpecAllStr(systemTenantName, systemId));
    }

    // Fetch the system. If system not found then return
    TSystem system = dao.getSystem(systemTenantName, systemId, true);
    if (system == null) return;

    // Resolve effectiveUserId if necessary
    String effectiveUserId = system.getEffectiveUserId();
    effectiveUserId = resolveEffectiveUserId(effectiveUserId, system.getOwner(), apiUserId);

    // Remove credentials associated with the system.
    // TODO: Have SK do this in one operation?
    // TODO: How to remove for users other than effectiveUserId?
    // Remove credentials in Security Kernel if effectiveUser is static
    if (!effectiveUserId.equals(APIUSERID_VAR)) {
      // Use private internal method instead of public API to skip auth and other checks not needed here.
      deleteCredential(skClient, tenantName, apiUserId, systemTenantName, system.getId(), effectiveUserId);
    }
  }

  /**
   * Revoke permissions
   * No checks are done for incoming arguments and the system must exist
   */
  private static int revokePermissions(SKClient skClient, String systemTenantName, String systemId, String userName, Set<Permission> permissions)
          throws TapisClientException
  {
    // Create a set of individual permSpec entries based on the list passed in
    Set<String> permSpecSet = getPermSpecSet(systemTenantName, systemId, permissions);
    // Remove perms from default user role
    for (String permSpec : permSpecSet)
    {
      skClient.revokeUserPermission(systemTenantName, userName, permSpec);
    }
    return permSpecSet.size();
  }

  /**
   * Merge a patch into an existing TSystem
   * Attributes that can be updated:
   *   description, host, effectiveUserId, defaultAuthnMethod, transferMethods,
   *   port, useProxy, proxyHost, proxyPort, dtnSystemId, dtnMountPoint, dtnMountSourcePath,
   *   jobRuntimes, jobWorkingDir, jobEnvVariables, jobMaxJobs, jobMaxJobsPerUers, jobIsBatch,
   *   batchScheduler, batchLogicalQueues, batchDefaultLogicalQueue, jobCapabilities, tags, notes.
   * The only attribute that can be reset to default is effectiveUserId. It is reset when
   *   a blank string is passed in.
   */
  private TSystem createPatchedTSystem(TSystem o, PatchSystem p)
  {
    TSystem p1 = new TSystem(o);
    if (p.getDescription() != null) p1.setDescription(p.getDescription());
    if (p.getHost() != null) p1.setHost(p.getHost());
    if (p.getEffectiveUserId() != null) {
      if (StringUtils.isBlank(p.getEffectiveUserId())) {
        p1.setEffectiveUserId(DEFAULT_EFFECTIVEUSERID);
      } else {
        p1.setEffectiveUserId(p.getEffectiveUserId());
      }
    }
    if (p.getDefaultAuthnMethod() != null) p1.setDefaultAuthnMethod(p.getDefaultAuthnMethod());
    if (p.getTransferMethods() != null) p1.setTransferMethods(p.getTransferMethods());
    if (p.getPort() != null) p1.setPort(p.getPort());
    if (p.isUseProxy() != null) p1.setUseProxy(p.isUseProxy());
    if (p.getProxyHost() != null) p1.setProxyHost(p.getProxyHost());
    if (p.getProxyPort() != null) p1.setProxyPort(p.getProxyPort());
    if (p.getDtnSystemId() != null) p1.setDtnSystemId(p.getDtnSystemId());
    if (p.getDtnMountPoint() != null) p1.setDtnMountPoint(p.getDtnMountPoint());
    if (p.getDtnMountSourcePath() != null) p1.setDtnMountSourcePath(p.getDtnMountSourcePath());
    if (p.getJobRuntimes() != null) p1.setJobRuntimes(p.getJobRuntimes());
    if (p.getJobWorkingDir() != null) p1.setJobWorkingDir(p.getJobWorkingDir());
    if (p.getJobEnvVariables() != null) p1.setJobEnvVariables(p.getJobEnvVariables());
    if (p.getJobMaxJobs() != null) p1.setJobMaxJobs(p.getJobMaxJobs());
    if (p.getJobMaxJobsPerUser() != null) p1.setJobMaxJobsPerUser(p.getJobMaxJobsPerUser());
    if (p.getJobIsBatch() != null) p1.setJobIsBatch(p.getJobIsBatch());
    if (p.getBatchScheduler() != null) p1.setBatchScheduler(p.getBatchScheduler());
    if (p.getBatchLogicalQueues() != null) p1.setBatchLogicalQueues(p.getBatchLogicalQueues());
    if (p.getBatchDefaultLogicalQueue() != null) p1.setBatchDefaultLogicalQueue(p.getBatchDefaultLogicalQueue());
    if (p.getJobCapabilities() != null) p1.setJobCapabilities(p.getJobCapabilities());
    if (p.getTags() != null) p1.setTags(p.getTags());
    if (p.getNotes() != null) p1.setNotes(p.getNotes());
    return p1;
  }
}
