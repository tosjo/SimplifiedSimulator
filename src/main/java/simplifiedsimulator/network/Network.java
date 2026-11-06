package simplifiedsimulator.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * The lane-level model of the crossing: lanes, routes through it, signal groups, detectors
 * and bus stops. Loaded from {@code /crossing/network.json}, which is generated from
 * OpenStreetMap and the PDOK aerial photo by {@code tools/crossing/build_network.py}.
 */
public final class Network {

    public static final String DEFAULT_RESOURCE = "/crossing/network.json";

    private final String name;
    private final Map<String, Approach> approaches;
    private final Map<String, Lane> lanes;
    private final List<Route> routes;
    private final Map<String, SignalGroup> groups;
    private final List<Detector> detectors;
    private final List<BusStop> busStops;
    private final List<String> sources;

    private Network(String name, Map<String, Approach> approaches, Map<String, Lane> lanes, List<Route> routes,
                    Map<String, SignalGroup> groups, List<Detector> detectors, List<BusStop> busStops,
                    List<String> sources) {
        this.name = name;
        this.approaches = approaches;
        this.lanes = lanes;
        this.routes = routes;
        this.groups = groups;
        this.detectors = detectors;
        this.busStops = busStops;
        this.sources = sources;
    }

    /** Loads the network of the Harlingerstraatweg crossing bundled with the simulator. */
    public static Network loadDefault() {
        try (InputStream in = Network.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(DEFAULT_RESOURCE + " is missing from the classpath");
            }
            return load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Network load(InputStream in) {
        Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
        return parse(JsonParser.parseReader(reader).getAsJsonObject());
    }

    static Network parse(JsonObject root) {
        Map<String, Approach> approaches = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("approaches").entrySet()) {
            JsonObject a = e.getValue().getAsJsonObject();
            approaches.put(e.getKey(), new Approach(e.getKey(), a.get("name").getAsString(),
                    a.get("heading").getAsString()));
        }

        Map<String, Lane> lanes = new LinkedHashMap<>();
        for (JsonElement el : root.getAsJsonArray("lanes")) {
            JsonObject l = el.getAsJsonObject();
            List<Vec2> pts = new ArrayList<>();
            for (JsonElement p : l.getAsJsonArray("points")) {
                JsonArray xy = p.getAsJsonArray();
                pts.add(new Vec2(xy.get(0).getAsDouble(), xy.get(1).getAsDouble()));
            }
            Lane lane = new Lane(l.get("id").getAsString(), Mode.parse(l.get("kind").getAsString()),
                    l.get("width").getAsDouble(), l.get("speed").getAsDouble(), new Polyline(pts));
            lanes.put(lane.id(), lane);
        }

        Map<String, SignalGroup> groups = new LinkedHashMap<>();
        for (JsonElement el : root.getAsJsonArray("signalGroups")) {
            JsonObject g = el.getAsJsonObject();
            List<SignalHead> heads = new ArrayList<>();
            for (JsonElement h : g.getAsJsonArray("heads")) {
                JsonObject head = h.getAsJsonObject();
                JsonArray pos = head.getAsJsonArray("position");
                Lane lane = head.has("lane") && !head.get("lane").isJsonNull()
                        ? lanes.get(head.get("lane").getAsString()) : null;
                heads.add(new SignalHead(new Vec2(pos.get(0).getAsDouble(), pos.get(1).getAsDouble()),
                        head.get("facing").getAsDouble(), lane));
            }
            SignalGroup group = new SignalGroup(g.get("id").getAsString(), Mode.parse(g.get("kind").getAsString()),
                    g.get("name").getAsString(), g.get("approach").getAsString(), heads);
            groups.put(group.id(), group);
        }

        List<Route> routes = new ArrayList<>();
        for (JsonElement el : root.getAsJsonArray("routes")) {
            JsonObject r = el.getAsJsonObject();
            Set<Mode> modes = EnumSet.noneOf(Mode.class);
            r.getAsJsonArray("kinds").forEach(k -> modes.add(Mode.parse(k.getAsString())));
            List<Lane> routeLanes = new ArrayList<>();
            r.getAsJsonArray("lanes").forEach(id -> routeLanes.add(require(lanes, id.getAsString(), "lane")));
            SignalGroup group = require(groups, r.get("signalGroup").getAsString(), "signal group");
            Route route = new Route(r.get("id").getAsString(), modes, routeLanes, group,
                    require(lanes, r.get("stopLane").getAsString(), "lane"), r.get("stopS").getAsDouble(),
                    Movement.parse(r.get("movement").getAsString()), r.get("weight").getAsDouble(),
                    r.has("mergeYield") && r.get("mergeYield").getAsBoolean());
            group.addRoute(route);
            routes.add(route);
        }

        List<Detector> detectors = new ArrayList<>();
        for (JsonElement el : root.getAsJsonArray("detectors")) {
            JsonObject d = el.getAsJsonObject();
            detectors.add(new Detector(d.get("id").getAsString(), require(lanes, d.get("lane").getAsString(), "lane"),
                    require(groups, d.get("group").getAsString(), "signal group"),
                    Detector.Role.parse(d.get("role").getAsString()), d.get("from").getAsDouble(),
                    d.get("to").getAsDouble()));
        }

        List<BusStop> stops = new ArrayList<>();
        if (root.has("busStops")) {
            for (JsonElement el : root.getAsJsonArray("busStops")) {
                JsonObject b = el.getAsJsonObject();
                stops.add(new BusStop(b.get("id").getAsString(), b.get("name").getAsString(),
                        require(lanes, b.get("lane").getAsString(), "lane"), b.get("s").getAsDouble(),
                        b.get("dwell").getAsDouble()));
            }
        }

        List<String> sources = new ArrayList<>();
        if (root.has("sources")) {
            root.getAsJsonArray("sources").forEach(s -> sources.add(s.getAsString()));
        }

        return new Network(root.get("name").getAsString(), Collections.unmodifiableMap(approaches),
                Collections.unmodifiableMap(lanes), List.copyOf(routes), Collections.unmodifiableMap(groups),
                List.copyOf(detectors), List.copyOf(stops), List.copyOf(sources));
    }

    private static <T> T require(Map<String, T> map, String id, String what) {
        T value = map.get(id);
        if (value == null) {
            throw new IllegalArgumentException("Unknown " + what + " '" + id + "' in network file");
        }
        return value;
    }

    public String name() {
        return name;
    }

    public Map<String, Approach> approaches() {
        return approaches;
    }

    public Iterable<Lane> lanes() {
        return lanes.values();
    }

    public Lane lane(String id) {
        Lane lane = lanes.get(id);
        if (lane == null) {
            throw new NoSuchElementException("No lane " + id);
        }
        return lane;
    }

    public List<Route> routes() {
        return routes;
    }

    public Route route(String id) {
        return routes.stream().filter(r -> r.id().equals(id)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("No route " + id));
    }

    public Iterable<SignalGroup> signalGroups() {
        return groups.values();
    }

    public SignalGroup signalGroup(String id) {
        SignalGroup group = groups.get(id);
        if (group == null) {
            throw new NoSuchElementException("No signal group " + id);
        }
        return group;
    }

    public List<Detector> detectors() {
        return detectors;
    }

    public List<BusStop> busStops() {
        return busStops;
    }

    /** Data sources and their licences, for display in the application. */
    public List<String> sources() {
        return sources;
    }
}
