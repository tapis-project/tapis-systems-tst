package edu.utexas.tacc.tapis.systems.service;

import static edu.utexas.tacc.tapis.systems.model.Credential.SK_KEY_ACCESS_KEY;
import static edu.utexas.tacc.tapis.systems.model.Credential.SK_KEY_ACCESS_SECRET;
import static edu.utexas.tacc.tapis.systems.model.Credential.SK_KEY_PASSWORD;
import static edu.utexas.tacc.tapis.systems.model.Credential.SK_KEY_PRIVATE_KEY;
import static edu.utexas.tacc.tapis.systems.model.Credential.SK_KEY_PUBLIC_KEY;
import static edu.utexas.tacc.tapis.systems.model.Credential.TOP_LEVEL_SECRET_NAME;
import static edu.utexas.tacc.tapis.systems.model.TSystem.APIUSERID_VAR;
import static edu.utexas.tacc.tapis.systems.model.TSystem.DEFAULT_EFFECTIVEUSERID;
import static edu.utexas.tacc.tapis.systems.model.TSystem.OWNER_VAR;
import static edu.utexas.tacc.tapis.systems.model.TSystem.TENANT_VAR;

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
import edu.utexas.tacc.tapis.shared.security.ServiceClients;
import org.apache.commons.lang3.StringUtils;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.utexas.tacc.tapis.client.shared.exceptions.TapisClientException;
import edu.utexas.tacc.tapis.search.parser.ASTParser;
import edu.utexas.tacc.tapis.search.parser.ASTNode;
import edu.utexas.tacc.tapis.search.SearchUtils;
import edu.utexas.tacc.tapis.security.client.SKClient;
import edu.utexas.tacc.tapis.security.client.gen.model.SkRole;
import edu.utexas.tacc.tapis.security.client.gen.model.SkSecret;
import edu.utexas.tacc.tapis.security.client.gen.model.SkSecretVersionMetadata;
import edu.utexas.tacc.tapis.security.client.model.KeyType;
import edu.utexas.tacc.tapis.security.client.model.SKSecretDeleteParms;
import edu.utexas.tacc.tapis.security.client.model.SKSecretMetaParms;
import edu.utexas.tacc.tapis.security.client.model.SKSecretReadParms;
import edu.utexas.tacc.tapis.security.client.model.SKSecretWriteParms;
import edu.utexas.tacc.tapis.security.client.model.SecretType;
import edu.utexas.tacc.tapis.shared.exceptions.TapisException;
import edu.utexas.tacc.tapis.shared.security.ServiceContext;
import edu.utexas.tacc.tapis.shared.security.TenantManager;
import edu.utexas.tacc.tapis.shared.threadlocal.TapisThreadContext;
import edu.utexas.tacc.tapis.shared.utils.TapisGsonUtils;
import edu.utexas.tacc.tapis.sharedapi.security.AuthenticatedUser;
import edu.utexas.tacc.tapis.systems.config.RuntimeParameters;
import edu.utexas.tacc.tapis.systems.dao.SystemsDao;
import edu.utexas.tacc.tapis.systems.model.Credential;
import edu.utexas.tacc.tapis.systems.model.PatchSystem;
import edu.utexas.tacc.tapis.systems.model.SystemBasic;
import edu.utexas.tacc.tapis.systems.model.TSystem;
import edu.utexas.tacc.tapis.systems.model.TSystem.AuthnMethod;
import edu.utexas.tacc.tapis.systems.model.TSystem.Permission;
import edu.utexas.tacc.tapis.systems.model.TSystem.SystemOperation;
import edu.utexas.tacc.tapis.systems.model.TSystem.TransferMethod;
import edu.utexas.tacc.tapis.systems.utils.LibUtils;
import edu.utexas.tacc.tapis.tenants.client.gen.model.Tenant;

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

  private static final String[] ALL_VARS = {APIUSERID_VAR, OWNER_VAR, TENANT_VAR};
  private static final Set<Permission> ALL_PERMS = new HashSet<>(Set.of(Permission.READ, Permission.MODIFY, Permission.EXECUTE));
  private static final Set<Permission> READMODIFY_PERMS = new HashSet<>(Set.of(Permission.READ, Permission.MODIFY));
  private static final String PERM_SPEC_PREFIX = "system:";

  private static final String HDR_TAPIS_TOKEN = "X-Tapis-Token";
  private static final String HDR_TAPIS_TENANT = "X-Tapis-Tenant";
  private static final String HDR_TAPIS_USER = "X-Tapis-User";

  private static final String FILES_SERVICE = TapisConstants.SERVICE_NAME_FILES;
  private static final String JOBS_SERVICE = TapisConstants.SERVICE_NAME_JOBS;
  private static final Set<String> SVCLIST_GETCRED = new HashSet<>(Set.of(FILES_SERVICE, JOBS_SERVICE));
  private static final Set<String> SVCLIST_READ = new HashSet<>(Set.of(FILES_SERVICE, JOBS_SERVICE));

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

//  @Inject
//  private ServiceClients svcClients;

  @Inject
  private ServiceContext serviceContext;

  // TODO remove?
  @Inject
  private SKClient skClient;

  // We must be running on a specific site and this will never change.
  private static String siteId;

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
   * @return Sequence id of object created
   * @throws TapisException - for Tapis related exceptions
   * @throws IllegalStateException - system exists OR TSystem in invalid state
   * @throws IllegalArgumentException - invalid parameter passed in
   * @throws NotAuthorizedException - unauthorized
   */
  @Override
  public int createSystem(AuthenticatedUser authenticatedUser, TSystem system, String scrubbedText)
          throws TapisException, TapisClientException, IllegalStateException, IllegalArgumentException, NotAuthorizedException
  {
    SystemOperation op = SystemOperation.create;
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    if (system == null) throw new IllegalArgumentException(LibUtils.getMsgAuth("SYSLIB_NULL_INPUT_SYSTEM", authenticatedUser));
    // Extract various names for convenience
    String tenantName = authenticatedUser.getTenantId();
    String apiUserId = authenticatedUser.getName();
    String systemId = system.getId();
    String systemTenantName = authenticatedUser.getTenantId();
    // For service request use oboTenant for tenant associated with the system
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType())) systemTenantName = authenticatedUser.getOboTenantId();

    // ---------------------------- Check inputs ------------------------------------
    // Required system attributes: name, type, host, defaultAuthnMethod
    if (StringUtils.isBlank(tenantName) || StringUtils.isBlank(apiUserId) || StringUtils.isBlank(systemId) ||
        system.getSystemType() == null || StringUtils.isBlank(system.getHost()) ||
        system.getDefaultAuthnMethod() == null || StringUtils.isBlank(apiUserId) || StringUtils.isBlank(scrubbedText))
    {
      throw new IllegalArgumentException(LibUtils.getMsgAuth("SYSLIB_CREATE_ERROR_ARG", authenticatedUser, systemId));
    }

    // Check if system already exists
    if (dao.checkForTSystem(systemTenantName, systemId, true))
    {
      throw new IllegalStateException(LibUtils.getMsgAuth("SYSLIB_SYS_EXISTS", authenticatedUser, systemId));
    }

    // Make sure owner, effectiveUserId, notes and tags are all set
    // Note that this is done before auth so owner can get resolved and used during auth check.
    system.setTenant(systemTenantName);
    TSystem.checkAndSetDefaults(system);
    String effectiveUserId = system.getEffectiveUserId();

    // ----------------- Resolve variables for any attributes that might contain them --------------------
    resolveVariables(system, authenticatedUser.getOboUser());

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, system.getId(), system.getOwner(), null, null);

    // ---------------- Check constraints on TSystem attributes ------------------------
    validateTSystem(authenticatedUser, system);

    // Construct Json string representing the TSystem (without credentials) about to be created
    TSystem scrubbedSystem = new TSystem(system);
    scrubbedSystem.setAuthnCredential(null);
    String createJsonStr = TapisGsonUtils.getGson().toJson(scrubbedSystem);

    // ----------------- Create all artifacts --------------------
    // Creation of system and role/perms/creds not in single DB transaction. Need to handle failure of role/perms/creds operations
    // Use try/catch to rollback any writes in case of failure.
    int itemSeqId = -1;
    String roleNameR = null;
    String systemsPermSpecR = getPermSpecStr(systemTenantName, systemId, Permission.READ);
    String systemsPermSpecALL = getPermSpecAllStr(systemTenantName, systemId);
    // TODO remove filesPermSpec related code (jira cic-3071)
    String filesPermSpec = "files:" + systemTenantName + ":*:" + systemId;

    // Get SK client now. If we cannot get this rollback not needed.
    var skClient = getSKClient(authenticatedUser);
    try {
      // ------------------- Make Dao call to persist the system -----------------------------------
      itemSeqId = dao.createTSystem(authenticatedUser, system, createJsonStr, scrubbedText);

      // Add permission roles for the system. This is only used for filtering systems based on who is authz
      //   to READ, so no other roles needed.
      roleNameR = TSystem.ROLE_READ_PREFIX + itemSeqId;
      // TODO REMOVE DEBUG
      _log.debug("authUser.user=" + authenticatedUser.getName());
      _log.debug("authUser.tenant=" + authenticatedUser.getTenantId());
      _log.debug("authUser.OboUser=" + authenticatedUser.getOboUser());
      _log.debug("authUser.OboTenant=" + authenticatedUser.getOboTenantId());
      _log.debug("systemTenantName=" + systemTenantName);
      _log.debug("system.getOwner=" + system.getOwner());
      _log.debug("roleNameR="+ roleNameR);
      _log.debug("systemsPermSpecR=" + systemsPermSpecR);
      _log.debug("authenticatedUser.getJwt=" + authenticatedUser.getJwt());
      _log.debug("serviceJwt.getAccessJWT(siteId)=" + serviceContext.getServiceJWT().getAccessJWT(siteId));
      // TODO: Delete fails due to SK authz failure
      //       SK_API_AUTHORIZATION_FAILED These authorization checks failed for request tenant/user=dev/null
      //       (jwt tenant/user=admin/systems, obo tenant/user=dev/owner1, account=service):  IsAdmin, OwnedRoles
      // TODO: And call to get role throws a not found, which we could catch and handle, but this is getting
      //       awkward. Seems like delete should not fail, should return 0 and get should return null.
      // Delete role, because role may already exist due to failure of rollback
//      SkRole tstRole = skClient.getRoleByName(systemTenantName, roleNameR);
//      if (tstRole != null) skClient.deleteRoleByName(systemTenantName, roleNameR);
//      skClient.deleteRoleByName(systemTenantName, roleNameR);
      skClient.createRole(systemTenantName, roleNameR, "Role allowing READ for system " + systemId);
      skClient.addRolePermission(systemTenantName, roleNameR, systemsPermSpecR);

      // ------------------- Add permissions and role assignments -----------------------------
      // Give owner and possibly effectiveUser full access to the system
      skClient.grantUserPermission(systemTenantName, system.getOwner(), systemsPermSpecALL);
      skClient.grantUserRole(systemTenantName, system.getOwner(), roleNameR);
      if (!effectiveUserId.equals(APIUSERID_VAR) && !effectiveUserId.equals(OWNER_VAR)) {
        skClient.grantUserPermission(systemTenantName, effectiveUserId, systemsPermSpecALL);
        skClient.grantUserRole(systemTenantName, effectiveUserId, roleNameR);
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
      if (itemSeqId != -1) try {dao.hardDeleteTSystem(systemTenantName, systemId); }
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
      // Remove role assignments and roles
      if (!StringUtils.isBlank(roleNameR)) {
        try { skClient.revokeUserRole(systemTenantName, system.getOwner(), roleNameR);  }
        catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "revokeRoleOwner", e.getMessage()));}
        try { skClient.revokeUserRole(systemTenantName, effectiveUserId, roleNameR);  }
        catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "revokeRoleEffUsr", e.getMessage()));}
        try { skClient.deleteRoleByName(systemTenantName, roleNameR);  }
        catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "deleteRole", e.getMessage()));}
      }
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
    return itemSeqId;
  }

  /**
   * Update a system object given a PatchSystem and the text used to create the PatchSystem.
   * Secrets in the text should be masked.
   * Attributes that can be updated:
   *   description, host, enabled, effectiveUserId, defaultAuthnMethod, transferMethods,
   *   port, useProxy, proxyHost, proxyPort, jobCapabilities, tags, notes.
   * Attributes that cannot be updated:
   *   tenant, id, systemType, owner, authnCredential, bucketName, rootDir, canExec
   * @param authenticatedUser - principal user containing tenant and user info
   * @param patchSystem - Pre-populated PatchSystem object
   * @param scrubbedText - Text used to create the PatchSystem object - secrets should be scrubbed. Saved in update record.
   * @return Sequence id of object updated
   * @throws TapisException - for Tapis related exceptions
   * @throws IllegalStateException - Resulting TSystem would be in an invalid state
   * @throws IllegalArgumentException - invalid parameter passed in
   * @throws NotAuthorizedException - unauthorized
   * @throws NotFoundException - System not found
   */
  @Override
  public int updateSystem(AuthenticatedUser authenticatedUser, PatchSystem patchSystem, String scrubbedText)
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
    if (!dao.checkForTSystem(systemTenantName, systemId, false))
    {
      throw new NotFoundException(LibUtils.getMsgAuth(NOT_FOUND, authenticatedUser, systemId));
    }

    // Retrieve the system being patched and create fully populated TSystem with changes merged in
    TSystem origTSystem = dao.getTSystem(systemTenantName, systemId);
    TSystem patchedTSystem = createPatchedTSystem(origTSystem, patchSystem);

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, origTSystem.getOwner(), null, null);

    // ---------------- Check constraints on TSystem attributes ------------------------
    validateTSystem(authenticatedUser, patchedTSystem);

    // Construct Json string representing the PatchSystem about to be used to update the system
    String updateJsonStr = TapisGsonUtils.getGson().toJson(patchSystem);

    // ----------------- Create all artifacts --------------------
    // No distributed transactions so no distributed rollback needed
    // ------------------- Make Dao call to persist the system -----------------------------------
    dao.updateTSystem(authenticatedUser, patchedTSystem, patchSystem, updateJsonStr, scrubbedText);
    return origTSystem.getSeqId();
  }

  /**
   * Change owner of a system
   * @param authenticatedUser - principal user containing tenant and user info
   * @param systemId - name of system
   * @param newOwnerName - User name of new owner
   * @return Number of items updated
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
    if (!dao.checkForTSystem(systemTenantName, systemId, false))
         throw new NotFoundException(LibUtils.getMsgAuth(NOT_FOUND, authenticatedUser, systemId));

    // Retrieve the system being updated
    TSystem tmpSystem = dao.getTSystem(systemTenantName, systemId);
    int seqId = tmpSystem.getSeqId();
    String oldOwnerName = tmpSystem.getOwner();

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, tmpSystem.getOwner(), null, null);

    // If new owner same as old owner then this is a no-op
    if (newOwnerName.equals(oldOwnerName)) return 0;

    // ----------------- Make all updates --------------------
    // Changes not in single DB transaction. Need to handle failure of role/perms/creds operations
    // Use try/catch to rollback any changes in case of failure.
    // Get SK client now. If we cannot get this rollback not needed.
    var skClient = getSKClient(authenticatedUser);
    String systemsPermSpec = getPermSpecAllStr(systemTenantName, systemId);
    String roleNameR = TSystem.ROLE_READ_PREFIX + seqId;
    // TODO remove addition of files related permSpec (jira cic-3071)
    String filesPermSpec = "files:" + systemTenantName + ":*:" + systemId;
    try {
      // ------------------- Make Dao call to update the system owner -----------------------------------
      dao.updateSystemOwner(authenticatedUser, seqId, newOwnerName);
      // Add role and permissions for new owner
      skClient.grantUserRole(systemTenantName, newOwnerName, roleNameR);
      skClient.grantUserPermission(systemTenantName, newOwnerName, systemsPermSpec);
      // TODO remove addition of files related permSpec (jira cic-3071)
      // Give owner files service related permission for root directory
      skClient.grantUserPermission(systemTenantName, newOwnerName, filesPermSpec);
      // Remove role and permissions from old owner
      skClient.revokeUserRole(systemTenantName, oldOwnerName, roleNameR);
      skClient.revokeUserPermission(systemTenantName, oldOwnerName, systemsPermSpec);
      // TODO: Notify files service of the change (jira cic-3071)
    }
    catch (Exception e0)
    {
      // Something went wrong. Attempt to undo all changes and then re-throw the exception
      try { dao.updateSystemOwner(authenticatedUser, seqId, oldOwnerName); } catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "updateOwner", e.getMessage()));}
      // TODO remove filesPermSpec related code (jira cic-3071)
      try { skClient.revokeUserRole(systemTenantName, newOwnerName, roleNameR); }
      catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "revokeRoleNewOwner", e.getMessage()));}
      try { skClient.revokeUserPermission(systemTenantName, newOwnerName, filesPermSpec); }
      catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "revokePermNewOwner", e.getMessage()));}
      try { skClient.revokeUserPermission(systemTenantName, newOwnerName, filesPermSpec); }
      catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "revokePermF1", e.getMessage()));}
      try { skClient.grantUserPermission(systemTenantName, oldOwnerName, systemsPermSpec); }
      catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "grantPermOldOwner", e.getMessage()));}
      try { skClient.grantUserRole(systemTenantName, oldOwnerName, roleNameR); }
      catch (Exception e) {_log.warn(LibUtils.getMsgAuth(ERROR_ROLLBACK, authenticatedUser, systemId, "grantRoleOldOwner", e.getMessage()));}
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
    if (!dao.checkForTSystem(systemTenantName, systemId, false)) return 0;

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, null, null, null);

    // Remove SK artifacts
    removeSKArtifacts(authenticatedUser, systemId, op);

    // Delete the system
    int systemSeqId = dao.getTSystemSeqId(systemTenantName, systemId);
    return dao.softDeleteTSystem(authenticatedUser, systemSeqId);
  }

  /**
   * Hard delete a system record given the system name.
   * Also remove artifacts from the Security Kernel
   * NOTE: This is public so test code can use it but it is not part of the public interface.
   *
   * @param authenticatedUser - principal user containing tenant and user info
   * @param systemId - name of system
   * @return Number of items deleted
   * @throws TapisException - for Tapis related exceptions
   * @throws TapisClientException - for Tapis related exceptions
   * @throws NotAuthorizedException - unauthorized
   */
  public int hardDeleteSystem(AuthenticatedUser authenticatedUser, String systemId)
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
    if (!dao.checkForTSystem(systemTenantName, systemId, true)) return 0;

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, null, null, null);

    // Remove SK artifacts
    removeSKArtifacts(authenticatedUser, systemId, op);

    // Delete the system
    return dao.hardDeleteTSystem(systemTenantName, systemId);
  }

  /**
   * Initialize the service:
   *   Check for Systems admin role. If not found create it
   */
  public void initService(String svcSiteId) throws TapisException, TapisClientException
  {
    siteId = svcSiteId;
//    // Get service admin tenant
//    String svcAdminTenant = RuntimeParameters.getInstance().getServiceAdminTenant();
//    if (StringUtils.isBlank(svcAdminTenant)) svcAdminTenant = SYSTEMS_DEFAULT_ADMIN_TENANT;
//    // Create user for SK client
//    // NOTE: getSKClient() does not require the jwt to be set in AuthenticatedUser but we keep it here as a reminder
//    //       that in general this may be the pattern to follow.
//    String svcJwt = serviceJWT.getAccessJWT(siteId);
//    AuthenticatedUser svcUser =
//        new AuthenticatedUser(SERVICE_NAME_SYSTEMS, svcAdminTenant, TapisThreadContext.AccountType.service.name(),
//                              null, SERVICE_NAME_SYSTEMS, svcAdminTenant, null, siteId, svcJwt);
//    // TODO: Revisit how to manage skClient instances.
//    // Get service admin tenant
//    String svcAdminTenant = TenantManager.getInstance().getSiteAdminTenantId(siteId);
//    if (StringUtils.isBlank(svcAdminTenant)) svcAdminTenant = SYSTEMS_DEFAULT_ADMIN_TENANT;
//    // Create user for SK client
//    // NOTE: getSKClient() does not require the jwt to be set in AuthenticatedUser but we keep it here as a reminder
//    //       that in general this may be the pattern to follow.
//    String svcJwt = serviceContext.getAccessJWT(svcAdminTenant, SERVICE_NAME_SYSTEMS);
//    AuthenticatedUser svcUser =
//        new AuthenticatedUser(SERVICE_NAME_SYSTEMS, svcAdminTenant, TapisThreadContext.AccountType.service.name(),
//                              null, SERVICE_NAME_SYSTEMS, svcAdminTenant, null, siteId, svcJwt);
//    // Use SK client to check for admin role and create it if necessary
//    var skClient = getSKClient(svcUser);
//    // Check for admin role, continue if error getting role.
//    // TODO: Move msgs to properties file
//    // TODO/TBD: Do we still need the special service admin role "SystemsAdmin" or should be use the tenant admin role?
//    SkRole adminRole = null;
//    try
//    {
//      adminRole = skClient.getRoleByName(svcAdminTenant, SYSTEMS_ADMIN_ROLE);
//    }
//    catch (TapisClientException e)
//    {
//      String msg = e.getTapisMessage();
//      // If we have a special message then log it
//      if (!StringUtils.isBlank(msg)) _log.error("Unable to get Admin Role. Caught TapisClientException: " + msg);
//      // If there is no message or the message is something other than "role does not exist" then log the exception.
//      // There may be a problem with SK but do not throw (i.e. fail) just because we cannot get the role at this point.
//      if (msg == null || !msg.startsWith("TAPIS_NOT_FOUND")) _log.error("Unable to get Admin Role. Caught Exception: " + e);
//    }
//    if (adminRole == null)
//    {
//      _log.info("Systems administrative role not found. Role name: " + SYSTEMS_ADMIN_ROLE);
//      skClient.createRole(svcAdminTenant, SYSTEMS_ADMIN_ROLE, SYSTEMS_ADMIN_DESCRIPTION);
//      _log.info("Systems administrative created. Role name: " + SYSTEMS_ADMIN_ROLE);
//    }
//    else
//    {
//      _log.info("Systems administrative role found. Role name: " + SYSTEMS_ADMIN_ROLE);
//    }
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
    if (dao.checkForTSystem(systemTenantName, systemId, false)) {
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
    if (!dao.checkForTSystem(systemTenantName, systemId, false)) return null;

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, null, null, null);
    // If flag is set to also require EXECUTE perm then make a special auth call
    if (requireExecPerm)
    {
      checkAuthUser(authenticatedUser, SystemOperation.execute, systemTenantName, authenticatedUser.getOboUser(),
                    systemId, null, null, null);
    }

    TSystem result = dao.getTSystem(systemTenantName, systemId);
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
   * @param sortBy - attribute and optional direction for sorting, e.g. sortBy=created(desc). Default direction is (asc)
   * @param startAfter - where to start when sorting, e.g. sortBy=id(asc)&startAfter=101 (may not be used with skip)
   * @return Count of TSystem objects
   * @throws TapisException - for Tapis related exceptions
   */
  @Override
  public int getSystemsTotalCount(AuthenticatedUser authenticatedUser, List<String> searchList,
                                  String sortBy, String sortDirection, String startAfter)
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

    // Get list of seqIDs of systems for which requester has READ permission.
    // This is either all systems (null) or a list of seqIDs based on roles.
    List<Integer> allowedSeqIDs = getAllowedSeqIDs(authenticatedUser, systemTenantName);

    // If none are allowed we know count is 0
    if (allowedSeqIDs != null && allowedSeqIDs.isEmpty()) return 0;

    // Count all allowed systems matching the search conditions
    return dao.getTSystemsCount(authenticatedUser.getTenantId(), verifiedSearchList, null, allowedSeqIDs,
                                sortBy, sortDirection, startAfter);
  }

  /**
   * Get all systems matching certain criteria and for which user has READ permission
   * @param authenticatedUser - principal user containing tenant and user info
   * @param searchList - optional list of conditions used for searching
   * @param limit - indicates maximum number of results to be included, -1 for unlimited
   * @param sortBy - attribute and optional direction for sorting, e.g. sortBy=created(desc). Default direction is (asc)
   * @param skip - number of results to skip (may not be used with startAfter)
   * @param startAfter - where to start when sorting, e.g. limit=10&sortBy=id(asc)&startAfter=101 (may not be used with skip)
   * @return List of TSystem objects
   * @throws TapisException - for Tapis related exceptions
   */
  @Override
  public List<TSystem> getSystems(AuthenticatedUser authenticatedUser, List<String> searchList, int limit,
                                  String sortBy, String sortDirection, int skip, String startAfter)
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

    // Get list of seqIDs of systems for which requester has READ permission.
    // This is either all systems (null) or a list of seqIDs based on roles.
    List<Integer> allowedSeqIDs = getAllowedSeqIDs(authenticatedUser, systemTenantName);

    // Get all allowed systems matching the search conditions
    List<TSystem> systems = dao.getTSystems(authenticatedUser.getTenantId(), verifiedSearchList, null, allowedSeqIDs,
                                            limit, sortBy, sortDirection, skip, startAfter);

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
   * @param sortBy - attribute and optional direction for sorting, e.g. sortBy=created(desc). Default direction is (asc)
   * @param skip - number of results to skip (may not be used with startAfter)
   * @param startAfter - where to start when sorting, e.g. limit=10&sortBy=id(asc)&startAfter=101 (may not be used with skip)
   * @return List of TSystem objects
   * @throws TapisException - for Tapis related exceptions
   */
  @Override
  public List<TSystem> getSystemsUsingSqlSearchStr(AuthenticatedUser authenticatedUser, String sqlSearchStr, int limit,
                                                   String sortBy, String sortDirection, int skip, String startAfter)
          throws TapisException, TapisClientException
  {
    // If search string is empty delegate to getSystems()
    if (StringUtils.isBlank(sqlSearchStr)) return getSystems(authenticatedUser, null, limit, sortBy, sortDirection, skip, startAfter);

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

    // Get list of seqIDs of systems for which requester has READ permission.
    // This is either all systems (null) or a list of seqIDs based on roles.
    List<Integer> allowedSeqIDs = getAllowedSeqIDs(authenticatedUser, systemTenantName);

    // Get all allowed systems matching the search conditions
    List<TSystem> systems = dao.getTSystems(authenticatedUser.getTenantId(), null, searchAST, allowedSeqIDs,
                                                          limit, sortBy, sortDirection, skip, startAfter);

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

    // Get list of seqIDs of systems for which requester has READ permission.
    // This is either all systems (null) or a list of seqIDs based on roles.
    List<Integer> allowedSeqIDs = getAllowedSeqIDs(authenticatedUser, systemTenantName);

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
    List<TSystem> systems = dao.getTSystemsSatisfyingConstraints(authenticatedUser.getTenantId(), matchAST,
                                                                 allowedSeqIDs);

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
    if (!dao.checkForTSystem(systemTenantName, systemId, false)) return null;

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
   * @param sortBy - attribute and optional direction for sorting, e.g. sortBy=created(desc). Default direction is (asc)
   * @param skip - number of results to skip (may not be used with startAfter)
   * @param startAfter - where to start when sorting, e.g. limit=10&sortBy=id(asc)&startAfter=101 (may not be used with skip)
   * @return List of TSystem objects
   * @throws TapisException - for Tapis related exceptions
   */
  @Override
  public List<SystemBasic> getSystemsBasic(AuthenticatedUser authenticatedUser, List<String> searchList, int limit,
                                           String sortBy, String sortDirection, int skip, String startAfter)
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

    // Get list of seqIDs of systems for which requester has READ permission.
    // This is either all systems (null) or a list of seqIDs based on roles.
    List<Integer> allowedSeqIDs = getAllowedSeqIDs(authenticatedUser, systemTenantName);

    // Get all allowed systems matching the search conditions
    return dao.getSystemsBasic(authenticatedUser.getTenantId(), verifiedSearchList, null, allowedSeqIDs,
                               limit, sortBy, sortDirection, skip, startAfter);
  }

  /**
   * Get all systems for which user has READ permission.
   * Use provided string containing a valid SQL where clause for the search.
   * @param authenticatedUser - principal user containing tenant and user info
   * @param sqlSearchStr - string containing a valid SQL where clause
   * @param limit - indicates maximum number of results to be included, -1 for unlimited
   * @param sortBy - attribute and optional direction for sorting, e.g. sortBy=created(desc). Default direction is (asc)
   * @param skip - number of results to skip (may not be used with startAfter)
   * @param startAfter - where to start when sorting, e.g. limit=10&sortBy=id(asc)&startAfter=101 (may not be used with skip)
   * @return List of TSystem objects
   * @throws TapisException - for Tapis related exceptions
   */
  @Override
  public List<SystemBasic> getSystemsBasicUsingSqlSearchStr(AuthenticatedUser authenticatedUser, String sqlSearchStr, int limit,
                                                            String sortBy, String sortDirection, int skip, String startAfter)
          throws TapisException, TapisClientException
  {
    // If search string is empty delegate to getSystemsBasic()
    if (StringUtils.isBlank(sqlSearchStr)) return getSystemsBasic(authenticatedUser, null, limit, sortBy, sortDirection, skip, startAfter);

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

    // Get list of seqIDs of systems for which requester has READ permission.
    // This is either all systems (null) or a list of seqIDs based on roles.
    List<Integer> allowedSeqIDs = getAllowedSeqIDs(authenticatedUser, systemTenantName);

    // Get all allowed systems matching the search conditions
    return dao.getSystemsBasic(authenticatedUser.getTenantId(), null, searchAST, allowedSeqIDs,
                                                    limit, sortBy, sortDirection, skip, startAfter);
  }

  /**
   * Get list of system names
   * @param authenticatedUser - principal user containing tenant and user info
   * @return - list of systems
   * @throws TapisException - for Tapis related exceptions
   */
  @Override
  public List<String> getSystemNames(AuthenticatedUser authenticatedUser) throws TapisException
  {
    SystemOperation op = SystemOperation.read;
    if (authenticatedUser == null) throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT_AUTHUSR"));
    // Get all system names
    List<String> systemIds = dao.getTSystemNames(authenticatedUser.getTenantId());
    var allowedNames = new ArrayList<String>();
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
    if (!dao.checkForTSystem(systemTenantName, systemId, false)) return null;

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, null, null, null);

    return dao.getTSystemOwner(authenticatedUser.getTenantId(), systemId);
  }

  // -----------------------------------------------------------------------
  // --------------------------- Permissions -------------------------------
  // -----------------------------------------------------------------------

  /**
   * Grant permissions to a user for a system
   * NOTE: This only impacts the default user role
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
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType())) systemTenantName = authenticatedUser.getOboTenantId();

    // If system does not exist or has been soft deleted then throw an exception
    if (!dao.checkForTSystem(systemTenantName, systemId, false))
      throw new TapisException(LibUtils.getMsgAuth(NOT_FOUND, authenticatedUser, systemId));

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, null, null, null);

    int seqId = dao.getTSystemSeqId(systemTenantName, systemId);

    // Extract various names for convenience
    String tenantName = authenticatedUser.getTenantId();

    // Check inputs. If anything null or empty throw an exception
    if (StringUtils.isBlank(tenantName) || permissions == null || permissions.isEmpty())
    {
      throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT"));
    }

    // Create a set of individual permSpec entries based on the list passed in
    Set<String> permSpecSet = getPermSpecSet(systemTenantName, systemId, permissions);

    // Get the Security Kernel client
    var skClient = getSKClient(authenticatedUser);

    // TODO: Mutliple txns. Need to handle failure
    // TODO: Use try/catch to rollback in case of failure.

    // Assign perms and roles to user.
    try
    {
      // Grant permission roles as appropriate, RoleR
      String roleNameR = TSystem.ROLE_READ_PREFIX + seqId;
      for (Permission perm : permissions)
      {
        if (perm.equals(Permission.READ)) skClient.grantUserRole(systemTenantName, userName, roleNameR);
      }
      // Assign perms to user. SK creates a default role for the user
      for (String permSpec : permSpecSet)
      {
        skClient.grantUserPermission(systemTenantName, userName, permSpec);
      }
    }
    // If tapis client exception then log error and convert to TapisException
    catch (TapisClientException tce)
    {
      _log.error(tce.toString());
      throw new TapisException(LibUtils.getMsgAuth("SYSLIB_PERM_SK_ERROR", authenticatedUser, systemId, op.name()), tce);
    }
    // Construct Json string representing the update
    String updateJsonStr = TapisGsonUtils.getGson().toJson(permissions);
    // Create a record of the update
    dao.addUpdateRecord(authenticatedUser, seqId, op, updateJsonStr, updateText);
  }

  /**
   * Revoke permissions from a user for a system
   * NOTE: This only impacts the default user role
   * @param authenticatedUser - principal user containing tenant and user info
   * @param systemId - name of system
   * @param userName - Target user for operation
   * @param permissions - list of permissions to be revoked
   * @param updateText - Client provided text used to create the permissions list. Saved in update record.
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
    if (!dao.checkForTSystem(systemTenantName, systemId, false)) return 0;

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, null, null, null);

    // Retrieve the sequence Id. Used to add an update record.
    int seqId = dao.getTSystemSeqId(systemTenantName, systemId);

    // Extract various names for convenience
    String tenantName = authenticatedUser.getTenantId();

    // Check inputs. If anything null or empty throw an exception
    if (StringUtils.isBlank(tenantName) || permissions == null || permissions.isEmpty())
    {
      throw new IllegalArgumentException(LibUtils.getMsg("SYSLIB_NULL_INPUT"));
    }

    var skClient = getSKClient(authenticatedUser);
    int changeCount;

    // TODO: Mutliple txns. Need to handle failure
    // TODO: Use try/catch to rollback in case of failure.

    try {
      // Revoke permission roles as appropriate, RoleR
      String roleNameR = TSystem.ROLE_READ_PREFIX + seqId;
      for (Permission perm : permissions) {
        if (perm.equals(Permission.READ)) skClient.revokeUserRole(systemTenantName, userName, roleNameR);
      }
      changeCount = revokePermissions(skClient, systemTenantName, systemId, userName, permissions);
    }
    catch (TapisClientException tce)
    {
      // If tapis client exception then log error and convert to TapisException
      _log.error(tce.toString());
      throw new TapisException(LibUtils.getMsgAuth("SYSLIB_PERM_SK_ERROR", authenticatedUser, systemId, op.name()), tce);
    }
    // Construct Json string representing the update
    String updateJsonStr = TapisGsonUtils.getGson().toJson(permissions);
    // Create a record of the update
    dao.addUpdateRecord(authenticatedUser, seqId, op, updateJsonStr, updateText);
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
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType())) systemTenantName = authenticatedUser.getOboTenantId();

    // If system does not exist or has been soft deleted then return null
    if (!dao.checkForTSystem(systemTenantName, systemId, false)) return null;

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, null, userName, null);

    // Use Security Kernel client to check for each permission in the enum list
    var userPerms = new HashSet<Permission>();
    var skClient = getSKClient(authenticatedUser);
    for (Permission perm : Permission.values())
    {
      String permSpec = PERM_SPEC_PREFIX + systemTenantName + ":" + perm.name() + ":" + systemId;
      try
      {
        Boolean isAuthorized = skClient.isPermitted(systemTenantName, userName, permSpec);
        if (Boolean.TRUE.equals(isAuthorized)) userPerms.add(perm);
      }
      // If tapis client exception then log error and convert to TapisException
      catch (TapisClientException tce)
      {
        _log.error(tce.toString());
        throw new TapisException(LibUtils.getMsgAuth("SYSLIB_PERM_SK_ERROR", authenticatedUser, systemId, op.name()), tce);
      }
    }
    return userPerms;
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
    if (!dao.checkForTSystem(systemTenantName, systemId, false))
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
    // Get the Security Kernel client
    var skClient = getSKClient(authenticatedUser);

    // TODO: Mutliple txns. Need to handle failure
    // TODO: Use try/catch to rollback in case of failure.
    // Create credential
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
    int seqId = dao.getTSystemSeqId(systemTenantName, systemId);
    dao.addUpdateRecord(authenticatedUser, seqId, op, updateJsonStr, updateText);
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
    if (!dao.checkForTSystem(systemTenantName, systemId, false)) return changeCount;

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
    var skClient = getSKClient(authenticatedUser);

    // TODO: Mutliple txns. Need to handle failure
    // TODO: Use try/catch to rollback in case of failure.
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
    int seqId = dao.getTSystemSeqId(systemTenantName, systemId);
    dao.addUpdateRecord(authenticatedUser, seqId, op, updateJsonStr, null);
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
    if (!dao.checkForTSystem(systemTenantName, systemId, false)) return null;

    // ------------------------- Check service level authorization -------------------------
    checkAuth(authenticatedUser, op, systemId, null, userName, null);

    // If authnMethod not passed in fill in with default from system
    if (authnMethod == null)
    {
      TSystem sys = dao.getTSystem(systemTenantName, systemId);
      if (sys == null)  throw new TapisException(LibUtils.getMsgAuth(NOT_FOUND, authenticatedUser, systemId));
      authnMethod = sys.getDefaultAuthnMethod();
    }

    Credential credential = null;
    try
    {
      // Get the Security Kernel client
      var skClient = getSKClient(authenticatedUser);
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
      // TODO/TBD: why not pass in tenant and apiUser here?
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
              null); //dataMap.get(CERT) TODO: how to get ssh certificate
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
   *  TODO: revisit this. There is now ServiceContext/ServiceClients which will probably help.
   * Get Security Kernel client associated with specified tenant
   * @param authenticatedUser - name of tenant
   * @return SK client
   * @throws TapisException - for Tapis related exceptions
   */
  private SKClient getSKClient(AuthenticatedUser authenticatedUser) throws TapisException
  {
    // Use TenantManager to get tenant info. Needed for tokens and SK base URLs.
    Tenant userTenant = TenantManager.getInstance().getTenant(authenticatedUser.getTenantId());

    // Update SKClient on the fly. If this becomes a bottleneck we can add a cache.
    // Get Security Kernel URL from the env or the tenants service. Env value has precedence.
    //    String skURL = "https://dev.develop.tapis.io/v3";
    String skURL = RuntimeParameters.getInstance().getSkSvcURL();
    if (StringUtils.isBlank(skURL)) skURL = userTenant.getSecurityKernel();
    if (StringUtils.isBlank(skURL)) throw new TapisException(LibUtils.getMsgAuth("SYSLIB_CREATE_SK_URL_ERROR", authenticatedUser));
    // TODO remove strip-off of everything after /v3 once tenant is updated or we do something different for base URL in auto-generated clients
    // Strip off everything after the /v3 so we have a valid SK base URL
    skURL = skURL.substring(0, skURL.indexOf("/v3") + 3);

    skClient.setBasePath(skURL);
    skClient.addDefaultHeader(HDR_TAPIS_TOKEN, serviceContext.getServiceJWT().getAccessJWT(siteId));

    // For service jwt pass along oboTenant and oboUser in OBO headers
    // For user jwt use authenticated user name and tenant in OBO headers
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType()))
    {
      skClient.addDefaultHeader(HDR_TAPIS_TENANT, authenticatedUser.getOboTenantId());
      skClient.addDefaultHeader(HDR_TAPIS_USER, authenticatedUser.getOboUser());
    }
    else
    {
      skClient.addDefaultHeader(HDR_TAPIS_TENANT, authenticatedUser.getTenantId());
      skClient.addDefaultHeader(HDR_TAPIS_USER, authenticatedUser.getName());
    }
    return skClient;
  }

  /**
   * Resolve variables for TSystem attributes
   * @param system - the TSystem to process
   */
  private static void resolveVariables(TSystem system, String oboUser)
  {
    // Resolve owner if necessary. If empty or "${apiUserId}" then fill in oboUser.
    // Note that for a user request oboUser and apiUserId are the same and for a service request we want oboUser here.
    String owner = system.getOwner();
    if (StringUtils.isBlank(owner) || owner.equalsIgnoreCase(APIUSERID_VAR)) owner = oboUser;
    system.setOwner(owner);

    // Perform variable substitutions that happen at create time: bucketName, rootDir, jobWorkingDir
    // NOTE: effectiveUserId is not processed. Var reference is retained and substitution done as needed when system is retrieved.
    //    ALL_VARS = {APIUSERID_VAR, OWNER_VAR, TENANT_VAR};
    String[] allVarSubstitutions = {oboUser, owner, system.getTenant()};
    system.setBucketName(StringUtils.replaceEach(system.getBucketName(), ALL_VARS, allVarSubstitutions));
    system.setRootDir(StringUtils.replaceEach(system.getRootDir(), ALL_VARS, allVarSubstitutions));
    system.setJobWorkingDir(StringUtils.replaceEach(system.getJobWorkingDir(), ALL_VARS, allVarSubstitutions));
  }

  /**
   * Check constraints on TSystem attributes.
   * Notes must be json
   * effectiveUserId is restricted.
   * If transfer mechanism S3 is supported then bucketName must be set.
   * @param system - the TSystem to check
   * @throws IllegalStateException - if any constraints are violated
   */
  private static void validateTSystem(AuthenticatedUser authenticatedUser, TSystem system) throws IllegalStateException
  {
    String msg;
    var errMessages = new ArrayList<String>();
    // Check for valid effectiveUserId
    // For CERT authn the effectiveUserId cannot be static string other than owner
    String effectiveUserId = system.getEffectiveUserId();
    if (system.getDefaultAuthnMethod().equals(AuthnMethod.CERT) &&
        !effectiveUserId.equals(TSystem.APIUSERID_VAR) &&
        !effectiveUserId.equals(TSystem.OWNER_VAR) &&
        !StringUtils.isBlank(system.getOwner()) &&
        !effectiveUserId.equals(system.getOwner()))
    {
      // For CERT authn the effectiveUserId cannot be static string other than owner
      msg = LibUtils.getMsg("SYSLIB_INVALID_EFFECTIVEUSERID_INPUT");
      errMessages.add(msg);
    }
    if (system.getTransferMethods() != null && system.getTransferMethods().contains(TransferMethod.S3) &&
             StringUtils.isBlank(system.getBucketName()))
    {
      // For S3 support bucketName must be set
      msg = LibUtils.getMsg("SYSLIB_S3_NOBUCKET_INPUT");
      errMessages.add(msg);
    }
    if (system.getAuthnCredential() != null && effectiveUserId.equals(TSystem.APIUSERID_VAR))
    {
      // If effectiveUserId is dynamic then providing credentials is disallowed
      msg = LibUtils.getMsg("SYSLIB_CRED_DISALLOWED_INPUT");
      errMessages.add(msg);
    }
    // If validation failed throw an exception
    if (!errMessages.isEmpty())
    {
      // Construct message reporting all errors
      String allErrors = getListOfErrors(authenticatedUser, system.getId(), errMessages);
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
    return PERM_SPEC_PREFIX + tenantName + ":" + perm.name().toUpperCase() + ":" + systemId;
  }

  /**
   * Create a permSpec for all permissions
   * @return - permSpec entry for all permissions
   */
  private static String getPermSpecAllStr(String tenantName, String systemId)
  {
    return PERM_SPEC_PREFIX + tenantName + ":*:" + systemId;
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
   *  Create - must be owner or have admin role
   *  Read - must be owner or have admin role or have READ or MODIFY permission or be in list of allowed services
   *  Delete - must be owner or have admin role
   *  Modify - must be owner or have admin role or have MODIFY permission
   *  Execute - must be owner or have admin role or have EXECUTE permission
   *  ChangeOwner - must be owner or have admin role
   *  GrantPerm -  must be owner or have admin role
   *  RevokePerm -  must be owner or have admin role or apiUserId=targetUser and meet certain criteria (allowUserRevokePerm)
   *  SetCred -  must be owner or have admin role or apiUserId=targetUser and meet certain criteria (allowUserCredOp)
   *  RemoveCred -  must be owner or have admin role or apiUserId=targetUser and meet certain criteria (allowUserCredOp)
   *  GetCred -  must be a service in the list of allowed services
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
    if (StringUtils.isBlank(owner)) owner = dao.getTSystemOwner(tenantName, systemId);
    if (StringUtils.isBlank(owner)) {
      String msg = LibUtils.getMsgAuth("SYSLIB_AUTH_NO_OWNER", authenticatedUser, systemId, operation.name());
      _log.error(msg);
      throw new NotAuthorizedException(msg);
    }
    switch(operation) {
      case create:
      case softDelete:
      case changeOwner:
      case grantPerms:
        if (owner.equals(userName) || hasAdminRole(authenticatedUser, tenantName, userName)) return;
        break;
      case hardDelete:
        if (hasAdminRole(authenticatedUser, tenantName, userName)) return;
        break;
      case read:
      case getPerms:
        if (owner.equals(userName) || hasAdminRole(authenticatedUser, tenantName, userName) ||
              isPermittedAny(authenticatedUser, tenantName, userName, systemId, READMODIFY_PERMS)) return;
        break;
      case modify:
        if (owner.equals(userName) || hasAdminRole(authenticatedUser, tenantName, userName) ||
                isPermitted(authenticatedUser, tenantName, userName, systemId, Permission.MODIFY)) return;
        break;
      case execute:
        if (owner.equals(userName) || hasAdminRole(authenticatedUser, tenantName, userName) ||
                isPermitted(authenticatedUser, tenantName, userName, systemId, Permission.EXECUTE)) return;
        break;
      case revokePerms:
        if (owner.equals(userName) || hasAdminRole(authenticatedUser, tenantName, userName) ||
                (userName.equals(targetUser) &&
                        allowUserRevokePerm(authenticatedUser, tenantName, userName, systemId, perms))) return;
        break;
      case setCred:
      case removeCred:
        if (owner.equals(userName) || hasAdminRole(authenticatedUser, tenantName, userName) ||
                (userName.equals(targetUser) &&
                        allowUserCredOp(authenticatedUser, systemId, operation))) return;
        break;
    }
    // Not authorized, throw an exception
    String msg = LibUtils.getMsgAuth("SYSLIB_UNAUTH", authenticatedUser, systemId, operation.name());
    throw new NotAuthorizedException(msg);
  }

  /**
   * Determine all systems that a user is allowed to see.
   * If all systems return null else return list of seqIDs
   * An empty list indicates no systems allowed.
   */
  private List<Integer> getAllowedSeqIDs(AuthenticatedUser authenticatedUser, String systemTenantName)
          throws TapisException, TapisClientException
  {
    // If requester is a service or an admin then all systems allowed
    if (TapisThreadContext.AccountType.service.name().equals(authenticatedUser.getAccountType()) ||
        hasAdminRole(authenticatedUser, null, null)) return null;
    var seqIDs = new ArrayList<Integer>();
    // Get roles for user and extract system seqIDs
    List<String> userRoles = getSKClient(authenticatedUser).getUserRoles(systemTenantName, authenticatedUser.getName());
    // Find roles of the form Systems_R_<id> and generate a list of seqIDs
    for (String role: userRoles)
    {
      if (role.startsWith(TSystem.ROLE_READ_PREFIX))
      {
        String seqIdStr = role.substring(role.indexOf(TSystem.ROLE_READ_PREFIX) + TSystem.ROLE_READ_PREFIX.length());
        // If id part of string is not integer then ignore this role.
        try {
          Integer seqId = Integer.parseInt(seqIdStr);
          seqIDs.add(seqId);
        } catch (NumberFormatException e) {};
      }
    }
    return seqIDs;
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
    // TODO: Remove this
    if ("testuser9".equalsIgnoreCase(userName)) return true;
    var skClient = getSKClient(authenticatedUser);
    return skClient.isAdmin(tenantName, userName);
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
    var skClient = getSKClient(authenticatedUser);
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
    var skClient = getSKClient(authenticatedUser);
    var permSpecs = new ArrayList<String>();
    for (Permission perm : perms) {
      permSpecs.add(getPermSpecStr(tenantName, systemId, perm));
    }
    return skClient.isPermittedAny(tenantName, userName, permSpecs.toArray(new String[0]));
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
    String effectiveUserId = dao.getTSystemEffectiveUserId(authenticatedUser.getTenantId(), systemId);
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
    // TODO what about ssh certificate? Nothing to do here?
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
      //TODO How to better check and return 0 if credential not there?
      _log.warn(e.getMessage());
      skMetaSecret = null;
    }
    if (skMetaSecret == null) return changeCount;

    // Construct basic SK secret parameters
//    var sParms = new SKSecretMetaParms(SecretType.System).setSecretName(TOP_LEVEL_SECRET_NAME).setSysId(systemId).setSysUser(userName);
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
    // TODO/TBD: This currently throws a "not found" exception. How to handle it? Have SK make it a no-op? Catch exception for each call?
//      sParms.setKeyType(KeyType.cert);
//      skClient.destroySecret(tenantName, apiUserId, sParms);
    // TODO/TBD Also clean up secret metadata

    // If anything destroyed we consider it the removal of a single credential
    if (changeCount > 0) changeCount = 1;
    return changeCount;
  }

  /**
   * Remove all SK artifacts associated with a System: user credentials, user permissions, System role
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

    // Fetch the system. If system not found then return
    TSystem system = dao.getTSystem(systemTenantName, systemId, true);

    // Resolve effectiveUserId if necessary
    String effectiveUserId = system.getEffectiveUserId();
    effectiveUserId = resolveEffectiveUserId(effectiveUserId, system.getOwner(), apiUserId);

    var skClient = getSKClient(authenticatedUser);
    // TODO: Remove all credentials associated with the system.
    // TODO: Have SK do this in one operation?
    // TODO: How to remove for users other than effectiveUserId?
    // Remove credentials in Security Kernel if cred provided and effectiveUser is static
    if (!effectiveUserId.equals(APIUSERID_VAR)) {
      // Use private internal method instead of public API to skip auth and other checks not needed here.
// TODO/TBD: Do we need to convert this from TCE to TE?
//      try {
//        deleteCredential(skClient, tenantName, apiUserId, systemTenantName, system.getId(), effectiveUserId);
//      }
//      // If tapis client exception then log error and convert to TapisException
//      catch (TapisClientException tce)
//      {
//        _log.error(tce.toString());
//        throw new TapisException(LibUtils.getMsgAuth("SYSLIB_CRED_SK_ERROR", authenticatedUser, systemId, op.name()), tce);
//      }
      deleteCredential(skClient, tenantName, apiUserId, systemTenantName, system.getId(), effectiveUserId);
    }

    // TODO/TBD: How to make sure all perms for a system are removed?
    // TODO: See if it makes sense to have a SK method to do this in one operation
    // Use Security Kernel client to find all users with perms associated with the system.
    String permSpec = PERM_SPEC_PREFIX + systemTenantName + ":%:" + systemId;
    var userNames = skClient.getUsersWithPermission(systemTenantName, permSpec);
    // Revoke all perms for all users
    for (String userName : userNames) {
      revokePermissions(skClient, systemTenantName, systemId, userName, ALL_PERMS);
    }
    // If role is present then remove role assignments and roles
    // TODO: Ask SK to either provide checkForRole() or return null if role does not exist.
    String roleNameR = TSystem.ROLE_READ_PREFIX + system.getSeqId();
    SkRole role = null;
    try
    {
      role = skClient.getRoleByName(systemTenantName, roleNameR);
    }
    catch (TapisClientException tce)
    {
      if (!tce.getTapisMessage().startsWith("TAPIS_NOT_FOUND")) throw tce;
    }
    if (role != null)
    {
      // Remove role assignments for owner and effective user
      skClient.revokeUserRole(systemTenantName, system.getOwner(), roleNameR);
      skClient.revokeUserRole(systemTenantName, effectiveUserId, roleNameR);
      // Remove role assignments for other users
      userNames = skClient.getUsersWithRole(systemTenantName, roleNameR);
      for (String userName : userNames) skClient.revokeUserRole(systemTenantName, userName, roleNameR);
      // Remove the role
      skClient.deleteRoleByName(systemTenantName, roleNameR);
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
   *   description, host, enabled, effectiveUserId, defaultAuthnMethod, transferMethods,
   *   port, useProxy, proxyHost, proxyPort, jobCapabilities, tags, notes.
   * The only attribute that can be reset to default is effectiveUserId. It is reset when
   *   a blank string is passed in.
   */
  private TSystem createPatchedTSystem(TSystem o, PatchSystem p)
  {
    TSystem p1 = new TSystem(o);
    if (p.getDescription() != null) p1.setDescription(p.getDescription());
    if (p.getHost() != null) p1.setHost(p.getHost());
    if (p.isEnabled() != null) p1.setEnabled(p.isEnabled());
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
    if (p.getJobCapabilities() != null) p1.setJobCapabilities(p.getJobCapabilities());
    if (p.getTags() != null) p1.setTags(p.getTags());
    if (p.getNotes() != null) p1.setNotes(p.getNotes());
    return p1;
  }
}
