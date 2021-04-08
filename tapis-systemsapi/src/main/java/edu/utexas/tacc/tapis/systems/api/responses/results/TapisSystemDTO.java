package edu.utexas.tacc.tapis.systems.api.responses.results;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import edu.utexas.tacc.tapis.shared.utils.JsonObjectSerializer;
import edu.utexas.tacc.tapis.systems.api.utils.ApiUtils;
import edu.utexas.tacc.tapis.systems.api.utils.KeyValuePair;
import edu.utexas.tacc.tapis.systems.model.Capability;
import edu.utexas.tacc.tapis.systems.model.Credential;
import edu.utexas.tacc.tapis.systems.model.JobRuntime;
import edu.utexas.tacc.tapis.systems.model.LogicalQueue;
import edu.utexas.tacc.tapis.systems.model.TSystem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/*
    Class representing a TSystem result to be returned
 */
public final class TapisSystemDTO
{
  public String tenant;
  public String id;
  public String description;
  public TSystem.SystemType systemType;
  public String owner;
  public String host;
  public boolean enabled;
  public String effectiveUserId;
  public TSystem.AuthnMethod defaultAuthnMethod;
  public Credential authnCredential;
  public String bucketName;
  public String rootDir;
  public List<TSystem.TransferMethod> transferMethods;
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
  public List<KeyValuePair> jobEnvVariables;
  public int jobMaxJobs;
  public int jobMaxJobsPerUser;
  public boolean jobIsBatch;
  public TSystem.SchedulerType batchScheduler;
  public List<ResultLogicalQueue> batchLogicalQueues;
  public String batchDefaultLogicalQueue;
  public List<Capability> jobCapabilities;
  public String[] tags;
  // Json objects require special serializer for Jackson to handle properly in outgoing response.
  @JsonSerialize(using = JsonObjectSerializer.class)
  public Object notes;
  public UUID uuid;
  public boolean deleted;
  public Instant created;
  public Instant updated;

  public TapisSystemDTO(TSystem s)
  {
    // Convert jobEnvVariables from array of "key=value" to list of KeyValuePair
    jobEnvVariables = ApiUtils.getKeyValuesAsList(s.getJobEnvVariables());
    // All other attributes come directly from TSystem
    tenant = s.getTenant();
    id = s.getId();
    description = s.getDescription();
    systemType = s.getSystemType();
    owner = s.getOwner();
    host = s.getHost();
    enabled = s.isEnabled();
    effectiveUserId = s.getEffectiveUserId();
    defaultAuthnMethod = s.getDefaultAuthnMethod();
    authnCredential = s.getAuthnCredential();
    bucketName = s.getBucketName();
    rootDir = s.getRootDir();
    transferMethods = s.getTransferMethods();
    port = s.getPort();
    useProxy = s.isUseProxy();
    proxyHost = s.getProxyHost();
    proxyPort = s.getProxyPort();
    dtnSystemId = s.getDtnSystemId();
    dtnMountPoint = s.getDtnMountPoint();
    dtnMountSourcePath = s.getDtnMountSourcePath();
    isDtn = s.isDtn();
    canExec = s.getCanExec();
    jobRuntimes = s.getJobRuntimes();
    jobWorkingDir = s.getJobWorkingDir();
    jobMaxJobs = s.getJobMaxJobs();
    jobMaxJobsPerUser = s.getJobMaxJobsPerUser();
    jobIsBatch = s.getJobIsBatch();
    batchScheduler = s.getBatchScheduler();
    batchLogicalQueues = new ArrayList<>();
    if (s.getBatchLogicalQueues() != null )
      for (LogicalQueue q : s.getBatchLogicalQueues()) { batchLogicalQueues.add(new ResultLogicalQueue(q)); }
    batchDefaultLogicalQueue = s.getBatchDefaultLogicalQueue();
    jobCapabilities = s.getJobCapabilities();
    tags = s.getTags();
    notes = s.getNotes();
    uuid = s.getUuid();
    deleted = s.isDeleted();
    created = s.getCreated();
    updated = s.getUpdated();
    // Check for -1 in max values and return Integer.MAX_VALUE instead.
    //   As requested by Jobs service.
    if (jobMaxJobs < 0) jobMaxJobs = Integer.MAX_VALUE;
    if (jobMaxJobsPerUser < 0) jobMaxJobsPerUser = Integer.MAX_VALUE;
  }
}
