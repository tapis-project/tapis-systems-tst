package edu.utexas.tacc.tapis.systems.api.responses;

import edu.utexas.tacc.tapis.sharedapi.responses.RespSearch;
import edu.utexas.tacc.tapis.sharedapi.responses.results.ResultMetadata;
import edu.utexas.tacc.tapis.sharedapi.responses.results.ResultSearch;
import edu.utexas.tacc.tapis.systems.api.responses.results.ResultSystem;
import edu.utexas.tacc.tapis.systems.api.responses.results.ResultSystemBasic;
import edu.utexas.tacc.tapis.systems.model.SystemBasic;
import edu.utexas.tacc.tapis.systems.model.TSystem;

import java.util.ArrayList;
import java.util.List;

/*
  Results from a retrieval of TSystem resources.
 */
public final class RespSystemsBasic extends RespSearch
{

  // NOTE: Having this attribute here seems necessary although not clear why since it appears to be unused.
  //       Without it the returned json has java object references listed in the result.search list.
//  public List<ResultSystemBasic> results;

  public RespSystemsBasic(List<SystemBasic> sList, int limit, String orderBy, int skip, String startAfter, int totalCount)
  {
    List<ResultSystemBasic> resultSystems = new ArrayList<>();
    for (SystemBasic sys : sList) { resultSystems.add(new ResultSystemBasic(sys)); }
    result = new ResultSearch();
    result.search = resultSystems;
    ResultMetadata tmpMeta = new ResultMetadata();
    tmpMeta.recordCount = sList.size();
    tmpMeta.recordLimit = limit;
    tmpMeta.recordsSkipped = skip;
    tmpMeta.orderBy = orderBy;
    tmpMeta.startAfter = startAfter;
    tmpMeta.totalCount = totalCount;
    result.metadata = tmpMeta;
  }
}