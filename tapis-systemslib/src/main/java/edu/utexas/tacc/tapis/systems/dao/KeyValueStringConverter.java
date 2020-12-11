package edu.utexas.tacc.tapis.systems.dao;

import edu.utexas.tacc.tapis.systems.model.KeyValueString;
import org.jooq.Converter;

public class KeyValueStringConverter implements Converter<KeyValueString, String>
{
  public KeyValueString to(String sa)
  {
    return KeyValueString.fromString(sa);
  }

  public String from(KeyValueString kvsa)
  {
    return kvsa.toString();
  }

  public Class<String> toType() { return String.class; }
  public Class<KeyValueString> fromType() { return KeyValueString.class; }
}
//public class KeyValueStringConverter implements Converter<KeyValueString[], String[]>
//{
//  public KeyValueString[] to(String[] sa)
//  {
//    KeyValueString[] kvsa = new KeyValueString[sa.length];
//    for (int i = 0; i < sa.length; i++) kvsa[i] = kvsa[i].fromString(sa[i]);
//    return kvsa;
//  }
//
//  public String[] from(KeyValueString[] kvsa)
//  {
//    String[] sa = new String[kvsa.length];
//    for (int i = 0; i < kvsa.length; i++) sa[i] = kvsa[i].toString();
//    return sa;
//  }
//
//  public Class<String[]> toType() { return String[].class; }
//  public Class<KeyValueString[]> fromType() { return KeyValueString[].class; }
//}