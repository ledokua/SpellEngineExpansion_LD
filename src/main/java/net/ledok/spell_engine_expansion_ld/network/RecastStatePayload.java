package net.ledok.spell_engine_expansion_ld.network;

import net.ledok.spell_engine_expansion_ld.SpellEngineExpansionMod;
import net.ledok.spell_engine_expansion_ld.recast.RecastState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Server → client: every open recast window (root, the stage its slot casts now, window ticks relative
/// to the sender's cooldown-manager tick).
public record RecastStatePayload(List<Entry> entries) implements CustomPacketPayload {
    public record Entry(ResourceLocation root, ResourceLocation stage, int windowStart, int windowEnd) {
        public RecastState toState(int tick) {
            return new RecastState(stage, 0, tick + windowStart, tick + windowEnd);
        }
    }

    public static final Type<RecastStatePayload> TYPE = new Type<>(SpellEngineExpansionMod.id("recast_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RecastStatePayload> CODEC =
            StreamCodec.of(RecastStatePayload::write, RecastStatePayload::read);

    public static RecastStatePayload of(int tick, Map<ResourceLocation, RecastState> states) {
        var entries = new ArrayList<Entry>(states.size());
        for (var entry : states.entrySet()) {
            var s = entry.getValue();
            entries.add(new Entry(entry.getKey(), s.stage, s.windowStart - tick, s.windowEnd - tick));
        }
        return new RecastStatePayload(entries);
    }

    private static void write(RegistryFriendlyByteBuf buf, RecastStatePayload payload) {
        buf.writeVarInt(payload.entries.size());
        for (var e : payload.entries) {
            buf.writeResourceLocation(e.root);
            buf.writeResourceLocation(e.stage);
            buf.writeInt(e.windowStart);
            buf.writeInt(e.windowEnd);
        }
    }

    private static RecastStatePayload read(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        var entries = new ArrayList<Entry>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buf.readResourceLocation(), buf.readResourceLocation(), buf.readInt(), buf.readInt()));
        }
        return new RecastStatePayload(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
