package gg.pigraid.CpsCounter;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.event.server.DataPacketReceiveEvent;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.InventoryTransactionPacket;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.TextFormat;

import cn.nukkit.utils.Config;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * CpsCounter - Displays CPS (clicks per second) and combo counter to players
 * Ported to NukkitPetteriM1Edition
 */
public class CpsCounter extends PluginBase implements Listener {
    private static final int MAX_CLICKS = 100;
    private static final double RESET_THRESHOLD = 2.0; // Reset combo after 2 seconds

    private final Map<UUID, Deque<Long>> clicksData = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> combosData = new ConcurrentHashMap<>();
    private final Map<Character, String> unicodeNumbers = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadUnicodeNumbers();
        getServer().getPluginManager().registerEvents(this, this);

        // Schedule cleanup task every 10 ticks (0.5 seconds)
        getServer().getScheduler().scheduleDelayedRepeatingTask(this, () -> {
            long now = System.currentTimeMillis();
            for (UUID uuid : new HashSet<>(clicksData.keySet())) {
                Deque<Long> clicks = clicksData.get(uuid);
                if (clicks == null || clicks.isEmpty()) {
                    continue;
                }

                // Remove clicks older than 1 second
                clicks.removeIf(click -> click < now - 1000);

                // Reset combo if no clicks for 2 seconds
                Long lastClick = clicks.peekLast();
                if (lastClick != null) {
                    double timeSinceLastClick = (now - lastClick) / 1000.0;
                    if (timeSinceLastClick > RESET_THRESHOLD) {
                        clicks.clear();
                        combosData.put(uuid, 0);
                    }
                }
            }
        }, 10, 10);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        clicksData.putIfAbsent(uuid, new ConcurrentLinkedDeque<>());
        combosData.putIfAbsent(uuid, 0);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        clicksData.remove(uuid);
        combosData.remove(uuid);
    }

    @EventHandler
    public void onPacket(DataPacketReceiveEvent event) {
        DataPacket dataPacket = event.getPacket();
        if (dataPacket instanceof InventoryTransactionPacket) {
            InventoryTransactionPacket packet = (InventoryTransactionPacket) dataPacket;
            Player player = event.getPlayer();

            // Transaction type 3 is USE_ITEM_ON_ENTITY (attacking)
            if (packet.transactionType != InventoryTransactionPacket.TYPE_USE_ITEM_ON_ENTITY) {
                return;
            }

            addClick(player);
            sendPopup(player);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getDamager();
        if (!(entity instanceof Player)) {
            return;
        }
        Player attacker = (Player) entity;

        entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player victim = (Player) entity;

        // Increment attacker's combo
        incrementCombo(attacker);

        // Reset victim's combo
        combosData.remove(victim.getUniqueId());
    }

    private void addClick(Player player) {
        UUID uuid = player.getUniqueId();
        Deque<Long> queue = clicksData.computeIfAbsent(uuid, k -> new ConcurrentLinkedDeque<>());
        queue.add(System.currentTimeMillis());

        // Keep only recent clicks
        while (queue.size() > MAX_CLICKS) {
            queue.poll();
        }
    }

    private void incrementCombo(Player player) {
        UUID uuid = player.getUniqueId();
        combosData.put(uuid, combosData.getOrDefault(uuid, 0) + 1);
    }

    private int getCombo(Player player) {
        return combosData.getOrDefault(player.getUniqueId(), 0);
    }

    private int getCps(Player player) {
        UUID uuid = player.getUniqueId();
        Deque<Long> queue = clicksData.get(uuid);
        if (queue == null || queue.isEmpty()) {
            return 0;
        }

        long now = System.currentTimeMillis();
        long oneSecondAgo = now - 1000;

        return (int) queue.stream().filter(t -> t >= oneSecondAgo).count();
    }

    private void loadUnicodeNumbers() {
        Config config = getConfig();
        Map<String, Object> mapping = config.getSection("unicode-numbers").getAllMap();
        for (Map.Entry<String, Object> entry : mapping.entrySet()) {
            String key = entry.getKey();
            if (key.length() == 1) {
                unicodeNumbers.put(key.charAt(0), String.valueOf(entry.getValue()));
            }
        }
    }

    private String toUnicode(int number) {
        StringBuilder sb = new StringBuilder();
        for (char c : String.valueOf(number).toCharArray()) {
            sb.append(unicodeNumbers.getOrDefault(c, String.valueOf(c)));
        }
        return sb.toString();
    }

    private void sendPopup(Player player) {
        int cps = getCps(player);
        int combo = getCombo(player);
        player.sendTip(TextFormat.GRAY + "§l§p   ᰪᰫᰬ§7ᰭ§p" + toUnicode(cps) + " §7ᰮ §pᰰᰱᰲᰳᰱ§7ᰭ" + toUnicode(combo));
    }
}
