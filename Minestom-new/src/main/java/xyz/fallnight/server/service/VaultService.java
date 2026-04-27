package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.domain.vault.PlayerVault;
import xyz.fallnight.server.domain.vault.VaultPage;
import xyz.fallnight.server.persistence.vault.VaultRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VaultService {
    private static final Logger LOGGER = LoggerFactory.getLogger(VaultService.class);
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final VaultRepository repository;
    private final RankService rankService;
    private final ConcurrentMap<String, PlayerVault> vaults;
    private final ConcurrentMap<AbstractInventory, OpenVaultSession> openInventories;
    private final ConcurrentMap<String, Boolean> openVaultByOwner;

    public VaultService(VaultRepository repository, RankService rankService) {
        this.repository = repository;
        this.rankService = rankService;
        this.vaults = new ConcurrentHashMap<>();
        this.openInventories = new ConcurrentHashMap<>();
        this.openVaultByOwner = new ConcurrentHashMap<>();

        MinecraftServer.getGlobalEventHandler().addListener(InventoryCloseEvent.class, this::handleInventoryClose);
    }

    public void loadAll() throws IOException {
        Map<String, PlayerVault> loaded = repository.loadAll();
        vaults.clear();
        vaults.putAll(loaded);
    }

    public PlayerVault getOrCreate(String username) {
        return vaults.computeIfAbsent(normalize(username), ignored -> new PlayerVault(username));
    }

    public Optional<PlayerVault> find(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(vaults.get(normalize(username)));
    }

    public int resolveAccessiblePageCount(UserProfile profile, PlayerVault vault) {
        int basePages = Math.max(0, vault.getMaxPages());
        int profileBound = profileMaxVaults(profile);
        if (profileBound > 0) {
            if (profileBound > vault.getMaxPages()) {
                vault.setMaxPages(profileBound);
            }
            basePages = Math.max(basePages, profileBound);
        }
        int rankBonus = Math.max(0, rankService.vaultPageLimit(profile));
        return Math.max(0, basePages + rankBonus);
    }

    public boolean openVaultPage(Player player, PlayerVault vault, int pageNumber, int maxPagesVisible) {
        int accessiblePages = Math.max(1, maxPagesVisible);
        int clampedPage = Math.max(1, Math.min(pageNumber, accessiblePages));
        String ownerKey = normalize(vault.getOwner());
        if (openVaultByOwner.putIfAbsent(ownerKey, Boolean.TRUE) != null) {
            return false;
        }

        Inventory inventory = new Inventory(InventoryType.CHEST_6_ROW, vaultTitle(player, vault));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> handleInventoryClick(event, inventory));
        OpenVaultSession session = new OpenVaultSession(ownerKey, vault.getOwner(), clampedPage, accessiblePages);
        openInventories.put(inventory, session);
        renderPage(inventory, vault, session);
        InventoryOpeners.replace(player, inventory);
        return true;
    }

    private void handleInventoryClick(InventoryPreClickEvent event, Inventory inventory) {
        OpenVaultSession session = openInventories.get(inventory);
        if (session == null) {
            return;
        }
        int slot = event.getSlot();
        if (slot < 45 || slot > 53) {
            return;
        }
        event.setCancelled(true);
        PlayerVault vault = vaults.get(session.ownerKey());
        if (vault == null) {
            return;
        }
        if (slot == 48 && session.pageNumber() > 1) {
            persistInventoryPage(inventory, vault, session.pageNumber());
            OpenVaultSession next = session.withPage(session.pageNumber() - 1);
            openInventories.put(inventory, next);
            renderPage(inventory, vault, next);
            return;
        }
        if (slot == 50 && session.pageNumber() < session.maxPagesVisible()) {
            persistInventoryPage(inventory, vault, session.pageNumber());
            OpenVaultSession next = session.withPage(session.pageNumber() + 1);
            openInventories.put(inventory, next);
            renderPage(inventory, vault, next);
            return;
        }
    }

    private void renderPage(Inventory inventory, PlayerVault vault, OpenVaultSession session) {
        VaultPage page = vault.page(session.pageNumber());
        for (int slot = 0; slot < VaultPage.STORAGE_SLOT_COUNT; slot++) {
            ItemStack item = page.getItem(slot);
            inventory.setItemStack(slot, item == null ? ItemStack.AIR : item);
        }
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItemStack(slot, menuItem(Material.RED_STAINED_GLASS_PANE, "§r§c/"));
        }
        inventory.setItemStack(48, session.pageNumber() > 1
            ? menuItem(Material.PAPER, "§r§bPrevious §7page")
            : menuItem(Material.BARRIER, "§r§c/"));
        inventory.setItemStack(49, menuItem(Material.CHEST, "§r§7Page §b" + session.pageNumber() + "§8/§b" + session.maxPagesVisible()));
        inventory.setItemStack(50, session.pageNumber() < session.maxPagesVisible()
            ? menuItem(Material.PAPER, "§r§bNext §7page")
            : menuItem(Material.BARRIER, "§r§c/"));
    }

    private static ItemStack menuItem(Material material, String name) {
        return ItemStack.of(material).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(name)));
    }

    private static Component vaultTitle(Player viewer, PlayerVault vault) {
        if (viewer != null && vault != null && viewer.getUsername().equalsIgnoreCase(vault.getOwner())) {
            return LEGACY.deserialize("§8Vault");
        }
        return LEGACY.deserialize("§b" + vault.getOwner() + "§8's vault");
    }

    private static void persistInventoryPage(AbstractInventory inventory, PlayerVault vault, int pageNumber) {
        VaultPage persistedPage = new VaultPage();
        for (int slot = 0; slot < VaultPage.STORAGE_SLOT_COUNT; slot++) {
            ItemStack item = inventory.getItemStack(slot);
            if (item == null || item.material() == Material.AIR) {
                continue;
            }
            persistedPage.setItem(slot, item);
        }
        vault.setPage(pageNumber, persistedPage);
    }

    public void save(PlayerVault vault) {
        try {
            repository.save(vault);
            vaults.put(normalize(vault.getOwner()), vault);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public void saveAll() throws IOException {
        repository.saveAll();
    }

    public void resetAll() {
        for (PlayerVault vault : vaults.values()) {
            vault.setMaxPages(1);
            for (int page = vault.loadedPageCount(); page >= 1; page--) {
                vault.setPage(page, new VaultPage());
            }
            save(vault);
        }
    }

    private void handleInventoryClose(InventoryCloseEvent event) {
        AbstractInventory inventory = event.getInventory();
        OpenVaultSession session = openInventories.remove(inventory);
        if (session == null) {
            return;
        }
        openVaultByOwner.remove(session.ownerKey());

        PlayerVault vault = vaults.get(session.ownerKey());
        if (vault == null) {
            return;
        }

        persistInventoryPage(inventory, vault, session.pageNumber());
        try {
            repository.save(vault);
        } catch (IOException exception) {
            LOGGER.error("Failed to save vault for {}", vault.getOwner(), exception);
            event.getPlayer().sendMessage(LEGACY.deserialize("§r§c§l> §r§7Failed to save your vault, please try again."));
        }
    }

    private static int profileMaxVaults(UserProfile profile) {
        if (profile == null) {
            return 0;
        }

        for (Map.Entry<String, Object> entry : profile.getExtraData().entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }

            String normalized = key.toLowerCase(Locale.ROOT);
            if (!normalized.equals("maxvaults")
                && !normalized.equals("maxvaultpages")
                && !normalized.equals("vaultmaxpages")
                && !normalized.equals("maxvault")) {
                continue;
            }

            int parsed = parsePositiveInt(entry.getValue());
            if (parsed > 0) {
                return parsed;
            }
        }

        return 0;
    }

    private static int parsePositiveInt(Object value) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (value instanceof String stringValue) {
            try {
                return Math.max(0, Integer.parseInt(stringValue.trim()));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private record OpenVaultSession(String ownerKey, String ownerName, int pageNumber, int maxPagesVisible) {
        private OpenVaultSession withPage(int nextPage) {
            return new OpenVaultSession(ownerKey, ownerName, nextPage, maxPagesVisible);
        }
    }
}
