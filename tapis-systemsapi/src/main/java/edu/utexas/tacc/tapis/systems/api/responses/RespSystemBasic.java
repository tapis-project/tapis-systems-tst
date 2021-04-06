package edu.utexas.tacc.tapis.systems.api.responses;

import edu.utexas.tacc.tapis.sharedapi.responses.RespAbstract;
import edu.utexas.tacc.tapis.systems.model.SystemBasic;

public final class RespSystemBasic extends RespAbstract
{
  public SystemBasic result;

  public RespSystemBasic(SystemBasic result) { this.result = result; }
}
