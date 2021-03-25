package edu.utexas.tacc.tapis.systems.api.requests;

import edu.utexas.tacc.tapis.systems.model.Capability;
import edu.utexas.tacc.tapis.systems.api.utils.KeyValuePair;
import edu.utexas.tacc.tapis.systems.model.JobRuntime;
import edu.utexas.tacc.tapis.systems.model.LogicalQueue;
import edu.utexas.tacc.tapis.systems.model.TSystem.AuthnMethod;
import edu.utexas.tacc.tapis.systems.model.TSystem.TransferMethod;
import edu.utexas.tacc.tapis.systems.model.TSystem.SchedulerType;

import java.util.List;

import static edu.utexas.tacc.tapis.systems.model.TSystem.DEFAULT_JOBMAXJOBS;
import static edu.utexas.tacc.tapis.systems.model.TSystem.DEFAULT_JOBMAXJOBSPERUSER;

/*
 * Class representing all system attributes that can be set in an incoming patch request json body
 */
public final class ReqUpdateSystem
{
  public String description;
  public String host;
  public String effectiveUserId;
  public AuthnMethod defaultAuthnMethod;
  public List<TransferMethod> transferMethods;
  public Integer port;
  public Boolean useProxy;
  public String proxyHost;
  public Integer proxyPort;
  public String dtnSystemId;
  public String dtnMountPoint;
  public String dtnMountSourcePath;
  public List<JobRuntime> jobRuntimes;
  public String jobWorkingDir;
  public List<KeyValuePair> jobEnvVariables;
  public int jobMaxJobs = DEFAULT_JOBMAXJOBS;
  public int jobMaxJobsPerUser = DEFAULT_JOBMAXJOBSPERUSER;
  public boolean jobIsBatch;
  public SchedulerType batchScheduler;
  public List<LogicalQueue> batchLogicalQueues;
  public String batchDefaultLogicalQueue;
  public List<Capability> jobCapabilities;
  public String[] tags;
  public Object notes;
}
