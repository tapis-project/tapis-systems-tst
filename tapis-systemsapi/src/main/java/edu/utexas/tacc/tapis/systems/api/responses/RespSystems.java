package edu.utexas.tacc.tapis.systems.api.responses;

import edu.utexas.tacc.tapis.sharedapi.responses.RespSearch;
import edu.utexas.tacc.tapis.sharedapi.responses.results.ResultMetadata;
import edu.utexas.tacc.tapis.sharedapi.responses.results.ResultSearch;
import edu.utexas.tacc.tapis.systems.api.responses.results.ResultSystem;
import edu.utexas.tacc.tapis.systems.model.TSystem;

import java.util.ArrayList;
import java.util.List;

/*
  Results from a retrieval of TSystem resources.
 */
public final class RespSystems extends RespSearch
{

  public RespSystems(List<TSystem> sList, int limit, String orderBy, int skip, String startAfter, int totalCount)
  {
    List<ResultSystem> resultSystems = new ArrayList<>();
    for (TSystem sys : sList) { resultSystems.add(new ResultSystem(sys)); }
    result = new ResultSearch();
    result.search = resultSystems;
    ResultMetadata tmpMeta = new ResultMetadata();
    tmpMeta.recordCount = resultSystems.size();
    tmpMeta.recordLimit = limit;
    tmpMeta.recordsSkipped = skip;
    tmpMeta.orderBy = orderBy;
    tmpMeta.startAfter = startAfter;
    tmpMeta.totalCount = totalCount;
    result.metadata = tmpMeta;
  }
}
