package edu.utexas.tacc.tapis.systems.dao;

import org.jooq.Converter;
import edu.utexas.tacc.tapis.systems.model.TSystem.TransferMethod;

/*
 NOTE: Could not get this to work. This was with jooq v 3.13.1. Looks like later version of jooq (3.14.8) may have
       better support for converting to/from array types.
 */
public class TransferMethodArrayConverter implements Converter<String[], TransferMethod[]>
{
  public TransferMethod[] from(String[] sa)
  {
    TransferMethod[] ta = {};
    return ta;
  }

  public String[] to(TransferMethod[] ta)
  {
    String[] sa = {};
    return sa;
  }

  public Class<String[]> fromType() { return String[].class; }
  public Class<TransferMethod[]> toType() { return TransferMethod[].class; }
}