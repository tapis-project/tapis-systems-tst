package edu.utexas.tacc.tapis.systems.api.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

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
/**
 * This class is a singleton for getting the default object mapper for the files service. This way, we can
 * use the mapper the web api and also when serializing objects in rabbitmq etc.
 */
// TODO: this should be static and/or in a separate class for re-use?
class TapisObjectMapper
{

  private ObjectMapper mapper;

  /**
   * gets/creates the object mapper and configures it for datetimes etc.
   *
   * @return ObjectMapper
   */
  public ObjectMapper getMapper()
  {
    if (mapper == null)
    {
      mapper = new ObjectMapper();
      mapper.registerModule(new JavaTimeModule());
      mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
      return mapper;
    }
    return mapper;
  }
}

