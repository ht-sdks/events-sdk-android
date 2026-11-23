package com.hightouch.analytics;

import android.content.SharedPreferences;
import android.util.Base64;
import com.hightouch.analytics.integrations.AliasPayload;
import com.hightouch.analytics.integrations.BasePayload;
import com.hightouch.analytics.integrations.GroupPayload;
import com.hightouch.analytics.integrations.IdentifyPayload;
import com.hightouch.analytics.integrations.Logger;
import com.hightouch.analytics.integrations.ScreenPayload;
import com.hightouch.analytics.integrations.TrackPayload;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static com.hightouch.analytics.SegmentIntegration.UTF_8;

final class StartupQueue {
    private final SharedPreferences prefs;
    private final Cartographer json;
    private final Crypto crypto;
    private final Logger logger;
    private final List<BasePayload> memory = new ArrayList<>();
    private final Map<String, String> replaying = new HashMap<>();
    private final Set<String> claimed = new HashSet<>();

    StartupQueue(SharedPreferences prefs, Cartographer json, Crypto crypto, Logger logger) {
        this.prefs = prefs;
        this.json = json;
        this.crypto = crypto;
        this.logger = logger;
    }

    synchronized void add(BasePayload event) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            json.toJson(event, new OutputStreamWriter(crypto.encrypt(bytes), UTF_8));
            long index = prefs.getLong("next", 0);
            if (!prefs.edit().putString(Long.toString(index), Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP))
                    .putLong("next", index + 1).commit()) throw new IOException("Preference write failed");
        } catch (Exception error) {
            logger.error(error, "Startup event could only be retained in memory.");
            memory.add(event);
        }
    }

    synchronized List<BasePayload> restore() {
        List<BasePayload> result = new ArrayList<>();
        TreeMap<Long, String> sorted = new TreeMap<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!entry.getKey().equals("next")) sorted.put(Long.parseLong(entry.getKey()), (String) entry.getValue());
        }
        for (Map.Entry<Long, String> entry : sorted.entrySet()) {
            try {
                byte[] bytes = Base64.decode(entry.getValue(), Base64.DEFAULT);
                ValueMap fields = new ValueMap(json.fromJson(new InputStreamReader(
                        crypto.decrypt(new ByteArrayInputStream(bytes)), UTF_8)));
                BasePayload event = decode(fields);
                replaying.put(event.messageId(), entry.getKey().toString());
                result.add(event);
            } catch (Exception error) {
                logger.error(error, "Could not restore startup event %s.", entry.getKey());
            }
        }
        result.addAll(memory);
        memory.clear();
        return result;
    }

    static BasePayload decode(ValueMap fields) {
        BasePayload.Builder<?, ?> builder;
        switch (fields.getEnum(BasePayload.Type.class, "type")) {
            case track: builder = new TrackPayload.Builder().event(fields.getString("event")); break;
            case screen: builder = new ScreenPayload.Builder().name(fields.getString("name")).category(fields.getString("category")); break;
            case group: builder = new GroupPayload.Builder().groupId(fields.getString("groupId")); break;
            case alias: builder = new AliasPayload.Builder().previousId(fields.getString("previousId")); break;
            case identify: builder = new IdentifyPayload.Builder().traits(fields.getValueMap("traits")); break;
            default: throw new IllegalArgumentException("Unknown startup event type");
        }
        if (fields.getString("anonymousId") != null) builder.anonymousId(fields.getString("anonymousId"));
        if (fields.getString("userId") != null) builder.userId(fields.getString("userId"));
        BasePayload result = builder.build();
        result.putAll(fields);
        return result;
    }

    synchronized void claim(String id) {
        if (replaying.containsKey(id)) claimed.add(id);
    }

    synchronized void finishDispatch(String id) {
        if (!claimed.contains(id)) acknowledge(id);
    }

    synchronized void acknowledge(String id) {
        String key = replaying.get(id);
        if (key != null && prefs.edit().remove(key).commit()) {
            replaying.remove(id);
            claimed.remove(id);
        }
    }
}
