package net.ledok.spell_engine_expansion_ld.network;

import net.ledok.spell_engine_expansion_ld.SpellEngineExpansionMod;
import net.ledok.spell_engine_expansion_ld.charges.ChargeState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Server → client: every charged spell with something to show (spent charges, a running recharge or
/// an active lock). Tick values are relative to the sender's cooldown-manager tick, like Spell Engine's
/// own cooldown sync, because the two managers count their own ticks.
public record ChargeStatePayload(List<Entry> entries) implements CustomPacketPayload {
    public record Entry(ResourceLocation spell, int missing, boolean recharging,
                        int rechargeStart, int rechargeEnd, int lockStart, int lockEnd) {
        public ChargeState toState(int tick) {
            var state = new ChargeState();
            state.missing = missing;
            state.recharging = recharging;
            state.rechargeStart = tick + rechargeStart;
            state.rechargeEnd = tick + rechargeEnd;
            state.lockStart = tick + lockStart;
            state.lockEnd = tick + lockEnd;
            return state;
        }
    }

    public static final Type<ChargeStatePayload> TYPE = new Type<>(SpellEngineExpansionMod.id("charge_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChargeStatePayload> CODEC =
            StreamCodec.of(ChargeStatePayload::write, ChargeStatePayload::read);

    public static ChargeStatePayload of(int tick, Map<ResourceLocation, ChargeState> states) {
        var entries = new ArrayList<Entry>(states.size());
        for (var entry : states.entrySet()) {
            var s = entry.getValue();
            entries.add(new Entry(entry.getKey(), s.missing, s.recharging,
                    s.rechargeStart - tick, s.rechargeEnd - tick, s.lockStart - tick, s.lockEnd - tick));
        }
        return new ChargeStatePayload(entries);
    }

    private static void write(RegistryFriendlyByteBuf buf, ChargeStatePayload payload) {
        buf.writeVarInt(payload.entries.size());
        for (var e : payload.entries) {
            buf.writeResourceLocation(e.spell);
            buf.writeVarInt(e.missing);
            buf.writeBoolean(e.recharging);
            buf.writeInt(e.rechargeStart);
            buf.writeInt(e.rechargeEnd);
            buf.writeInt(e.lockStart);
            buf.writeInt(e.lockEnd);
        }
    }

    private static ChargeStatePayload read(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        var entries = new ArrayList<Entry>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buf.readResourceLocation(), buf.readVarInt(), buf.readBoolean(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));
        }
        return new ChargeStatePayload(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
