package com.cavmkii.seancraft;

import com.cavmkii.seancraft.entity.Wander;
import com.cavmkii.seancraft.tarkov.BodyPart;
import com.cavmkii.seancraft.tarkov.Health;
import com.cavmkii.seancraft.world.WorldDirector;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * /seancraft where | summon joe|sean | babywars start|stop | pedestal | dungeons [build <boss>] | reload | injure <part> <kind> [percent]
 */
public final class GoblinCommands {
    private GoblinCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("seancraft")
                .then(Commands.literal("where").executes(ctx -> reply(ctx, director(ctx).describe())))
                .then(Commands.literal("summon").requires(src -> src.hasPermission(2))
                        .then(Commands.literal("joe").executes(ctx -> summon(ctx, WorldDirector.Kind.JOE)))
                        .then(Commands.literal("sean").executes(ctx -> summon(ctx, WorldDirector.Kind.SEAN))))
                .then(Commands.literal("babywars").requires(src -> src.hasPermission(2))
                        .then(Commands.literal("start").executes(GoblinCommands::startWar))
                        .then(Commands.literal("stop").executes(ctx -> reply(ctx,
                                director(ctx).war.end("An admin called off the Baby Wars.") ? "Calling off the Baby Wars." : "No war is running."))))
                .then(Commands.literal("pedestal").requires(src -> src.hasPermission(2)).executes(GoblinCommands::pedestal))
                .then(Commands.literal("dungeons").requires(src -> src.hasPermission(2))
                        .executes(ctx -> reply(ctx, String.join("\n", com.cavmkii.seancraft.boss.BossSites.describe(ctx.getSource().getServer().overworld()))))
                        .then(Commands.literal("build").then(Commands.argument("boss", StringArgumentType.word())
                                .executes(ctx -> reply(ctx, com.cavmkii.seancraft.boss.BossSites.build(ctx.getSource().getServer().overworld(),
                                        StringArgumentType.getString(ctx, "boss")) ? "Built." : "No such dungeon.")))))
                .then(Commands.literal("reload").requires(src -> src.hasPermission(2))
                        .executes(ctx -> reply(ctx, Cfg.load() + "\n" + JoeLines.load())))
                .then(Commands.literal("injure").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("part", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        java.util.Arrays.stream(BodyPart.values()).map(p -> p.name().toLowerCase(java.util.Locale.ROOT)), b))
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[] {"bleed", "fracture", "destroy", "scar", "heal"}, b))
                                        .executes(ctx -> injure(ctx, 50))
                                        .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                                .executes(ctx -> injure(ctx, IntegerArgumentType.getInteger(ctx, "percent"))))))));
    }

    /** Testing and admin: give yourself a bleed, fracture, lasting damage, a destroyed part, or heal a part. */
    private static int injure(CommandContext<CommandSourceStack> ctx, int percent) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        BodyPart part;
        try {
            part = BodyPart.valueOf(StringArgumentType.getString(ctx, "part").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("No such body part."));
            return 0;
        }
        String result = Health.injure(player, part, StringArgumentType.getString(ctx, "kind"), percent / 100f);
        if (result == null) {
            ctx.getSource().sendFailure(Component.literal("Kind is bleed, fracture, destroy, scar or heal."));
            return 0;
        }
        return reply(ctx, result);
    }

    private static WorldDirector director(CommandContext<CommandSourceStack> ctx) {
        return WorldDirector.get();
    }

    /** Until boss dungeons exist: put a Summoning Pedestal under your feet, as the middle of a boss ring. */
    private static int pedestal(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
        net.minecraft.core.BlockPos pos = player.blockPosition().below();
        player.serverLevel().setBlockAndUpdate(pos, com.cavmkii.seancraft.boss.BossContent.PEDESTAL.defaultBlockState());
        player.teleportTo(player.getX(), pos.getY() + 1, player.getZ());
        return reply(ctx, "Summoning Pedestal placed. Use a boss totem on it; the fight ring is 40 blocks across.");
    }

    private static int reply(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /** Replace Joe or Sean with a fresh one a few blocks in front of you. */
    private static int summon(CommandContext<CommandSourceStack> ctx, WorldDirector.Kind kind) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null || player.level() != director(ctx).overworld()) {
            ctx.getSource().sendFailure(Component.literal("Run this as a player in the overworld."));
            return 0;
        }
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize().scale(kind == WorldDirector.Kind.JOE ? 4 : 7);
        // Stand it on the ground, not at the player's height: on a slope that's inside a block.
        BlockPos spot = Wander.surface(player.serverLevel(), player.position().add(look));
        director(ctx).replace(kind, spot != null ? spot : player.blockPosition());
        return reply(ctx, "Summoning " + (kind == WorldDirector.Kind.JOE ? "Joe" : "Sean") + ".");
    }

    private static int startWar(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        WorldDirector director = director(ctx);
        if (player == null || player.level() != director.overworld()) {
            ctx.getSource().sendFailure(Component.literal("Run this as a player in the overworld."));
            return 0;
        }
        if (director.war.isActive()) {
            return reply(ctx, "The Baby Wars are already on.");
        }
        director.war.forceStart(player);
        return reply(ctx, "The Baby Wars begin...");
    }
}
