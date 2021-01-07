package edu.utexas.tacc.tapis.systems;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import edu.utexas.tacc.tapis.shared.utils.TapisGsonUtils;
import edu.utexas.tacc.tapis.systems.model.Capability;
import edu.utexas.tacc.tapis.systems.model.JobRuntime;
import edu.utexas.tacc.tapis.systems.model.LogicalQueue;
import edu.utexas.tacc.tapis.systems.model.TSystem;
import edu.utexas.tacc.tapis.systems.model.TSystem.AuthnMethod;
import edu.utexas.tacc.tapis.systems.model.TSystem.TransferMethod;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/*
 * Utilities and data for integration testing
 */
public final class IntegrationUtils
{
  // Test data
  public static final String tenantName = "dev";
  public static final String owner1 = "owner1";
  public static final String owner2 = "owner2";
  public static final String apiUser = "testApiUser";
  public static final String sysNamePrefix = "TestSys";
  public static final Gson gson =  TapisGsonUtils.getGson();
  public static final List<TransferMethod> txfrMethodsList = new ArrayList<>(List.of(TransferMethod.SFTP, TransferMethod.S3));
  public static final List<TransferMethod> txfrMethodsEmpty = new ArrayList<>();
  public static final boolean isEnabled = true;
  public static final boolean isDtn = false;
  public static final boolean canExec = true;
  public static final String dtnSystemId = "fakeDTNSystem";
  public static final String dtnMountPoint = "/fake/mountpoint";
  public static final String dtnMountSourcePath = "/fake/mountsourcepath";
  public static final String jobWorkingDir = "/fake/job/working_dir";
  public static final String batchScheduler = "SLURM";
  public static final String batchDefaultLogicalQueue = "fakeLogicalQueue";
//  public static final KeyValuePair kv1 = new KeyValuePair("a","b");
//  public static final KeyValuePair kv2 = new KeyValuePair("HOME","/home/testuser2");
//  public static final KeyValuePair kv3 = new KeyValuePair("TMP","/tmp");
//  public static final List<KeyValueString> jobEnvVariables = new ArrayList<>(List.of(kv1,kv2,kv3));
  public static final String[] jobEnvVariables = {"a=b", "HOME=/home/testuser2", "TMP=/tmp"};
  public static final boolean jobIsBatch = true;
  public static final int jobMaxJobs = -1;
  public static final int jobMaxJobsPerUser = -1;
  public static final String[] tags = {"value1", "value2", "a",
    "Long tag (1 3 2) special chars [_ $ - & * % @ + = ! ^ ? < > , . ( ) { } / \\ | ]. Backslashes must be escaped."};
  public static final Object notes = TapisGsonUtils.getGson().fromJson("{\"project\": \"myproj1\", \"testdata\": \"abc1\"}", JsonObject.class);
  public static final JsonObject notesObj = (JsonObject) notes;
  public static final Protocol prot1 = new Protocol(AuthnMethod.PKI_KEYS, txfrMethodsList, 22, false, "", 0);
  public static final Protocol prot2 = new Protocol(AuthnMethod.PASSWORD, txfrMethodsList, 0, true, "localhost",2222);
  public static final String scrubbedJson = "{}";

  public static final JobRuntime runtimeA1 = new JobRuntime(JobRuntime.RuntimeType.DOCKER, "0.0.1A1");
  public static final JobRuntime runtimeB1 = new JobRuntime(JobRuntime.RuntimeType.SINGULARITY, "0.0.1B1");
  public static final List<JobRuntime> runtimeList1 = new ArrayList<>(List.of(runtimeA1, runtimeB1));
  public static final LogicalQueue queueA1 = new LogicalQueue("lqA1","hqA1", 1, 1, 1, 1, 1, 1);
  public static final LogicalQueue queueB1 = new LogicalQueue("lqB1","hqB1", 2, 2, 2, 2, 2, 2);
  public static final LogicalQueue queueC1 = new LogicalQueue("lqC1","hqC1", 3, 3, 3, 3, 3, 3);
  public static final List<LogicalQueue> queueList1 = new ArrayList<>(List.of(queueA1, queueB1, queueC1));
  public static final LogicalQueue queueA2 = new LogicalQueue("lqA2","hqA2", 10, 10, 10, 10,10, 10);
  public static final LogicalQueue queueB2 = new LogicalQueue("lqB2","hqB1", 20, 20, 20, 20,20, 20);
  public static final List<LogicalQueue> queueList2 = new ArrayList<>(List.of(queueA2, queueB2));

  public static final Capability capA = new Capability(Capability.Category.SCHEDULER, "Type",
                                                       Capability.Datatype.STRING, Capability.DEFAULT_PRECEDENCE, "Slurm");
  public static final Capability capB = new Capability(Capability.Category.HARDWARE, "CoresPerNode",
                                                       Capability.Datatype.INTEGER, Capability.DEFAULT_PRECEDENCE, "4");
  public static final Capability capC = new Capability(Capability.Category.SOFTWARE, "OpenMP",
                                                       Capability.Datatype.STRING, Capability.DEFAULT_PRECEDENCE, "4.5");
  public static final Capability capD = new Capability(Capability.Category.CONTAINER, "Singularity",
                                                       Capability.Datatype.STRING, Capability.DEFAULT_PRECEDENCE, null);
  public static final List<Capability> capList1 = new ArrayList<>(List.of(capA, capB, capC, capD));
  public static final Capability capA1 = new Capability(Capability.Category.SCHEDULER, "Type",
          Capability.Datatype.STRING, Capability.DEFAULT_PRECEDENCE, "PBS");
  public static final Capability capB1 = new Capability(Capability.Category.HARDWARE, "CoresPerNode",
          Capability.Datatype.INTEGER, Capability.DEFAULT_PRECEDENCE, "8");
  public static final Capability capC1 = new Capability(Capability.Category.SOFTWARE, "MPI",
          Capability.Datatype.STRING, Capability.DEFAULT_PRECEDENCE, "3.1");
  public static final Capability capD1 = new Capability(Capability.Category.CONTAINER, "Docker",
          Capability.Datatype.STRING, Capability.DEFAULT_PRECEDENCE, null);
  public static final List<Capability> capList2 = new ArrayList<>(List.of(capA1, capB1, capC1, capD1));

  public static final boolean isDeleted = false;
  public static final String importRefId = null;
  public static final Instant created = null;
  public static final Instant updated = null;
  public static final int qMaxJobs = -1;
  public static final int qMaxJobsPerUser = -1;
  public static final int qMaxNodeCount = -1;
  public static final int qMaxCoresPerNode = -1;
  public static final int qMaxMemoryMB = -1;
  public static final int qMaxMinutes = -1;

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
    for (int i = 0; i < n; i++)
    {
      // Suffix which should be unique for each system within each integration test
      String suffix = key + "_" + String.format("%03d", i+1);
      String name = getSysName(key, i+1);
      // Constructor initializes all attributes except for JobCapabilities and Credential
      systems[i] = new TSystem(-1, tenantName, name, "description "+suffix, TSystem.SystemType.LINUX, owner1,
              "host"+suffix, isEnabled,"effUser"+suffix, prot1.getAuthnMethod(), "bucket"+suffix, "/root"+suffix,
              prot1.getTransferMethods(), prot1.getPort(), prot1.isUseProxy(), prot1.getProxyHost(), prot1.getProxyPort(),
              dtnSystemId, dtnMountPoint, dtnMountSourcePath, isDtn,
              canExec, "jobWorkDir"+suffix, jobEnvVariables, jobMaxJobs, jobMaxJobsPerUser, jobIsBatch,
              "batchScheduler"+suffix, queueA1.getName(), tags, notes, importRefId , isDeleted, created, updated);
      systems[i].setJobRuntimes(runtimeList1);
      systems[i].setBatchLogicalQueues(queueList1);
      systems[i].setJobCapabilities(capList1);
    }
    return systems;
  }

  /**
   * Create a TSystem in memory with minimal attributes set based on TSystem given
   *   id, systemType, host, defaultAuthnMethod, canExec
   * NOTE: many args to constructor are primitives so cannot be set to null.
   */
  public static TSystem makeMinimalSystem(TSystem tSys)
  {
    return new TSystem(-1, tenantName, tSys.getId(), null, tSys.getSystemType(), null,
              "hostMinimal", isEnabled, null, tSys.getDefaultAuthnMethod(), null, null,
              null, prot1.getPort(), prot1.isUseProxy(), null, prot1.getProxyPort(),
              null, null, null, isDtn,
              canExec, null, null, jobMaxJobs, jobMaxJobsPerUser, jobIsBatch,
              null, null, null, null, null, isDeleted, null, null);
  }

  public static String getSysName(String key, int idx)
  {
    String suffix = key + "_" + String.format("%03d", idx);
    return sysNamePrefix + "_" + suffix;
  }
}
