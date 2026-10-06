package com.example.immersivemap.client;

import com.example.immersivemap.network.PlayerPositionsS2C;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Positions of players outside entity tracking range, as broadcast by a server running the mod. */
public final class PlayerTracker {
    public static final class Remote {
        public final UUID id;
        double prevX;
        double prevZ;
        double x;
        double z;
        float yaw;
        long receivedAt;
        long interval = 500L;

        Remote(UUID id) {
            this.id = id;
        }

        public double x(long now) {
            return MathHelper.lerp(progress(now), prevX, x);
        }

        public double z(long now) {
            return MathHelper.lerp(progress(now), prevZ, z);
        }

        public float yaw() {
            return yaw;
        }

        private double progress(long now) {
            return MathHelper.clamp((double) (now - receivedAt) / interval, 0.0, 1.0);
        }
    }

    private final Map<UUID, Remote> players = new HashMap<>();

    public void clear() {
        players.clear();
    }

    public Iterable<Remote> all() {
        return players.values();
    }

    public void accept(PlayerPositionsS2C payload) {
        long now = System.currentTimeMillis();
        Set<UUID> seen = new HashSet<>();
        for (PlayerPositionsS2C.Entry entry : payload.players()) {
            seen.add(entry.id());
            Remote remote = players.get(entry.id());
            if (remote == null) {
                remote = new Remote(entry.id());
                remote.prevX = remote.x = entry.x();
                remote.prevZ = remote.z = entry.z();
                players.put(entry.id(), remote);
            } else {
                remote.prevX = remote.x(now);
                remote.prevZ = remote.z(now);
                remote.x = entry.x();
                remote.z = entry.z();
                remote.interval = MathHelper.clamp(now - remote.receivedAt, 50L, 2000L);
            }
            remote.yaw = entry.yaw();
            remote.receivedAt = now;
        }
        players.keySet().retainAll(seen);
    }
}
