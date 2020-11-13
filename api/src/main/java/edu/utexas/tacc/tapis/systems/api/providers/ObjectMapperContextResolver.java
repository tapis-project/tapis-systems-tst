package edu.utexas.tacc.tapis.systems.api.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

/*
 * Custom mapper for Jackson
 * TODO: This and TapisObjectMapper are classes from tapis-files repo. Move to shared code?
 */
@Provider
public class ObjectMapperContextResolver implements ContextResolver<ObjectMapper>
{
  private final ObjectMapper mapper;

  public ObjectMapperContextResolver() {
    this.mapper = createObjectMapper();
  }

  @Override
  public ObjectMapper getContext(Class<?> type) {
    return mapper;
  }

  private ObjectMapper createObjectMapper() {
    ObjectMapper mapper = new TapisObjectMapper().getMapper();
    return mapper;
  }
}

