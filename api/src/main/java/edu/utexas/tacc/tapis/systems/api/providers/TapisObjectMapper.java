package edu.utexas.tacc.tapis.systems.api.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * This class is a singleton for getting the default object mapper for the files service. This way, we can
 * use the mapper the web api and also when serializing objects in rabbitmq etc.
 * TODO: This and ObjectMapperContextResolver are classes from tapis-files repo. Move to shared code?
 */
class TapisObjectMapper
{
  private static ObjectMapper mapper;
  static
  {
    mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
  }
  /**
   * gets/creates the object mapper and configures it for datetimes etc.
   *
   * @return ObjectMapper
   */
  public ObjectMapper getMapper() { return mapper; }
}
