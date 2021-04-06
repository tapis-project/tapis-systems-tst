package edu.utexas.tacc.tapis.systems.api.resources;

import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import edu.utexas.tacc.tapis.client.shared.exceptions.TapisClientException;
import edu.utexas.tacc.tapis.shared.i18n.MsgUtils;
import edu.utexas.tacc.tapis.shared.security.ServiceContext;
import edu.utexas.tacc.tapis.shared.security.TenantManager;
import edu.utexas.tacc.tapis.shared.utils.CallSiteToggle;
import edu.utexas.tacc.tapis.shared.utils.TapisUtils;
import edu.utexas.tacc.tapis.sharedapi.dto.ResponseWrapper;
import edu.utexas.tacc.tapis.sharedapi.responses.RespBasic;
import edu.utexas.tacc.tapis.sharedapi.utils.TapisRestUtils;
import edu.utexas.tacc.tapis.systems.api.SystemsApplication;
import edu.utexas.tacc.tapis.systems.api.utils.ApiUtils;
import edu.utexas.tacc.tapis.systems.service.SystemsServiceImpl;
import edu.utexas.tacc.tapis.systems.utils.LibUtils;
import org.glassfish.grizzly.http.server.Request;

import org.jooq.tools.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* Tapis Systems general resources including healthcheck and readycheck
 *
 *  NOTE: Switching to hand-crafted openapi located in repo tapis-client-java at systems-client/SystemsAPI.yaml
 *        Could not fully automate generation of spec and annotations have some limits. E.g., how to mark a parameter
 *        in a request body as required?, how to better describe query parameters?
 */
@Path("/v3/systems")
public class SystemsResource
{
  /* **************************************************************************** */
  /*                                   Constants                                  */
  /* **************************************************************************** */
  // Local logger.
  private static final Logger _log = LoggerFactory.getLogger(SystemsResource.class);

  /* **************************************************************************** */
  /*                                    Fields                                    */
  /* **************************************************************************** */
  /* Jax-RS context dependency injection allows implementations of these abstract
   * types to be injected (ch 9, jax-rs 2.0):
   *
   *      javax.ws.rs.container.ResourceContext
   *      javax.ws.rs.core.Application
   *      javax.ws.rs.core.HttpHeaders
   *      javax.ws.rs.core.Request
   *      javax.ws.rs.core.SecurityContext
   *      javax.ws.rs.core.UriInfo
   *      javax.ws.rs.core.Configuration
   *      javax.ws.rs.ext.Providers
   *
   * In a servlet environment, Jersey context dependency injection can also
   * initialize these concrete types (ch 3.6, jersey spec):
   *
   *      javax.servlet.HttpServletRequest
   *      javax.servlet.HttpServletResponse
   *      javax.servlet.ServletConfig
   *      javax.servlet.ServletContext
   *
   * Inject takes place after constructor invocation, so fields initialized in this
   * way can not be accessed in constructors.
   */
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

  // Count the number of health check requests received.
  private static final AtomicLong healthCheckCount = new AtomicLong();

  // Count the number of health check requests received.
  private static final AtomicLong readyCheckCount = new AtomicLong();

  // Use CallSiteToggle to limit logging for readyCheck endpoint
  private static final CallSiteToggle checkTenantsOK = new CallSiteToggle();
  private static final CallSiteToggle checkJWTOK = new CallSiteToggle();
  private static final CallSiteToggle checkDBOK = new CallSiteToggle();

  // **************** Inject Services using HK2 ****************
  @Inject
  private SystemsServiceImpl svcImpl;
  @Inject
  private ServiceContext serviceContext;

  /* **************************************************************************** */
  /*                                Public Methods                                */
  /* **************************************************************************** */

  /**
   * Lightweight non-authenticated health check endpoint.
   * Note that no JWT is required on this call and no logging is done.
   * @return a success response if all is ok
   */
  @GET
  @Path("/healthcheck")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @PermitAll
  public Response healthCheck()
  {
    // Get the current check count.
    long checkNum = healthCheckCount.incrementAndGet();
    RespBasic resp = new RespBasic("Health check received. Count: " + checkNum);

    // Manually create a success response with git info included in version
    resp.status = ResponseWrapper.RESPONSE_STATUS.success.name();
    resp.message = MsgUtils.getMsg("TAPIS_HEALTHY", "Systems Service");
    resp.version = TapisUtils.getTapisFullVersion();
    return Response.ok(resp).build();
  }

  /**
   * Lightweight non-authenticated ready check endpoint.
   * Note that no JWT is required on this call and CallSiteToggle is used to limit logging.
   * Based on similar method in tapis-securityapi.../SecurityResource
   *
   * For this service readiness means service can:
   *    - retrieve tenants map
   *    - get a service JWT
   *    - connect to the DB and verify and that main service table exists
   *
   * It is intended as the endpoint that monitoring applications can use to check
   * whether the application is ready to accept traffic.  In particular, kubernetes
   * can use this endpoint as part of its pod readiness check.
   *
   * Note that no JWT is required on this call.
   *
   * A good synopsis of the difference between liveness and readiness checks:
   *
   * ---------
   * The probes have different meaning with different results:
   *
   *    - failing liveness probes  -> restart pod
   *    - failing readiness probes -> do not send traffic to that pod
   *
   * See https://stackoverflow.com/questions/54744943/why-both-liveness-is-needed-with-readiness
   * ---------
   *
   * @return a success response if all is ok
   */
  @GET
  @Path("/readycheck")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @PermitAll
  public Response readyCheck()
  {
    // Get the current check count.
    long checkNum = readyCheckCount.incrementAndGet();

    // Check that we can get tenants list
    Exception readyCheckException = checkTenants();
    if (readyCheckException != null)
    {
      RespBasic r = new RespBasic("Readiness tenants check failed. Check number: " + checkNum);
      String msg = MsgUtils.getMsg("TAPIS_NOT_READY", "Systems Service");
      // We failed so set the log limiter check.
      if (checkTenantsOK.toggleOff())
      {
        _log.warn(msg, readyCheckException);
        _log.warn(ApiUtils.getMsg("SYSAPI_READYCHECK_TENANTS_ERRTOGGLE_SET"));
      }
      return Response.status(Status.SERVICE_UNAVAILABLE).entity(TapisRestUtils.createErrorResponse(msg, false, r)).build();
    }
    else
    {
      // We succeeded so clear the log limiter check.
      if (checkTenantsOK.toggleOn()) _log.info(ApiUtils.getMsg("SYSAPI_READYCHECK_TENANTS_ERRTOGGLE_CLEARED"));
    }

    // Check that we have a service JWT
    readyCheckException = checkJWT();
    if (readyCheckException != null)
    {
      RespBasic r = new RespBasic("Readiness JWT check failed. Check number: " + checkNum);
      String msg = MsgUtils.getMsg("TAPIS_NOT_READY", "Systems Service");
      // We failed so set the log limiter check.
      if (checkJWTOK.toggleOff())
      {
        _log.warn(msg, readyCheckException);
        _log.warn(ApiUtils.getMsg("SYSAPI_READYCHECK_JWT_ERRTOGGLE_SET"));
      }
      return Response.status(Status.SERVICE_UNAVAILABLE).entity(TapisRestUtils.createErrorResponse(msg, false, r)).build();
    }
    else
    {
      // We succeeded so clear the log limiter check.
      if (checkJWTOK.toggleOn()) _log.info(ApiUtils.getMsg("SYSAPI_READYCHECK_JWT_ERRTOGGLE_CLEARED"));
    }

    // Check that we can connect to the DB
    readyCheckException = checkDB();
    if (readyCheckException != null)
    {
      RespBasic r = new RespBasic("Readiness DB check failed. Check number: " + checkNum);
      String msg = MsgUtils.getMsg("TAPIS_NOT_READY", "Systems Service");
      // We failed so set the log limiter check.
      if (checkDBOK.toggleOff())
      {
        _log.warn(msg, readyCheckException);
        _log.warn(ApiUtils.getMsg("SYSAPI_READYCHECK_DB_ERRTOGGLE_SET"));
      }
      return Response.status(Status.SERVICE_UNAVAILABLE).entity(TapisRestUtils.createErrorResponse(msg, false, r)).build();
    }
    else
    {
      // We succeeded so clear the log limiter check.
      if (checkDBOK.toggleOn()) _log.info(ApiUtils.getMsg("SYSAPI_READYCHECK_DB_ERRTOGGLE_CLEARED"));
    }

    // ---------------------------- Success -------------------------------
    // Create the response payload.
    RespBasic resp = new RespBasic("Ready check passed. Count: " + checkNum);
    // Manually create a success response with git info included in version
    resp.status = ResponseWrapper.RESPONSE_STATUS.success.name();
    resp.message = MsgUtils.getMsg("TAPIS_READY", "Systems Service");
    resp.version = TapisUtils.getTapisFullVersion();
    return Response.ok(resp).build();
  }

  /* **************************************************************************** */
  /*                                Private Methods                               */
  /* **************************************************************************** */

  /**
   * Verify that we have a valid service JWT.
   * @return null if OK, otherwise return an exception
   */
  private Exception checkJWT()
  {
    Exception result = null;
    try {
      String jwt = serviceContext.getServiceJWT().getAccessJWT(SystemsApplication.getSiteId());
      if (StringUtils.isBlank(jwt)) result = new TapisClientException(LibUtils.getMsg("SYSLIB_CHECKJWT_EMPTY"));
    }
    catch (Exception e) { result = e; }
    return result;
  }

  /**
   * Check the database
   * @return null if OK, otherwise return an exception
   */
  private Exception checkDB()
  {
    Exception result;
    try { result = svcImpl.checkDB(); }
    catch (Exception e) { result = e; }
    return result;
  }

  /**
   * Retrieve the cached tenants map.
   * @return null if OK, otherwise return an exception
   */
  private Exception checkTenants()
  {
    Exception result = null;
    try
    {
      // Make sure the cached tenants map is not null or empty.
      var tenantMap = TenantManager.getInstance().getTenants();
      if (tenantMap == null || tenantMap.isEmpty()) result = new TapisClientException(LibUtils.getMsg("SYSLIB_CHECKTENANTS_EMPTY"));
    }
    catch (Exception e) { result = e; }
    return result;
  }
}
