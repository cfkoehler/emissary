package emissary.util;

import java.util.HashMap;
import java.util.Map;

public class ObjectTracing {

    public static Map<String, String> CreateTraceMessageMap(String id, String stage) {
        Map<String, String> jsonMap = new HashMap<>();
        jsonMap.put("ID", id); // TODO: Make ID field key configurable
        jsonMap.put("STAGE", stage);
        return jsonMap;
    }


}

