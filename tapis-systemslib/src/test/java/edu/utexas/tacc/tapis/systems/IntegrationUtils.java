package edu.utexas.tacc.tapis.systems;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import edu.utexas.tacc.tapis.shared.threadlocal.OrderBy;
import edu.utexas.tacc.tapis.shared.utils.TapisGsonUtils;
import edu.utexas.tacc.tapis.systems.model.Capability;
import edu.utexas.tacc.tapis.systems.model.Credential;
import edu.utexas.tacc.tapis.systems.model.JobRuntime;
import edu.utexas.tacc.tapis.systems.model.LogicalQueue;
import edu.utexas.tacc.tapis.systems.model.PatchSystem;
import edu.utexas.tacc.tapis.systems.model.TSystem;
import edu.utexas.tacc.tapis.systems.model.TSystem.AuthnMethod;
import edu.utexas.tacc.tapis.systems.model.TSystem.TransferMethod;
import edu.utexas.tacc.tapis.systems.model.TSystem.SchedulerType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/*
 * Utilities and data for integration testing
 */
public final class IntegrationUtils
{
  public static final Gson gson =  TapisGsonUtils.getGson();

  // Test data
  public static final String siteId = "tacc";
  public static final String adminTenantName = "admin";
  public static final String tenantName = "dev";
  public static final String svcName = "systems";
  public static final String filesSvcName = "files";
  public static final String adminUser = "testuser9";

  // NOTE: Continue to use the fake users owner1, owner2 since some operations involve modifying credentials
  //       Although it should not be a problem because credentials are stored for each system it is best to be safe.
  public static final String owner1 = "owner1";
  public static final String owner2 = "owner2";
  public static final String testUser0 = "testuser0";
  public static final String testUser1 = "testuser1";
  public static final String testUser2 = "testuser2";
  public static final String testUser3 = "testuser3";
  public static final String testUser4 = "testuser4";
  public static final String apiUser = "testApiUser";
  public static final String sysNamePrefix = "TestSys";
  public static final String description1 = "System description 1";
  public static final String description2 = "System description 2";
  public static final String hostname2 = "my.system2.org";
  public static final String hostnameNull = null;
  public static final String effectiveUserId1 = "effUsr1";
  public static final String effectiveUserId2 = "effUsr2";
  public static final String effectiveUserIdNull = null;
  public static final List<TransferMethod> txfrMethodsList = new ArrayList<>(List.of(TransferMethod.SFTP, TransferMethod.S3));
  public static final List<TransferMethod> txfrMethodsEmpty = new ArrayList<>();
  public static final List<TransferMethod> txfrMethodsNull = null;
  public static final Integer portNull = null;
  public static final Boolean userProxyNull = null;
  public static final String proxyHostNull = null;
  public static final Integer proxyPortNull = null;
  public static final boolean isEnabledTrue = true;
  public static final boolean isDtnTrue = true;
  public static final boolean isDtnFalse = false;
  public static final boolean canExecTrue = true;
  public static final boolean canExecFalse = false;
  public static final String hostPatchedId = "patched.system.org";
  public static final String hostMinimalId = "minimal.system.org";
  public static final String rootDir1 = "/root/dir1";
  public static final String rootDir2 = "/root/dir2";
  public static TSystem dtnSystem1;
  public static TSystem dtnSystem2;
  public static final String dtnSystemId1 = "test-dtn-system1";
  public static final String dtnSystemId2 = "test-dtn-system2";
  public static final String dtnSystemIdNull = null;
  public static final String dtnSystemValidHostname = "dtn.system.org";
  public static final String dtnSystemFakeHostname = "fakeDTNSystem";
  public static final String dtnMountPoint1 = "/fake/mountpoint1";
  public static final String dtnMountPoint2 = "/fake/mountpoint2";
  public static final String dtnMountPointNull = null;
  public static final String dtnMountSourcePath1 = "/fake/mountsourcepath1";
  public static final String dtnMountSourcePath2 = "/fake/mountsourcepath2";
  public static final String dtnMountSourcePathNull = null;
  public static final String jobWorkingDir1 = "/fake/job/working_dir1";
  public static final String jobWorkingDir2 = "/fake/job/working_dir2";
  public static final String jobWorkingDirNull = null;
  public static final SchedulerType batchScheduler1 = SchedulerType.SLURM;
  public static final SchedulerType batchScheduler2 = SchedulerType.PBS;
  public static final String batchDefaultLogicalQueue1 = "lqA1";
  public static final String batchDefaultLogicalQueue2 = "lqA2";
  public static final String batchDefaultLogicalQueueNull = null;
//  public static final KeyValuePair kv1 = new KeyValuePair("a","b");
//  public static final KeyValuePair kv2 = new KeyValuePair("HOME","/home/testuser2");
//  public static final KeyValuePair kv3 = new KeyValuePair("TMP","/tmp");
//  public static final List<KeyValuePair> jobEnvVariables = new ArrayList<>(List.of(kv1,kv2,kv3));
  public static final String[] jobEnvVariables1 = {"a1=b1", "HOME=/home/testuser1", "TMP=/tmp1"};
  public static final String[] jobEnvVariables2 = {"a2=b2", "HOME=/home/testuser2", "TMP=/tmp2"};
  public static final String[] jobEnvVariablesNull = null;
  public static final SchedulerType batchSchedulerNull = null;
  public static final String queueNameNull = null;
  public static final boolean jobIsBatchTrue = true;
  public static final boolean jobIsBatchFalse = false;
  public static final Boolean jobIsBatchNull = null;
  public static final int jobMaxJobs1 = 1;
  public static final int jobMaxJobs2 = 2;
  public static final Integer jobMaxJobsNull = null;
  public static final int jobMaxJobsPerUser1 = 1;
  public static final int jobMaxJobsPerUser2 = 2;
  public static final String[] tags1 = {"value1", "value2", "a",
    "Long tag (1 3 2) special chars [_ $ - & * % @ + = ! ^ ? < > , . ( ) { } / \\ | ]. Backslashes must be escaped."};
  public static final String[] tags2 = {"value3", "value4"};
  public static final String[] tagsNull = null;
  public static final Object notes1 = TapisGsonUtils.getGson().fromJson("{\"project\": \"myproj1\", \"testdata\": \"abc1\"}", JsonObject.class);
  public static final Object notes2 = TapisGsonUtils.getGson().fromJson("{\"project\": \"myproj2\", \"testdata\": \"abc2\"}", JsonObject.class);
  public static final JsonObject notesObj1 = (JsonObject) notes1;
  public static final Object notesNull = null;

  public static final Protocol prot1 = new Protocol(AuthnMethod.PKI_KEYS, txfrMethodsList, 22, false, "", 0);
  public static final Protocol prot2 = new Protocol(AuthnMethod.PASSWORD, txfrMethodsList, 0, true, "localhost",2222);
  public static final String scrubbedJson = "{}";

  // Job Runtimes
  public static final JobRuntime runtimeA1 = new JobRuntime(JobRuntime.RuntimeType.DOCKER, "0.0.1A1");
  public static final JobRuntime runtimeB1 = new JobRuntime(JobRuntime.RuntimeType.SINGULARITY, "0.0.1B1");
  public static final List<JobRuntime> runtimeList1 = new ArrayList<>(List.of(runtimeA1, runtimeB1));
  public static final JobRuntime runtimeA2 = new JobRuntime(JobRuntime.RuntimeType.SINGULARITY, "0.0.1A2");
  public static final JobRuntime runtimeB2 = new JobRuntime(JobRuntime.RuntimeType.DOCKER, "0.0.1B2");
  public static final List<JobRuntime> runtimeList2 = new ArrayList<>(List.of(runtimeA2, runtimeB2));

  // Logical Queues
  public static final LogicalQueue queueA1 = new LogicalQueue("lqA1","hqA1", 1, 1, 1, 1, 1, 1);
  public static final LogicalQueue queueB1 = new LogicalQueue("lqB1","hqB1", 2, 2, 2, 2, 2, 2);
  public static final LogicalQueue queueC1 = new LogicalQueue("lqC1","hqC1", 3, 3, 3, 3, 3, 3);
  public static final List<LogicalQueue> logicalQueueList1 = new ArrayList<>(List.of(queueA1, queueB1, queueC1));
  public static final LogicalQueue queueA2 = new LogicalQueue("lqA2","hqA2", 10, 10, 10, 10,10, 10);
  public static final LogicalQueue queueB2 = new LogicalQueue("lqB2","hqB1", 20, 20, 20, 20,20, 20);
  public static final List<LogicalQueue> logicalQueueList2 = new ArrayList<>(List.of(queueA2, queueB2));
  public static final List<LogicalQueue> logicalQueueListNull = null;

  // Job Capabilities
  public static final Capability capA1 = new Capability(Capability.Category.SCHEDULER, "Type",
                                                       Capability.Datatype.STRING, Capability.DEFAULT_PRECEDENCE, "Slurm");
  public static final Capability capB1 = new Capability(Capability.Category.HARDWARE, "CoresPerNode",
                                                       Capability.Datatype.INTEGER, Capability.DEFAULT_PRECEDENCE, "4");
  public static final Capability capC1 = new Capability(Capability.Category.SOFTWARE, "OpenMP",
                                                       Capability.Datatype.STRING, Capability.DEFAULT_PRECEDENCE, "4.5");
  public static final List<Capability> capList1 = new ArrayList<>(List.of(capA1, capB1, capC1));

  public static final Capability capA2 = new Capability(Capability.Category.SCHEDULER, "Type",
          Capability.Datatype.STRING, Capability.DEFAULT_PRECEDENCE, "PBS");
  public static final Capability capB2 = new Capability(Capability.Category.HARDWARE, "CoresPerNode",
          Capability.Datatype.INTEGER, Capability.DEFAULT_PRECEDENCE, "8");
  public static final Capability capC2 = new Capability(Capability.Category.SOFTWARE, "MPI",
          Capability.Datatype.STRING, Capability.DEFAULT_PRECEDENCE, "3.1");
  public static final Capability capD2 = new Capability(Capability.Category.CONTAINER, "Docker",
          Capability.Datatype.STRING, Capability.DEFAULT_PRECEDENCE, null);
  public static final List<Capability> capList2 = new ArrayList<>(List.of(capA2, capB2, capC2, capD2));

  public static final Capability capA3 = new Capability(Capability.Category.SCHEDULER, "Type",
          Capability.Datatype.STRING, Capability.DEFAULT_PRECEDENCE, "Condor");
  public static final Capability capB3 = new Capability(Capability.Category.HARDWARE, "CoresPerNode",
          Capability.Datatype.INTEGER, Capability.DEFAULT_PRECEDENCE, "128");
  public static final Capability capC3 = new Capability(Capability.Category.SOFTWARE, "OpenMP",
          Capability.Datatype.STRING, Capability.DEFAULT_PRECEDENCE, "3.1");
  public static final List<Capability> capList3 = new ArrayList<>(List.of(capA3, capB3, capC3));
  public static final List<Capability> capListNull = null;

  public static final UUID uuidNull = null;
  public static final boolean isDeletedFalse = false;
  public static final Instant createdNull = null;
  public static final Instant updatedNull = null;
  public static final int qMaxJobs = -1;
  public static final int qMaxJobsPerUser = -1;
  public static final int qMaxNodeCount = -1;
  public static final int qMaxCoresPerNode = -1;
  public static final int qMaxMemoryMB = -1;
  public static final int qMaxMinutes = -1;

  public static final List<OrderBy> orderByListNull = null;
  public static final List<OrderBy> orderByListAsc = Collections.singletonList(OrderBy.fromString("id(asc)"));
  public static final List<OrderBy> orderByListDesc = Collections.singletonList(OrderBy.fromString("id(desc)"));
  public static final String startAfterNull = null;

  public static final String invalidPrivateSshKey = "-----BEGIN OPENSSH PRIVATE KEY-----";
  public static final String invalidPublicSshKey = "testPubSshKey";

  public static final Credential credInvalidPrivateSshKey =
          new Credential(null, invalidPrivateSshKey, invalidPublicSshKey, null, null, null);

  // Permissions
  public static final Set<TSystem.Permission> testPermsREADMODIFY = new HashSet<>(Set.of(TSystem.Permission.READ, TSystem.Permission.MODIFY));
  public static final Set<TSystem.Permission> testPermsREADEXECUTE = new HashSet<>(Set.of(TSystem.Permission.READ, TSystem.Permission.EXECUTE));
  public static final Set<TSystem.Permission> testPermsREAD = new HashSet<>(Set.of(TSystem.Permission.READ));
  public static final Set<TSystem.Permission> testPermsMODIFY = new HashSet<>(Set.of(TSystem.Permission.MODIFY));

  // Search and sort
  public static final List<String> searchListNull = null;
  public static final int limitNone = -1;
  public static final List<String> orderByAttrEmptyList = Arrays.asList("");
  public static final List<String> orderByDirEmptyList = Arrays.asList("");
  public static final int skipZero = 0;
  public static final String startAferEmpty = "";

  /**
   * Create an array of TSystem objects in memory
   * Names will be of format TestSys_K_NNN where K is the key and NNN runs from 000 to 999
   * We need a key because maven runs the tests in parallel so each set of systems created by an integration
   *   test will need its own namespace.
   * @param n number of systems to create
   * @return array of TSystem objects
   */
  public static TSystem[] makeSystems(int n, String key)
  {
    TSystem[] systems = new TSystem[n];
    // Create DTN systems for other systems to reference. Otherwise some system definitions are not valid.
    dtnSystem1 = new TSystem(-1, tenantName, dtnSystemId1, "DTN System1 for tests", TSystem.SystemType.LINUX, owner1,
            dtnSystemValidHostname, isEnabledTrue,"effUserDtn1", prot1.getAuthnMethod(), "bucketDtn1", "/root/dtn1",
            prot1.getTransferMethods(), prot1.getPort(), prot1.isUseProxy(), prot1.getProxyHost(), prot1.getProxyPort(),
            dtnSystemIdNull, dtnMountPointNull, dtnMountSourcePathNull, isDtnTrue,
            canExecFalse, jobWorkingDirNull, jobEnvVariablesNull, jobMaxJobs1, jobMaxJobsPerUser1, jobIsBatchFalse,
            batchSchedulerNull, queueNameNull, tags1, notes1, uuidNull , isDeletedFalse, createdNull, updatedNull);
    dtnSystem2 = new TSystem(-1, tenantName, dtnSystemId2, "DTN System2 for tests", TSystem.SystemType.LINUX, owner1,
            dtnSystemValidHostname, isEnabledTrue,"effUserDtn2", prot2.getAuthnMethod(), "bucketDtn2", "/root/dtn2",
            prot2.getTransferMethods(), prot2.getPort(), prot2.isUseProxy(), prot2.getProxyHost(), prot2.getProxyPort(),
            dtnSystemIdNull, dtnMountPointNull, dtnMountSourcePathNull, isDtnTrue,
            canExecFalse, jobWorkingDirNull, jobEnvVariablesNull, jobMaxJobs2, jobMaxJobsPerUser2, jobIsBatchFalse,
            batchSchedulerNull, queueNameNull, tags2, notes2, uuidNull , isDeletedFalse, createdNull, updatedNull);
    for (int i = 0; i < n; i++)
    {
      // Suffix which should be unique for each system within each integration test
      String iStr = String.format("%03d", i+1);
      String suffix = key + "_" + iStr;
      String name = getSysName(key, i+1);
      String hostName = "host" + key + iStr + ".test.org";
      // Constructor initializes all attributes except for JobCapabilities and Credential
      systems[i] = new TSystem(-1, tenantName, name, description1+suffix, TSystem.SystemType.LINUX, owner1,
              hostName, isEnabledTrue,effectiveUserId1+suffix, prot1.getAuthnMethod(), "bucket"+suffix, "/root"+suffix,
              prot1.getTransferMethods(), prot1.getPort(), prot1.isUseProxy(), prot1.getProxyHost(), prot1.getProxyPort(),
              dtnSystemId1, dtnMountPoint1, dtnMountSourcePath1, isDtnFalse,
              canExecTrue, "jobWorkDir"+suffix, jobEnvVariables1, jobMaxJobs1, jobMaxJobsPerUser1, jobIsBatchTrue,
              batchScheduler1, queueA1.getName(), tags1, notes1, uuidNull, isDeletedFalse, createdNull, updatedNull);
      systems[i].setJobRuntimes(runtimeList1);
      systems[i].setBatchLogicalQueues(logicalQueueList1);
      systems[i].setJobCapabilities(capList1);
    }
    return systems;
  }

  /**
   * Create a TSystem in memory with minimal attributes set based on TSystem given
   *   id, systemType, host, defaultAuthnMethod, canExec
   *   and since sytemType is LINUX must also set rootDir
   * NOTE: many args to constructor are primitives so cannot be set to null.
   */
  public static TSystem makeMinimalSystem(TSystem tSys)
  {
    return new TSystem(-1, tenantName, tSys.getId(), null, tSys.getSystemType(), null,
              hostMinimalId, isEnabledTrue, null, tSys.getDefaultAuthnMethod(), null, rootDir1,
              null, prot1.getPort(), prot1.isUseProxy(), null, prot1.getProxyPort(),
              null, null, null, isDtnFalse,
              canExecFalse, null, null, jobMaxJobs1, jobMaxJobsPerUser1, jobIsBatchFalse,
              null, null, null, null, uuidNull, isDeletedFalse, null, null);
  }

  /**
   * Create a PatchSystem in memory for use in testing.
   * All attributes are to be updated.
   */
  public static PatchSystem makePatchSystemFull()
  {
    return new PatchSystem(description2, hostname2, effectiveUserId2,
            prot2.getAuthnMethod(), prot2.getTransferMethods(),
            prot2.getPort(), prot2.isUseProxy(), prot2.getProxyHost(), prot2.getProxyPort(),
            dtnSystemId2, dtnMountPoint2, dtnMountSourcePath2, runtimeList2, jobWorkingDir2, jobEnvVariables2,
            jobMaxJobs2, jobMaxJobsPerUser2, jobIsBatchTrue, batchScheduler2, logicalQueueList2, batchDefaultLogicalQueue2,
            capList2, tags2, notes2);
  }

  /**
   * Create a PatchSystem in memory for use in testing.
   * Some attributes are to be updated: description, authnMethod, dtnMountPoint, runtimeList, jobMaxJobsPerUser
   */
  public static PatchSystem makePatchSystemPartial()
  {
    return new PatchSystem(description2, hostnameNull, effectiveUserIdNull,
            prot2.getAuthnMethod(), txfrMethodsNull, portNull, userProxyNull, proxyHostNull, proxyPortNull,
            dtnSystemIdNull, dtnMountPoint2, dtnMountSourcePathNull, runtimeList2, jobWorkingDirNull, jobEnvVariablesNull,
            jobMaxJobsNull, jobMaxJobsPerUser2, jobIsBatchNull, batchSchedulerNull, logicalQueueListNull, batchDefaultLogicalQueueNull,
            capListNull, tagsNull, notesNull);
  }

  public static String getSysName(String key, int idx)
  {
    String suffix = key + "_" + String.format("%03d", idx);
    return sysNamePrefix + "_" + suffix;
  }
}
