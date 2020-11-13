package edu.utexas.tacc.tapis.systems.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.gson.JsonObject;

import java.io.IOException;

public class JsonObjectSerializer extends JsonSerializer<JsonObject> {

  @Override
  public void serialize(JsonObject jsonObject, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException
  {
    jsonGenerator.writeRawValue(jsonObject.toString());
  }
}