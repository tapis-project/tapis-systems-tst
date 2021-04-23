package edu.utexas.tacc.tapis.systems.api.requests;

import edu.utexas.tacc.tapis.systems.api.utils.ApiUtils;
import edu.utexas.tacc.tapis.systems.model.Capability;
import edu.utexas.tacc.tapis.systems.model.Credential;
import edu.utexas.tacc.tapis.systems.api.utils.KeyValuePair;
import edu.utexas.tacc.tapis.systems.model.JobRuntime;
import edu.utexas.tacc.tapis.systems.model.LogicalQueue;
import edu.utexas.tacc.tapis.systems.model.TSystem.AuthnMethod;
import edu.utexas.tacc.tapis.systems.model.TSystem.SystemType;
import edu.utexas.tacc.tapis.systems.model.TSystem.SchedulerType;

import java.util.List;

import static edu.utexas.tacc.tapis.systems.model.TSystem.DEFAULT_EFFECTIVEUSERID;
import static edu.utexas.tacc.tapis.systems.model.TSystem.DEFAULT_ENABLED;
import static edu.utexas.tacc.tapis.systems.model.TSystem.DEFAULT_JOBENV_VARIABLES;
import static edu.utexas.tacc.tapis.systems.model.TSystem.DEFAULT_JOBMAXJOBS;
import static edu.utexas.tacc.tapis.systems.model.TSystem.DEFAULT_JOBMAXJOBSPERUSER;
import static edu.utexas.tacc.tapis.systems.model.TSystem.DEFAULT_NOTES;
import static edu.utexas.tacc.tapis.systems.model.TSystem.DEFAULT_OWNER;
import static edu.utexas.tacc.tapis.systems.model.TSystem.DEFAULT_PORT;
import static edu.utexas.tacc.tapis.systems.model.TSystem.DEFAULT_PROXYHOST;
import static edu.utexas.tacc.tapis.systems.model.TSystem.DEFAULT_PROXYPORT;
import static edu.utexas.tacc.tapis.systems.model.TSystem.DEFAULT_USEPROXY;
import static edu.utexas.tacc.tapis.systems.model.TSystem.EMPTY_STR_ARRAY;

/*
 * Class representing all system attributes that can be set in an incoming create request json body
 */
public final class ReqCreateSystem
{
  public String id;       // Name of the system
  public String description; // Full description of the system
  public SystemType systemType; // Type of system, e.g. LINUX, OBJECT_STORE
  public String owner = DEFAULT_OWNER;      // User who owns the system and has full privileges
  public String host;       // Host name or IP address
  public boolean enabled = DEFAULT_ENABLED; // Indicates if systems is currently enabled
  public String effectiveUserId = DEFAULT_EFFECTIVEUSERID; // User to use when accessing system, may be static or dynamic
  public AuthnMethod defaultAuthnMethod; // How access authorization is handled by default
  public Credential authnCredential; // Credential to be stored in or retrieved from the Security Kernel
  public String bucketName; // Name of bucket for system of type OBJECT_STORE
  public String rootDir;    // Effective root directory for system of type LINUX, can also be used for system of type OBJECT_STORE
  public int port = DEFAULT_PORT;          // Port number used to access the system
  public boolean useProxy = DEFAULT_USEPROXY;  // Indicates if a system should be accessed through a proxy
  public String proxyHost = DEFAULT_PROXYHOST;  // Name or IP address of proxy host
  public int proxyPort = DEFAULT_PROXYPORT;     // Port number for proxy host
  public String dtnSystemId;
  public String dtnMountPoint;
  public String dtnMountSourcePath;
  public boolean isDtn;
  public boolean canExec; // Indicates if system will be used to execute jobs
  public List<JobRuntime> jobRuntimes;
  public String jobWorkingDir; // Parent directory from which jobs are run, inputs and application assets are staged
  public List<KeyValuePair> jobEnvVariables = ApiUtils.getKeyValuesAsList(DEFAULT_JOBENV_VARIABLES);
  public int jobMaxJobs = DEFAULT_JOBMAXJOBS;
  public int jobMaxJobsPerUser = DEFAULT_JOBMAXJOBSPERUSER;
  public boolean jobIsBatch;
  public SchedulerType batchScheduler;
  public List<LogicalQueue> batchLogicalQueues;
  public String batchDefaultLogicalQueue;
  public List<Capability> jobCapabilities; // List of job related capabilities supported by the system
  public String[] tags = EMPTY_STR_ARRAY;       // List of arbitrary tags as strings
  public Object notes = DEFAULT_NOTES;      // Simple metadata as json
}
