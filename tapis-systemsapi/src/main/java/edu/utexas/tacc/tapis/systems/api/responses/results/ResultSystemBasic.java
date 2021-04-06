package edu.utexas.tacc.tapis.systems.api.responses.results;

import edu.utexas.tacc.tapis.systems.model.SystemBasic;
import edu.utexas.tacc.tapis.systems.model.TSystem;

/*
    Class representing a SystemBasic result to be returned
 */
public final class ResultSystemBasic
{
  public String id;
  public TSystem.SystemType systemType;
  public String host;
  public TSystem.AuthnMethod defaultAuthnMethod;
  public boolean canExec;

  public ResultSystemBasic(SystemBasic s)
  {
    id = s.getId();
    systemType = s.getSystemType();
    host = s.getHost();
    defaultAuthnMethod = s.getDefaultAuthnMethod();
    canExec = s.getCanExec();
  }
}
