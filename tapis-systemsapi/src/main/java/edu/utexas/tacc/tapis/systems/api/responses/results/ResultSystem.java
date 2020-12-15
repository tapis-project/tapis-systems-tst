package edu.utexas.tacc.tapis.systems.api.responses.results;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import edu.utexas.tacc.tapis.shared.utils.JsonObjectSerializer;
import edu.utexas.tacc.tapis.systems.api.utils.ApiUtils;
import edu.utexas.tacc.tapis.systems.api.utils.KeyValueString;
import edu.utexas.tacc.tapis.systems.model.Capability;
import edu.utexas.tacc.tapis.systems.model.Credential;
import edu.utexas.tacc.tapis.systems.model.LogicalQueue;
import edu.utexas.tacc.tapis.systems.model.TSystem;

import java.util.List;

/*
    Class representing a returned TSystem result
 */
public final class ResultSystem
{
  public int seqId;
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
  public String jobWorkingDir;
  public List<KeyValueString> jobEnvVariables;
  public int jobMaxJobs;
  public int jobMaxJobsPerUser;
  public boolean jobIsBatch;
  public String batchScheduler;
  public List<LogicalQueue> batchLogicalQueues;
  public String batchDefaultLogicalQueue;
  public List<Capability> jobCapabilities;
  public String[] tags;
  // Json objects require special serializer for Jackson to handle properly in outgoing response.
  @JsonSerialize(using = JsonObjectSerializer.class)
  public Object notes;
  public String refImportId;

  // Zero arg constructor needed to use jersey's SelectableEntityFilteringFeature
  public ResultSystem() { }

  public ResultSystem(TSystem s)
  {
    // Convert jobEnvVariables from array of "key=value" to list of KeyValueString
    jobEnvVariables = ApiUtils.getKeyValuesAsList(s.getJobEnvVariables());
    // All other attributes come directly from TSystem
    seqId = s.getSeqId();
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
    isDtn = s.getIsDtn();
    canExec = s.getCanExec();
    jobWorkingDir = s.getJobWorkingDir();
    jobMaxJobs = s.getJobMaxJobs();
    jobMaxJobsPerUser = s.getJobMaxJobsPerUser();
    jobIsBatch = s.getJobIsBatch();
    batchScheduler = s.getBatchScheduler();
    batchLogicalQueues = s.getBatchLogicalQueues();
    batchDefaultLogicalQueue = s.getBatchDefaultLogicalQueue();
    jobCapabilities = s.getJobCapabilities();
    tags = s.getTags();
    notes = s.getNotes();
    refImportId = s.getImportRefId();
  }
}
