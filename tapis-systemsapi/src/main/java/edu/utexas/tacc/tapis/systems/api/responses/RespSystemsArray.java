package edu.utexas.tacc.tapis.systems.api.responses;

import edu.utexas.tacc.tapis.sharedapi.responses.RespAbstract;
import edu.utexas.tacc.tapis.systems.api.responses.results.ResultSystem;
import edu.utexas.tacc.tapis.systems.model.TSystem;

import java.util.ArrayList;
import java.util.List;

public final class RespSystemsArray extends RespAbstract
{
  public List<ResultSystem> result;
  // Zero arg constructor needed to use jersey's SelectableEntityFilteringFeature
  public RespSystemsArray() { }

  public RespSystemsArray(List<TSystem> sList)
  {
    result = new ArrayList<>();
    for (TSystem sys : sList) { result.add(new ResultSystem(sys)); }
  }
}
