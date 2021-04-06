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
public final class RespSystemsSearch extends RespSearch
{

  // NOTE: Having this attribute here seems necessary although not clear why since it appears to be unused.
  //       Without it the returned json has java object references listed in the result.search list.
  public List<TSystem> results;

  // Zero arg constructor needed to use jersey's SelectableEntityFilteringFeature
// TODO needed?  public RespSystemsSearch() { }

  public RespSystemsSearch(List<TSystem> sList, int limit, String orderBy, int skip, String startAfter, int totalCount)
  {
    List<ResultSystem> tmpResults;
    tmpResults = new ArrayList<>();
    for (TSystem sys : sList) { tmpResults.add(new ResultSystem(sys)); }
    result = new ResultSearch();
    result.search = tmpResults;
    ResultMetadata tmpMeta = new ResultMetadata();
    tmpMeta.recordCount = tmpResults.size();
    tmpMeta.recordLimit = limit;
    tmpMeta.recordsSkipped = skip;
    tmpMeta.orderBy = orderBy;
    tmpMeta.startAfter = startAfter;
    tmpMeta.totalCount = totalCount;
    result.metadata = tmpMeta;
  }
}