package com.gmail.mooman219;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.collect.ImmutableList;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * This class will request the signed skin from Mojang for each of the given
 * UUIDs. Upon receiving the skin, it is cached for a default of 30 minutes.
 * This value can be changed to as low as 1 minute before receiving a "Too many
 * Requests" error.
 *
 * The returned JSONObject is composed as such:
 * {"name":"textures","value":(Blob),"signature":(Blob)}
 * @author Mooman
 */
public class SkinFetcher implements Callable<Map<UUID, JSONObject>> {

    private static final String SKIN_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final int UNIQUE_REQUEST_DELAY = 30; // The minimum 1 request per UUID per minute.
    private static final Cache<UUID, JSONObject> skinCache = buildCache();
    private final List<UUID> uuids;

    public SkinFetcher(List<UUID> uuids) {
        this.uuids = ImmutableList.copyOf(uuids);
    }

    @Override
    public Map<UUID, JSONObject> call() throws Exception {
        Map<UUID, JSONObject> skinMap = new HashMap<UUID, JSONObject>();
        for(UUID uuid : uuids) {
            if(uuid == null) {
                continue;
            }
            skinMap.put(uuid, skinCache.getUnchecked(uuid));
        }
        return skinMap;
    }

    private static HttpURLConnection createConnection(UUID uuid) throws Exception {
        URL url = new URL(SKIN_URL + uuid.toString().replace("-", "") + "?unsigned=false");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setUseCaches(false);
        connection.setDoInput(true);
        connection.setDoOutput(false);
        return connection;
    }

    private static Cache<UUID, JSONObject> buildCache() {
        return CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(UNIQUE_REQUEST_DELAY, TimeUnit.MINUTES)
            .build(new CacheLoader<UUID, JSONObject>() {
                @Override
                public JSONObject load(UUID uuid) throws Exception {
                    HttpURLConnection connection = createConnection(uuid);
                    JSONObject texture = null;
                    JSONObject result = (JSONObject) new JSONParser().parse(new InputStreamReader(connection.getInputStream()));
                    if(result != null && result.containsKey("properties") && ((JSONArray) result.get("properties")).size() > 0) {
                        texture = (JSONObject) ((JSONArray) result.get("properties")).get(0);
                    }
                    return texture;
                }
            });
    }

    public static JSONObject getSkinOf(UUID uuid) throws Exception {
        return new SkinFetcher(Arrays.asList(uuid)).call().get(uuid);
    }
}
