package com.limachi.microtech_lib;

import com.google.common.reflect.Reflection;
import com.limachi.microtech_lib.common.Registries;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.thread.EffectiveSide;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Type;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.limachi.microtech_lib.MicroTechLib.MOD_ID;

@Mod(MOD_ID)
public class MicroTechLib {

    public static final String MOD_ID = "microtech_lib";
    public static final Logger LOGGER = LogManager.getLogger();
    public static final Registries REGISTRIES = Registries.createInstance(MOD_ID);

    public static ResourceLocation location(String path) { return new ResourceLocation(MOD_ID, path); }

    /**
     * will debug methods actually log something
     */
    public static final boolean DO_DEBUG = true;
    /**
     * how debug will be logged (LOGGER::info or LOGGER::debug)
     */
    public static final Consumer<String> DEBUG = LOGGER::info;

    @SuppressWarnings({"UnusedReturnValue", "unused"})
    public static <T> T debug(T v, String s) { if (DO_DEBUG) DEBUG.accept(Thread.currentThread().getStackTrace()[2].toString() + " V: " + v + " : "+ s); return v; }
    @SuppressWarnings({"UnusedReturnValue", "unused"})
    public static <T> T debug(T v) { if (DO_DEBUG) DEBUG.accept(Thread.currentThread().getStackTrace()[2].toString() + " V: " + v); return v; }
    @SuppressWarnings({"UnusedReturnValue", "unused"})
    public static <T> T debug(T v, int depth) { if (DO_DEBUG) DEBUG.accept(Thread.currentThread().getStackTrace()[2 + depth].toString() + " V: " + v); return v; }
    @SuppressWarnings({"UnusedReturnValue", "unused"})
    public static <T> T debug(T v, int depth, String s) { if (DO_DEBUG) DEBUG.accept(Thread.currentThread().getStackTrace()[2 + depth].toString() + " V: " + v + " : "+ s); return v; }

    static {
        Type type = Type.getType(StaticInit.class);
        for (ModFileScanData.AnnotationData data : ModList.get().getAllScanData().stream().map(ModFileScanData::getAnnotations).flatMap(Collection::stream).filter(a-> type.equals(a.getAnnotationType())).collect(Collectors.toList())) {
            try {
                Reflection.initialize(Class.forName(data.getClassType().getClassName()));
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public MicroTechLib() {
        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ConfigManager.create(MOD_ID, ModConfig.Type.COMMON, MOD_ID, new String[]{".common"});
        Registries.registerAll(eventBus);
//        eventBus.addListener(CuriosIntegration::enqueueIMC);

        // Register the setup method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        // Register the enqueueIMC method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::enqueueIMC);
        // Register the processIMC method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::processIMC);
        // Register the doClientStuff method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    /** try by all means to know if the current invocation is on a logical client or logical server */
    public static boolean isServer(@Nullable World world) {
        if (world != null)
            return !world.isClientSide();
        return EffectiveSide.get() == LogicalSide.SERVER;
    }

    /** get the local minecraft player (only on client logical and physical side, returns null otherwise) */
    public static PlayerEntity getPlayer() {
        return runLogicalSide(null, ()->()-> Minecraft.getInstance().player, ()->()->null);
    }

    public static List<? extends PlayerEntity> getPlayers() {
        return runLogicalSide(null, ()->()-> Collections.singletonList(Minecraft.getInstance().player), ()->()->getServer().getPlayerList().getPlayers());
    }

    /** try to get the current server we are connected on, return null if we aren't connected (hanging in main menu for example) */
    public static MinecraftServer getServer() { return ServerLifecycleHooks.getCurrentServer(); }

    public static CommandSource silentCommandSource() {
        MinecraftServer server = getServer();
        ServerWorld serverworld = server.overworld();
        BlockPos pos = serverworld.getSharedSpawnPos();
        return new CommandSource(server, new Vector3d(pos.getX(), pos.getY(), pos.getZ()), Vector2f.ZERO, serverworld, 4, "DimBag Silent Command", new StringTextComponent("DimBag Silent Command"), server, null).withSuppressedOutput();
    }

    /** execute the first wrapped callable only on logical client + physical client, and the second wrapped callable on logical server (any physical side) */
    public static <T> T runLogicalSide(@Nullable World world, Supplier<Callable<T>> client, Supplier<Callable<T>> server) {
        if (isServer(world))
            try {
                return server.get().call();
            } catch (Exception e) { return null; }
        else
            return DistExecutor.callWhenOn(Dist.CLIENT, client);
    }

    private void setup(final FMLCommonSetupEvent event)
    {
        // some preinit code
        LOGGER.info("HELLO FROM PREINIT");
        LOGGER.info("DIRT BLOCK >> {}", Blocks.DIRT.getRegistryName());
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        // do something that can only be done on the client
        LOGGER.info("Got game settings {}", event.getMinecraftSupplier().get().options);
    }

    private void enqueueIMC(final InterModEnqueueEvent event)
    {
        // some example code to dispatch IMC to another mod
        InterModComms.sendTo("examplemod", "helloworld", () -> { LOGGER.info("Hello world from the MDK"); return "Hello world";});
    }

    private void processIMC(final InterModProcessEvent event)
    {
        // some example code to receive and process InterModComms from other mods
        LOGGER.info("Got IMC {}", event.getIMCStream().
                map(m->m.getMessageSupplier().get()).
                collect(Collectors.toList()));
    }

    // You can use EventBusSubscriber to automatically subscribe events on the contained class (this is subscribing to the MOD
    // Event bus for receiving Registry Events)
    @Mod.EventBusSubscriber(bus=Mod.EventBusSubscriber.Bus.MOD)
    public static class RegistryEvents {
        @SubscribeEvent
        public static void onBlocksRegistry(final RegistryEvent.Register<Block> blockRegistryEvent) {
            // register a new block here
            LOGGER.info("HELLO from Register Block");
        }
    }
}
