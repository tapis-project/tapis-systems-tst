package edu.utexas.tacc.tapis.systems.api.responses;

import edu.utexas.tacc.tapis.sharedapi.responses.RespSearch;
import edu.utexas.tacc.tapis.sharedapi.responses.results.ResultMetadata;
import edu.utexas.tacc.tapis.sharedapi.responses.results.ResultSearch;
import edu.utexas.tacc.tapis.systems.api.responses.results.TapisSystemBasicDTO;
import edu.utexas.tacc.tapis.systems.model.SystemBasic;

import java.util.ArrayList;
import java.util.List;

/*
  Results from a retrieval of SystemBasic resources.
 */
public final class RespSystemsBasic extends RespSearch
{

  public RespSystemsBasic(List<SystemBasic> sList, int limit, String orderBy, int skip, String startAfter, int totalCount)
  {
    List<TapisSystemBasicDTO> resultSystems = new ArrayList<>();
    for (SystemBasic sys : sList) { resultSystems.add(new TapisSystemBasicDTO(sys)); }
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