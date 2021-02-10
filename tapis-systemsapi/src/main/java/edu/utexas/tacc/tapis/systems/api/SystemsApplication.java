package edu.utexas.tacc.tapis.systems.api;

import javax.ws.rs.ApplicationPath;

import edu.utexas.tacc.tapis.security.client.SKClient;
import edu.utexas.tacc.tapis.shared.security.ServiceContext;
import edu.utexas.tacc.tapis.shared.security.TenantManager;
import edu.utexas.tacc.tapis.shared.TapisConstants;
import edu.utexas.tacc.tapis.shared.utils.TapisUtils;
import edu.utexas.tacc.tapis.sharedapi.jaxrs.filters.JWTValidateRequestFilter;
import edu.utexas.tacc.tapis.sharedapi.providers.ObjectMapperContextResolver;
import edu.utexas.tacc.tapis.sharedapi.providers.TapisExceptionMapper;
import edu.utexas.tacc.tapis.sharedapi.providers.ValidationExceptionMapper;
import edu.utexas.tacc.tapis.systems.api.filtering.TapisEntityProcessor;
import edu.utexas.tacc.tapis.systems.api.filtering.TapisScopeResolver;
import edu.utexas.tacc.tapis.systems.api.model.TSystemFilterView;
import edu.utexas.tacc.tapis.systems.config.RuntimeParameters;
import edu.utexas.tacc.tapis.systems.dao.SystemsDao;
import edu.utexas.tacc.tapis.systems.dao.SystemsDaoImpl;
import edu.utexas.tacc.tapis.systems.service.SystemsService;
import edu.utexas.tacc.tapis.systems.service.SystemsServiceImpl;
import edu.utexas.tacc.tapis.systems.service.SystemsServiceContextFactory;

import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.message.filtering.EntityFilteringFeature;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ResourceConfig;

import org.jooq.tools.StringUtils;

import java.lang.annotation.Annotation;
import java.net.URI;

/*
 * Main startup class for the web application. Uses Jersey and Grizzly frameworks.
 *   Performs setup for HK2 dependency injection.
 *   Registers packages and features for Jersey.
 *   Gets runtime parameters from the environment.
 *   Initializes the service:
 *     Tapis site id.
 *     DB creation or migration
 *   Starts the Grizzly server.
 *
 * The path here is appended to the context root and is configured to work when invoked in a standalone
 * container (command line) and in an IDE (such as eclipse).
 * ApplicationPath set to "/" since each resource class includes "/v3/systems" in the
 *     path set at the class level. See SystemResource.java, PermsResource.java, etc.
 *     This has been found to be a more robust scheme for keeping startup working for both
 *     running in an IDE and standalone.
 */
@ApplicationPath("/")
public class SystemsApplication extends ResourceConfig
{
  // We must be running on a specific site and this will never change
  private static String siteId;
  public static String getSiteId() {return siteId;}

  // For all logging use println or similar so we do not have a dependency on a logging subsystem.
  public SystemsApplication()
  {
    // Log our existence.
    // Output version information on startup
    System.out.println("**** Starting tapis-systems. Version: " + TapisUtils.getTapisFullVersion() + " ****");

    // Setup and register Jersey's dynamic filtering
    // This allows for returning selected attributes in a return result
    //   using the query parameter select, e.g.
    //   /v3/systems?select=result.id,result.name,result.host,result.enabled
//    property(SelectableEntityFilteringFeature.QUERY_PARAM_NAME, "select");
//    register(SelectableEntityFilteringFeature.class);
    // Register either Jackson or Moxy for SelectableEntityFiltering
    // NOTE: Using shaded jar and Moxy works when running from Intellij IDE but breaks things when running in docker.
    // NOTE: Using Jackson results in following TSystem attributes not being returned: notes, created, updated.
    // NOTE: Using unshaded jar and Moxy appears to resolve all issues.
//    register(new MoxyJsonConfig().resolver());
// NOTE on Selectable feature: gave up on this (for now) as too cumbersome and limited. Awkward to specify attributes
//      and could not get it to work when list of systems nested in results->search.

    // Register Jersey filtering feature to support returning selected attributes in response.
//    property(EntityFilteringFeature.ENTITY_FILTERING_SCOPE, new Annotation[] {TSystemFilterView.Factory.get()});
    register(EntityFilteringFeature.class);
    register(TapisEntityProcessor.class);
    register(TapisScopeResolver.class);


    // Use jackson as opposed to Moxy.
    // Initially there were problems with notes and authnCredential but with a custom objectmapper
    // and custom jsonobject serializer the problems were resolved.
    register(JacksonFeature.class);

    // Needed for properly returning timestamps
    // Also allows for setting a breakpoint when response is being constructed.
    register(ObjectMapperContextResolver.class);

    // Register classes needed for returning a standard Tapis response for non-Tapis exceptions.
    register(TapisExceptionMapper.class);
    register(ValidationExceptionMapper.class);
    // Register service class for calling init method during application startup
    register(SystemsServiceImpl.class);

    // We specify what packages JAX-RS should recursively scan to find annotations. By setting the value to the
    // top-level directory in all projects, we can use JAX-RS annotations in any tapis class. In particular, the filter
    // classes in tapis-shared-api will be discovered whenever that project is included as a maven dependency.
    packages("edu.utexas.tacc.tapis");

    // Set the application name. Note that this has no impact on base URL.
    setApplicationName(TapisConstants.SERVICE_NAME_SYSTEMS);

    // Perform remaining init steps in try block so we can print a fatal error message if something goes wrong.
    try {
      // Get runtime parameters
      RuntimeParameters runParms = RuntimeParameters.getInstance();

      // Set site on which we are running. This is a required runtime parameter.
      siteId = runParms.getSiteId();

      // Initialize security filter used when processing a request.
      JWTValidateRequestFilter.setService(TapisConstants.SERVICE_NAME_SYSTEMS);
      JWTValidateRequestFilter.setSiteId(siteId);

      // Initialize tenant manager singleton. This can be used by all subsequent application code, including filters.
      // The base url of the tenants service is a required input parameter.
      // Retrieve the tenant list from the tenant service now to fail fast if we cannot access the list.
      String url = runParms.getTenantsSvcURL();
      TenantManager.getInstance(url).getTenants();

      // Initialize bindings for HK2 dependency injection
      register(new AbstractBinder() {
        @Override
        protected void configure() {
          bind(SystemsServiceImpl.class).to(SystemsService.class); // Used in Resource classes for most service calls
          bind(SystemsServiceImpl.class).to(SystemsServiceImpl.class); // Used in SystemsResource for checkDB
          bind(SystemsDaoImpl.class).to(SystemsDao.class); // Used in service impl
          bindFactory(SystemsServiceContextFactory.class).to(ServiceContext.class); // Used in service impl and SystemsResource
          bind(SKClient.class).to(SKClient.class); // Used in service impl
        }
      });
    } catch (Exception e) {
      // This is a fatal error
      System.out.println("**** FAILURE TO INITIALIZE: tapis-systemsapi ****");
      e.printStackTrace();
      throw e;
    }
  }

  /**
   * Embedded Grizzly HTTP server
   */
  public static void main(String[] args) throws Exception
  {
    // If TAPIS_SERVICE_PORT set in env then use it.
    // Useful for starting service locally on a busy system where 8080 may not be available.
    String servicePort = System.getenv("TAPIS_SERVICE_PORT");
    if (StringUtils.isBlank(servicePort)) servicePort = "8080";

    // Set base protocol and port. If mainly running in k8s this may not need to be configurable.
    final URI baseUri = URI.create("http://0.0.0.0:" + servicePort + "/");

    // Initialize the application container
    SystemsApplication config = new SystemsApplication();

    // Initialize the service
    // In order to instantiate our service class using HK2 we need to create an application handler
    //   which allows us to get an injection manager which is used to get a locator.
    //   The locator is used get classes that have been registered using AbstractBinder.
    // NOTE: As of Jersey 2.26 dependency injection was abstracted out to make it easier to use DI frameworks
    //       other than HK2, although finding docs and examples on how to do so seems difficult.
    ApplicationHandler handler = new ApplicationHandler(config);
    InjectionManager im = handler.getInjectionManager();
    ServiceLocator locator = im.getInstance(ServiceLocator.class);
    SystemsServiceImpl svcImpl = locator.getService(SystemsServiceImpl.class);
    // Call the main service init method
    svcImpl.initService(SystemsApplication.getSiteId());
    // Create and start the server
    final HttpServer server = GrizzlyHttpServerFactory.createHttpServer(baseUri, config, false);
    server.start();
  }
}
