package edu.utexas.tacc.tapis.systems.service;

import com.google.gson.JsonObject;
import edu.utexas.tacc.tapis.shared.exceptions.TapisException;
import edu.utexas.tacc.tapis.shared.security.ServiceClients;
import edu.utexas.tacc.tapis.shared.security.ServiceContext;
import edu.utexas.tacc.tapis.shared.security.TenantManager;
import edu.utexas.tacc.tapis.shared.threadlocal.TapisThreadContext;
import edu.utexas.tacc.tapis.sharedapi.security.AuthenticatedUser;
import edu.utexas.tacc.tapis.systems.IntegrationUtils;
import edu.utexas.tacc.tapis.systems.config.RuntimeParameters;
import edu.utexas.tacc.tapis.systems.dao.SystemsDao;
import edu.utexas.tacc.tapis.systems.dao.SystemsDaoImpl;
import edu.utexas.tacc.tapis.systems.model.Capability;
import edu.utexas.tacc.tapis.systems.model.Credential;
import edu.utexas.tacc.tapis.systems.model.JobRuntime;
import edu.utexas.tacc.tapis.systems.model.LogicalQueue;
import edu.utexas.tacc.tapis.systems.model.PatchSystem;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import edu.utexas.tacc.tapis.systems.model.TSystem;
import edu.utexas.tacc.tapis.systems.model.TSystem.AuthnMethod;
import edu.utexas.tacc.tapis.systems.model.TSystem.TransferMethod;
import edu.utexas.tacc.tapis.systems.model.TSystem.Permission;

import javax.ws.rs.NotAuthorizedException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static edu.utexas.tacc.tapis.systems.IntegrationUtils.*;

/**
 * Test the SystemsService implementation class against a DB running locally
 * Note that this test has the following dependencies running locally or in dev
 *    Database - typically local
 *    Tenants service - typically dev
 *    Tokens service - typically dev and obtained from tenants service
 *    Security Kernel service - typically dev and obtained from tenants service
 *
 */
@Test(groups={"integration"})
public class SystemsServiceTest
{
  private SystemsService svc;
  private SystemsServiceImpl svcImpl;
  private AuthenticatedUser authenticatedOwner1, authenticatedTestUser0, authenticatedTestUser1, authenticatedTestUser2,
          authenticatedTestUser3, authenticatedTestUser4, authenticatedAdminUser, authenticatedSystemsSvc,
          authenticatedFilesSvcOwner1, authenticatedFilesSvcTestUser3, authenticatedFilesSvcTestUser4;

  // Create test system definitions in memory
  int numSystems = 23;
  TSystem[] systems = IntegrationUtils.makeSystems(numSystems, "Svc");

  @BeforeSuite
  public void setUp() throws Exception
  {
    System.out.println("Executing BeforeSuite setup method: " + SystemsServiceTest.class.getSimpleName());
    // Setup for HK2 dependency injection
    ServiceLocator locator = ServiceLocatorUtilities.createAndPopulateServiceLocator();
    ServiceLocatorUtilities.bind(locator, new AbstractBinder() {
      @Override
      protected void configure() {
        bind(SystemsServiceImpl.class).to(SystemsService.class);
        bind(SystemsServiceImpl.class).to(SystemsServiceImpl.class);
        bind(SystemsDaoImpl.class).to(SystemsDao.class);
        bindFactory(ServiceContextFactory.class).to(ServiceContext.class);
        bindFactory(ServiceClientsFactory.class).to(ServiceClients.class);
      }
    });
    locator.inject(this);

    // Initialize TenantManager and services
    String url = RuntimeParameters.getInstance().getTenantsSvcURL();
    TenantManager.getInstance(url).getTenants();

    // Initialize services
    svc = locator.getService(SystemsService.class);
    svcImpl = locator.getService(SystemsServiceImpl.class);
    svcImpl.initService(siteId, adminTenantName, RuntimeParameters.getInstance().getServicePassword());

    // Initialize authenticated user and service
    authenticatedAdminUser = new AuthenticatedUser(adminUser, tenantName, TapisThreadContext.AccountType.user.name(),
                                                    null, adminUser, tenantName, null, null, null);
    authenticatedOwner1 = new AuthenticatedUser(owner1, tenantName, TapisThreadContext.AccountType.user.name(),
                                                null, owner1, tenantName, null, null, null);
    authenticatedTestUser0 = new AuthenticatedUser(testUser0, tenantName, TapisThreadContext.AccountType.user.name(),
                                                   null, testUser0, tenantName, null, null, null);
    authenticatedTestUser1 = new AuthenticatedUser(testUser1, tenantName, TapisThreadContext.AccountType.user.name(),
                                                   null, testUser1, tenantName, null, null, null);
    authenticatedTestUser2 = new AuthenticatedUser(testUser2, tenantName, TapisThreadContext.AccountType.user.name(),
                                                   null, testUser2, tenantName, null, null, null);
    authenticatedTestUser3 = new AuthenticatedUser(testUser3, tenantName, TapisThreadContext.AccountType.user.name(),
                                                   null, testUser3, tenantName, null, null, null);
    authenticatedTestUser4 = new AuthenticatedUser(testUser4, tenantName, TapisThreadContext.AccountType.user.name(),
                                                   null, testUser4, tenantName, null, null, null);
    authenticatedSystemsSvc = new AuthenticatedUser(svcName, adminTenantName, TapisThreadContext.AccountType.service.name(),
                                                    null, svcName, adminTenantName, null, null, null);
    authenticatedFilesSvcOwner1 = new AuthenticatedUser(filesSvcName, adminTenantName, TapisThreadContext.AccountType.service.name(),
                                                   null, owner1, tenantName, null, null, null);
    authenticatedFilesSvcTestUser3 = new AuthenticatedUser(filesSvcName, adminTenantName, TapisThreadContext.AccountType.service.name(),
                                                   null, testUser3, tenantName, null, null, null);
    authenticatedFilesSvcTestUser4 = new AuthenticatedUser(filesSvcName, adminTenantName, TapisThreadContext.AccountType.service.name(),
                                                   null, testUser4, tenantName, null, null, null);

    // Cleanup anything leftover from previous failed run
    tearDown();

    // Create DTN systems for other systems to reference. Otherwise some system definitions are not valid.
    svc.createSystem(authenticatedOwner1, dtnSystem1, scrubbedJson);
    svc.createSystem(authenticatedOwner1, dtnSystem2, scrubbedJson);
  }

  @AfterSuite
  public void tearDown() throws Exception
  {
    System.out.println("Executing AfterSuite teardown for " + SystemsServiceTest.class.getSimpleName());
    // Remove non-owner permissions granted during the tests
    try { svc.revokeUserPermissions(authenticatedOwner1, systems[9].getId(), testUser3, testPermsREADMODIFY, scrubbedJson); }
    catch (Exception e) { }
    try { svc.revokeUserPermissions(authenticatedOwner1, systems[12].getId(), testUser3, testPermsREADMODIFY, scrubbedJson); }
    catch (Exception e) { }
    try { svc.revokeUserPermissions(authenticatedOwner1, systems[12].getId(), testUser2, testPermsREADMODIFY, scrubbedJson); }
    catch (Exception e) { }
    try { svc.revokeUserPermissions(authenticatedOwner1, systems[14].getId(), testUser3, testPermsREADMODIFY, scrubbedJson); }
    catch (Exception e) { }
    try { svc.revokeUserPermissions(authenticatedOwner1, systems[14].getId(), testUser2, testPermsREADMODIFY, scrubbedJson); }
    catch (Exception e) { }

    //Remove all objects created by tests
    for (int i = 0; i < numSystems; i++)
    {
      svcImpl.hardDeleteSystem(authenticatedAdminUser, systems[i].getId());
    }
    svcImpl.hardDeleteSystem(authenticatedAdminUser, dtnSystem2.getId());
    svcImpl.hardDeleteSystem(authenticatedAdminUser, dtnSystem1.getId());

    TSystem tmpSys = svc.getSystem(authenticatedAdminUser, systems[0].getId(), false, null, false);
    Assert.assertNull(tmpSys, "System not deleted. System name: " + systems[0].getId());
  }

  @Test
  public void testCreateSystem() throws Exception
  {
    TSystem sys0 = systems[0];
    svc.createSystem(authenticatedOwner1, sys0, scrubbedJson);
  }

  // Create a system using minimal attributes:
  //   id, systemType, host, defaultAuthnMethod, canExec
  @Test
  public void testCreateSystemMinimal() throws Exception
  {
    TSystem sys0 = makeMinimalSystem(systems[11]);
    svc.createSystem(authenticatedOwner1, sys0, scrubbedJson);
  }

  // Test retrieving a system including default authn method
  //   and test retrieving for specified authn method.
  @Test
  public void testGetSystem() throws Exception
  {
    TSystem sys0 = systems[1];
    sys0.setJobCapabilities(capList1);
    Credential cred0 = new Credential("fakePassword", "fakePrivateKey", "fakePublicKey",
            "fakeAccessKey", "fakeAccessSecret", "fakeCert");
    sys0.setAuthnCredential(cred0);
    svc.createSystem(authenticatedOwner1, sys0, scrubbedJson);
    // Retrieve system as owner, without and with requireExecPerm
    TSystem tmpSys = svc.getSystem(authenticatedOwner1, sys0.getId(), false, null, false);
    checkCommonSysAttrs(sys0, tmpSys);
    tmpSys = svc.getSystem(authenticatedOwner1, sys0.getId(), false, null, true);
    checkCommonSysAttrs(sys0, tmpSys);
    // Retrieve the system including the credential using the default authn method defined for the system
    // Use files service AuthenticatedUser since only certain services can retrieve the cred.
    tmpSys = svc.getSystem(authenticatedFilesSvcOwner1, sys0.getId(), true, null, false);
    checkCommonSysAttrs(sys0, tmpSys);
    // Verify credentials. Only cred for default authnMethod is returned. In this case PKI_KEYS.
    Credential cred = tmpSys.getAuthnCredential();
    Assert.assertNotNull(cred, "AuthnCredential should not be null");
    Assert.assertEquals(cred.getPrivateKey(), cred0.getPrivateKey());
    Assert.assertEquals(cred.getPublicKey(), cred0.getPublicKey());
    Assert.assertNull(cred.getPassword(), "AuthnCredential password should be null");
    Assert.assertNull(cred.getAccessKey(), "AuthnCredential access key should be null");
    Assert.assertNull(cred.getAccessSecret(), "AuthnCredential access secret should be null");
    Assert.assertNull(cred.getCertificate(), "AuthnCredential certificate should be null");

    // Test retrieval using specified authn method
    tmpSys = svc.getSystem(authenticatedFilesSvcOwner1, sys0.getId(), true, AuthnMethod.PASSWORD, false);
    System.out.println("Found item: " + sys0.getId());
    // Verify credentials. Only cred for default authnMethod is returned. In this case PASSWORD.
    cred = tmpSys.getAuthnCredential();
    Assert.assertNotNull(cred, "AuthnCredential should not be null");
    Assert.assertEquals(cred.getPassword(), cred0.getPassword());
    Assert.assertNull(cred.getPrivateKey(), "AuthnCredential private key should be null");
    Assert.assertNull(cred.getPublicKey(), "AuthnCredential public key should be null");
    Assert.assertNull(cred.getAccessKey(), "AuthnCredential access key should be null");
    Assert.assertNull(cred.getAccessSecret(), "AuthnCredential access secret should be null");
    Assert.assertNull(cred.getCertificate(), "AuthnCredential certificate should be null");
  }

  // Test updating a system
  // Both update of all possible attributes and only some attributes
  @Test
  public void testUpdateSystem() throws Exception
  {
    TSystem sys0 = systems[13];
    String systemId = sys0.getId();
    sys0.setJobRuntimes(runtimeList1);
    sys0.setBatchLogicalQueues(logicalQueueList1);
    sys0.setJobCapabilities(capList1);
    String createText = "{\"testUpdate\": \"0-create1\"}";
    svc.createSystem(authenticatedOwner1, sys0, createText);

    // Create patchSystem where all updatable attributes are changed
    String patch1Text = "{\"testUpdate\": \"1-patch1\"}";
    PatchSystem patchSystemFull = IntegrationUtils.makePatchSystemFull();
    patchSystemFull.setTenant(tenantName);
    patchSystemFull.setId(systemId);

    // Update using patchSys
    svc.updateSystem(authenticatedOwner1, patchSystemFull, patch1Text);
    TSystem tmpSysFull = svc.getSystem(authenticatedOwner1, sys0.getId(), false, null, false);

    // Update original definition with patched values so we can use the checkCommon method.
    sys0.setDescription(description2);
    sys0.setHost(hostname2);
    sys0.setEffectiveUserId(effectiveUserId2);
    sys0.setDefaultAuthnMethod(prot2.getAuthnMethod());
    sys0.setTransferMethods(prot2.getTransferMethods());
    sys0.setPort(prot2.getPort());
    sys0.setUseProxy(prot2.isUseProxy());
    sys0.setProxyHost(prot2.getProxyHost());
    sys0.setProxyPort(prot2.getProxyPort());
    sys0.setDtnSystemId(dtnSystemId2);
    sys0.setDtnMountPoint(dtnMountPoint2);
    sys0.setDtnMountSourcePath(dtnMountSourcePath2);
    sys0.setJobWorkingDir(jobWorkingDir2);
    sys0.setJobEnvVariables(jobEnvVariables2);
    sys0.setJobMaxJobs(jobMaxJobs2);
    sys0.setJobMaxJobsPerUser(jobMaxJobsPerUser2);
    sys0.setBatchScheduler(batchScheduler2);
    sys0.setBatchDefaultLogicalQueue(batchDefaultLogicalQueue2);
    sys0.setTags(tags2);
    sys0.setNotes(notes2);
    sys0.setJobRuntimes(runtimeList2);
    sys0.setBatchLogicalQueues(logicalQueueList2);
    sys0.setJobCapabilities(capList2);
    // Check common system attributes:
    checkCommonSysAttrs(sys0, tmpSysFull);

    // Test updating just a few attributes
    sys0 = systems[22];
    systemId = sys0.getId();
    sys0.setJobRuntimes(runtimeList1);
    sys0.setBatchLogicalQueues(logicalQueueList1);
    sys0.setJobCapabilities(capList1);
    createText = "{\"testUpdate\": \"0-create2\"}";
    svc.createSystem(authenticatedOwner1, sys0, createText);
    // Create patchSystem where some attributes are changed
    //   * Some attributes are to be updated: description, authnMethod, dtnMountPoint, runtimeList, jobMaxJobsPerUser
    String patch2Text = "{\"testUpdate\": \"1-patch2\"}";
    PatchSystem patchSystemPartial = IntegrationUtils.makePatchSystemPartial();
    patchSystemPartial.setTenant(tenantName);
    patchSystemPartial.setId(systemId);

    // Update using patchSys
    svc.updateSystem(authenticatedOwner1, patchSystemPartial, patch2Text);
    TSystem tmpSysPartial = svc.getSystem(authenticatedOwner1, sys0.getId(), false, null, false);

    // Update original definition with patched values so we can use the checkCommon method.
    sys0.setDescription(description2);
    sys0.setDefaultAuthnMethod(prot2.getAuthnMethod());
    sys0.setDtnMountPoint(dtnMountPoint2);
    sys0.setJobMaxJobsPerUser(jobMaxJobsPerUser2);
    sys0.setJobRuntimes(runtimeList2);
    // Check common system attributes:
    checkCommonSysAttrs(sys0, tmpSysPartial);
  }

  // Test changing system owner
  @Test
  public void testChangeSystemOwner() throws Exception
  {
    TSystem sys0 = systems[15];
    sys0.setJobCapabilities(capList1);
    String createText = "{\"testChangeOwner\": \"0-create\"}";
    String newOwnerName = testUser2;
    svc.createSystem(authenticatedOwner1, sys0, createText);
    // Change owner using api
    svc.changeSystemOwner(authenticatedOwner1, sys0.getId(), newOwnerName);
    TSystem tmpSys = svc.getSystem(authenticatedTestUser2, sys0.getId(), false, null, false);
    Assert.assertEquals(tmpSys.getOwner(), newOwnerName);
    // Check expected auxiliary updates have happened
    // New owner should be able to retrieve permissions and have all permissions
    Set<Permission> userPerms = svc.getUserPermissions(authenticatedTestUser2, sys0.getId(), newOwnerName);
    Assert.assertNotNull(userPerms, "Null returned when retrieving perms.");
    for (Permission perm : Permission.values())
    {
      Assert.assertTrue(userPerms.contains(perm));
    }
    // Original owner should no longer have the modify or execute permission
    userPerms = svc.getUserPermissions(authenticatedTestUser2, sys0.getId(), owner1);
    Assert.assertFalse(userPerms.contains(Permission.READ));
    Assert.assertFalse(userPerms.contains(Permission.MODIFY));
    Assert.assertFalse(userPerms.contains(Permission.EXECUTE));
    Assert.assertTrue(userPerms.isEmpty());
    // Original owner should not be able to modify system
    try {
      svc.softDeleteSystem(authenticatedOwner1, sys0.getId());
      Assert.fail("Original owner should not have permission to update system after change of ownership. System name: " + sys0.getId() +
              " Old owner: " + authenticatedOwner1.getName() + " New Owner: " + newOwnerName);
    } catch (Exception e) {
      Assert.assertEquals(e.getMessage(), "HTTP 401 Unauthorized");
    }
    // Original owner should not be able to read system
    try {
      svc.getSystem(authenticatedOwner1, sys0.getId(), false, null, false);
      Assert.fail("Original owner should not have permission to read system after change of ownership. System name: " + sys0.getId() +
              " Old owner: " + authenticatedOwner1.getName() + " New Owner: " + newOwnerName);
    } catch (Exception e) {
      Assert.assertEquals(e.getMessage(), "HTTP 401 Unauthorized");
    }
  }

  // Check that when a system is created variable substitution is correct for:
  //   owner, effectiveUser, bucketName, rootDir, jobWorkingDir
  // And when system is retrieved effectiveUserId is resolved
  @Test
  public void testGetSystemWithVariables() throws Exception
  {
    TSystem sys0 = systems[7];
    sys0.setOwner("${apiUserId}");
    sys0.setEffectiveUserId("${owner}");
    sys0.setBucketName("bucket8-${tenant}-${apiUserId}");
    sys0.setRootDir("/root8/${tenant}");
    sys0.setJobWorkingDir("jobWorkDir8/${owner}/${tenant}/${apiUserId}");
    svc.createSystem(authenticatedOwner1, sys0, scrubbedJson);
    TSystem tmpSys = svc.getSystem(authenticatedOwner1, sys0.getId(), false, null, false);
    Assert.assertNotNull(tmpSys, "Failed to create item: " + sys0.getId());
    System.out.println("Found item: " + sys0.getId());
    String effectiveUserId = owner1;
    String bucketName = "bucket8-" + tenantName + "-" + effectiveUserId;
    String rootDir = "/root8/" + tenantName;
    String jobWorkingDir = "jobWorkDir8/" + owner1 + "/" + tenantName + "/" + effectiveUserId;
    Assert.assertEquals(tmpSys.getId(), sys0.getId());
    Assert.assertEquals(tmpSys.getDescription(), sys0.getDescription());
    Assert.assertEquals(tmpSys.getSystemType().name(), sys0.getSystemType().name());
    Assert.assertEquals(tmpSys.getOwner(), owner1);
    Assert.assertEquals(tmpSys.getHost(), sys0.getHost());
    Assert.assertEquals(tmpSys.getEffectiveUserId(), effectiveUserId);
    Assert.assertEquals(tmpSys.getDefaultAuthnMethod().name(), sys0.getDefaultAuthnMethod().name());
    Assert.assertEquals(tmpSys.isEnabled(), sys0.isEnabled());
    Assert.assertEquals(tmpSys.getBucketName(), bucketName);
    Assert.assertEquals(tmpSys.getRootDir(), rootDir);
    Assert.assertEquals(tmpSys.getJobWorkingDir(), jobWorkingDir);
    Assert.assertEquals(tmpSys.getPort(), sys0.getPort());
    Assert.assertEquals(tmpSys.isUseProxy(), sys0.isUseProxy());
    Assert.assertEquals(tmpSys.getProxyHost(), sys0.getProxyHost());
    Assert.assertEquals(tmpSys.getProxyPort(), sys0.getProxyPort());
    List<TransferMethod> tMethodsList = tmpSys.getTransferMethods();
    Assert.assertNotNull(tMethodsList);
    Assert.assertNotNull(sys0.getTransferMethods());
    for (TransferMethod txfrMethod : sys0.getTransferMethods())
    {
      Assert.assertTrue(tMethodsList.contains(txfrMethod), "List of transfer methods did not contain: " + txfrMethod.name());
    }
  }

  @Test
  public void testGetSystemNames() throws Exception
  {
    TSystem sys0 = systems[2];
    svc.createSystem(authenticatedOwner1, sys0, scrubbedJson);
    sys0 = systems[3];
    svc.createSystem(authenticatedOwner1, sys0, scrubbedJson);
    Set<String> systemNames = svc.getSystemIDs(authenticatedOwner1);
    for (String name : systemNames) {
      System.out.println("Found item: " + name);
    }
    Assert.assertTrue(systemNames.contains(systems[2].getId()), "List of systems did not contain system name: " + systems[2].getId());
    Assert.assertTrue(systemNames.contains(systems[3].getId()), "List of systems did not contain system name: " + systems[3].getId());
  }

  @Test
  public void testGetSystems() throws Exception
  {
    TSystem sys0 = systems[4];
    svc.createSystem(authenticatedOwner1, sys0, scrubbedJson);
    List<TSystem> systems = svc.getSystems(authenticatedOwner1, searchListNull, limitNone, orderByListNull, skipZero, startAferEmpty);
    for (TSystem system : systems) {
      System.out.println("Found item with id: " + system.getId() + " and name: " + system.getId());
    }
  }

  // Check that user only sees systems they are authorized to see.
  //   and same for a service when it is calling with oboUser (i.e. not as itself).
  @Test
  public void testGetSystemsAuth() throws Exception
  {
    // Create 3 systems, 2 of which are owned by testUser4.
    TSystem sys0 = systems[16];
    String sys1Name = sys0.getId();
    sys0.setOwner(authenticatedTestUser4.getName());
    svc.createSystem(authenticatedTestUser4, sys0, scrubbedJson);
    sys0 = systems[17];
    String sys2Name = sys0.getId();
    sys0.setOwner(authenticatedTestUser4.getName());
    svc.createSystem(authenticatedTestUser4, sys0, scrubbedJson);
    sys0 = systems[18];
    svc.createSystem(authenticatedOwner1, sys0, scrubbedJson);
    // When retrieving systems as testUser4 only 2 should be returned
    List<TSystem> systems = svc.getSystems(authenticatedTestUser4, searchListNull, limitNone, orderByListNull, skipZero, startAferEmpty);
    System.out.println("Total number of systems retrieved by testuser4: " + systems.size());
    for (TSystem system : systems)
    {
      System.out.println("Found item with id: " + system.getId() + " and name: " + system.getId());
      Assert.assertTrue(system.getId().equals(sys1Name) || system.getId().equalsIgnoreCase(sys2Name));
    }
    Assert.assertEquals(systems.size(), 2);

    // When retrieving systems as a service with oboUser = testuser4 only 2 should be returned.
    systems = svc.getSystems(authenticatedFilesSvcTestUser4, searchListNull, limitNone, orderByListNull, skipZero, startAferEmpty);
    System.out.println("Total number of systems retrieved by Files svc calling with oboUser=testuser4: " + systems.size());
    for (TSystem system : systems)
    {
      System.out.println("Found item with id: " + system.getId() + " and name: " + system.getId());
      Assert.assertTrue(system.getId().equals(sys1Name) || system.getId().equalsIgnoreCase(sys2Name));
    }
    Assert.assertEquals(systems.size(), 2);
  }

  @Test
  public void testEnableDisable() throws Exception
  {
    // Create the app
    TSystem sys0 = systems[21];
    svc.createSystem(authenticatedOwner1, sys0, scrubbedJson);
    // Enabled should start off true, then become false and finally true again.
    TSystem tmpSys = svc.getSystem(authenticatedOwner1, sys0.getId(),false, null, false);
    Assert.assertTrue(tmpSys.isEnabled());
    int changeCount = svc.disableSystem(authenticatedOwner1, sys0.getId());
    Assert.assertEquals(changeCount, 1, "Change count incorrect when updating the system.");
    tmpSys = svc.getSystem(authenticatedOwner1, sys0.getId(), false, null, false);
    Assert.assertFalse(tmpSys.isEnabled());
    changeCount = svc.enableSystem(authenticatedOwner1, sys0.getId());
    Assert.assertEquals(changeCount, 1, "Change count incorrect when updating the system.");
    tmpSys = svc.getSystem(authenticatedOwner1, sys0.getId(), false, null, false);
    Assert.assertTrue(tmpSys.isEnabled());
  }

  @Test
  public void testSoftDelete() throws Exception
  {
    // Create the system
    TSystem sys0 = systems[5];
    svc.createSystem(authenticatedOwner1, sys0, scrubbedJson);
    // Soft delete the system
    int changeCount = svc.softDeleteSystem(authenticatedOwner1, sys0.getId());
    Assert.assertEquals(changeCount, 1, "Change count incorrect when deleting a system.");
    TSystem tmpSys2 = svc.getSystem(authenticatedOwner1, sys0.getId(), false, null, false);
    Assert.assertNull(tmpSys2, "System not deleted. System name: " + sys0.getId());
  }

  @Test
  public void testSystemExists() throws Exception
  {
    // If system not there we should get false
    Assert.assertFalse(svc.checkForSystem(authenticatedOwner1, systems[6].getId()));
    // After creating system we should get true
    TSystem sys0 = systems[6];
    svc.createSystem(authenticatedOwner1, sys0, scrubbedJson);
    Assert.assertTrue(svc.checkForSystem(authenticatedOwner1, systems[6].getId()));
  }

  // Check that if systems already exists we get an IllegalStateException when attempting to create
  @Test(expectedExceptions = {IllegalStateException.class},  expectedExceptionsMessageRegExp = "^SYSLIB_SYS_EXISTS.*")
  public void testCreateSystemAlreadyExists() throws Exception
  {
    // Create the system
    TSystem sys0 = systems[8];
    svc.createSystem(authenticatedOwner1, sys0, scrubbedJson);
    Assert.assertTrue(svc.checkForSystem(authenticatedOwner1, sys0.getId()));
    // Now attempt to create again, should get IllegalStateException with msg SYSLIB_SYS_EXISTS
    svc.createSystem(authenticatedOwner1, sys0, scrubbedJson);
  }

  // Check that reserved names are honored.
  // Because of endpoints certain IDs should not be allowed: healthcheck, readycheck, search
  @Test
  public void testReservedNames() throws Exception
  {
    TSystem sys0 = systems[20];
    for (String id : TSystem.RESERVED_ID_SET)
    {
      System.out.println("Testing create fail for reserved ID: " + id);
      sys0.setId(id);
      try
      {
        svc.createSystem(authenticatedOwner1, sys0, scrubbedJson);
        Assert.fail("System create call should have thrown an exception when using a reserved ID. Id: " + id);
      } catch (IllegalStateException e)
      {
        Assert.assertTrue(e.getMessage().contains("SYSLIB_CREATE_RESERVED"));
      }
    }
  }

  // Check that if credential contains invalid private key then create/update fails.
  @Test
  public void testInvalidPrivateSshKey() throws Exception
  {
    TSystem sys0 = systems[19];
    sys0.setAuthnCredential(credInvalidPrivateSshKey);
    // Test system create
    boolean pass = false;
    try {
      svc.createSystem(authenticatedOwner1, sys0, scrubbedJson);
      Assert.fail("System create call should have thrown an exception when private ssh key is invalid");
    } catch (Exception e) {
      Assert.assertTrue(e.getMessage().contains("SYSLIB_CRED_INVALID_PRIVATE_SSHKEY1"));
      pass = true;
    }
    Assert.assertTrue(pass, "System create call should fail when private ssh key is invalid");

    // Test credential update
    sys0.setAuthnCredential(null);
    svc.createSystem(authenticatedOwner1, sys0, scrubbedJson);
    pass = false;
    try {
      svc.createUserCredential(authenticatedOwner1, sys0.getId(), sys0.getOwner(), credInvalidPrivateSshKey, scrubbedJson);
      Assert.fail("Credential update call should have thrown an exception when private ssh key is invalid");
    } catch (Exception e) {
      Assert.assertTrue(e.getMessage().contains("SYSLIB_CRED_INVALID_PRIVATE_SSHKEY2"));
      pass = true;
    }
    Assert.assertTrue(pass, "Credential update call should fail when private ssh key is invalid");
  }

  // Test creating, reading and deleting user permissions for a system
  @Test
  public void testUserPerms() throws Exception
  {
    // Create a system
    TSystem sys0 = systems[9];
    svc.createSystem(authenticatedOwner1, sys0, scrubbedJson);
    // Create user perms for the system
    svc.grantUserPermissions(authenticatedOwner1, sys0.getId(), testUser3, testPermsREADMODIFY, scrubbedJson);
    // Get the system perms for the user and make sure permissions are there
    Set<Permission> userPerms = svc.getUserPermissions(authenticatedOwner1, sys0.getId(), testUser3);
    Assert.assertNotNull(userPerms, "Null returned when retrieving perms.");
    Assert.assertEquals(userPerms.size(), testPermsREADMODIFY.size(), "Incorrect number of perms returned.");
    for (Permission perm: testPermsREADMODIFY) { if (!userPerms.contains(perm)) Assert.fail("User perms should contain permission: " + perm.name()); }
    // Remove perms for the user. Should return a change count of 2
    int changeCount = svc.revokeUserPermissions(authenticatedOwner1, sys0.getId(), testUser3, testPermsREADMODIFY, scrubbedJson);
    Assert.assertEquals(changeCount, 2, "Change count incorrect when revoking permissions.");
    // Get the system perms for the user and make sure permissions are gone.
    userPerms = svc.getUserPermissions(authenticatedOwner1, sys0.getId(), testUser3);
    for (Permission perm: testPermsREADMODIFY) { if (userPerms.contains(perm)) Assert.fail("User perms should not contain permission: " + perm.name()); }

    // Owner should not be able to update perms. It would be confusing since owner always authorized. Perms not checked.
    boolean pass = false;
    try {
      svc.grantUserPermissions(authenticatedOwner1, sys0.getId(), sys0.getOwner(), testPermsREAD, scrubbedJson);
      Assert.fail("Update of perms by owner for owner should have thrown an exception");
    } catch (Exception e) {
      Assert.assertTrue(e.getMessage().contains("SYSLIB_PERM_OWNER_UPDATE"));
      pass = true;
    }
    Assert.assertTrue(pass, "Update of perms by owner for owner did not throw correct exception");
    pass = false;
    try {
      svc.revokeUserPermissions(authenticatedOwner1, sys0.getId(), sys0.getOwner(), testPermsREAD, scrubbedJson);
      Assert.fail("Update of perms by owner for owner should have thrown an exception");
    } catch (Exception e) {
      Assert.assertTrue(e.getMessage().contains("SYSLIB_PERM_OWNER_UPDATE"));
      pass = true;
    }
    Assert.assertTrue(pass, "Update of perms by owner for owner did not throw correct exception");
  }

  // Test creating, reading and deleting user credentials for a system
  // Including retrieving credentials with a system when effectiveUserId=apiUserId for a system.
  @Test
  public void testUserCredentials() throws Exception
  {
    // Create a system with effUsr = apiUserId
    TSystem sys0 = systems[10];
    sys0.setEffectiveUserId("${apiUserId}");
    svc.createSystem(authenticatedOwner1, sys0, scrubbedJson);
    Credential cred1 = new Credential("fakePassword1", "fakePrivateKey1", "fakePublicKey1",
            "fakeAccessKey1", "fakeAccessSecret1", "fakeCert1");
    Credential cred3 = new Credential("fakePassword3", "fakePrivateKey3", "fakePublicKey3",
            "fakeAccessKey3", "fakeAccessSecret3", "fakeCert3");
    // Store and retrieve multiple secret types: password, ssh keys, access key and secret
    svc.createUserCredential(authenticatedOwner1, sys0.getId(), owner1, cred1, scrubbedJson);
    svc.createUserCredential(authenticatedOwner1, sys0.getId(), testUser3, cred3, scrubbedJson);

    // Get system as owner1 using files service and should get cred for owner1
    TSystem tmpSys = svc.getSystem(authenticatedFilesSvcOwner1, sys0.getId(), true, AuthnMethod.PASSWORD, false);
    Credential cred0 = tmpSys.getAuthnCredential();
    Assert.assertNotNull(cred0, "AuthnCredential should not be null for user: " + owner1);
    Assert.assertNotNull(cred0.getPassword(), "AuthnCredential password should not be null for user: " + owner1);
    Assert.assertEquals(cred0.getPassword(), cred1.getPassword());

    // Get system as testUser3 using files service and should get cred for testUser3
    tmpSys = svc.getSystem(authenticatedFilesSvcTestUser3, sys0.getId(), true, AuthnMethod.PASSWORD, false);
    cred0 = tmpSys.getAuthnCredential();
    Assert.assertNotNull(cred0, "AuthnCredential should not be null for user: " + testUser3);
    Assert.assertNotNull(cred0.getPassword(), "AuthnCredential password should not be null for user: " + testUser3);
    Assert.assertEquals(cred0.getPassword(), cred3.getPassword());

    // Get credentials for testUser3 and validate
    // Use files service AuthenticatedUser since only certain services can retrieve the cred.
    cred0 = svc.getUserCredential(authenticatedFilesSvcOwner1, sys0.getId(), testUser3, AuthnMethod.PASSWORD);
    // Verify credentials
    Assert.assertEquals(cred0.getPassword(), cred3.getPassword());
    cred0 = svc.getUserCredential(authenticatedFilesSvcOwner1, sys0.getId(), testUser3, AuthnMethod.PKI_KEYS);
    Assert.assertEquals(cred0.getPublicKey(), cred3.getPublicKey());
    Assert.assertEquals(cred0.getPrivateKey(), cred3.getPrivateKey());
    cred0 = svc.getUserCredential(authenticatedFilesSvcOwner1, sys0.getId(), testUser3, AuthnMethod.ACCESS_KEY);
    Assert.assertEquals(cred0.getAccessKey(), cred3.getAccessKey());
    Assert.assertEquals(cred0.getAccessSecret(), cred3.getAccessSecret());

    // Delete credentials and verify they were destroyed
    int changeCount = svc.deleteUserCredential(authenticatedOwner1, sys0.getId(), owner1);
    Assert.assertEquals(changeCount, 1, "Change count incorrect when removing credential for user: " + owner1);
    changeCount = svc.deleteUserCredential(authenticatedOwner1, sys0.getId(), testUser3);
    Assert.assertEquals(changeCount, 1, "Change count incorrect when removing credential for user: " + testUser3);

    cred0 = svc.getUserCredential(authenticatedFilesSvcOwner1, sys0.getId(), owner1, AuthnMethod.PASSWORD);
    Assert.assertNull(cred0, "Credential not deleted. System name: " + sys0.getId() + " User name: " + owner1);
    cred0 = svc.getUserCredential(authenticatedFilesSvcOwner1, sys0.getId(), testUser3, AuthnMethod.PASSWORD);
    Assert.assertNull(cred0, "Credential not deleted. System name: " + sys0.getId() + " User name: " + testUser3);

    // Attempt to delete again, should return 0 for change count
    changeCount = svc.deleteUserCredential(authenticatedOwner1, sys0.getId(), testUser3);
    Assert.assertEquals(changeCount, 0, "Change count incorrect when removing a credential already removed.");

    // Set just ACCESS_KEY only and test
    cred3 = new Credential(null, null, null, "fakeAccessKey3a", "fakeAccessSecret3a", null);
    svc.createUserCredential(authenticatedOwner1, sys0.getId(), testUser3, cred3, scrubbedJson);
    cred0 = svc.getUserCredential(authenticatedFilesSvcOwner1, sys0.getId(), testUser3, AuthnMethod.ACCESS_KEY);
    Assert.assertEquals(cred0.getAccessKey(), cred3.getAccessKey());
    Assert.assertEquals(cred0.getAccessSecret(), cred3.getAccessSecret());
    // Attempt to retrieve secret that has not been set
    cred0 = svc.getUserCredential(authenticatedFilesSvcOwner1, sys0.getId(), testUser3, AuthnMethod.PKI_KEYS);
    Assert.assertNull(cred0, "Credential was non-null for missing secret. System name: " + sys0.getId() + " User name: " + testUser3);
    // Delete credentials and verify they were destroyed
    changeCount = svc.deleteUserCredential(authenticatedOwner1, sys0.getId(), testUser3);
    Assert.assertEquals(changeCount, 1, "Change count incorrect when removing a credential.");
    cred0 = svc.getUserCredential(authenticatedFilesSvcOwner1, sys0.getId(), testUser3, AuthnMethod.ACCESS_KEY);
    Assert.assertNull(cred0, "Credential not deleted. System name: " + sys0.getId() + " User name: " + testUser3);
  }

  // Test various cases when system is missing
  //  - get owner with no system
  //  - get perm with no system
  //  - grant perm with no system
  //  - revoke perm with no system
  //  - get credential with no system
  //  - create credential with no system
  //  - delete credential with no system
  @Test
  public void testMissingSystem() throws Exception
  {
    String fakeSystemName = "AMissingSystemName";
    String fakeUserName = "AMissingUserName";
    // Make sure system does not exist
    Assert.assertFalse(svc.checkForSystem(authenticatedOwner1, fakeSystemName));

    // Get TSystem with no system should return null
    TSystem tmpSys = svc.getSystem(authenticatedOwner1, fakeSystemName, false, null, false);
    Assert.assertNull(tmpSys, "TSystem not null for non-existent system");

    // Delete system with no system should return 0 changes
    int changeCount = svc.softDeleteSystem(authenticatedOwner1, fakeSystemName);
    Assert.assertEquals(changeCount, 0, "Change count incorrect when deleting non-existent system.");

    // Get owner with no system should return null
    String owner = svc.getSystemOwner(authenticatedOwner1, fakeSystemName);
    Assert.assertNull(owner, "Owner not null for non-existent system.");

    // Get perms with no system should return null
    Set<Permission> perms = svc.getUserPermissions(authenticatedOwner1, fakeSystemName, fakeUserName);
    Assert.assertNull(perms, "Perms list was not null for non-existent system");

    // Revoke perm with no system should return 0 changes
    changeCount = svc.revokeUserPermissions(authenticatedOwner1, fakeSystemName, fakeUserName, testPermsREADMODIFY, scrubbedJson);
    Assert.assertEquals(changeCount, 0, "Change count incorrect when revoking perms for non-existent system.");

    // Grant perm with no system should throw an exception
    boolean pass = false;
    try { svc.grantUserPermissions(authenticatedOwner1, fakeSystemName, fakeUserName, testPermsREADMODIFY, scrubbedJson); }
    catch (TapisException tce)
    {
      Assert.assertTrue(tce.getMessage().startsWith("SYSLIB_NOT_FOUND"));
      pass = true;
    }
    Assert.assertTrue(pass);

    //Get credential with no system should return null
    Credential cred = svc.getUserCredential(authenticatedOwner1, fakeSystemName, fakeUserName, AuthnMethod.PKI_KEYS);
    Assert.assertNull(cred, "Credential was not null for non-existent system");

    // Create credential with no system should throw an exception
    pass = false;
    cred = new Credential(null, null, null, null,"fakeAccessKey2", "fakeAccessSecret2");
    try { svc.createUserCredential(authenticatedOwner1, fakeSystemName, fakeUserName, cred, scrubbedJson); }
    catch (TapisException te)
    {
      Assert.assertTrue(te.getMessage().startsWith("SYSLIB_NOT_FOUND"));
      pass = true;
    }
    Assert.assertTrue(pass);

    // Delete credential with no system should 0 changes
    changeCount = svc.deleteUserCredential(authenticatedOwner1, fakeSystemName, fakeUserName);
    Assert.assertEquals(changeCount, 0, "Change count incorrect when deleting a user credential for non-existent system.");
  }

  // Test Auth denials
  // testUser0 - no perms, not owner
  // testUser3 - READ perm
  // testUser2 - MODIFY perm
  // NOTE: owner1 is owner - all perms
  @Test
  public void testAuthDeny() throws Exception
  {
    // NOTE: By default seed data has owner as owner1 == "owner1"
    TSystem sys0 = systems[12];
    PatchSystem patchSys = new PatchSystem("description PATCHED", "hostPATCHED", "effUserPATCHED",
            prot2.getAuthnMethod(), prot2.getTransferMethods(),
            prot2.getPort(), prot2.isUseProxy(), prot2.getProxyHost(), prot2.getProxyPort(),
            dtnSystemFakeHostname, dtnMountPoint1, dtnMountSourcePath1, runtimeList1, jobWorkingDir1, jobEnvVariables1, jobMaxJobs1,
            jobMaxJobsPerUser1, jobIsBatchTrue, batchScheduler1, logicalQueueList1, batchDefaultLogicalQueue1,
            capList2, tags2, notes2);
    patchSys.setId(sys0.getId());
    patchSys.setTenant(tenantName);
    // CREATE - Deny user not owner/admin, deny service
    boolean pass = false;
    try { svc.createSystem(authenticatedTestUser0, sys0, scrubbedJson); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);
    pass = false;
    try { svc.createSystem(authenticatedFilesSvcOwner1, sys0, scrubbedJson); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);

    // Create system for remaining auth access tests
    Credential cred0 = new Credential("fakePassword", "fakePrivateKey", "fakePublicKey",
            "fakeAccessKey", "fakeAccessSecret", "fakeCert");
    sys0.setAuthnCredential(cred0);
    svc.createSystem(authenticatedOwner1, sys0, scrubbedJson);
    // Grant testUesr3 - READ and testUser2 - MODIFY
    svc.grantUserPermissions(authenticatedOwner1, sys0.getId(), testUser3, testPermsREAD, scrubbedJson);
    svc.grantUserPermissions(authenticatedOwner1, sys0.getId(), testUser2, testPermsMODIFY, scrubbedJson);

    // READ - deny user not owner/admin and no READ or MODIFY access
    pass = false;
    try { svc.getSystem(authenticatedTestUser0, sys0.getId(), false, null, false); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);

    // EXECUTE - deny user not owner/admin with READ but not EXECUTE
    pass = false;
    try { svc.getSystem(authenticatedTestUser3, sys0.getId(), false, null, true); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);


    // MODIFY Deny user with no READ or MODIFY, deny user with only READ, deny service
    pass = false;
    try { svc.updateSystem(authenticatedTestUser0, patchSys, scrubbedJson); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);
    pass = false;
    try { svc.updateSystem(authenticatedTestUser3, patchSys, scrubbedJson); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);
    pass = false;
    try { svc.updateSystem(authenticatedFilesSvcOwner1, patchSys, scrubbedJson); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);

    // DELETE - deny user not owner/admin, deny service
    pass = false;
    try { svc.softDeleteSystem(authenticatedTestUser3, sys0.getId()); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);
    pass = false;
    try { svc.softDeleteSystem(authenticatedFilesSvcOwner1, sys0.getId()); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);

    // CHANGE_OWNER - deny user not owner/admin, deny service
    pass = false;
    try { svc.changeSystemOwner(authenticatedTestUser3, sys0.getId(), testUser2); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);
    pass = false;
    try { svc.changeSystemOwner(authenticatedFilesSvcOwner1, sys0.getId(), testUser2); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);

    // GET_PERMS - deny user not owner/admin and no READ or MODIFY access
    pass = false;
    try { svc.getUserPermissions(authenticatedTestUser0, sys0.getId(), owner1); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);

    // GRANT_PERMS - deny user not owner/admin, deny service
    pass = false;
    try { svc.grantUserPermissions(authenticatedTestUser3, sys0.getId(), testUser0, testPermsREADMODIFY, scrubbedJson); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);
    pass = false;
    try { svc.grantUserPermissions(authenticatedFilesSvcOwner1, sys0.getId(), testUser0, testPermsREADMODIFY, scrubbedJson); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);

    // REVOKE_PERMS - deny user not owner/admin, deny service
    pass = false;
    try { svc.revokeUserPermissions(authenticatedTestUser3, sys0.getId(), owner1, testPermsREADMODIFY, scrubbedJson); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);
    pass = false;
    try { svc.revokeUserPermissions(authenticatedFilesSvcOwner1, sys0.getId(), owner1, testPermsREADMODIFY, scrubbedJson); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);

    // SET_CRED - deny user not owner/admin and not target user, deny service
    pass = false;
    try { svc.createUserCredential(authenticatedTestUser3, sys0.getId(), owner1, cred0, scrubbedJson); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);
    pass = false;
    try { svc.createUserCredential(authenticatedFilesSvcOwner1, sys0.getId(), owner1, cred0, scrubbedJson); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);

    // REMOVE_CRED - deny user not owner/admin and not target user, deny service
    pass = false;
    try { svc.deleteUserCredential(authenticatedTestUser3, sys0.getId(), owner1); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);
    pass = false;
    try { svc.deleteUserCredential(authenticatedFilesSvcOwner1, sys0.getId(), owner1); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);

    // GET_CRED - deny user not owner/admin, deny owner
    pass = false;
    try { svc.getUserCredential(authenticatedTestUser3, sys0.getId(), owner1, null); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);
    pass = false;
    try { svc.getUserCredential(authenticatedOwner1, sys0.getId(), owner1, null); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = true;
    }
    Assert.assertTrue(pass);
  }

  // Test Auth allow
  // Many cases covered during other tests
  // Test special cases here:
  //    MODIFY implies READ
  // testUser0 - no perms
  // testUser3 - READ,EXECUTE perm
  // testUser2 - MODIFY perm
  // NOTE: testUser1 is owner - all perms
  @Test
  public void testAuthAllow() throws Exception
  {
    // NOTE: By default seed data has owner as testUser1
    TSystem sys0 = systems[14];
    // Create system for remaining auth access tests
    Credential cred0 = new Credential("fakePassword", "fakePrivateKey", "fakePublicKey",
            "fakeAccessKey", "fakeAccessSecret", "fakeCert");
    sys0.setAuthnCredential(cred0);
    svc.createSystem(authenticatedOwner1, sys0, scrubbedJson);
    // Grant User1 - READ and User2 - MODIFY
    svc.grantUserPermissions(authenticatedOwner1, sys0.getId(), testUser3, testPermsREADEXECUTE, scrubbedJson);
    svc.grantUserPermissions(authenticatedOwner1, sys0.getId(), testUser2, testPermsMODIFY, scrubbedJson);

    // READ - allow owner, service, with READ only, with MODIFY only
    boolean pass = true;
    try
    {
      svc.getSystem(authenticatedOwner1, sys0.getId(), false, null, false);
      svc.getSystem(authenticatedOwner1, sys0.getId(), false, null, true);
    }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = false;
    }
    Assert.assertTrue(pass);
    pass = true;
    try { svc.getSystem(authenticatedFilesSvcOwner1, sys0.getId(), false, null, false); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = false;
    }
    Assert.assertTrue(pass);
    pass = true;
    try
    {
      svc.getSystem(authenticatedTestUser3, sys0.getId(), false, null, false);
      svc.getSystem(authenticatedTestUser3, sys0.getId(), false, null, true);
    }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = false;
    }
    Assert.assertTrue(pass);
    pass = true;
    try { svc.getSystem(authenticatedTestUser2, sys0.getId(), false, null, false); }
    catch (NotAuthorizedException e)
    {
      Assert.assertTrue(e.getMessage().startsWith("HTTP 401 Unauthorized"));
      pass = false;
    }
    Assert.assertTrue(pass);
  }

  // ************************************************************************
  // *********************** Private Methods ********************************
  // ************************************************************************

  /**
   * Check common attributes after creating and retrieving a system
   * @param sys0 - Test system
   * @param tmpSys - Retrieved system
   */
  private static void checkCommonSysAttrs(TSystem sys0, TSystem tmpSys)
  {
    Assert.assertNotNull(tmpSys, "Failed to create item: " + sys0.getId());
    System.out.println("Found item: " + sys0.getId());
    Assert.assertEquals(tmpSys.getId(), sys0.getId());
    Assert.assertEquals(tmpSys.getDescription(), sys0.getDescription());
    Assert.assertEquals(tmpSys.getSystemType().name(), sys0.getSystemType().name());
    Assert.assertEquals(tmpSys.getOwner(), sys0.getOwner());
    Assert.assertEquals(tmpSys.getHost(), sys0.getHost());
    Assert.assertEquals(tmpSys.getEffectiveUserId(), sys0.getEffectiveUserId());
    Assert.assertEquals(tmpSys.getDefaultAuthnMethod().name(), sys0.getDefaultAuthnMethod().name());
    Assert.assertEquals(tmpSys.getBucketName(), sys0.getBucketName());
    Assert.assertEquals(tmpSys.getRootDir(), sys0.getRootDir());

    // Verify transfer methods
    List<TransferMethod> tMethodsList = tmpSys.getTransferMethods();
    Assert.assertNotNull(tMethodsList);
    Assert.assertNotNull(sys0.getTransferMethods(), "Orig TxfrMethods should not be null");
    for (TransferMethod txfrMethod : sys0.getTransferMethods())
    {
      Assert.assertTrue(tMethodsList.contains(txfrMethod), "List of transfer methods did not contain: " + txfrMethod.name());
    }

    Assert.assertEquals(tmpSys.getPort(), sys0.getPort());
    Assert.assertEquals(tmpSys.isUseProxy(), sys0.isUseProxy());
    Assert.assertEquals(tmpSys.getProxyHost(), sys0.getProxyHost());
    Assert.assertEquals(tmpSys.getProxyPort(), sys0.getProxyPort());
    Assert.assertEquals(tmpSys.getDtnSystemId(), sys0.getDtnSystemId());
    Assert.assertEquals(tmpSys.getDtnMountSourcePath(), sys0.getDtnMountSourcePath());
    Assert.assertEquals(tmpSys.getDtnMountPoint(), sys0.getDtnMountPoint());
    Assert.assertEquals(tmpSys.isDtn(), sys0.isDtn());
    Assert.assertEquals(tmpSys.getCanExec(), sys0.getCanExec());
    Assert.assertEquals(tmpSys.getJobWorkingDir(), sys0.getJobWorkingDir());

    // Verify jobEnvVariables
    String[] origVars = sys0.getJobEnvVariables();
    String[] tmpVars = tmpSys.getJobEnvVariables();
    Assert.assertNotNull(origVars, "Orig jobEnvVariables should not be null");
    Assert.assertNotNull(tmpVars, "Fetched jobEnvVariables should not be null");
    var varsList = Arrays.asList(tmpVars);
    Assert.assertEquals(tmpVars.length, origVars.length, "Wrong number of jobEnvVariables");
    for (String varStr : origVars)
    {
      Assert.assertTrue(varsList.contains(varStr));
      System.out.println("Found jobEnvVariable: " + varStr);
    }

    Assert.assertEquals(tmpSys.getJobMaxJobs(), sys0.getJobMaxJobs());
    Assert.assertEquals(tmpSys.getJobMaxJobsPerUser(), sys0.getJobMaxJobsPerUser());
    Assert.assertEquals(tmpSys.getJobIsBatch(), sys0.getJobIsBatch());
    Assert.assertEquals(tmpSys.getBatchScheduler(), sys0.getBatchScheduler());
    Assert.assertEquals(tmpSys.getBatchDefaultLogicalQueue(), sys0.getBatchDefaultLogicalQueue());

    // Verify tags
    String[] origTags = sys0.getTags();
    String[] tmpTags = tmpSys.getTags();
    Assert.assertNotNull(origTags, "Orig Tags should not be null");
    Assert.assertNotNull(tmpTags, "Fetched Tags value should not be null");
    var tagsList = Arrays.asList(tmpTags);
    Assert.assertEquals(tmpTags.length, origTags.length, "Wrong number of tags.");
    for (String tagStr : origTags)
    {
      Assert.assertTrue(tagsList.contains(tagStr));
      System.out.println("Found tag: " + tagStr);
    }
    // Verify notes
    Assert.assertNotNull(sys0.getNotes(), "Orig Notes should not be null");
    Assert.assertNotNull(tmpSys.getNotes(), "Fetched Notes should not be null");
    System.out.println("Found notes: " + sys0.getNotes().toString());
    JsonObject tmpObj = (JsonObject) tmpSys.getNotes();
    JsonObject origNotes = (JsonObject) sys0.getNotes();
    Assert.assertTrue(tmpObj.has("project"));
    String projStr = origNotes.get("project").getAsString();
    Assert.assertEquals(tmpObj.get("project").getAsString(), projStr);
    Assert.assertTrue(tmpObj.has("testdata"));
    String testdataStr = origNotes.get("testdata").getAsString();
    Assert.assertEquals(tmpObj.get("testdata").getAsString(), testdataStr);

    // Verify jobRuntimes
    List<JobRuntime> origRuntimes = sys0.getJobRuntimes();
    List<JobRuntime> jobRuntimes = tmpSys.getJobRuntimes();
    Assert.assertNotNull(origRuntimes, "Orig Runtimes was null");
    Assert.assertNotNull(jobRuntimes, "Fetched Runtimes was null");
    Assert.assertEquals(jobRuntimes.size(), origRuntimes.size());
    var runtimeVersionsFound = new ArrayList<String>();
    for (JobRuntime runtimeFound : jobRuntimes) {runtimeVersionsFound.add(runtimeFound.getVersion());}
    for (JobRuntime runtimeSeedItem : origRuntimes)
    {
      Assert.assertTrue(runtimeVersionsFound.contains(runtimeSeedItem.getVersion()),
              "List of jobRuntimes did not contain a runtime with version: " + runtimeSeedItem.getVersion());
    }

    // Verify logicalQueues
    List<LogicalQueue> origLogicalQueues = sys0.getBatchLogicalQueues();
    List<LogicalQueue> logicalQueues = tmpSys.getBatchLogicalQueues();
    Assert.assertNotNull(origLogicalQueues, "Orig LogicalQueues was null");
    Assert.assertNotNull(logicalQueues, "Fetched LogicalQueues was null");
    Assert.assertEquals(logicalQueues.size(), origLogicalQueues.size());
    var logicalQueueNamesFound = new ArrayList<String>();
    for (LogicalQueue logicalQueueFound : logicalQueues) {logicalQueueNamesFound.add(logicalQueueFound.getName());}
    for (LogicalQueue logicalQueueSeedItem : origLogicalQueues)
    {
      Assert.assertTrue(logicalQueueNamesFound.contains(logicalQueueSeedItem.getName()),
              "List of logicalQueues did not contain a logicalQueue with name: " + logicalQueueSeedItem.getName());
    }

    // Verify capabilities
    List<Capability> origCaps = sys0.getJobCapabilities();
    List<Capability> jobCaps = tmpSys.getJobCapabilities();
    Assert.assertNotNull(origCaps, "Orig Caps was null");
    Assert.assertNotNull(jobCaps, "Fetched Caps was null");
    Assert.assertEquals(jobCaps.size(), origCaps.size());
    var capNamesFound = new ArrayList<String>();
    for (Capability capFound : jobCaps) {capNamesFound.add(capFound.getName());}
    for (Capability capSeedItem : origCaps)
    {
      Assert.assertTrue(capNamesFound.contains(capSeedItem.getName()),
              "List of capabilities did not contain a capability named: " + capSeedItem.getName());
    }

    Assert.assertNotNull(tmpSys.getCreated(), "Fetched created timestamp should not be null");
    Assert.assertNotNull(tmpSys.getUpdated(), "Fetched updated timestamp should not be null");
  }
}
