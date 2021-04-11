package edu.utexas.tacc.tapis.systems.api.responses;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import edu.utexas.tacc.tapis.shared.utils.JsonObjectSerializer;
import edu.utexas.tacc.tapis.shared.utils.TapisGsonUtils;
import edu.utexas.tacc.tapis.sharedapi.responses.RespAbstract;
import edu.utexas.tacc.tapis.sharedapi.responses.RespSearch;
import edu.utexas.tacc.tapis.sharedapi.responses.results.ResultMetadata;
import edu.utexas.tacc.tapis.sharedapi.responses.results.ResultSearch;
import edu.utexas.tacc.tapis.systems.api.responses.results.TapisSystemDTO;
import edu.utexas.tacc.tapis.systems.model.TSystem;

import java.util.ArrayList;
import java.util.List;

/*
  Results from a retrieval of TSystem resources.
 */
public final class RespSystems extends RespAbstract
{
  // Json objects require special serializer for Jackson to handle properly in outgoing response.
  @JsonSerialize(using = JsonObjectSerializer.class)
  public JsonObject result;

  public RespSystems(List<TSystem> sList, int limit, String orderBy, int skip, String startAfter, int totalCount,
                     List<String> selectList)
  {
    result = new JsonObject();
    JsonArray resultList = new JsonArray();
    for (TSystem sys : sList)
    {
      resultList.add(new TapisSystemDTO(sys).getDisplayObject(selectList));
    }
    ResultMetadata tmpMeta = new ResultMetadata();
    tmpMeta.recordCount = resultList.size();
    tmpMeta.recordLimit = limit;
    tmpMeta.recordsSkipped = skip;
    tmpMeta.orderBy = orderBy;
    tmpMeta.startAfter = startAfter;
    tmpMeta.totalCount = totalCount;
    String metaJsonStr = TapisGsonUtils.getGson().toJson(tmpMeta);
    JsonObject metaJsonObj = TapisGsonUtils.getGson().fromJson(metaJsonStr, JsonObject.class);
    result.add("metadata", metaJsonObj);
    result.add("resultList", resultList);
  }
}
