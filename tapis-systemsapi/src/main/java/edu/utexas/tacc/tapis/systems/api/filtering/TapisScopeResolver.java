package edu.utexas.tacc.tapis.systems.api.filtering;

import org.glassfish.jersey.internal.util.Tokenizer;
import org.glassfish.jersey.message.filtering.spi.ScopeResolver;

import javax.inject.Singleton;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * Custom Jersey 2 entity filtering scope resolver for Tapis API.
 * Based on org.glassfish.jersey.message.filtering.SelectableScopeResolver (Jersey 2.33)
 * TODO/TBD: This may work for retrieving single entity but probably needs modification
 *           to handle getting a list where search result is at a lower level in the response.
 */
@Singleton
public class TapisScopeResolver implements ScopeResolver
{
  // Prefix for all filter scopes
  public static final String PREFIX = TapisScopeResolver.class.getName() + "_";
  // Default scope for when no filter is given (all fields included)
  public static final String DEFAULT_SCOPE = PREFIX + "*";

  @Context
  private UriInfo uriInfo;

  // Implement interface method for resolving entity filtering scopes based on annotations
  // In this case the annotations are generated dynamically at runtime based on a query parameter.
  @Override
  public Set<String> resolve(final Annotation[] annotations)
  {
    final Set<String> scopes = new HashSet<>();

    // TODO: Replace hard coded string with constant
    final List<String> fields = uriInfo.getQueryParameters().get("fields");

    // Some standard fields are always included: result, status, message, version, result.id
    // TODO: Getting close, but still not quite there. For example, ?fields=jobRuntimes results in only the
    //       version attribute of the jobRuntime object to be returned. Apparently adding "version" to the scope
    //       causes it to be picked up anywhere in the entity graph.
    //       Need to figure out how to implement a scope like jobRuntimes.*
    scopes.add(PREFIX + "status");
    scopes.add(PREFIX + "message");
    scopes.add(PREFIX + "version");
    scopes.add(PREFIX + "result");
    scopes.add(PREFIX + "id");

    // If filter has been given then only add the result fields specified,
    // else all fields are to be included.
    if (fields != null && !fields.isEmpty())
    {
      for (final String fieldList : fields) { scopes.addAll(getScopesForField(fieldList)); }
    }
    else
    {
      scopes.add(DEFAULT_SCOPE);
    }
    return scopes;
  }

  /**
   * Determine the annotation scopes for a comma separated list of field names
   * @param fieldNameList - list of comma separated field names
   * @return Resulting scopes
   */
  private Set<String> getScopesForField(final String fieldNameList)
  {
    final Set<String> scopes = new HashSet<>();
    // Query parameter may be repeated and each entry may have a comma separated list of fields to include.
    // add specific scope in case of specific request
    final String[] fields = Tokenizer.tokenize(fieldNameList, ",");
    for (final String field : fields) { scopes.add(PREFIX + field); }
    return scopes;
  }
}