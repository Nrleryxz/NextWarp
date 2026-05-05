package me.Nrleryx;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class NextWarp extends JavaPlugin implements CommandExecutor, TabCompleter {
    private final Map<UUID, CountdownTp> pendingTp = new ConcurrentHashMap<>();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
    private volatile Settings settings;
    private volatile Messages messages;
    private final Map<String, String> warpKeyByLower = new ConcurrentHashMap<>();
    private volatile Set<String> warpNames = Collections.emptySet();
    private volatile Set<String> instaTpLower = Collections.emptySet();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadAll();

        getCommand("warp").setExecutor(this);
        getCommand("warp").setTabCompleter(this);
        getCommand("warps").setExecutor(this);
        getCommand("warps").setTabCompleter(this);
        getCommand("setwarp").setExecutor(this);
        getCommand("setwarp").setTabCompleter(this);
        getCommand("delwarp").setExecutor(this);
        getCommand("delwarp").setTabCompleter(this);
        getCommand("nextwarp").setExecutor(this);
        getCommand("nextwarp").setTabCompleter(this);
    }

    private void reloadAll() {
        reloadConfig();
        rebuildWarpIndex();
        rebuildInstaTpIndex();
        settings = Settings.from(getConfig());
        messages = Messages.from(getConfig());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("nextwarp")) {
            if (!sender.hasPermission("nextwarp.admin")) {
                sender.sendMessage(messages.noPermissionLegacy);
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadAll();
                sender.sendMessage(messages.reloadedLegacy);
                if (sender instanceof Player player) {
                    playConfiguredSound(player, "settings.reload-sound", 0.9f, 1.2f);
                }
                return true;
            }
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.playerOnlyLegacy);
            return true;
        }

        if (cmd.equals("warps")) {
            Set<String> names = getWarpNames();
            if (names.isEmpty()) {
                player.sendMessage(messages.warpEmptyLegacy);
                return true;
            }
            player.sendMessage(messages.warpDestinationsPrefixLegacy + String.join(", ", names));
            return true;
        }

        if (cmd.equals("warp")) {
            if (!player.hasPermission("nextwarp.use")) {
                player.sendMessage(messages.noPermissionLegacy);
                return true;
            }
            if (args.length != 1) {
                player.sendMessage(messages.warpUsageLegacy);
                Set<String> names = getWarpNames();
                if (!names.isEmpty()) {
                    player.sendMessage(messages.warpDestinationsPrefixLegacy + String.join(", ", names));
                }
                return true;
            }
            String input = raw(args[0]).trim();
            String key = resolveWarpKey(input);
            Location loc = key != null ? getWarp(key) : null;
            if (loc == null) {
                player.sendMessage(messages.warpNotFoundLegacy.replace("{warp}", input));
                playConfiguredSound(player, "settings.warp-not-found-sound", 0.8f, 1.0f);
                return true;
            }
            startTeleport(player, key, loc);
            return true;
        }

        if (cmd.equals("setwarp")) {
            if (!player.hasPermission("nextwarp.admin")) {
                player.sendMessage(messages.noPermissionLegacy);
                return true;
            }
            if (args.length != 1) {
                return false;
            }
            String name = raw(args[0]).trim();
            if (name.isEmpty()) {
                return false;
            }
            saveWarp(name, player.getLocation());
            player.sendMessage(messages.warpCreatedLegacy.replace("{warp}", name));
            playConfiguredSound(player, "settings.warp-created-sound", 0.35f, 1.2f);
            return true;
        }

        if (cmd.equals("delwarp")) {
            if (!player.hasPermission("nextwarp.admin")) {
                player.sendMessage(messages.noPermissionLegacy);
                return true;
            }
            if (args.length != 1) {
                return false;
            }
            String input = raw(args[0]).trim();
            String key = resolveWarpKey(input);
            if (key == null || !deleteWarp(key)) {
                player.sendMessage(messages.warpNotFoundLegacy.replace("{warp}", input));
                playConfiguredSound(player, "settings.warp-not-found-sound", 0.8f, 1.0f);
                return true;
            }
            player.sendMessage(messages.warpDeletedLegacy.replace("{warp}", key));
            playConfiguredSound(player, "settings.warp-deleted-sound", 0.35f, 1.1f);
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("nextwarp")) {
            if (args.length == 1) {
                return filterPrefix(List.of("reload"), args[0]);
            }
            return Collections.emptyList();
        }

        if (cmd.equals("warp") || cmd.equals("delwarp")) {
            if (args.length == 1) {
                return filterPrefix(new ArrayList<>(getWarpNames()), args[0]);
            }
        }
        return Collections.emptyList();
    }

    private Set<String> getWarpNames() {
        return warpNames;
    }

    private String resolveWarpKey(String input) {
        String trimmed = input == null ? "" : input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return warpKeyByLower.get(trimmed.toLowerCase(Locale.ROOT));
    }

    private void rebuildWarpIndex() {
        ConfigurationSection sec = getConfig().getConfigurationSection("warps");
        if (sec == null) {
            warpNames = Collections.emptySet();
            warpKeyByLower.clear();
            return;
        }
        Set<String> keys = new TreeSet<>(sec.getKeys(false));
        warpNames = Collections.unmodifiableSet(keys);
        warpKeyByLower.clear();
        for (String k : keys) {
            warpKeyByLower.put(k.toLowerCase(Locale.ROOT), k);
        }
    }

    private void rebuildInstaTpIndex() {
        List<String> list = getConfig().getStringList("insta_tp");
        if (list == null || list.isEmpty()) {
            instaTpLower = Collections.emptySet();
            return;
        }
        Set<String> set = new HashSet<>(Math.max(16, list.size() * 2));
        for (String s : list) {
            if (s != null && !s.trim().isEmpty()) {
                set.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        instaTpLower = Collections.unmodifiableSet(set);
    }

    private Location getWarp(String name) {
        String path = "warps." + name + ".";
        String worldName = getConfig().getString(path + "world", null);
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        double x = getConfig().getDouble(path + "x");
        double y = getConfig().getDouble(path + "y");
        double z = getConfig().getDouble(path + "z");
        float yaw = (float) getConfig().getDouble(path + "yaw");
        float pitch = (float) getConfig().getDouble(path + "pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    private void saveWarp(String name, Location loc) {
        String path = "warps." + name + ".";
        getConfig().set(path + "world", loc.getWorld() != null ? loc.getWorld().getName() : "world");
        getConfig().set(path + "x", loc.getX());
        getConfig().set(path + "y", loc.getY());
        getConfig().set(path + "z", loc.getZ());
        getConfig().set(path + "yaw", loc.getYaw());
        getConfig().set(path + "pitch", loc.getPitch());
        saveConfig();
        warpKeyByLower.put(name.toLowerCase(Locale.ROOT), name);
        Set<String> next = new TreeSet<>(warpNames);
        next.add(name);
        warpNames = Collections.unmodifiableSet(next);
    }

    private boolean deleteWarp(String name) {
        String root = "warps." + name;
        if (!getConfig().contains(root)) {
            return false;
        }
        getConfig().set(root, null);
        saveConfig();
        warpKeyByLower.remove(name.toLowerCase(Locale.ROOT));
        Set<String> next = new TreeSet<>(warpNames);
        next.remove(name);
        warpNames = Collections.unmodifiableSet(next);
        return true;
    }

    private void startTeleport(Player player, String warpKey, Location loc) {
        UUID uuid = player.getUniqueId();
        CountdownTp prev = pendingTp.remove(uuid);
        if (prev != null) {
            prev.cancel();
        }

        int delay = settings.teleportDelaySeconds;
        if (isInstantWarp(warpKey)) {
            delay = 0;
        }

        if (delay <= 0) {
            doTeleport(player, warpKey, loc);
            return;
        }

        CountdownTp tp = new CountdownTp(this, player, warpKey, loc, delay);
        pendingTp.put(uuid, tp);
        tp.start();
    }

    private boolean isInstantWarp(String warpKey) {
        return instaTpLower.contains(warpKey.toLowerCase(Locale.ROOT));
    }

    private void doTeleport(Player player, String warpKey, Location loc) {
        Bukkit.getRegionScheduler().run(this, loc, task -> player.teleportAsync(loc).thenAccept(ok -> {
            if (ok) {
                sendActionBar(player, messages.teleportSuccess.applyWarp(warpKey));
                emitTeleportSuccessEffects(player);
                playTeleportSound(player);
            } else {
                sendActionBar(player, messages.teleportFailed);
                playConfiguredSound(player, "settings.teleport-failed-sound", 0.8f, 0.8f);
            }
            pendingTp.remove(player.getUniqueId());
        }));
    }

    private void sendActionBar(Player player, Component message) {
        player.sendActionBar(message);
    }

    private void playTeleportSound(Player player) {
        Settings s = settings;
        if (s.teleportSound != null) {
            player.playSound(player.getLocation(), s.teleportSound, s.teleportSoundVolume, s.teleportSoundPitch);
        }
    }

    private void playConfiguredSound(Player player, String path, float volume, float pitch) {
        String soundName = getConfig().getString(path, "");
        if (soundName == null || soundName.isBlank()) {
            return;
        }
        Sound sound;
        try {
            sound = Sound.valueOf(soundName);
        } catch (Exception ignored) {
            return;
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private void emitTeleportSuccessEffects(Player player) {
        Settings s = settings;
        if (s.teleportSuccessParticle == null || s.teleportSuccessParticleCount <= 0) {
            return;
        }
        Location loc = player.getLocation(s.tmpLoc());
        loc.add(0, 1.0, 0);
        player.spawnParticle(s.teleportSuccessParticle, loc, s.teleportSuccessParticleCount, 0.45, 0.8, 0.45, 0.01);
    }

    private static String raw(String s) {
        return s == null ? "" : s;
    }

    private static boolean isHex6(String s, int start) {
        for (int j = 0; j < 6; j++) {
            char c = s.charAt(start + j);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static String applyColors(String s) {
        String withAmp = ChatColor.translateAlternateColorCodes('&', raw(s));
        return translateHexColors(withAmp);
    }

    private static String translateHexColors(String s) {
        String in = raw(s);
        StringBuilder out = new StringBuilder(in.length() + 16);
        int i = 0;
        while (i < in.length()) {
            char c = in.charAt(i);
            if (c == '#' && i + 6 < in.length() && isHex6(in, i + 1)) {
                out.append('§').append('x');
                for (int j = 0; j < 6; j++) {
                    out.append('§').append(Character.toLowerCase(in.charAt(i + 1 + j)));
                }
                i += 7;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static String normalize(String s) {
        return raw(s).trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> filterPrefix(List<String> items, String prefix) {
        String p = normalize(prefix);
        if (p.isEmpty()) {
            return items.stream().sorted().collect(Collectors.toList());
        }
        return items.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(p)).sorted().collect(Collectors.toList());
    }

    private static final class CountdownTp {
        private final NextWarp plugin;
        private final UUID playerId;
        private final String warpKey;
        private final Location loc;
        private final Location startLoc;
        private final Location tmpLoc;
        private volatile int seconds;
        private volatile io.papermc.paper.threadedregions.scheduler.ScheduledTask task;

        private CountdownTp(NextWarp plugin, Player player, String warpKey, Location loc, int seconds) {
            this.plugin = plugin;
            this.playerId = player.getUniqueId();
            this.warpKey = warpKey;
            this.loc = loc;
            this.startLoc = player.getLocation().clone();
            this.tmpLoc = new Location(player.getWorld(), 0, 0, 0);
            this.seconds = seconds;
        }

        private void start() {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                plugin.pendingTp.remove(playerId);
                return;
            }
            plugin.sendActionBar(player, plugin.messages.teleportCount.forSeconds(seconds));
            plugin.emitCountdownEffects(player);
            task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> tick(), 20L, 20L);
        }

        private void tick() {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                cancel();
                plugin.pendingTp.remove(playerId);
                return;
            }
            if (plugin.shouldCancelOnMove() && moved(player)) {
                cancel();
                plugin.pendingTp.remove(playerId);
                plugin.sendActionBar(player, plugin.messages.teleportCancelled);
                plugin.playConfiguredSound(player, "settings.teleport-cancelled-sound", 0.9f, 1.0f);
                return;
            }
            seconds--;
            if (seconds > 0) {
                plugin.sendActionBar(player, plugin.messages.teleportCount.forSeconds(seconds));
                plugin.emitCountdownEffects(player);
                return;
            }
            cancel();
            plugin.doTeleport(player, warpKey, loc);
        }

        private boolean moved(Player player) {
            Location now = player.getLocation(tmpLoc);
            if (now.getWorld() != startLoc.getWorld()) {
                return true;
            }
            Settings s = plugin.settings;
            double dx = startLoc.getX() - now.getX();
            double dy = startLoc.getY() - now.getY();
            double dz = startLoc.getZ() - now.getZ();
            return (dx * dx + dy * dy + dz * dz) > s.cancelMoveDistanceSquared;
        }

        private void cancel() {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask t = task;
            if (t != null) {
                t.cancel();
            }
        }
    }

    private boolean shouldCancelOnMove() {
        return settings.cancelOnMove;
    }

    private void emitCountdownEffects(Player player) {
        Settings s = settings;
        if (s.countdownSound != null) {
            player.playSound(player.getLocation(), s.countdownSound, s.countdownSoundVolume, s.countdownSoundPitch);
        }
        if (s.countdownParticle != null && s.countdownParticleCount > 0) {
            Location loc = player.getLocation(s.tmpLoc());
            loc.add(0, 1.0, 0);
            player.spawnParticle(s.countdownParticle, loc, s.countdownParticleCount, 0.35, 0.6, 0.35, 0.01);
        }
    }

    private static final class Settings {
        private final int teleportDelaySeconds;
        private final boolean cancelOnMove;
        private final double cancelMoveDistanceSquared;
        private final Sound countdownSound;
        private final float countdownSoundVolume;
        private final float countdownSoundPitch;
        private final Particle countdownParticle;
        private final int countdownParticleCount;
        private final Sound teleportSound;
        private final float teleportSoundVolume;
        private final float teleportSoundPitch;
        private final Particle teleportSuccessParticle;
        private final int teleportSuccessParticleCount;
        private final ThreadLocal<Location> tmpLocation;

        private Settings(int teleportDelaySeconds,
                         boolean cancelOnMove,
                         double cancelMoveDistanceSquared,
                         Sound countdownSound,
                         float countdownSoundVolume,
                         float countdownSoundPitch,
                         Particle countdownParticle,
                         int countdownParticleCount,
                         Sound teleportSound,
                         float teleportSoundVolume,
                         float teleportSoundPitch,
                         Particle teleportSuccessParticle,
                         int teleportSuccessParticleCount) {
            this.teleportDelaySeconds = teleportDelaySeconds;
            this.cancelOnMove = cancelOnMove;
            this.cancelMoveDistanceSquared = cancelMoveDistanceSquared;
            this.countdownSound = countdownSound;
            this.countdownSoundVolume = countdownSoundVolume;
            this.countdownSoundPitch = countdownSoundPitch;
            this.countdownParticle = countdownParticle;
            this.countdownParticleCount = countdownParticleCount;
            this.teleportSound = teleportSound;
            this.teleportSoundVolume = teleportSoundVolume;
            this.teleportSoundPitch = teleportSoundPitch;
            this.teleportSuccessParticle = teleportSuccessParticle;
            this.teleportSuccessParticleCount = teleportSuccessParticleCount;
            this.tmpLocation = ThreadLocal.withInitial(() -> new Location(null, 0, 0, 0));
        }

        private Location tmpLoc() {
            return tmpLocation.get();
        }

        private static Settings from(org.bukkit.configuration.file.FileConfiguration config) {
            int delay = Math.max(0, config.getInt("settings.teleport-delay-seconds", 0));
            boolean cancelOnMove = config.getBoolean("settings.cancel-on-move", true);
            double dist = Math.max(0.0, config.getDouble("settings.cancel-move-distance", 0.1));
            double distSq = dist * dist;

            Sound countdownSound = parseSound(config.getString("settings.countdown-sound", ""));
            float countdownVol = (float) Math.max(0.0, config.getDouble("settings.countdown-sound-volume", 0.8));
            float countdownPitch = (float) config.getDouble("settings.countdown-sound-pitch", 1.2);

            Particle countdownParticle = parseParticle(config.getString("settings.countdown-particle", ""));
            int countdownParticleCount = Math.max(0, config.getInt("settings.countdown-particle-count", 12));

            Sound teleportSound = parseSound(config.getString("settings.teleport-sound", ""));
            float teleportVol = (float) Math.max(0.0, config.getDouble("settings.teleport-sound-volume", 0.6));
            float teleportPitch = (float) config.getDouble("settings.teleport-sound-pitch", 1.0);

            Particle teleportSuccessParticle = parseParticle(config.getString("settings.teleport-success-particle", ""));
            int teleportSuccessParticleCount = Math.max(0, config.getInt("settings.teleport-success-particle-count", 30));

            return new Settings(delay, cancelOnMove, distSq,
                countdownSound, countdownVol, countdownPitch,
                countdownParticle, countdownParticleCount,
                teleportSound, teleportVol, teleportPitch,
                teleportSuccessParticle, teleportSuccessParticleCount);
        }

        private static Sound parseSound(String name) {
            if (name == null || name.isBlank()) {
                return null;
            }
            try {
                return Sound.valueOf(name);
            } catch (Exception ignored) {
                return null;
            }
        }

        private static Particle parseParticle(String name) {
            if (name == null || name.isBlank()) {
                return null;
            }
            try {
                return Particle.valueOf(name);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static final class Messages {
        private final String noPermissionLegacy;
        private final String playerOnlyLegacy;
        private final String reloadedLegacy;
        private final String warpUsageLegacy;
        private final String warpDestinationsPrefixLegacy;
        private final String warpCreatedLegacy;
        private final String warpDeletedLegacy;
        private final String warpNotFoundLegacy;
        private final String warpEmptyLegacy;
        private final CountdownTemplate teleportCount;
        private final Component teleportFailed;
        private final Component teleportCancelled;
        private final WarpTemplate teleportSuccess;

        private Messages(String noPermissionLegacy,
                         String playerOnlyLegacy,
                         String reloadedLegacy,
                         String warpUsageLegacy,
                         String warpDestinationsPrefixLegacy,
                         String warpCreatedLegacy,
                         String warpDeletedLegacy,
                         String warpNotFoundLegacy,
                         String warpEmptyLegacy,
                         CountdownTemplate teleportCount,
                         Component teleportFailed,
                         Component teleportCancelled,
                         WarpTemplate teleportSuccess) {
            this.noPermissionLegacy = noPermissionLegacy;
            this.playerOnlyLegacy = playerOnlyLegacy;
            this.reloadedLegacy = reloadedLegacy;
            this.warpUsageLegacy = warpUsageLegacy;
            this.warpDestinationsPrefixLegacy = warpDestinationsPrefixLegacy;
            this.warpCreatedLegacy = warpCreatedLegacy;
            this.warpDeletedLegacy = warpDeletedLegacy;
            this.warpNotFoundLegacy = warpNotFoundLegacy;
            this.warpEmptyLegacy = warpEmptyLegacy;
            this.teleportCount = teleportCount;
            this.teleportFailed = teleportFailed;
            this.teleportCancelled = teleportCancelled;
            this.teleportSuccess = teleportSuccess;
        }

        private static Messages from(org.bukkit.configuration.file.FileConfiguration config) {
            String noPerm = applyColors(config.getString("messages.no-permission", ""));
            String playerOnly = applyColors(config.getString("messages.player-only", ""));
            String reloaded = applyColors(config.getString("messages.reloaded", ""));

            String warpUsage = applyColors(config.getString("messages.warp.usage", ""));
            String warpDestPrefix = applyColors(config.getString("messages.warp.destinations-prefix", ""));
            String warpCreated = applyColors(config.getString("messages.warp.created", ""));
            String warpDeleted = applyColors(config.getString("messages.warp.deleted", ""));
            String warpNotFound = applyColors(config.getString("messages.warp.not-found", ""));
            String warpEmpty = applyColors(config.getString("messages.warp.empty", ""));

            String countTpl = applyColors(config.getString("messages.teleport.count", ""));
            CountdownTemplate teleportCount = CountdownTemplate.compile(countTpl, "{countdown}", Math.max(1, config.getInt("settings.teleport-delay-seconds", 5)));

            Component teleportFailed = compileComponent(applyColors(config.getString("messages.teleport.failed", "")));
            Component teleportCancelled = compileComponent(applyColors(config.getString("messages.teleport.cancelled", "")));

            String successTpl = applyColors(config.getString("messages.teleport.success", ""));
            WarpTemplate teleportSuccess = WarpTemplate.compile(successTpl, "{warp}");

            return new Messages(noPerm, playerOnly, reloaded, warpUsage, warpDestPrefix, warpCreated, warpDeleted, warpNotFound, warpEmpty,
                teleportCount, teleportFailed, teleportCancelled, teleportSuccess);
        }
    }

    private static final class WarpTemplate {
        private final String prefix;
        private final String suffix;

        private WarpTemplate(String prefix, String suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }

        private Component applyWarp(String warp) {
            return compileComponent(prefix + warp + suffix);
        }

        private static WarpTemplate compile(String template, String token) {
            String t = raw(template);
            int idx = t.indexOf(token);
            if (idx < 0) {
                return new WarpTemplate(t, "");
            }
            String pre = t.substring(0, idx);
            String suf = t.substring(idx + token.length());
            return new WarpTemplate(pre, suf);
        }
    }

    private static final class CountdownTemplate {
        private final Component[] bySecond;

        private CountdownTemplate(Component[] bySecond) {
            this.bySecond = bySecond;
        }

        private Component forSeconds(int seconds) {
            if (seconds <= 0) {
                return bySecond[0];
            }
            int idx = Math.min(seconds, bySecond.length - 1);
            return bySecond[idx];
        }

        private static CountdownTemplate compile(String template, String token, int maxSeconds) {
            String t = raw(template);
            int idx = t.indexOf(token);
            String pre;
            String suf;
            if (idx < 0) {
                pre = t;
                suf = "";
            } else {
                pre = t.substring(0, idx);
                suf = t.substring(idx + token.length());
            }
            int size = Math.max(1, maxSeconds);
            Component[] arr = new Component[size + 1];
            arr[0] = compileComponent(pre + "0" + suf);
            for (int i = 1; i <= size; i++) {
                arr[i] = compileComponent(pre + i + suf);
            }
            return new CountdownTemplate(arr);
        }
    }

    private static Component compileComponent(String legacy) {
        return LEGACY_SECTION.deserialize(raw(legacy));
    }
}
