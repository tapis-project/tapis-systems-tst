package edu.utexas.tacc.tapis.systems.api.filtering;

import org.glassfish.jersey.message.filtering.spi.AbstractEntityProcessor;
import org.glassfish.jersey.message.filtering.spi.EntityGraph;
import org.glassfish.jersey.message.filtering.spi.EntityProcessor;

import javax.annotation.Priority;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

/*
 * Custom Jersey 2 entity processor for Tapis API.
 * Based on org.glassfish.jersey.message.filtering.SelectableEntityProcessor (Jersey 2.33)
 */
@Singleton
@Priority(Integer.MAX_VALUE - 5000)
public class TapisEntityProcessor extends AbstractEntityProcessor
{
  // Override abstract method for processing of each field name
  @Override
  protected Result process(final String fieldName, final Class<?> fieldClass, final Annotation[] fieldAnnotations,
                           final Annotation[] annotations, final EntityGraph graph) {
    if (fieldName != null)
    {
      final Set<String> scopes = new HashSet<>();
      // add default selectable scope in case of none requested
      scopes.add(TapisScopeResolver.DEFAULT_SCOPE);
      // add specific scope in case of specific request
      scopes.add(TapisScopeResolver.PREFIX + fieldName);
      // Call abstract method to add fields to the entity graph
      addFilteringScopes(fieldName, fieldClass, scopes, graph);
    }
    return EntityProcessor.Result.APPLY;
  }
}