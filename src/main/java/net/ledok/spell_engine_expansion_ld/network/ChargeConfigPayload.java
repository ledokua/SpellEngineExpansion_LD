package net.ledok.spell_engine_expansion_ld.network;

import net.ledok.spell_engine_expansion_ld.SpellEngineExpansionMod;
import net.ledok.spell_engine_expansion_ld.charges.ChargeConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/// Server → client: the whole charge table (on join and after `/reload`).
public record ChargeConfigPayload(Map<ResourceLocation, ChargeConfig> table) implements CustomPacketPayload {
    public static final Type<ChargeConfigPayload> TYPE = new Type<>(SpellEngineExpansionMod.id("charge_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChargeConfigPayload> CODEC =
            StreamCodec.of(ChargeConfigPayload::write, ChargeConfigPayload::read);

    public static ChargeConfigPayload of(Map<ResourceLocation, ChargeConfig> table) {
        return new ChargeConfigPayload(Map.copyOf(table));
    }

    private static void write(RegistryFriendlyByteBuf buf, ChargeConfigPayload payload) {
        buf.writeVarInt(payload.table.size());
        for (var entry : payload.table.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            buf.writeVarInt(entry.getValue().charges());
            buf.writeFloat(entry.getValue().recharge());
            buf.writeFloat(entry.getValue().recastLock());
        }
    }

    private static ChargeConfigPayload read(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        var table = new HashMap<ResourceLocation, ChargeConfig>(size);
        for (int i = 0; i < size; i++) {
            var id = buf.readResourceLocation();
            table.put(id, new ChargeConfig(buf.readVarInt(), buf.readFloat(), buf.readFloat()));
        }
        return new ChargeConfigPayload(table);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
