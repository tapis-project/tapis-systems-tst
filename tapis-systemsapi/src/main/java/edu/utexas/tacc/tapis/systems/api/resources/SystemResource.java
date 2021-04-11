package edu.utexas.tacc.tapis.systems.api.resources;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import edu.utexas.tacc.tapis.client.shared.exceptions.TapisClientException;
import edu.utexas.tacc.tapis.search.SearchUtils;
import edu.utexas.tacc.tapis.shared.TapisConstants;
import edu.utexas.tacc.tapis.shared.exceptions.TapisException;
import edu.utexas.tacc.tapis.shared.threadlocal.OrderBy;
import edu.utexas.tacc.tapis.shared.threadlocal.SearchParameters;
import edu.utexas.tacc.tapis.shared.utils.TapisUtils;
import edu.utexas.tacc.tapis.sharedapi.dto.ResponseWrapper;
import edu.utexas.tacc.tapis.sharedapi.responses.RespAbstract;
import edu.utexas.tacc.tapis.systems.api.requests.ReqImportSGCIResource;
import edu.utexas.tacc.tapis.systems.api.requests.ReqUpdateSGCISystem;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.grizzly.http.server.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.utexas.tacc.tapis.sharedapi.security.AuthenticatedUser;
import edu.utexas.tacc.tapis.systems.model.PatchSystem;
import edu.utexas.tacc.tapis.shared.exceptions.TapisJSONException;
import edu.utexas.tacc.tapis.shared.i18n.MsgUtils;
import edu.utexas.tacc.tapis.shared.schema.JsonValidator;
import edu.utexas.tacc.tapis.shared.schema.JsonValidatorSpec;
import edu.utexas.tacc.tapis.shared.threadlocal.TapisThreadContext;
import edu.utexas.tacc.tapis.shared.threadlocal.TapisThreadLocal;
import edu.utexas.tacc.tapis.shared.utils.TapisGsonUtils;
import edu.utexas.tacc.tapis.sharedapi.utils.RestUtils;
import edu.utexas.tacc.tapis.sharedapi.utils.TapisRestUtils;
import edu.utexas.tacc.tapis.sharedapi.responses.RespChangeCount;
import edu.utexas.tacc.tapis.sharedapi.responses.RespResourceUrl;
import edu.utexas.tacc.tapis.sharedapi.responses.results.ResultChangeCount;
import edu.utexas.tacc.tapis.sharedapi.responses.results.ResultResourceUrl;
import edu.utexas.tacc.tapis.systems.api.requests.ReqCreateSystem;
import edu.utexas.tacc.tapis.systems.api.requests.ReqUpdateSystem;
import edu.utexas.tacc.tapis.systems.api.responses.RespSystem;
import edu.utexas.tacc.tapis.systems.api.responses.RespSystems;
import edu.utexas.tacc.tapis.systems.api.utils.ApiUtils;
import edu.utexas.tacc.tapis.systems.model.TSystem;
import edu.utexas.tacc.tapis.systems.model.TSystem.AuthnMethod;
import edu.utexas.tacc.tapis.systems.service.SystemsService;

import static edu.utexas.tacc.tapis.systems.model.Credential.SECRETS_MASK;
import static edu.utexas.tacc.tapis.systems.model.TSystem.CAN_EXEC_FIELD;
import static edu.utexas.tacc.tapis.systems.model.TSystem.DEFAULT_AUTHN_METHOD_FIELD;
import static edu.utexas.tacc.tapis.systems.model.TSystem.EFFECTIVE_USER_ID_FIELD;
import static edu.utexas.tacc.tapis.systems.model.TSystem.HOST_FIELD;
import static edu.utexas.tacc.tapis.systems.model.TSystem.ID_FIELD;
import static edu.utexas.tacc.tapis.systems.model.TSystem.NOTES_FIELD;
import static edu.utexas.tacc.tapis.systems.model.TSystem.AUTHN_CREDENTIAL_FIELD;
import static edu.utexas.tacc.tapis.systems.model.TSystem.OWNER_FIELD;
import static edu.utexas.tacc.tapis.systems.model.TSystem.SYSTEM_TYPE_FIELD;

/*
 * JAX-RS REST resource for a Tapis System (edu.utexas.tacc.tapis.systems.model.TSystem)
 * jax-rs annotations map HTTP verb + endpoint to method invocation and map query parameters.
 * NOTE: Annotations for generating OpenAPI specification not currently used.
 *       Please see openapi-systems repo file SystemsAPI.yaml
 *       and note at top of SystemsResource.java
 */
@Path("/v3/systems")
public class SystemResource
{
  // ************************************************************************
  // *********************** Constants **************************************
  // ************************************************************************
  // Local logger.
  private static final Logger _log = LoggerFactory.getLogger(SystemResource.class);

  private static final String SYSTEMS_SVC = StringUtils.capitalize(TapisConstants.SERVICE_NAME_SYSTEMS);
  // Json schema resource files.
  private static final String FILE_SYSTEM_CREATE_REQUEST = "/edu/utexas/tacc/tapis/systems/api/jsonschema/SystemCreateRequest.json";
  private static final String FILE_SYSTEM_UPDATE_REQUEST = "/edu/utexas/tacc/tapis/systems/api/jsonschema/SystemUpdateRequest.json";
  private static final String FILE_SYSTEM_SEARCH_REQUEST = "/edu/utexas/tacc/tapis/systems/api/jsonschema/SystemSearchRequest.json";
  private static final String FILE_SYSTEM_MATCH_REQUEST = "/edu/utexas/tacc/tapis/systems/api/jsonschema/MatchConstraintsRequest.json";
  private static final String FILE_SYSTEM_IMPORTSGCI_REQUEST = "/edu/utexas/tacc/tapis/systems/api/jsonschema/SystemImportSGCIRequest.json";
  private static final String FILE_SYSTEM_UPDATESGCI_REQUEST = "/edu/utexas/tacc/tapis/systems/api/jsonschema/SystemUpdateSGCIRequest.json";

  // Message keys
  private static final String INVALID_JSON_INPUT = "NET_INVALID_JSON_INPUT";
  private static final String JSON_VALIDATION_ERR = "TAPIS_JSON_VALIDATION_ERROR";
  private static final String UPDATE_ERR = "SYSAPI_UPDATE_ERROR";
  private static final String CREATE_ERR = "SYSAPI_CREATE_ERROR";
  private static final String SELECT_ERR = "SYSAPI_SELECT_ERROR";
  private static final String LIB_UNAUTH = "SYSLIB_UNAUTH";
  private static final String API_UNAUTH = "SYSAPI_SYS_UNAUTH";
  private static final String TAPIS_FOUND = "TAPIS_FOUND";
  private static final String NOT_FOUND = "SYSAPI_NOT_FOUND";
  private static final String UPDATED = "SYSAPI_UPDATED";

  // Format strings
  private static final String SYS_CNT_STR = "%d systems";

  // Operation names
  private static final String OP_ENABLE = "enableSystem";
  private static final String OP_DISABLE = "disableSystem";
  private static final String OP_CHANGEOWNER = "changeSystemOwner";
  private static final String OP_DELETE = "deleteSystem";

  // Always return a nicely formatted response
  private static final boolean PRETTY = true;


  // Top level summary attributes to be included by default in some cases.
  public static final List<String> SUMMARY_ATTRS =
          new ArrayList<>(List.of(ID_FIELD, SYSTEM_TYPE_FIELD, OWNER_FIELD, HOST_FIELD,
                  EFFECTIVE_USER_ID_FIELD, DEFAULT_AUTHN_METHOD_FIELD, CAN_EXEC_FIELD));


  // ************************************************************************
  // *********************** Fields *****************************************
  // ************************************************************************
  @Context
  private HttpHeaders _httpHeaders;
  @Context
  private Application _application;
  @Context
  private UriInfo _uriInfo;
  @Context
  private SecurityContext _securityContext;
  @Context
  private ServletContext _servletContext;
  @Context
  private Request _request;

  // **************** Inject Services using HK2 ****************
  @Inject
  private SystemsService systemsService;

  // ************************************************************************
  // *********************** Public Methods *********************************
  // ************************************************************************

  /**
   * Create a system
   * @param payloadStream - request body
   * @param securityContext - user identity
   * @return response containing reference to created object
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response createSystem(InputStream payloadStream,
                               @Context SecurityContext securityContext)
  {
    String opName = "createSystem";

    // Note that although the following approximately 30 lines of code is very similar for many endpoints the slight
    //   variations and use of fetched data makes it difficult to refactor into common routines. Common routines
    //   might make the code even more complex and difficult to follow.

    // Trace this request.
    if (_log.isTraceEnabled()) logRequest(opName);

    // ------------------------- Retrieve and validate thread context -------------------------
    TapisThreadContext threadContext = TapisThreadLocal.tapisThreadContext.get();
    // Check that we have all we need from the context, the tenant name and apiUserId
    // Utility method returns null if all OK and appropriate error response if there was a problem.
    Response resp = ApiUtils.checkContext(threadContext, PRETTY);
    if (resp != null) return resp;

    // Get AuthenticatedUser which contains jwtTenant, jwtUser, oboTenant, oboUser, etc.
    AuthenticatedUser authenticatedUser = (AuthenticatedUser) securityContext.getUserPrincipal();

    // ------------------------- Extract and validate payload -------------------------
    // Read the payload into a string.
    String rawJson;
    String msg;
    try { rawJson = IOUtils.toString(payloadStream, StandardCharsets.UTF_8); }
    catch (Exception e)
    {
      msg = MsgUtils.getMsg(INVALID_JSON_INPUT, opName , e.getMessage());
      _log.error(msg, e);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }
    // Create validator specification and validate the json against the schema
    JsonValidatorSpec spec = new JsonValidatorSpec(rawJson, FILE_SYSTEM_CREATE_REQUEST);
    try { JsonValidator.validate(spec); }
    catch (TapisJSONException e)
    {
      msg = MsgUtils.getMsg(JSON_VALIDATION_ERR, e.getMessage());
      _log.error(msg, e);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }

    ReqCreateSystem req;
    // ------------------------- Create a TSystem from the json and validate constraints -------------------------
    try { req = TapisGsonUtils.getGson().fromJson(rawJson, ReqCreateSystem.class); }
    catch (JsonSyntaxException e)
    {
      msg = MsgUtils.getMsg(INVALID_JSON_INPUT, opName, e.getMessage());
      _log.error(msg, e);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }

    // Create a TSystem from the request
    TSystem tSystem = createTSystemFromRequest(req, rawJson);

    // Mask any secret info that might be contained in rawJson
    String scrubbedJson = rawJson;
    if (tSystem.getAuthnCredential() != null) scrubbedJson = maskCredSecrets(rawJson);
    if (_log.isTraceEnabled()) _log.trace(ApiUtils.getMsgAuth("SYSAPI_CREATE_TRACE", authenticatedUser, scrubbedJson));

    // Fill in defaults and check constraints on TSystem attributes
    tSystem = TSystem.setDefaults(tSystem);
    resp = validateTSystem(tSystem, authenticatedUser);
    if (resp != null) return resp;

    // ---------------------------- Make service call to create the system -------------------------------
    // Update tenant name and pull out system name for convenience
    tSystem.setTenant(authenticatedUser.getTenantId());
    String systemId = tSystem.getId();
    try
    {
      systemsService.createSystem(authenticatedUser, tSystem, scrubbedJson);
    }
    catch (IllegalStateException e)
    {
      if (e.getMessage().contains("SYSLIB_SYS_EXISTS"))
      {
        // IllegalStateException with msg containing SYS_EXISTS indicates object exists - return 409 - Conflict
        msg = ApiUtils.getMsgAuth("SYSAPI_SYS_EXISTS", authenticatedUser, systemId);
        _log.warn(msg);
        return Response.status(Status.CONFLICT).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
      }
      else if (e.getMessage().contains(LIB_UNAUTH))
      {
        // IllegalStateException with msg containing SYS_UNAUTH indicates operation not authorized for apiUser - return 401
        msg = ApiUtils.getMsgAuth(API_UNAUTH, authenticatedUser, systemId, opName);
        _log.warn(msg);
        return Response.status(Status.UNAUTHORIZED).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
      }
      else
      {
        // IllegalStateException indicates an Invalid TSystem was passed in
        msg = ApiUtils.getMsgAuth(CREATE_ERR, authenticatedUser, systemId, e.getMessage());
        _log.error(msg);
        return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
      }
    }
    catch (IllegalArgumentException e)
    {
      // IllegalArgumentException indicates somehow a bad argument made it this far
      msg = ApiUtils.getMsgAuth(CREATE_ERR, authenticatedUser, systemId, e.getMessage());
      _log.error(msg);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }
    catch (Exception e)
    {
      msg = ApiUtils.getMsgAuth(CREATE_ERR, authenticatedUser, systemId, e.getMessage());
      _log.error(msg, e);
      return Response.status(Status.INTERNAL_SERVER_ERROR).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }

    // ---------------------------- Success ------------------------------- 
    // Success means the object was created.
    ResultResourceUrl respUrl = new ResultResourceUrl();
    respUrl.url = _request.getRequestURL().toString() + "/" + systemId;
    RespResourceUrl resp1 = new RespResourceUrl(respUrl);
    return Response.status(Status.CREATED).entity(TapisRestUtils.createSuccessResponse(
      ApiUtils.getMsgAuth("SYSAPI_CREATED", authenticatedUser, systemId), PRETTY, resp1)).build();
  }

  /**
   * Update a system
   * @param systemId - name of the system
   * @param payloadStream - request body
   * @param securityContext - user identity
   * @return response containing reference to updated object
   */
  @PATCH
  @Path("{systemId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response updateSystem(@PathParam("systemId") String systemId,
                               InputStream payloadStream,
                               @Context SecurityContext securityContext)
  {
    String opName = "updateSystem";
    // Trace this request.
    if (_log.isTraceEnabled()) logRequest(opName);

    // ------------------------- Retrieve and validate thread context -------------------------
    TapisThreadContext threadContext = TapisThreadLocal.tapisThreadContext.get();
    // Check that we have all we need from the context, the tenant name and apiUserId
    // Utility method returns null if all OK and appropriate error response if there was a problem.
    Response resp = ApiUtils.checkContext(threadContext, PRETTY);
    if (resp != null) return resp;

    // Get AuthenticatedUser which contains jwtTenant, jwtUser, oboTenant, oboUser, etc.
    AuthenticatedUser authenticatedUser = (AuthenticatedUser) securityContext.getUserPrincipal();

    // ------------------------- Extract and validate payload -------------------------
    // Read the payload into a string.
    String rawJson;
    String msg;
    try { rawJson = IOUtils.toString(payloadStream, StandardCharsets.UTF_8); }
    catch (Exception e)
    {
      msg = MsgUtils.getMsg(INVALID_JSON_INPUT, opName , e.getMessage());
      _log.error(msg, e);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }
    // Create validator specification and validate the json against the schema
    JsonValidatorSpec spec = new JsonValidatorSpec(rawJson, FILE_SYSTEM_UPDATE_REQUEST);
    try { JsonValidator.validate(spec); }
    catch (TapisJSONException e)
    {
      msg = MsgUtils.getMsg(JSON_VALIDATION_ERR, e.getMessage());
      _log.error(msg, e);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }

    // ------------------------- Create a PatchSystem from the json and validate constraints -------------------------
    ReqUpdateSystem req;
    try {
      req = TapisGsonUtils.getGson().fromJson(rawJson, ReqUpdateSystem.class);
    }
    catch (JsonSyntaxException e)
    {
      msg = MsgUtils.getMsg(INVALID_JSON_INPUT, opName, e.getMessage());
      _log.error(msg, e);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }
    PatchSystem patchSystem = createPatchSystemFromRequest(req, authenticatedUser.getTenantId(), systemId, rawJson);

    // No attributes are required. Constraints validated and defaults filled in on server side.
    // No secrets in PatchSystem so no need to scrub

    // ---------------------------- Make service call to update the system -------------------------------
    try
    {
      systemsService.updateSystem(authenticatedUser, patchSystem, rawJson);
    }
    catch (NotFoundException e)
    {
      msg = ApiUtils.getMsgAuth(NOT_FOUND, authenticatedUser, systemId);
      _log.warn(msg);
      return Response.status(Status.NOT_FOUND).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }
    catch (IllegalStateException e)
    {
      if (e.getMessage().contains(LIB_UNAUTH))
      {
        // IllegalStateException with msg containing SYS_UNAUTH indicates operation not authorized for apiUser - return 401
        msg = ApiUtils.getMsgAuth(API_UNAUTH, authenticatedUser, systemId, opName);
        _log.warn(msg);
        return Response.status(Status.UNAUTHORIZED).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
      }
      else
      {
        // IllegalStateException indicates an Invalid PatchSystem was passed in
        msg = ApiUtils.getMsgAuth(UPDATE_ERR, authenticatedUser, systemId, opName, e.getMessage());
        _log.error(msg);
        return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
      }
    }
    catch (IllegalArgumentException e)
    {
      // IllegalArgumentException indicates somehow a bad argument made it this far
      msg = ApiUtils.getMsgAuth(UPDATE_ERR, authenticatedUser, systemId, opName, e.getMessage());
      _log.error(msg);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }
    catch (Exception e)
    {
      msg = ApiUtils.getMsgAuth(UPDATE_ERR, authenticatedUser, systemId, opName, e.getMessage());
      _log.error(msg, e);
      return Response.status(Status.INTERNAL_SERVER_ERROR).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }

    // ---------------------------- Success -------------------------------
    // Success means updates were applied
    ResultResourceUrl respUrl = new ResultResourceUrl();
    respUrl.url = _request.getRequestURL().toString();
    RespResourceUrl resp1 = new RespResourceUrl(respUrl);
    return Response.status(Status.OK).entity(TapisRestUtils.createSuccessResponse(
            ApiUtils.getMsgAuth(UPDATED, authenticatedUser, systemId, opName), PRETTY, resp1)).build();
  }

  // TODO cic-2658. If SGCI endpoint not finalized before production then remove.
  /**
   * Import a system - create a system based on an attributes from an external source
   * @param payloadStream - request body
   * @param securityContext - user identity
   * @return response containing reference to created object
   */
  @POST
  @Path("import/sgci")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response importSGCIResource(InputStream payloadStream,
                                     @Context SecurityContext securityContext)
  {
    String opName = "importSGCIResource";
    // Trace this request.
    if (_log.isTraceEnabled()) logRequest(opName);

    // ------------------------- Retrieve and validate thread context -------------------------
    TapisThreadContext threadContext = TapisThreadLocal.tapisThreadContext.get();
    // Check that we have all we need from the context, the tenant name and apiUserId
    // Utility method returns null if all OK and appropriate error response if there was a problem.
    Response resp = ApiUtils.checkContext(threadContext, PRETTY);
    if (resp != null) return resp;

    // Get AuthenticatedUser which contains jwtTenant, jwtUser, oboTenant, oboUser, etc.
    AuthenticatedUser authenticatedUser = (AuthenticatedUser) securityContext.getUserPrincipal();

    // ------------------------- Extract and validate payload -------------------------
    // Read the payload into a string.
    String rawJson;
    String msg;
    try { rawJson = IOUtils.toString(payloadStream, StandardCharsets.UTF_8); }
    catch (Exception e)
    {
      msg = MsgUtils.getMsg(INVALID_JSON_INPUT, opName , e.getMessage());
      _log.error(msg, e);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }
    // Create validator specification and validate the json against the schema
    JsonValidatorSpec spec = new JsonValidatorSpec(rawJson, FILE_SYSTEM_IMPORTSGCI_REQUEST);
    try { JsonValidator.validate(spec); }
    catch (TapisJSONException e)
    {
      msg = MsgUtils.getMsg(JSON_VALIDATION_ERR, e.getMessage());
      _log.error(msg, e);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }

    ReqImportSGCIResource req;
    // ------------------------- Create a TSystem from the json and validate constraints -------------------------
    try {
      req = TapisGsonUtils.getGson().fromJson(rawJson, ReqImportSGCIResource.class);
    }
    catch (JsonSyntaxException e)
    {
      msg = MsgUtils.getMsg(INVALID_JSON_INPUT, opName, e.getMessage());
      _log.error(msg, e);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }

    // TODO ???????????????????????????????????????????????????????????????
    if (true)
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse("WIP: COMING SOON", PRETTY)).build();

    // Construct the name or get it from the request
    String systemId;
    if (StringUtils.isBlank(req.id))
    {
      systemId = "sgci-" + req.sgciResourceId;
    }
    else
    {
      systemId = req.id;
    }
    // TODO Create a TSystem from the request
    TSystem system = null; //createTSystemFromSGCIImportRequest(req, systemId);
//    system.setImport???RefId(req.sgciResourceId);
    // Fill in defaults and check constraints on TSystem attributes
    system = TSystem.setDefaults(system);
    resp = validateTSystem(system, authenticatedUser);
    if (resp != null) return resp;

    // Extract Notes from the raw json.
    Object notes = extractNotes(rawJson);
    system.setNotes(notes);

    // Mask any secret info that might be contained in rawJson
    String scrubbedJson = rawJson;
    if (system.getAuthnCredential() != null) scrubbedJson = maskCredSecrets(rawJson);

    // ---------------------------- Make service call to create the system -------------------------------
    // Update tenant name and pull out system name for convenience
    system.setTenant(authenticatedUser.getTenantId());
    try
    {
      systemsService.createSystem(authenticatedUser, system, scrubbedJson);
    }
    catch (IllegalStateException e)
    {
      if (e.getMessage().contains("SYSLIB_SYS_EXISTS"))
      {
        // IllegalStateException with msg containing SYS_EXISTS indicates object exists - return 409 - Conflict
        msg = ApiUtils.getMsgAuth("SYSAPI_SYS_EXISTS", authenticatedUser, systemId);
        _log.warn(msg);
        return Response.status(Status.CONFLICT).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
      }
      else if (e.getMessage().contains(LIB_UNAUTH))
      {
        // IllegalStateException with msg containing SYS_UNAUTH indicates operation not authorized for apiUser - return 401
        msg = ApiUtils.getMsgAuth(API_UNAUTH, authenticatedUser, systemId, opName);
        _log.warn(msg);
        return Response.status(Status.UNAUTHORIZED).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
      }
      else
      {
        // IllegalStateException indicates an Invalid TSystem was passed in
        msg = ApiUtils.getMsgAuth(CREATE_ERR, authenticatedUser, systemId, e.getMessage());
        _log.error(msg);
        return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
      }
    }
    catch (IllegalArgumentException e)
    {
      // IllegalArgumentException indicates somehow a bad argument made it this far
      msg = ApiUtils.getMsgAuth(CREATE_ERR, authenticatedUser, systemId, e.getMessage());
      _log.error(msg);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }
    catch (Exception e)
    {
      msg = ApiUtils.getMsgAuth(CREATE_ERR, authenticatedUser, systemId, e.getMessage());
      _log.error(msg, e);
      return Response.status(Status.INTERNAL_SERVER_ERROR).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }

    // ---------------------------- Success -------------------------------
    // Success means the object was created.
    ResultResourceUrl respUrl = new ResultResourceUrl();
    respUrl.url = _request.getRequestURL().toString() + "/" + systemId;
    RespResourceUrl resp1 = new RespResourceUrl(respUrl);
    return Response.status(Status.CREATED).entity(TapisRestUtils.createSuccessResponse(
            ApiUtils.getMsgAuth("SYSAPI_CREATED", authenticatedUser, systemId), PRETTY, resp1)).build();
  }

  /**
   * Update a system that was created based on an SGCI resource
   * @param payloadStream - request body
   * @param securityContext - user identity
   * @return response containing reference to updated object
   */
  @PATCH
  @Path("import/sgci/{systemId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response updateSGCISystem(@PathParam("systemId") String systemId,
                                   InputStream payloadStream,
                                   @Context SecurityContext securityContext)
  {
    String opName = "updateSGCISystem";
    // Trace this request.
    if (_log.isTraceEnabled()) logRequest(opName);

    // ------------------------- Retrieve and validate thread context -------------------------
    TapisThreadContext threadContext = TapisThreadLocal.tapisThreadContext.get();
    // Check that we have all we need from the context, the tenant name and apiUserId
    // Utility method returns null if all OK and appropriate error response if there was a problem.
    Response resp = ApiUtils.checkContext(threadContext, PRETTY);
    if (resp != null) return resp;

    // Get AuthenticatedUser which contains jwtTenant, jwtUser, oboTenant, oboUser, etc.
    AuthenticatedUser authenticatedUser = (AuthenticatedUser) securityContext.getUserPrincipal();

    // ------------------------- Extract and validate payload -------------------------
    // Read the payload into a string.
    String rawJson;
    String msg;
    try { rawJson = IOUtils.toString(payloadStream, StandardCharsets.UTF_8); }
    catch (Exception e)
    {
      msg = MsgUtils.getMsg(INVALID_JSON_INPUT, opName , e.getMessage());
      _log.error(msg, e);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }
    // Create validator specification and validate the json against the schema
    JsonValidatorSpec spec = new JsonValidatorSpec(rawJson, FILE_SYSTEM_UPDATESGCI_REQUEST);
    try { JsonValidator.validate(spec); }
    catch (TapisJSONException e)
    {
      msg = MsgUtils.getMsg(JSON_VALIDATION_ERR, e.getMessage());
      _log.error(msg, e);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }

    // ------------------------- Create a PatchSystem from the json and validate constraints -------------------------
    ReqUpdateSGCISystem req;
    try {
      req = TapisGsonUtils.getGson().fromJson(rawJson, ReqUpdateSGCISystem.class);
    }
    catch (JsonSyntaxException e)
    {
      msg = MsgUtils.getMsg(INVALID_JSON_INPUT, opName, e.getMessage());
      _log.error(msg, e);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }

    // TODO ???????????????????????????????????????????????????????????????
    if (true)
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse("WIP: COMING SOON", PRETTY)).build();

    // TODO
    PatchSystem patchSystem = null;// createPatchSystemFromSGCIRequest(req, authenticatedUser.getTenantId(), systemId);

    // Extract Notes from the raw json.
//    Object notes = extractNotes(rawJson);
//    patchSystem.setNotes(notes);

    // No attributes are required. Constraints validated and defaults filled in on server side.
    // No secrets in PatchSystem so no need to scrub

    // ---------------------------- Make service call to update the system -------------------------------
    try
    {
      systemsService.updateSystem(authenticatedUser, patchSystem, rawJson);
    }
    catch (NotFoundException e)
    {
      msg = ApiUtils.getMsgAuth(NOT_FOUND, authenticatedUser, systemId);
      _log.warn(msg);
      return Response.status(Status.NOT_FOUND).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }
    catch (IllegalStateException e)
    {
      if (e.getMessage().contains(LIB_UNAUTH))
      {
        // IllegalStateException with msg containing SYS_UNAUTH indicates operation not authorized for apiUser - return 401
        msg = ApiUtils.getMsgAuth(API_UNAUTH, authenticatedUser, systemId, opName);
        _log.warn(msg);
        return Response.status(Status.UNAUTHORIZED).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
      }
      else
      {
        // IllegalStateException indicates an Invalid PatchSystem was passed in
        msg = ApiUtils.getMsgAuth(UPDATE_ERR, authenticatedUser, systemId, opName, e.getMessage());
        _log.error(msg);
        return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
      }
    }
    catch (IllegalArgumentException e)
    {
      // IllegalArgumentException indicates somehow a bad argument made it this far
      msg = ApiUtils.getMsgAuth(UPDATE_ERR, authenticatedUser, systemId, opName, e.getMessage());
      _log.error(msg);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }
    catch (Exception e)
    {
      msg = ApiUtils.getMsgAuth(UPDATE_ERR, authenticatedUser, systemId, opName, e.getMessage());
      _log.error(msg, e);
      return Response.status(Status.INTERNAL_SERVER_ERROR).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }

    // ---------------------------- Success -------------------------------
    // Success means updates were applied
    ResultResourceUrl respUrl = new ResultResourceUrl();
    respUrl.url = _request.getRequestURL().toString();
    RespResourceUrl resp1 = new RespResourceUrl(respUrl);
    return Response.status(Status.OK).entity(TapisRestUtils.createSuccessResponse(
            ApiUtils.getMsgAuth(UPDATED, authenticatedUser, systemId, opName), PRETTY, resp1)).build();
  }

  /**
   * Enable a system
   * @param systemId - name of system
   * @param securityContext - user identity
   * @return - response with change count as the result
   */
  @POST
  @Path("{systemId}/enable")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response enableApp(@PathParam("systemId") String systemId,
                            @Context SecurityContext securityContext)
  {
    return postSystemSingleUpdate(OP_ENABLE, systemId, null, false, securityContext);
  }

  /**
   * Disable a system
   * @param systemId - name of the system
   * @param securityContext - user identity
   * @return - response with change count as the result
   */
  @POST
  @Path("{systemId}/disable")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response disableApp(@PathParam("systemId") String systemId,
                             @Context SecurityContext securityContext)
  {
    return postSystemSingleUpdate(OP_DISABLE, systemId, null, false, securityContext);
  }

  /**
   * Change owner of a system
   * @param systemId - name of the system
   * @param userName - name of the new owner
   * @param securityContext - user identity
   * @return response containing reference to updated object
   */
  @POST
  @Path("{systemId}/changeOwner/{userName}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response changeSystemOwner(@PathParam("systemId") String systemId,
                                    @PathParam("userName") String userName,
                                    @Context SecurityContext securityContext)
  {
    return postSystemSingleUpdate(OP_CHANGEOWNER, systemId, userName, false, securityContext);
  }

  /**
   * getSystem
   * @param systemId - name of the system
   * @param getCreds - should credentials of effectiveUser be included
   * @param authnMethodStr - authn method to use instead of default
   * @param requireExecPerm - check for EXECUTE permission as well as READ permission
   * @param securityContext - user identity
   * @return Response with system object as the result
   */
  @GET
  @Path("{systemId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response getSystem(@PathParam("systemId") String systemId,
                            @QueryParam("returnCredentials") @DefaultValue("false") boolean getCreds,
                            @QueryParam("authnMethod") @DefaultValue("") String authnMethodStr,
                            @QueryParam("requireExecPerm") @DefaultValue("false") boolean requireExecPerm,
                            @Context SecurityContext securityContext)
  {
    String opName = "getSystem";
    if (_log.isTraceEnabled()) logRequest(opName);

    // Check that we have all we need from the context, the tenant name and apiUserId
    // Utility method returns null if all OK and appropriate error response if there was a problem.
    TapisThreadContext threadContext = TapisThreadLocal.tapisThreadContext.get();
    Response resp = ApiUtils.checkContext(threadContext, PRETTY);
    if (resp != null) return resp;

    // Get AuthenticatedUser which contains jwtTenant, jwtUser, oboTenant, oboUser, etc.
    AuthenticatedUser authenticatedUser = (AuthenticatedUser) securityContext.getUserPrincipal();

    // Check that authnMethodStr is valid if is passed in
    AuthnMethod authnMethod = null;
    try { if (!StringUtils.isBlank(authnMethodStr)) authnMethod =  AuthnMethod.valueOf(authnMethodStr); }
    catch (IllegalArgumentException e)
    {
      String msg = ApiUtils.getMsgAuth("SYSAPI_ACCMETHOD_ENUM_ERROR", authenticatedUser, systemId, authnMethodStr, e.getMessage());
      _log.error(msg, e);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }

    List<String> selectList = threadContext.getSearchParameters().getSelectList();
    if (selectList != null && !selectList.isEmpty()) _log.debug("Using selectList. First item in list = " + selectList.get(0));

    TSystem tSystem;
    try
    {
      tSystem = systemsService.getSystem(authenticatedUser, systemId, getCreds, authnMethod, requireExecPerm);
    }
    catch (Exception e)
    {
      String msg = ApiUtils.getMsgAuth("SYSAPI_GET_NAME_ERROR", authenticatedUser, systemId, e.getMessage());
      _log.error(msg, e);
      return Response.status(RestUtils.getStatus(e)).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }

    // Resource was not found.
    if (tSystem == null)
    {
      String msg = ApiUtils.getMsgAuth(NOT_FOUND, authenticatedUser, systemId);
      return Response.status(Status.NOT_FOUND).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }

    // ---------------------------- Success -------------------------------
    // Success means we retrieved the system information.
    RespSystem resp1 = new RespSystem(tSystem, selectList);
    return createSuccessResponse(MsgUtils.getMsg(TAPIS_FOUND, "System", systemId), resp1);
  }

  /**
   * getSystems
   * Retrieve all systems accessible by requester and matching any search conditions provided.
   * NOTE: The query parameters pretty, search, limit, orderBy, skip, startAfter are all handled in the filter
   *       QueryParametersRequestFilter. No need to use @QueryParam here.
   * @param securityContext - user identity
   * @return - list of systems accessible by requester and matching search conditions.
   */
  @GET
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response getSystems(@Context SecurityContext securityContext)
  {
    String opName = "getSystems";
    // Trace this request.
    if (_log.isTraceEnabled()) logRequest(opName);

    // Check that we have all we need from the context, the tenant name and apiUserId
    // Utility method returns null if all OK and appropriate error response if there was a problem.
    TapisThreadContext threadContext = TapisThreadLocal.tapisThreadContext.get();
    Response resp = ApiUtils.checkContext(threadContext, PRETTY);
    if (resp != null) return resp;

    // Get AuthenticatedUser which contains jwtTenant, jwtUser, oboTenant, oboUser, etc.
    AuthenticatedUser authenticatedUser = (AuthenticatedUser) securityContext.getUserPrincipal();

    // ThreadContext designed to never return null for SearchParameters
    SearchParameters srchParms = threadContext.getSearchParameters();

    // ------------------------- Retrieve records -----------------------------
    Response successResponse;
    try
    {
      successResponse = getSearchResponse(authenticatedUser, null, srchParms);
    }
    catch (Exception e)
    {
      String msg = ApiUtils.getMsgAuth(SELECT_ERR, authenticatedUser, e.getMessage());
      _log.error(msg, e);
      return Response.status(RestUtils.getStatus(e)).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }
    return successResponse;
  }

  /**
   * searchSystemsQueryParameters
   * Dedicated search endpoint for System resource. Search conditions provided as query parameters.
   * @param securityContext - user identity
   * @return - list of systems accessible by requester and matching search conditions.
   */
  @GET
  @Path("search")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response searchSystemsQueryParameters(@Context SecurityContext securityContext)
  {
    String opName = "searchSystemsGet";
    // Trace this request.
    if (_log.isTraceEnabled()) logRequest(opName);

    // Check that we have all we need from the context, the tenant name and apiUserId
    // Utility method returns null if all OK and appropriate error response if there was a problem.
    TapisThreadContext threadContext = TapisThreadLocal.tapisThreadContext.get();
    Response resp = ApiUtils.checkContext(threadContext, PRETTY);
    if (resp != null) return resp;

    // Get AuthenticatedUser which contains jwtTenant, jwtUser, oboTenant, oboUser, etc.
    AuthenticatedUser authenticatedUser = (AuthenticatedUser) securityContext.getUserPrincipal();

    // Create search list based on query parameters
    // Note that some validation is done for each condition but the back end will handle translating LIKE wildcard
    //   characters (* and !) and deal with escaped characters.
    List<String> searchList;
    try
    {
      searchList = SearchUtils.buildListFromQueryParms(_uriInfo.getQueryParameters());
    }
    catch (Exception e)
    {
      String msg = ApiUtils.getMsgAuth("SYSAPI_SEARCH_ERROR", authenticatedUser, e.getMessage());
      _log.error(msg, e);
      return Response.status(Response.Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }

    if (searchList != null && !searchList.isEmpty()) _log.debug("Using searchList. First value = " + searchList.get(0));

    // ThreadContext designed to never return null for SearchParameters
    SearchParameters srchParms = threadContext.getSearchParameters();

    // ------------------------- Retrieve records -----------------------------
    Response successResponse;
    try
    {
      successResponse = getSearchResponse(authenticatedUser, null, srchParms);
    }
    catch (Exception e)
    {
      String msg = ApiUtils.getMsgAuth(SELECT_ERR, authenticatedUser, e.getMessage());
      _log.error(msg, e);
      return Response.status(RestUtils.getStatus(e)).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }

    // ---------------------------- Success -------------------------------
    return successResponse;
  }

  /**
   * searchSystemsRequestBody
   * Dedicated search endpoint for System resource. Search conditions provided in a request body.
   * Request body contains an array of strings that are concatenated to form the full SQL-like search string.
   * @param payloadStream - request body
   * @param securityContext - user identity
   * @return - list of systems accessible by requester and matching search conditions.
   */
  @POST
  @Path("search")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response searchSystemsRequestBody(InputStream payloadStream,
                                           @Context SecurityContext securityContext)
  {
    String opName = "searchSystemsPost";
    // Trace this request.
    if (_log.isTraceEnabled()) logRequest(opName);

    // Check that we have all we need from the context, the tenant name and apiUserId
    // Utility method returns null if all OK and appropriate error response if there was a problem.
    TapisThreadContext threadContext = TapisThreadLocal.tapisThreadContext.get();
    Response resp = ApiUtils.checkContext(threadContext, PRETTY);
    if (resp != null) return resp;

    // Get AuthenticatedUser which contains jwtTenant, jwtUser, oboTenant, oboUser, etc.
    AuthenticatedUser authenticatedUser = (AuthenticatedUser) securityContext.getUserPrincipal();

    // ------------------------- Extract and validate payload -------------------------
    // Read the payload into a string.
    String rawJson;
    String msg;
    try { rawJson = IOUtils.toString(payloadStream, StandardCharsets.UTF_8); }
    catch (Exception e)
    {
      msg = MsgUtils.getMsg(INVALID_JSON_INPUT, opName , e.getMessage());
      _log.error(msg, e);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }
    // Create validator specification and validate the json against the schema
    JsonValidatorSpec spec = new JsonValidatorSpec(rawJson, FILE_SYSTEM_SEARCH_REQUEST);
    try { JsonValidator.validate(spec); }
    catch (TapisJSONException e)
    {
      msg = MsgUtils.getMsg(JSON_VALIDATION_ERR, e.getMessage());
      _log.error(msg, e);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }

    // Construct final SQL-like search string using the json
    // When put together full string must be a valid SQL-like where clause. This will be validated in the service call.
    // Not all SQL syntax is supported. See SqlParser.jj in tapis-shared-searchlib.
    String sqlSearchStr;
    try
    {
      sqlSearchStr = SearchUtils.getSearchFromRequestJson(rawJson);
    }
    catch (JsonSyntaxException e)
    {
      msg = MsgUtils.getMsg(INVALID_JSON_INPUT, opName, e.getMessage());
      _log.error(msg, e);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }
    _log.debug(String.format("Using search string: %s", sqlSearchStr));

    // ThreadContext designed to never return null for SearchParameters
    SearchParameters srchParms = threadContext.getSearchParameters();

    // ------------------------- Retrieve records -----------------------------
    Response successResponse;
    try
    {
      successResponse = getSearchResponse(authenticatedUser, sqlSearchStr, srchParms);
    }
    catch (Exception e)
    {
      msg = ApiUtils.getMsgAuth(SELECT_ERR, authenticatedUser, e.getMessage());
      _log.error(msg, e);
      return Response.status(RestUtils.getStatus(e)).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }

    // ---------------------------- Success -------------------------------
    return successResponse;
  }

//  /**
//   * matchConstraints
//   * Retrieve details for systems. Use request body to specify constraint conditions as an SQL-like WHERE clause.
//   * Request body contains an array of strings that are concatenated to form the full SQL-like search string.
//   * @param payloadStream - request body
//   * @param securityContext - user identity
//   * @return - list of systems accessible by requester and matching constraint conditions.
//   */
//  @POST
//  @Path("match/constraints")
//  @Consumes(MediaType.APPLICATION_JSON)
//  @Produces(MediaType.APPLICATION_JSON)
//  public Response matchConstraints(InputStream payloadStream,
//                                   @Context SecurityContext securityContext)
//  {
//    String opName = "matchConstraints";
//    // Trace this request.
//    if (_log.isTraceEnabled()) logRequest(opName);
//
//    // Check that we have all we need from the context, the tenant name and apiUserId
//    // Utility method returns null if all OK and appropriate error response if there was a problem.
//    TapisThreadContext threadContext = TapisThreadLocal.tapisThreadContext.get();
//    Response resp = ApiUtils.checkContext(threadContext, PRETTY);
//    if (resp != null) return resp;
//
//    // Get AuthenticatedUser which contains jwtTenant, jwtUser, oboTenant, oboUser, etc.
//    AuthenticatedUser authenticatedUser = (AuthenticatedUser) securityContext.getUserPrincipal();
//
//    // ------------------------- Extract and validate payload -------------------------
//    // Read the payload into a string.
//    String rawJson;
//    String msg;
//    try { rawJson = IOUtils.toString(payloadStream, StandardCharsets.UTF_8); }
//    catch (Exception e)
//    {
//      msg = MsgUtils.getMsg(INVALID_JSON_INPUT, opName , e.getMessage());
//      _log.error(msg, e);
//      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
//    }
//    // Create validator specification and validate the json against the schema
//    JsonValidatorSpec spec = new JsonValidatorSpec(rawJson, FILE_SYSTEM_MATCH_REQUEST);
//    try { JsonValidator.validate(spec); }
//    catch (TapisJSONException e)
//    {
//      msg = MsgUtils.getMsg(JSON_VALIDATION_ERR, e.getMessage());
//      _log.error(msg, e);
//      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
//    }
//
//    // Construct final SQL-like search string using the json
//    // When put together full string must be a valid SQL-like where clause. This will be validated in the service call.
//    // Not all SQL syntax is supported. See SqlParser.jj in tapis-shared-searchlib.
//    String matchStr;
//    try
//    {
//      matchStr = SearchUtils.getMatchFromRequestJson(rawJson);
//    }
//    catch (JsonSyntaxException e)
//    {
//      msg = MsgUtils.getMsg(INVALID_JSON_INPUT, opName, e.getMessage());
//      _log.error(msg, e);
//      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
//    }
//    _log.debug(String.format("Using match string: %s", matchStr));
//
//    // ------------------------- Retrieve records -----------------------------
//    List<TSystem> systems;
//    try {
//      systems = systemsService.getSystemsSatisfyingConstraints(authenticatedUser, matchStr);
//    }
//    catch (Exception e)
//    {
//      msg = ApiUtils.getMsgAuth(SELECT_ERR, authenticatedUser, e.getMessage());
//      _log.error(msg, e);
//      return Response.status(RestUtils.getStatus(e)).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
//    }
//
//    if (systems == null) systems = Collections.emptyList();
//
//    // ---------------------------- Success -------------------------------
//    RespSystems resp1 = new RespSystems(systems);
//    String itemCountStr = String.format(SYS_CNT_STR, systems.size());
//    return createSuccessResponse(MsgUtils.getMsg(TAPIS_FOUND, SYSTEMS_SVC, itemCountStr), resp1);
//  }

  /**
   * deleteSystem
   * @param systemId - name of the system to delete
   * @param confirmDelete - confirm the action
   * @param securityContext - user identity
   * @return - response with change count as the result
   */
  @DELETE
  @Path("{systemId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response deleteSystem(@PathParam("systemId") String systemId,
                               @QueryParam("confirm") @DefaultValue("false") boolean confirmDelete,
                               @Context SecurityContext securityContext)
  {
    return postSystemSingleUpdate(OP_DELETE, systemId, null, confirmDelete, securityContext);
  }

  /* **************************************************************************** */
  /*                                Private Methods                               */
  /* **************************************************************************** */

  /**
   * changeOwner, enable, disable and delete follow same pattern
   * Note that userName only used for changeOwner and confirmDelete only used for delete
   * @param opName Name of operation.
   * @param systemId Id of app to update
   * @param userName new owner name for op changeOwner
   * @param confirmDelete confirmation flag for op delete
   * @param securityContext Security context from client call
   * @return Response to be returned to the client.
   */
  private Response postSystemSingleUpdate(String opName, String systemId, String userName, boolean confirmDelete,
                                       SecurityContext securityContext)
  {
    // Trace this request.
    if (_log.isTraceEnabled()) logRequest(opName);

    // ------------------------- Retrieve and validate thread context -------------------------
    TapisThreadContext threadContext = TapisThreadLocal.tapisThreadContext.get();
    // Check that we have all we need from the context, the tenant name and apiUserId
    // Utility method returns null if all OK and appropriate error response if there was a problem.
    Response resp = ApiUtils.checkContext(threadContext, PRETTY);
    if (resp != null) return resp;

    // Get AuthenticatedUser which contains jwtTenant, jwtUser, oboTenant, oboUser, etc.
    AuthenticatedUser authenticatedUser = (AuthenticatedUser) securityContext.getUserPrincipal();

    // If operation is delete and confirmDelete is false then return error response
    if (OP_DELETE.equals(opName) && !confirmDelete)
    {
      String msg = ApiUtils.getMsgAuth("SYSAPI_DELETE_NOCONFIRM", authenticatedUser, systemId);
      _log.warn(msg);
      return Response.status(Response.Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }

    // ---------------------------- Make service call to update the app -------------------------------
    int changeCount;
    String msg;
    try
    {
      if (OP_ENABLE.equals(opName))
        changeCount = systemsService.enableSystem(authenticatedUser, systemId);
      else if (OP_DISABLE.equals(opName))
        changeCount = systemsService.disableSystem(authenticatedUser, systemId);
      else if (OP_DELETE.equals(opName))
        changeCount = systemsService.softDeleteSystem(authenticatedUser, systemId);
      else
        changeCount = systemsService.changeSystemOwner(authenticatedUser, systemId, userName);
    }
    catch (NotFoundException e)
    {
      msg = ApiUtils.getMsgAuth("SYSAPI_NOT_FOUND", authenticatedUser, systemId);
      _log.warn(msg);
      return Response.status(Status.NOT_FOUND).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }
    catch (IllegalStateException e)
    {
      if (e.getMessage().contains("SYSLIB_UNAUTH"))
      {
        // IllegalStateException with msg containing SYS_UNAUTH indicates operation not authorized for apiUser - return 401
        msg = ApiUtils.getMsgAuth("SYSAPI_SYS_UNAUTH", authenticatedUser, systemId, opName);
        _log.warn(msg);
        return Response.status(Status.UNAUTHORIZED).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
      }
      else
      {
        // IllegalStateException indicates an Invalid PatchApp was passed in
        msg = ApiUtils.getMsgAuth(UPDATE_ERR, authenticatedUser, systemId, opName, e.getMessage());
        _log.error(msg);
        return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
      }
    }
    catch (IllegalArgumentException e)
    {
      // IllegalArgumentException indicates somehow a bad argument made it this far
      msg = ApiUtils.getMsgAuth(UPDATE_ERR, authenticatedUser, systemId, opName, e.getMessage());
      _log.error(msg);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }
    catch (Exception e)
    {
      msg = ApiUtils.getMsgAuth(UPDATE_ERR, authenticatedUser, systemId, opName, e.getMessage());
      _log.error(msg, e);
      return Response.status(Status.INTERNAL_SERVER_ERROR).entity(TapisRestUtils.createErrorResponse(msg, PRETTY)).build();
    }

    // ---------------------------- Success -------------------------------
    // Success means updates were applied
    // Return the number of objects impacted.
    ResultChangeCount count = new ResultChangeCount();
    count.changes = changeCount;
    RespChangeCount resp1 = new RespChangeCount(count);
    return Response.status(Status.OK).entity(TapisRestUtils.createSuccessResponse(
            ApiUtils.getMsgAuth(UPDATED, authenticatedUser, systemId, opName), PRETTY, resp1)).build();
  }

  /**
   * Create a TSystem from a ReqCreateSystem
   */
  private static TSystem createTSystemFromRequest(ReqCreateSystem req, String rawJson)
  {
    // Convert jobEnvVariables to array of strings
    String[] jobEnvVariables = ApiUtils.getKeyValuesAsArray(req.jobEnvVariables);
    // Extract Notes from the raw json.
    Object notes = extractNotes(rawJson);

    var tSystem = new TSystem(-1, null, req.id, req.description, req.systemType, req.owner, req.host,
                       req.enabled, req.effectiveUserId, req.defaultAuthnMethod, req.bucketName, req.rootDir,
                       req.transferMethods, req.port, req.useProxy, req.proxyHost, req.proxyPort,
                       req.dtnSystemId, req.dtnMountPoint, req.dtnMountSourcePath, req.isDtn, req.canExec, req.jobWorkingDir,
                       jobEnvVariables, req.jobMaxJobs, req.jobMaxJobsPerUser, req.jobIsBatch, req.batchScheduler,
                       req.batchDefaultLogicalQueue, req.tags, notes, null, false, null, null);
    tSystem.setAuthnCredential(req.authnCredential);
    tSystem.setBatchLogicalQueues(req.batchLogicalQueues);
    tSystem.setJobRuntimes(req.jobRuntimes);
    tSystem.setJobCapabilities(req.jobCapabilities);
    return tSystem;
  }

  /**
   * Create a PatchSystem from a ReqUpdateSystem
   * Note that tenant and id are for tracking and needed by the service call. They are not updated.
   */
  private static PatchSystem createPatchSystemFromRequest(ReqUpdateSystem req, String tenantName, String systemId,
                                                          String rawJson)
  {
    // Convert jobEnvVariables to array of strings
    String[] jobEnvVariables = ApiUtils.getKeyValuesAsArray(req.jobEnvVariables);

    // Extract Notes from the raw json.
    Object notes = extractNotes(rawJson);

    PatchSystem patchSystem = new PatchSystem(req.description, req.host, req.effectiveUserId,
                           req.defaultAuthnMethod, req.transferMethods, req.port, req.useProxy,
                           req.proxyHost, req.proxyPort, req.dtnSystemId, req.dtnMountPoint, req.dtnMountSourcePath,
                           req.jobRuntimes, req.jobWorkingDir, jobEnvVariables, req.jobMaxJobs, req.jobMaxJobsPerUser,
                           req.jobIsBatch, req.batchScheduler, req.batchLogicalQueues, req.batchDefaultLogicalQueue,
                           req.jobCapabilities, req.tags, notes);
    // Update tenant name and system name
    patchSystem.setTenant(tenantName);
    patchSystem.setId(systemId);
    return patchSystem;
  }

  /**
   * Fill in defaults and check restrictions on TSystem attributes
   * Use TSystem method to check internal consistency of attributes.
   * If DTN is used verify that dtnSystemId exists with isDtn = true
   * Collect and report as many errors as possible so they can all be fixed before next attempt
   * NOTE: JsonSchema validation should handle some of these checks but we check here again for robustness.
   *
   * @return null if OK or error Response
   */
  private Response validateTSystem(TSystem tSystem1, AuthenticatedUser authenticatedUser)
  {
    String msg;

    // Make call for lib level validation
    List<String> errMessages = tSystem1.checkAttributeRestrictions();

    // Now validate attributes that have special handling at API level.

    // If DTN is used (i.e. dtnSystemId is set) verify that dtnSystemId exists with isDtn = true
    if (!StringUtils.isBlank(tSystem1.getDtnSystemId()))
    {
      TSystem dtnSystem = null;
      try
      {
        dtnSystem = systemsService.getSystem(authenticatedUser, tSystem1.getDtnSystemId(), false, null, false);
      }
      catch (NotAuthorizedException e)
      {
        msg = ApiUtils.getMsg("SYSAPI_DTN_NOT_AUTH", tSystem1.getDtnSystemId());
        errMessages.add(msg);
      }
      catch (TapisClientException | TapisException e)
      {
        msg = ApiUtils.getMsg("SYSAPI_DTN_CHECK_ERROR", tSystem1.getDtnSystemId(), e.getMessage());
        _log.error(msg, e);
        errMessages.add(msg);
      }
      if (dtnSystem == null)
      {
        msg = ApiUtils.getMsg("SYSAPI_DTN_NO_SYSTEM", tSystem1.getDtnSystemId());
        errMessages.add(msg);
      }
      else if (!dtnSystem.isDtn())
      {
          msg = ApiUtils.getMsg("SYSAPI_DTN_NOT_DTN", tSystem1.getDtnSystemId());
          errMessages.add(msg);
      }
    }

    // If validation failed log error message and return response
    if (!errMessages.isEmpty())
    {
      // Construct message reporting all errors
      String allErrors = getListOfErrors(errMessages, authenticatedUser, tSystem1.getId());
      _log.error(allErrors);
      return Response.status(Status.BAD_REQUEST).entity(TapisRestUtils.createErrorResponse(allErrors, PRETTY)).build();
    }
    return null;
  }

  /**
   * Extract notes from the incoming json
   */
  private static Object extractNotes(String rawJson)
  {
    Object notes = TSystem.DEFAULT_NOTES;
    // Check inputs
    if (StringUtils.isBlank(rawJson)) return notes;
    // Turn the request string into a json object and extract the notes object
    JsonObject topObj = TapisGsonUtils.getGson().fromJson(rawJson, JsonObject.class);
    if (!topObj.has(NOTES_FIELD)) return notes;
    notes = topObj.getAsJsonObject(NOTES_FIELD);
    return notes;
  }

  /**
   * AuthnCredential details can contain secrets. Mask any secrets given
   * and return a string containing the final redacted Json.
   * @param rawJson Json from request
   * @return A string with any secrets masked out
   */
  private static String maskCredSecrets(String rawJson)
  {
    if (StringUtils.isBlank(rawJson)) return rawJson;
    // Get the Json object and prepare to extract info from it
    JsonObject sysObj = TapisGsonUtils.getGson().fromJson(rawJson, JsonObject.class);
    if (!sysObj.has(AUTHN_CREDENTIAL_FIELD)) return rawJson;
    var credObj = sysObj.getAsJsonObject(AUTHN_CREDENTIAL_FIELD);
    maskSecret(credObj, CredentialResource.PASSWORD_FIELD);
    maskSecret(credObj, CredentialResource.PRIVATE_KEY_FIELD);
    maskSecret(credObj, CredentialResource.PUBLIC_KEY_FIELD);
    maskSecret(credObj, CredentialResource.ACCESS_KEY_FIELD);
    maskSecret(credObj, CredentialResource.ACCESS_SECRET_FIELD);
    maskSecret(credObj, CredentialResource.CERTIFICATE_FIELD);
    sysObj.remove(AUTHN_CREDENTIAL_FIELD);
    sysObj.add(AUTHN_CREDENTIAL_FIELD, credObj);
    return sysObj.toString();
  }

  /**
   * If the Json object contains a non-blank value for the field then replace the value with the mask value.
   */
  private static void maskSecret(JsonObject credObj, String field)
  {
    if (!StringUtils.isBlank(ApiUtils.getValS(credObj.get(field), "")))
    {
      credObj.remove(field);
      credObj.addProperty(field, SECRETS_MASK);
    }
  }

  /**
   * Construct message containing list of errors
   */
  private static String getListOfErrors(List<String> msgList, AuthenticatedUser authenticatedUser, Object... parms) {
    if (msgList == null || msgList.isEmpty()) return "";
    var sb = new StringBuilder(ApiUtils.getMsgAuth("SYSAPI_CREATE_INVALID_ERRORLIST", authenticatedUser, parms));
    sb.append(System.lineSeparator());
    for (String msg : msgList) { sb.append("  ").append(msg).append(System.lineSeparator()); }
    return sb.toString();
  }

  private void logRequest(String opName) {
    String msg = MsgUtils.getMsg("TAPIS_TRACE_REQUEST", getClass().getSimpleName(), opName,
            "  " + _request.getRequestURL());
    _log.trace(msg);
  }

  /**
   * Create an OK response given message and base response to put in result
   * @param msg - message for resp.message
   * @param resp - base response (the result)
   * @return - Final response to return to client
   */
  private static Response createSuccessResponse(String msg, RespAbstract resp)
  {
    resp.message = msg;
    resp.status = ResponseWrapper.RESPONSE_STATUS.success.name();
    resp.version = TapisUtils.getTapisVersion();
    Response r1 = Response.ok(resp).build();
    return r1;
  }

  /**
   *  Common method to return a list of systems given a search list and search parameters.
   *  srchParms must be non-null
   *  One of srchParms.searchList or sqlSearchStr must be non-null
   */
  private Response getSearchResponse(AuthenticatedUser authenticatedUser, String sqlSearchStr, SearchParameters srchParms)
          throws Exception
  {
    RespAbstract resp1;
    List<TSystem> systems = null;
    int totalCount = -1;
    String itemCountStr;

    List<String> searchList = srchParms.getSearchList();
    List<String> selectList = srchParms.getSelectList();
    if (selectList == null || selectList.isEmpty()) selectList = SUMMARY_ATTRS;

    if (searchList != null && !searchList.isEmpty()) _log.debug("Using searchList. First condition in list = " + searchList.get(0));
    if (!selectList.isEmpty()) _log.debug("Using selectList. First item in list = " + selectList.get(0));

    // If limit was not specified then use the default
    int limit = (srchParms.getLimit() == null) ? SearchParameters.DEFAULT_LIMIT : srchParms.getLimit();
    // Set some variables to make code easier to read
    int skip = srchParms.getSkip();
    String startAfter = srchParms.getStartAfter();
    boolean computeTotal = srchParms.getComputeTotal();
    String orderBy = srchParms.getOrderBy();
    List<OrderBy> orderByList = srchParms.getOrderByList();

    if (StringUtils.isBlank(sqlSearchStr))
      systems = systemsService.getSystems(authenticatedUser, searchList, limit, orderByList, skip, startAfter);
    else
      systems = systemsService.getSystemsUsingSqlSearchStr(authenticatedUser, sqlSearchStr, limit, orderByList, skip,
                                                           startAfter);
    if (systems == null) systems = Collections.emptyList();
    itemCountStr = String.format(SYS_CNT_STR, systems.size());
    if (computeTotal && limit <= 0) totalCount = systems.size();

    // If we need the count and there was a limit then we need to make a call
    if (computeTotal && limit > 0)
    {
      totalCount = systemsService.getSystemsTotalCount(authenticatedUser, searchList, orderByList, startAfter);
    }

    // ---------------------------- Success -------------------------------
    resp1 = new RespSystems(systems, limit, orderBy, skip, startAfter, totalCount, selectList);

    return createSuccessResponse(MsgUtils.getMsg(TAPIS_FOUND, SYSTEMS_SVC, itemCountStr), resp1);
  }
}
