package edu.utexas.tacc.tapis.systems.api.model;

// TODO/TBD Create API model classes for Capability, Credential, JobRuntime, LogicalQueue
//          or use lib model classes? Apps service has some of each, review why that is.
import edu.utexas.tacc.tapis.systems.model.Capability;
import edu.utexas.tacc.tapis.systems.model.Credential;
import edu.utexas.tacc.tapis.systems.model.JobRuntime;
import edu.utexas.tacc.tapis.systems.model.LogicalQueue;

import static edu.utexas.tacc.tapis.systems.model.TSystem.AuthnMethod;
import static edu.utexas.tacc.tapis.systems.model.TSystem.SystemType;
import static edu.utexas.tacc.tapis.systems.model.TSystem.TransferMethod;

import java.time.Instant;
import java.util.List;

/*
 * Class for a TSystem for use in API module
 */
@TSystemFilterView
public class TSystem
{
  public int seqId;
  public String tenant;
  public String id;
  public String description;
  public SystemType systemType;
  public String owner;
  public String host;
  public boolean enabled;
  public String effectiveUserId;
  public AuthnMethod defaultAuthnMethod;
  public Credential authnCredential;
  public String bucketName;
  public String rootDir;
  public List<TransferMethod> transferMethods;
  public int port;
  public boolean useProxy;
  public String proxyHost;
  public int proxyPort;
  public String dtnSystemId;
  public String dtnMountPoint;
  public String dtnMountSourcePath;
  public boolean isDtn;
  public boolean canExec;
  public List<JobRuntime> jobRuntimes;
  public String jobWorkingDir;
  public String[] jobEnvVariables;
  public int jobMaxJobs;
  public int jobMaxJobsPerUser;
  public boolean jobIsBatch;
  public String batchScheduler;
  public List<LogicalQueue> batchLogicalQueues;
  public String batchDefaultLogicalQueue;
  public List<Capability> jobCapabilities;
  public String[] tags;
  public Object notes;
  public String importRefId;
  public boolean deleted;
  public Instant created;
  public Instant updated;
}
