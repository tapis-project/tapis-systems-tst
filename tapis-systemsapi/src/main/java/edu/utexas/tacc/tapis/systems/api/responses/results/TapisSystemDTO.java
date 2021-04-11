package edu.utexas.tacc.tapis.systems.api.responses.results;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.gson.JsonObject;

import edu.utexas.tacc.tapis.shared.utils.JsonObjectSerializer;
import edu.utexas.tacc.tapis.systems.api.utils.ApiUtils;
import edu.utexas.tacc.tapis.systems.api.utils.KeyValuePair;
import edu.utexas.tacc.tapis.systems.model.Capability;
import edu.utexas.tacc.tapis.systems.model.Credential;
import edu.utexas.tacc.tapis.systems.model.JobRuntime;
import edu.utexas.tacc.tapis.systems.model.LogicalQueue;
import edu.utexas.tacc.tapis.systems.model.TSystem;

import static edu.utexas.tacc.tapis.systems.model.TSystem.CANEXEC_FIELD;
import static edu.utexas.tacc.tapis.systems.model.TSystem.DEFAULT_AUTHN_METHOD_FIELD;
import static edu.utexas.tacc.tapis.systems.model.TSystem.EFFUSRID_FIELD;
import static edu.utexas.tacc.tapis.systems.model.TSystem.HOST_FIELD;
import static edu.utexas.tacc.tapis.systems.model.TSystem.ID_FIELD;
import static edu.utexas.tacc.tapis.systems.model.TSystem.OWNER_FIELD;
import static edu.utexas.tacc.tapis.systems.model.TSystem.SYSTEM_TYPE_FIELD;

/*
    Class representing a TSystem result to be returned
 */
public final class TapisSystemDTO
{

  private static final Set<String> SUMMARY_ATTRS =
          new HashSet<>(Set.of(ID_FIELD, SYSTEM_TYPE_FIELD, OWNER_FIELD, HOST_FIELD,
                               EFFUSRID_FIELD, DEFAULT_AUTHN_METHOD_FIELD, CANEXEC_FIELD));

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
    if (s.getBatchLogicalQueues() != null)
      for (LogicalQueue q : s.getBatchLogicalQueues())
      {
        batchLogicalQueues.add(new ResultLogicalQueue(q));
      }
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

  /**
   * Create a JsonObject containing the id attribute and any attribute in the selectSet that matches the name
   * of a public field in this class
   * If selectSet is null or empty then all attributes are included.
   * If selectSet contains one item:
   *    and that item is "allAttributes" then all attributes are included.
   *    and that item is "summaryAttributes" then only summary attributes are included.
   * @return JsonObject containing attributes in the select set.
   */
  public JsonObject getDisplayObject(Set<String> selectSet)
  {
    var retObj = new JsonObject();
    Field[] fields = TapisSystemDTO.class.getDeclaredFields();
    for (Field f : fields) System.out.println("Found field: " + f.getName());
//    for (Field f : fields)
//    {
//      attributeSet.add(f.getName());
//    }
    retObj.addProperty(ID_FIELD, id);
    retObj.addProperty(SYSTEM_TYPE_FIELD, systemType.name());
    retObj.addProperty(OWNER_FIELD, owner);
    retObj.addProperty(HOST_FIELD, host);
    retObj.addProperty(EFFUSRID_FIELD, effectiveUserId);
    retObj.addProperty(DEFAULT_AUTHN_METHOD_FIELD, defaultAuthnMethod.name());
    retObj.addProperty(CANEXEC_FIELD, Boolean.toString(canExec));
    return retObj;
  }
}
