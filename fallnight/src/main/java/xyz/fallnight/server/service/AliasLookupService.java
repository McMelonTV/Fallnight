package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.user.UserProfile;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AliasLookupService {
    private final PlayerProfileService profileService;

    public AliasLookupService(PlayerProfileService profileService) {
        this.profileService = profileService;
    }

    public AliasResult findAliases(String username) {
        UserProfile target = profileService.findByUsername(username).orElse(null);
        if (target == null) {
            return new AliasResult(null, List.of());
        }
        Set<String> ips = stringSet(target.getExtraData().get("iplist"));
        Set<String> cids = stringSet(target.getExtraData().get("clientIdList"));
        Set<String> dids = stringSet(target.getExtraData().get("deviceIdList"));
        List<AliasMatch> matches = new ArrayList<>();
        for (UserProfile profile : profileService.allProfiles()) {
            if (profile.getUsername().equalsIgnoreCase(target.getUsername())) {
                continue;
            }
            Set<String> matchReasons = new LinkedHashSet<>();
            intersect(ips, stringSet(profile.getExtraData().get("iplist")), "ip", matchReasons);
            intersect(cids, stringSet(profile.getExtraData().get("clientIdList")), "clientId", matchReasons);
            intersect(dids, stringSet(profile.getExtraData().get("deviceIdList")), "deviceId", matchReasons);
            if (!matchReasons.isEmpty()) {
                matches.add(new AliasMatch(profile.getUsername(), List.copyOf(matchReasons)));
            }
        }
        return new AliasResult(target.getUsername(), matches);
    }

    private static void intersect(Set<String> source, Set<String> target, String label, Set<String> reasons) {
        for (String value : source) {
            if (target.contains(value)) {
                reasons.add(label + ":" + value);
            }
        }
    }

    private static Set<String> stringSet(Object raw) {
        Set<String> values = new LinkedHashSet<>();
        if (raw instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null) {
                    String value = String.valueOf(item).trim().toLowerCase(Locale.ROOT);
                    if (!value.isBlank()) values.add(value);
                }
            }
            return values;
        }
        if (raw instanceof String text) {
            String value = text.trim().toLowerCase(Locale.ROOT);
            if (!value.isBlank()) values.add(value);
        }
        return values;
    }

    public record AliasResult(String target, List<AliasMatch> matches) {
    }

    public record AliasMatch(String username, List<String> reasons) {
    }
}
