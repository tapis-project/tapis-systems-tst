package edu.utexas.tacc.tapis2.api.jaxrs.filters;

import edu.utexas.tacc.tapis.shared.TapisConstants;
import edu.utexas.tacc.tapis.shared.threadlocal.TapisThreadContext;
import edu.utexas.tacc.tapis.shared.threadlocal.TapisThreadLocal;
import org.jooq.tools.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 *  jax-rs filter to intercept various query parameters and set values in the thread context.
 *  Parameters:
 *    pretty - Boolean indicating if response should be pretty printed
 *    search - String indicating search conditions to use when retrieving results
 *    limit - Integer indicating maximum number of results to be included, -1 for unlimited
 *    offset - number of results to skip
 *    sort_by, e.g. sort_by=owner(asc),created(desc)
 *    start_after, e.g. systems?limit=10&sort_by=id(asc)&start_after=101
 *
 *  NOTE: Process "pretty" here because it is a parameter for all endpoints and
 *        is not needed for the java client or the back-end (tapis-systemslib)
 *  NOTE: Returning selected attributes in returned results is provided by Jersey's
 *        dynamic filtering (SelectableEntityFilteringFeature). See SystemsApplication.java
 *        So no need to process here. Downside is it is therefore not available in
 *        the java client or calls by some some other front-end to the back-end.
 *  NOTE: Many of these parameters are also included in SystemResource.java using @QueryParam
 *        so that they are available in the generated java client.
 */
@Provider
@Priority(TapisConstants.JAXRS_FILTER_PRIORITY_AFTER_AUTHENTICATION)
public class QueryParametersRequestFilter implements ContainerRequestFilter
{
  /* ********************************************************************** */
  /*                               Constants                                */
  /* ********************************************************************** */
  // Tracing.
  private static final Logger _log = LoggerFactory.getLogger(QueryParametersRequestFilter.class);

  // Query parameter names
  private static final String PARM_PRETTY = "pretty";
  private static final String PARM_SEARCH = "search";
  private static final String PARM_LIMIT = "limit";
  private static final String PARM_OFFSET = "offset";
  private static final String PARM_SORTBY = "sort_by";
  private static final String PARM_STARTAFTER = "start_after";

  /* ********************************************************************** */
  /*                            Public Methods                              */
  /* ********************************************************************** */
  /* ---------------------------------------------------------------------- */
  /* filter:                                                                */
  /* ---------------------------------------------------------------------- */
  @Override
  public void filter(ContainerRequestContext requestContext)
  {
    // Tracing.
    if (_log.isTraceEnabled())
      _log.trace("Executing JAX-RX request filter: " + this.getClass().getSimpleName() + ".");
    // Retrieve all query parameters. If none we are done.
    MultivaluedMap<String, String> queryParameters = requestContext.getUriInfo().getQueryParameters();
    if (queryParameters == null || queryParameters.isEmpty()) return;
    // Get thread context
    TapisThreadContext threadContext = TapisThreadLocal.tapisThreadContext.get();

    // Look for and extract pretty print query parameter.
    String parmName = PARM_PRETTY;
    String parmValue;
    // Common checks for query parameters
    if (badParm(requestContext, parmName)) { return; }
    parmValue = getQueryParm(queryParameters, parmName);
    if (!StringUtils.isBlank(parmValue))
    {
      boolean prettyPrint;
      // Check that it is a boolean
      if (!"true".equalsIgnoreCase(parmValue) && !"false".equalsIgnoreCase(parmValue))
      {
        String msg = "Invalid pretty pint query parameter: Must be boolean. Value: " + parmValue;
        _log.warn(msg);
        return;
      }
      // Provided parameter is valid. Set as boolean
      prettyPrint = Boolean.parseBoolean(parmValue);
      threadContext.setPrettyPrint(prettyPrint);
    }

    // Look for and extract search query parameter.
    parmName = PARM_SEARCH;
    if (badParm(requestContext, parmName)) { return; }
    parmValue = getQueryParm(queryParameters, parmName);
    if (!StringUtils.isBlank(parmValue)) threadContext.setSearch(parmValue);

    // Look for and extract limit query parameter.
    parmName = PARM_LIMIT;
    if (badParm(requestContext, parmName)) { return; }
    parmValue = getQueryParm(queryParameters, parmName);
    if (!StringUtils.isBlank(parmValue))
    {
      int limit;
      // Check that it is an integer
      try { limit = Integer.parseInt(parmValue); }
      catch (NumberFormatException e)
      {
        String msg = "Invalid query parameter. Must be an integer. Parameter name: " + parmName + " Value: " + parmValue;
        _log.error(msg);
        requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST).entity(msg).build());
        return;
      }
      threadContext.setLimit(limit);
    }

    // Look for and extract offset query parameter.
    parmName = PARM_OFFSET;
    if (badParm(requestContext, parmName)) { return; }
    parmValue = getQueryParm(queryParameters, parmName);
    if (!StringUtils.isBlank(parmValue))
    {
      int offset;
      // Check that it is an integer
      try { offset = Integer.parseInt(parmValue); }
      catch (NumberFormatException e)
      {
        String msg = "Invalid query parameter. Must be an integer. Parameter name: " + parmName + " Value: " + parmValue;
        _log.error(msg);
        requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST).entity(msg).build());
        return;
      }
      threadContext.setOffset(offset);
    }

    // Look for and extract sort_by query parameter.
    parmName = PARM_SORTBY;
    if (badParm(requestContext, parmName)) { return; }
    parmValue = getQueryParm(queryParameters, parmName);
    if (!StringUtils.isBlank(parmValue)) threadContext.setSortBy(parmValue);

    // Look for and extract start_after query parameter.
    parmName = PARM_STARTAFTER;
    if (badParm(requestContext, parmName)) { return; }
    parmValue = getQueryParm(queryParameters, parmName);
    if (!StringUtils.isBlank(parmValue)) threadContext.setStartAfter(parmValue);
  }

  /**
   * Common checks for query parameters
   *   - Check that if parameter is present there is only one value
   * @param requestContext - context containing parameters
   * @param parmName - parameter to check
   * @return true if problems
   */
  private static boolean badParm(ContainerRequestContext requestContext, String parmName)
  {
    MultivaluedMap<String, String> queryParameters = requestContext.getUriInfo().getQueryParameters();
    // Check that it is a single value
    if (queryParameters.containsKey(parmName) && queryParameters.get(parmName).size() != 1)
    {
      String msg = "Invalid query parameter: Multiple values specified. Parameter name: " + parmName;
      _log.error(msg);
      requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST).entity(msg).build());
      return true;
    }
    return false;
  }

  /**
   * Get query parameter if present
   * @param queryParameters - parameters from request context
   * @param parmName - parameter to retrieve
   * @return string value of parameter or null if parameter not present
   */
  private static String getQueryParm(MultivaluedMap<String, String> queryParameters, String parmName)
  {
    String parmValue = null;
    if (queryParameters.containsKey(parmName))
    {
      parmValue = queryParameters.get(PARM_PRETTY).get(0);
      _log.debug("Found query parameter. Name: " + parmName + " Value: " + parmValue);
    }
    return parmValue;
  }
}
