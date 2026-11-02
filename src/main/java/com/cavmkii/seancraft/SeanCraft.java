package com.cavmkii.seancraft;

import com.cavmkii.seancraft.entity.ModEntities;
import com.cavmkii.seancraft.item.ModItems;
import com.cavmkii.seancraft.rpg.Rpg;
import com.cavmkii.seancraft.tarkov.Tarkov;
import com.cavmkii.seancraft.world.WorldDirector;
import java.util.Arrays;
import java.util.Locale;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SeanCraft implements ModInitializer {
    public static final String MOD_ID = "seancraft";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        LOG.info(Cfg.load());
        LOG.info(JoeLines.load());

        ModItems.init();
        ModEntities.init();
        Rpg.init();
        com.cavmkii.seancraft.pattern.Patterns.init();
        com.cavmkii.seancraft.parts.PartItems.init();
        com.cavmkii.seancraft.tetris.Tetris.init();
        Tarkov.init();
        com.cavmkii.seancraft.carry.Carry.init();
        com.cavmkii.seancraft.combat.Combat.init();
        com.cavmkii.seancraft.combat.Damage.init();
        com.cavmkii.seancraft.food.Food.init();
        com.cavmkii.seancraft.food.Rest.init();
        com.cavmkii.seancraft.boss.BossContent.init();
        com.cavmkii.seancraft.hazard.Hazards.init();
        com.cavmkii.seancraft.compat.GrappleCompat.init();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SnowQuip.reset();
            WorldDirector.start(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> WorldDirector.stop());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            WorldDirector director = WorldDirector.get();
            if (director != null) {
                director.tick();
            }
            SnowQuip.tick(server);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            GoblinCommands.register(dispatcher);
            Rpg.registerCommands(dispatcher);
        });

        // Saying the phrase: normal chat, and /say or /me from a player.
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> onSaid(sender, message.signedContent()));
        ServerMessageEvents.COMMAND_MESSAGE.register((message, source, params) -> {
            if (source.getPlayer() != null) {
                onSaid(source.getPlayer(), message.signedContent());
            }
        });

        // Offerings at Joe's War Cauldron.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            WorldDirector director = WorldDirector.get();
            if (level.isClientSide || hand != InteractionHand.MAIN_HAND || director == null || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (!director.war.isActive() || level != director.overworld()) {
                return InteractionResult.PASS;
            }
            return director.war.onCauldronUse(serverPlayer, hit.getBlockPos(), player.getItemInHand(hand))
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });

        // The war camp can't be taken apart while the war is on.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            WorldDirector director = WorldDirector.get();
            return director == null || level != director.overworld() || !director.war.isCampBlock(pos);
        });
    }

    private static void onSaid(ServerPlayer speaker, String text) {
        WorldDirector director = WorldDirector.get();
        if (director == null || text == null || text.isEmpty()) {
            return;
        }
        String said = normalize(text);
        boolean triggered = Arrays.stream(Cfg.ragePhrases.split(","))
                .map(SeanCraft::normalize)
                .anyMatch(phrase -> !phrase.isEmpty() && said.contains(phrase));
        if (triggered) {
            // Chat events fire on the server thread already, but be safe.
            director.server.execute(() -> director.noteRage(speaker));
        }
    }

    /** Lowercase letters and digits only, so "Baby Wars!", "baby-wars" and "babywars" all match. */
    static String normalize(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
