package edu.utexas.tacc.tapis.systems.api.responses;

import edu.utexas.tacc.tapis.sharedapi.responses.RespAbstract;
import edu.utexas.tacc.tapis.systems.api.responses.results.ResultSystem;
import edu.utexas.tacc.tapis.systems.model.TSystem;

public final class RespSystem extends RespAbstract
{
  public ResultSystem result;

  public RespSystem(TSystem s)
  {
    result = new ResultSystem(s);
  }
}
