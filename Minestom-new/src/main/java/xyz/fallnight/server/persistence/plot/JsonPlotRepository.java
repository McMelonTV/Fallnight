package xyz.fallnight.server.persistence.plot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import xyz.fallnight.server.domain.plot.PlotCoordinate;
import xyz.fallnight.server.domain.plot.PlotEntry;
import xyz.fallnight.server.persistence.JacksonMappers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonPlotRepository implements PlotRepository {
    private final Path plotsFile;
    private final ObjectMapper mapper;

    public JsonPlotRepository(Path plotsFile) {
        this(plotsFile, JacksonMappers.jsonMapper());
    }

    public JsonPlotRepository(Path plotsFile, ObjectMapper mapper) {
        this.plotsFile = plotsFile;
        this.mapper = mapper;
    }

    @Override
    public Map<PlotCoordinate, PlotEntry> loadAll() throws IOException {
        ensureParentDirectory();
        if (Files.notExists(plotsFile)) {
            return Map.of();
        }

        JsonNode root = mapper.readTree(plotsFile.toFile());
        JsonNode plotsNode = root != null && root.has("plots") ? root.get("plots") : root;
        if (plotsNode == null || !plotsNode.isObject()) {
            return Map.of();
        }

        Map<PlotCoordinate, PlotEntry> loaded = new LinkedHashMap<>();
        plotsNode.fields().forEachRemaining(entry -> {
            PlotCoordinate.parse(entry.getKey()).ifPresent(coordinate -> {
                PlotEntry plotEntry = parsePlotEntry(entry.getValue());
                if (plotEntry.getOwner() == null || plotEntry.getOwner().isBlank()) {
                    return;
                }
                loaded.put(coordinate, plotEntry);
            });
        });
        return Map.copyOf(loaded);
    }

    @Override
    public void saveAll(Map<PlotCoordinate, PlotEntry> plots) throws IOException {
        ensureParentDirectory();

        ObjectNode root = mapper.createObjectNode();
        ObjectNode plotsNode = root.putObject("plots");
        plots.entrySet().stream()
            .sorted(Map.Entry.comparingByKey((left, right) -> {
                int xCompare = Integer.compare(left.x(), right.x());
                if (xCompare != 0) {
                    return xCompare;
                }
                return Integer.compare(left.z(), right.z());
            }))
            .forEach(entry -> plotsNode.set(entry.getKey().key(), toNode(entry.getValue())));

        mapper.writeValue(plotsFile.toFile(), root);
    }

    private PlotEntry parsePlotEntry(JsonNode node) {
        PlotEntry entry = new PlotEntry();
        if (node == null || !node.isObject()) {
            return entry;
        }

        entry.setOwner(text(node, "owner"));
        entry.setName(text(node, "name"));
        entry.setMembers(stringList(node.get("members")));
        entry.setBlockedUsers(stringList(node.get("blockedUsers")));
        return entry;
    }

    private ObjectNode toNode(PlotEntry entry) {
        ObjectNode node = mapper.createObjectNode();
        node.put("owner", entry.getOwner());

        var members = node.putArray("members");
        for (String member : entry.getMembers()) {
            members.add(member);
        }

        var blockedUsers = node.putArray("blockedUsers");
        for (String blocked : entry.getBlockedUsers()) {
            blockedUsers.add(blocked);
        }

        node.put("name", entry.getName() == null ? "" : entry.getName());
        return node;
    }

    private void ensureParentDirectory() throws IOException {
        Path parent = plotsFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText();
    }

    private static List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode child : node) {
            if (child == null || child.isNull()) {
                continue;
            }
            String text = child.asText().trim();
            if (!text.isEmpty()) {
                values.add(text);
            }
        }
        return values;
    }
}
