package xyz.fallnight.server.persistence.vault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import xyz.fallnight.server.domain.vault.PlayerVault;
import xyz.fallnight.server.domain.vault.VaultPage;
import xyz.fallnight.server.persistence.JacksonMappers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.kyori.adventure.nbt.TagStringIO;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class JsonVaultRepository implements VaultRepository {
    private static final String[] MATERIAL_KEYS = {"material", "type", "id", "minecraftId", "name"};
    private static final String[] AMOUNT_KEYS = {"amount", "count", "qty"};

    private final Path vaultsDirectory;
    private final ObjectMapper mapper;
    private final ConcurrentMap<String, PlayerVault> cache;

    public JsonVaultRepository(Path vaultsDirectory) {
        this(vaultsDirectory, JacksonMappers.jsonMapper());
    }

    public JsonVaultRepository(Path vaultsDirectory, ObjectMapper mapper) {
        this.vaultsDirectory = vaultsDirectory;
        this.mapper = mapper;
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public Map<String, PlayerVault> loadAll() throws IOException {
        Files.createDirectories(vaultsDirectory);
        Map<String, PlayerVault> loaded = new LinkedHashMap<>();

        try (var files = Files.list(vaultsDirectory)) {
            files
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .forEach(path -> {
                    try {
                        PlayerVault vault = readVault(path);
                        String key = normalize(vault.getOwner() == null ? stripExtension(path) : vault.getOwner());
                        if (vault.getOwner() == null || vault.getOwner().isBlank()) {
                            vault.setOwner(stripExtension(path));
                        }
                        loaded.put(key, vault);
                    } catch (IOException exception) {
                        throw new RepositoryReadException(path, exception);
                    }
                });
        } catch (RepositoryReadException wrapped) {
            throw wrapped.getCause();
        }

        cache.clear();
        cache.putAll(loaded);
        return Map.copyOf(loaded);
    }

    @Override
    public Optional<PlayerVault> findByOwner(String owner) {
        if (owner == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.get(normalize(owner)));
    }

    @Override
    public void save(PlayerVault vault) throws IOException {
        Files.createDirectories(vaultsDirectory);

        String key = normalize(vault.getOwner());
        Path file = vaultsDirectory.resolve(key + ".json");
        mapper.writeValue(file.toFile(), toJson(vault));
        cache.put(key, vault);
    }

    @Override
    public void saveAll() throws IOException {
        for (PlayerVault vault : cache.values()) {
            save(vault);
        }
    }

    private PlayerVault readVault(Path path) throws IOException {
        JsonNode root = mapper.readTree(path.toFile());
        PlayerVault vault = new PlayerVault();

        String owner = text(root, "owner");
        if (owner == null || owner.isBlank()) {
            owner = stripExtension(path);
        }
        vault.setOwner(owner);

        int maxPages = integer(root, "maxPages", 1);
        JsonNode pagesNode = root.get("pages");
        if (pagesNode instanceof ArrayNode array) {
            maxPages = Math.max(maxPages, array.size());
        } else if (pagesNode != null && pagesNode.isObject()) {
            pagesNode.properties().forEach(entry -> {
                int pageNumber = parsePositiveInt(entry.getKey(), -1);
                if (pageNumber > 0) {
                    vault.setMaxPages(Math.max(vault.getMaxPages(), pageNumber));
                }
            });
            maxPages = Math.max(maxPages, vault.getMaxPages());
        }
        vault.setMaxPages(maxPages);

        if (pagesNode instanceof ArrayNode pages) {
            for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
                VaultPage page = deserializePage(pages.get(pageIndex));
                vault.setPage(pageIndex + 1, page);
            }
        } else if (pagesNode != null && pagesNode.isObject()) {
            pagesNode.properties().forEach(entry -> {
                int pageNumber = parsePositiveInt(entry.getKey(), -1);
                if (pageNumber > 0) {
                    vault.setPage(pageNumber, deserializePage(entry.getValue()));
                }
            });
        }

        return vault;
    }

    private ObjectNode toJson(PlayerVault vault) {
        ObjectNode root = mapper.createObjectNode();
        root.put("owner", vault.getOwner());
        root.put("maxPages", vault.getMaxPages());

        ObjectNode pages = root.putObject("pages");
        for (int pageIndex = 1; pageIndex <= vault.loadedPageCount(); pageIndex++) {
            VaultPage page = vault.page(pageIndex);
            if (page.items().isEmpty()) {
                continue;
            }
            ObjectNode serializedPage = pages.putObject(Integer.toString(pageIndex));
            for (Map.Entry<Integer, ItemStack> entry : page.items().entrySet()) {
                if (!VaultPage.isStorageSlot(entry.getKey())) {
                    continue;
                }
                serializedPage.set(Integer.toString(entry.getKey()), serializeItem(entry.getValue()));
            }
        }

        return root;
    }

    private VaultPage deserializePage(JsonNode pageNode) {
        VaultPage page = new VaultPage();
        if (pageNode == null || pageNode.isNull()) {
            return page;
        }

        if (pageNode.isArray()) {
            int index = 0;
            for (JsonNode element : pageNode) {
                int slot = element.has("slot") ? integer(element, "slot", index) : index;
                JsonNode itemNode = element.has("item") ? element.get("item") : element;
                decodeItem(itemNode).ifPresent(item -> page.setItem(slot, item));
                index++;
            }
            return page;
        }

        if (pageNode.isObject()) {
            if (pageNode.has("items") && pageNode.get("items").isArray()) {
                return deserializePage(pageNode.get("items"));
            }
            pageNode.properties().forEach(entry -> {
                int slot = parsePositiveInt(entry.getKey(), -1);
                if (VaultPage.isStorageSlot(slot)) {
                    decodeItem(entry.getValue()).ifPresent(item -> page.setItem(slot, item));
                }
            });
        }

        return page;
    }

    private ObjectNode serializeItem(ItemStack item) {
        ObjectNode node = mapper.createObjectNode();
        node.put("material", item.material().name().toLowerCase(Locale.ROOT));
        node.put("amount", item.amount());
        try {
            node.put("nbt", TagStringIO.get().asString(item.toItemNBT()));
        } catch (Exception ignored) {
        }
        return node;
    }

    private Optional<ItemStack> decodeItem(JsonNode node) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        if (node.isTextual()) {
            return materialFrom(node.asText()).map(material -> ItemStack.of(material, 1));
        }
        if (!node.isObject()) {
            return Optional.empty();
        }

        String nbt = text(node, "nbt");
        if (nbt != null && !nbt.isBlank()) {
            try {
                return Optional.of(ItemStack.fromItemNBT(TagStringIO.get().asCompound(nbt)));
            } catch (Exception ignored) {
            }
        }

        String materialName = null;
        for (String key : MATERIAL_KEYS) {
            materialName = text(node, key);
            if (materialName != null && !materialName.isBlank()) {
                break;
            }
        }

        Optional<Material> material = materialFrom(materialName);
        if (material.isEmpty()) {
            return Optional.empty();
        }

        int amount = 1;
        for (String key : AMOUNT_KEYS) {
            int candidate = integer(node, key, amount);
            if (candidate > 0) {
                amount = candidate;
                break;
            }
        }

        return Optional.of(ItemStack.of(material.get(), Math.max(1, amount)));
    }

    private static Optional<Material> materialFrom(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        return Optional.ofNullable(Material.fromKey(normalized));
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static int integer(JsonNode node, String key, int fallback) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (value.isInt() || value.isLong()) {
            return value.intValue();
        }
        if (value.isTextual()) {
            try {
                return Integer.parseInt(value.asText());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static int parsePositiveInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String normalize(String owner) {
        return owner.toLowerCase(Locale.ROOT);
    }

    private static String stripExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot == -1 ? name : name.substring(0, dot);
    }

    private static final class RepositoryReadException extends RuntimeException {
        private final IOException cause;

        private RepositoryReadException(Path path, IOException cause) {
            super("Failed reading vault file: " + path, cause);
            this.cause = cause;
        }

        @Override
        public synchronized IOException getCause() {
            return cause;
        }
    }
}
