package com.cavmkii.seancraft.boss;

import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.rpg.PlayerData;
import com.cavmkii.seancraft.rpg.Rpg;
import com.cavmkii.seancraft.rpg.Skill;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Boss content: the bosses, the pedestal and totems, trophies and the rack, and boss drops (design doc 8). */
public final class BossContent {
    public static final EntityType<Mark> MARK = Registry.register(BuiltInRegistries.ENTITY_TYPE, SeanCraft.id("mark"),
            EntityType.Builder.<Mark>of(Mark::new, MobCategory.MONSTER).sized(0.6f, 1.95f).clientTrackingRange(10).fireImmune().build("mark"));
    public static final EntityType<BoDaddy> BO_DADDY = Registry.register(BuiltInRegistries.ENTITY_TYPE, SeanCraft.id("bo_daddy"),
            EntityType.Builder.<BoDaddy>of(BoDaddy::new, MobCategory.MONSTER).sized(0.6f, 1.95f).clientTrackingRange(10).fireImmune().build("bo_daddy"));
    public static final EntityType<Daylan> DAYLAN = Registry.register(BuiltInRegistries.ENTITY_TYPE, SeanCraft.id("daylan"),
            EntityType.Builder.<Daylan>of(Daylan::new, MobCategory.MONSTER).sized(0.6f, 1.95f).clientTrackingRange(10).build("daylan"));
    public static final EntityType<Follower> FOLLOWER = Registry.register(BuiltInRegistries.ENTITY_TYPE, SeanCraft.id("follower"),
            EntityType.Builder.<Follower>of(Follower::new, MobCategory.MISC).sized(0.5f, 0.5f).clientTrackingRange(8).fireImmune().build("follower"));
    public static final EntityType<BossShot> BOSS_SHOT = Registry.register(BuiltInRegistries.ENTITY_TYPE, SeanCraft.id("boss_shot"),
            EntityType.Builder.<BossShot>of(BossShot::new, MobCategory.MISC).sized(0.4f, 0.4f).clientTrackingRange(8).updateInterval(2).build("boss_shot"));

    /** Built into each boss dungeon; unbreakable. A matching totem used on it starts the fight (8.4.1). */
    public static final Block PEDESTAL = Registry.register(BuiltInRegistries.BLOCK, SeanCraft.id("summoning_pedestal"),
            new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1, 3600000).sound(SoundType.LODESTONE).noLootTable()));
    public static final Item PEDESTAL_ITEM = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("summoning_pedestal"),
            new BlockItem(PEDESTAL, new Item.Properties().rarity(Rarity.EPIC)));

    public static final Block TROPHY_RACK = Registry.register(BuiltInRegistries.BLOCK, SeanCraft.id("trophy_rack"),
            new Trophies.RackBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD)));
    public static final Item TROPHY_RACK_ITEM = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("trophy_rack"),
            new BlockItem(TROPHY_RACK, new Item.Properties()));
    public static final BlockEntityType<Trophies.RackEntity> RACK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            SeanCraft.id("trophy_rack"), BlockEntityType.Builder.of(Trophies.RackEntity::new, TROPHY_RACK).build(null));

    public static final Item MARK_TOTEM = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("mark_totem"),
            new TotemItem(() -> MARK, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final Item TROPHY_OLD_SALT = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("trophy_old_salt"),
            new Trophies.TrophyItem(Trophies.Power.TAILWIND, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final Item MARKS_REEL = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("marks_reel"),
            new ReelItem(new Item.Properties().durability(160).rarity(Rarity.RARE)));

    public static final Item BO_DADDY_TOTEM = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("bo_daddy_totem"),
            new TotemItem(() -> BO_DADDY, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final Item TROPHY_TOO_MUCH_SOUL = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("trophy_too_much_soul"),
            new Trophies.TrophyItem(Trophies.Power.BASS_DROP, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final Item SUBWOOFER = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("subwoofer"),
            new BossItems.Subwoofer(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.item.JukeboxSong> TWINKLE_SONG = net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.JUKEBOX_SONG, SeanCraft.id("twinkle"));
    public static final Item TWINKLE_DISC = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("music_disc_twinkle"),
            new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(TWINKLE_SONG)));

    public static final Item DAYLAN_TOTEM = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("daylan_totem"),
            new TotemItem(() -> DAYLAN, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final Item TROPHY_CLOUT = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("trophy_clout"),
            new Trophies.TrophyItem(Trophies.Power.CLOUT, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final Item FOLLOWER_LANTERN = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("follower_lantern"),
            new BossItems.FollowerLantern(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final Item BLUE_CHECK = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("blue_check"),
            new BossItems.BlueCheck(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** Bosses each player has beaten, for first-kill rewards. Kept through death. */
    public static final AttachmentType<Set<String>> BEATEN = AttachmentRegistry.<Set<String>>builder()
            .persistent(Codec.STRING.listOf().xmap(HashSet::new, List::copyOf))
            .copyOnDeath()
            .initializer(HashSet::new)
            .buildAndRegister(SeanCraft.id("bosses_beaten"));
    public static final AttachmentType<Trophies.State> POWER = AttachmentRegistry.<Trophies.State>builder()
            .persistent(Trophies.State.CODEC)
            .copyOnDeath()
            .buildAndRegister(SeanCraft.id("trophy_power"));

    private BossContent() {}

    public static void init() {
        FabricDefaultAttributeRegistry.register(MARK, BossEntity.bossAttributes());
        FabricDefaultAttributeRegistry.register(BO_DADDY, BossEntity.bossAttributes());
        FabricDefaultAttributeRegistry.register(DAYLAN, BossEntity.bossAttributes());
        FabricDefaultAttributeRegistry.register(FOLLOWER, Follower.attributes());
        BossDef.init();
        Arenas.init();
        Trophies.init();
        BossSites.init();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Restraint.tick(player);
                if (server.getTickCount() % 20 == 0) {
                    BossItems.FollowerLantern.tick(player);
                }
            }
        });
    }

    /** The Stale Logbook (A.3): your stats from the day you got it, never updated. */
    static ItemStack staleLogbook(ServerPlayer player) {
        PlayerData data = Rpg.data(player);
        StringBuilder page = new StringBuilder("Personnel file: " + player.getName().getString() + "\n\nLevel " + data.level + "\n");
        for (Skill skill : Skill.values()) {
            page.append(skill.label).append(": ").append(data.skillLevel(skill)).append("\n");
        }
        page.append("\nApproved by: nobody.");
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Stale Logbook"), "Mark", 0,
                List.of(Filterable.passThrough(Component.literal(page.toString()))), true));
        return book;
    }

    /** A boss's Summoning Totem: used on a Summoning Pedestal, it starts the fight and is used up (8.4.1). */
    public static class TotemItem extends Item {
        private final Supplier<EntityType<? extends BossEntity>> boss;

        public TotemItem(Supplier<EntityType<? extends BossEntity>> boss, Properties properties) {
            super(properties);
            this.boss = boss;
        }

        public EntityType<? extends BossEntity> boss() {
            return boss.get();
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            if (!level.getBlockState(pos).is(PEDESTAL)) {
                return InteractionResult.PASS;
            }
            if (level instanceof ServerLevel server) {
                Player player = context.getPlayer();
                if (Arenas.busy(level, pos)) {
                    if (player != null) {
                        player.displayClientMessage(Component.literal("A fight is already running here.").withStyle(ChatFormatting.RED), true);
                    }
                    return InteractionResult.FAIL;
                }
                context.getItemInHand().consume(1, player);
                Arenas.summon(server, pos, boss.get());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    /** Mark's Reel: a grapple rod. Hook a block up to 24 blocks away and get pulled to it. */
    public static class ReelItem extends Item {
        static final double REACH = 24;

        public ReelItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            Vec3 eye = player.getEyePosition();
            Vec3 end = eye.add(player.getLookAngle().scale(REACH));
            BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) {
                return InteractionResultHolder.fail(stack);
            }
            if (!level.isClientSide) {
                Vec3 to = hit.getLocation().subtract(player.position());
                double dist = to.length();
                Vec3 v = to.normalize().scale(Math.min(2.6, 0.6 + dist * 0.12));
                player.setDeltaMovement(v.x, Math.max(v.y, 0) + 0.45, v.z);
                player.hurtMarked = true;
                player.fallDistance = 0;
                level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1f, 1.2f);
                stack.hurtAndBreak(1, player, net.minecraft.world.entity.LivingEntity.getSlotForHand(hand));
            }
            player.getCooldowns().addCooldown(this, 30);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
    }
}
