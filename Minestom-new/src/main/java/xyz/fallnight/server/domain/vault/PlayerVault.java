package xyz.fallnight.server.domain.vault;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class PlayerVault {
    private String owner;
    private int maxPages;
    @JsonIgnore
    private final List<VaultPage> pages;

    public PlayerVault() {
        this.maxPages = 1;
        this.pages = new ArrayList<>();
    }

    public PlayerVault(String owner) {
        this();
        this.owner = owner;
    }

    public String getOwner() {
        return owner;
    }

    @JsonAlias({"username", "player", "name"})
    public void setOwner(String owner) {
        this.owner = owner;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
        trimToMaxPages();
    }

    public VaultPage page(int pageNumber) {
        int normalizedPage = Math.max(1, pageNumber);
        ensurePageCapacity(normalizedPage);
        return pages.get(normalizedPage - 1);
    }

    public void setPage(int pageNumber, VaultPage page) {
        if (page == null) {
            return;
        }
        int normalizedPage = Math.max(1, pageNumber);
        ensurePageCapacity(normalizedPage);
        pages.set(normalizedPage - 1, page);
    }

    public int loadedPageCount() {
        return pages.size();
    }

    private void ensurePageCapacity(int pageNumber) {
        while (pages.size() < pageNumber) {
            pages.add(new VaultPage());
        }
    }

    private void trimToMaxPages() {
        int effectiveMax = Math.max(0, maxPages);
        while (pages.size() > effectiveMax) {
            pages.removeLast();
        }
    }
}
