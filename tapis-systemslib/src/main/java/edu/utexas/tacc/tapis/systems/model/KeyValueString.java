package edu.utexas.tacc.tapis.systems.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * A simple general purpose key-value pair class to make it easier to process json with the same structure.
 * Used for TSystem.jobEnvVariables
 */
public final class KeyValueString
{
  /* ********************************************************************** */
  /*                                 Fields                                 */
  /* ********************************************************************** */
  // Logging
  private static final Logger _log = LoggerFactory.getLogger(KeyValueString.class);

  private final String key;   // Name for the logical queue
  private final String value;   // Name for the logical queue
  private final String keyValueStr;

  /* ********************************************************************** */
  /*                           Constructors                                 */
  /* ********************************************************************** */
  public KeyValueString(String key1, String value1)
  {
    key = key1;
    value = value1;
    keyValueStr = key1 + "=" + value1;
  }

  /* ********************************************************************** */
  /*                               Accessors                                */
  /* ********************************************************************** */
  public String getKey() { return key; }
  public String getValue() { return value; }

  @Override
  public String toString() {return keyValueStr;}

  public static KeyValueString fromString(String s)
  {
    // TODO
    return new KeyValueString("KVS", "TODO");
  }
}
