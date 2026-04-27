package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.user.UserProfile;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminModeService {
    private static final Set<String> ENABLED_USERS = ConcurrentHashMap.newKeySet();

    private AdminModeService() {
    }

    public static boolean isEnabled(UserProfile profile) {
        if (profile == null || profile.getUsername() == null) {
            return false;
        }
        return ENABLED_USERS.contains(profile.getUsername().toLowerCase());
    }

    public static boolean toggle(UserProfile profile) {
        if (profile == null || profile.getUsername() == null || profile.getUsername().isBlank()) {
            return false;
        }
        String key = profile.getUsername().toLowerCase();
        if (ENABLED_USERS.remove(key)) {
            return false;
        }
        ENABLED_USERS.add(key);
        return true;
    }

    public static void clear(UserProfile profile) {
        if (profile != null && profile.getUsername() != null) {
            ENABLED_USERS.remove(profile.getUsername().toLowerCase());
        }
    }
}
