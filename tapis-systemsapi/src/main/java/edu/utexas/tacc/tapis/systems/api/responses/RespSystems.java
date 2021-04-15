package edu.utexas.tacc.tapis.systems.api.responses;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import edu.utexas.tacc.tapis.shared.utils.JsonArraySerializer;
import edu.utexas.tacc.tapis.shared.utils.TapisGsonUtils;
import edu.utexas.tacc.tapis.sharedapi.responses.RespAbstract;
import edu.utexas.tacc.tapis.sharedapi.responses.results.ResultListMetadata;
import edu.utexas.tacc.tapis.systems.api.responses.results.TapisSystemDTO;
import edu.utexas.tacc.tapis.systems.model.TSystem;

/*
  Results from a retrieval of TSystem resources.
 */
public final class RespSystems extends RespAbstract
{
  // Json objects require special serializer for Jackson to handle properly in outgoing response.
  @JsonSerialize(using = JsonArraySerializer.class)
  public JsonArray result;

  public RespSystems(List<TSystem> sList, int limit, String orderBy, int skip, String startAfter, int totalCount,
                     List<String> selectList)
  {
    result = new JsonArray();
    for (TSystem sys : sList)
    {
      result.add(new TapisSystemDTO(sys).getDisplayObject(selectList));
    }

    ResultListMetadata meta = new ResultListMetadata();
    meta.recordCount = result.size();
    meta.recordLimit = limit;
    meta.recordsSkipped = skip;
    meta.orderBy = orderBy;
    meta.startAfter = startAfter;
    meta.totalCount = totalCount;
    String metaJsonStr = TapisGsonUtils.getGson().toJson(meta);
    metadata = TapisGsonUtils.getGson().fromJson(metaJsonStr, JsonObject.class);
  }
}
