package com.limachi.microtech_lib.common;

import com.mojang.datafixers.types.Type;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.fluid.Fluid;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.common.extensions.IForgeContainerType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class Registries {

    private static final HashMap<String, Registries> instanceMap = new HashMap<>();

    private final String mod_id;
    private boolean initializationFinished = false;

    private final Logger LOGGER;

    private final DeferredRegister<Block> BLOCK_REGISTER;
    private final DeferredRegister<Item> ITEM_REGISTER;
    private final DeferredRegister<net.minecraft.tileentity.TileEntityType<?>> TILE_ENTITY_REGISTER;
    private final DeferredRegister<ContainerType<?>> CONTAINER_TYPE_REGISTER;
    private final DeferredRegister<EntityType<?>> ENTITY_REGISTER;
    private final DeferredRegister<Fluid> FLUID_REGISTER;

    private final Map<String, RegistryObject<Block>> BLOCKS = new HashMap<>();
    private final Map<String, RegistryObject<Item>> ITEMS = new HashMap<>();
    private final Map<String, RegistryObject<Fluid>> FLUIDS = new HashMap<>();
    private final Map<String, RegistryObject<net.minecraft.tileentity.TileEntityType<?>>> TILE_ENTITY_TYPES = new HashMap<>();
    private final Map<String, RegistryObject<ContainerType<?>>> CONTAINER_TYPES = new HashMap<>();
    private final Map<String, RegistryObject<EntityType<?>>> ENTITY_TYPES = new HashMap<>();

    private Registries(String mod_id) {
        this.mod_id = mod_id;
        LOGGER = LogManager.getLogger(mod_id);
        BLOCK_REGISTER = DeferredRegister.create(ForgeRegistries.BLOCKS, mod_id);
        ITEM_REGISTER = DeferredRegister.create(ForgeRegistries.ITEMS, mod_id);
        TILE_ENTITY_REGISTER = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, mod_id);
        CONTAINER_TYPE_REGISTER = DeferredRegister.create(ForgeRegistries.CONTAINERS, mod_id);
        ENTITY_REGISTER = DeferredRegister.create(ForgeRegistries.ENTITIES, mod_id);
        FLUID_REGISTER = DeferredRegister.create(ForgeRegistries.FLUIDS, mod_id);
    }

    public static Registries createInstance(String mod_id) {
        instanceMap.put(mod_id, new Registries(mod_id));
        return instanceMap.get(mod_id);
    }

    public static void registerAll(IEventBus bus) {
        for (Registries i : instanceMap.values()) {
            i.BLOCK_REGISTER.register(bus);
            i.ITEM_REGISTER.register(bus);
            i.TILE_ENTITY_REGISTER.register(bus);
            i.CONTAINER_TYPE_REGISTER.register(bus);
            i.ENTITY_REGISTER.register(bus);
            i.LOGGER.info("Finished registration");
            i.initializationFinished = true;
        }
    }

    /**
     * helper function to generate a tile entity type and bind a tile entity to a block
     */
    public void registerTileEntity(String name, Supplier<? extends TileEntity> teSup, Supplier<? extends Block> blockSup, @Nullable Type<?> fixer) {
        registerTileEntityType(name, ()-> TileEntityType.Builder.of(teSup, blockSup.get()).build(fixer));
    }

    /**
     * helper function to generate block items
     */
    public Supplier<BlockItem> registerBlockItem(String name, String blockName, Item.Properties properties) {
        return registerItem(name, ()->new BlockItem(getBlock(blockName), properties));
    }

    /**
     * helper function to generate containers
     */
    public void registerContainer(String name, net.minecraftforge.fml.network.IContainerFactory<? extends Container> factory) {
        registerContainerType(name, () -> IForgeContainerType.create(factory));
    }

    /**
     * should be called in a static block
     */
    public <F extends Fluid> Supplier<F> registerFluid(String name, Supplier<F> sup) {
        LOGGER.info("Registering Fluid: " + name);
        if (initializationFinished)
            LOGGER.warn("Trying to register a block after initialization phase! Please move this registration to static phase: " + name);
        else
            FLUIDS.put(name, FLUID_REGISTER.register(name, sup));
        return ()->getFluid(name);
    }

    /**
     * should be called in a static block
     */
    public <T extends Block> Supplier<T> registerBlock(String name, Supplier<T> sup) {
        LOGGER.info("Registering Block: " + name);
        if (initializationFinished)
            LOGGER.warn("Trying to register a block after initialization phase! Please move this registration to static phase: " + name);
        else
            BLOCKS.put(name, BLOCK_REGISTER.register(name, sup));
        return ()->getBlock(name);
    }

    /**
     * should be called in a static block
     */
    public <T extends Item> Supplier<T> registerItem(String name, Supplier<T> sup) {
        LOGGER.info("Registering Item: " + name);
        if (initializationFinished)
            LOGGER.error("Trying to register an item after initialization phase! Please move this registration to static phase: " + name);
        else
            ITEMS.put(name, ITEM_REGISTER.register(name, sup));
        return ()->getItem(name);
    }

    /**
     * should be called in a static block
     */
    public <T extends TileEntityType<?>> Supplier<T> registerTileEntityType(String name, Supplier<T> sup) {
        LOGGER.info("Registering TileEntityType: " + name);
        if (initializationFinished)
            LOGGER.error("Trying to register a tile entity type after initialization phase! Please move this registration to static phase: " + name);
        else
            TILE_ENTITY_TYPES.put(name, TILE_ENTITY_REGISTER.register(name, sup));
        return ()->getBlockEntityType(name);
    }

    /**
     * should be called in a static block
     */
    public <T extends ContainerType<?>> Supplier<T> registerContainerType(String name, Supplier<T> sup) {
        LOGGER.info("Registering ContainerType: " + name);
        if (initializationFinished)
            LOGGER.error("Trying to register a container type after initialization phase! Please move this registration to static phase: " + name);
        else
            CONTAINER_TYPES.put(name, CONTAINER_TYPE_REGISTER.register(name, sup));
        return ()->getContainerType(name);
    }

    /**
     * should be called in a static block
     */
    public <T extends EntityType<?>> Supplier<T> registerEntityType(String name, Supplier<T> sup) {
        LOGGER.info("Registering EntityType: " + name);
        if (initializationFinished)
            LOGGER.error("Trying to register an entity type after initialization phase! Please move this registration to static phase: " + name);
        else
            ENTITY_TYPES.put(name, ENTITY_REGISTER.register(name, sup));
        return ()->getEntityType(name);
    }

    /**
     * only valid after register phase
     */
    public <B extends Block> B getBlock(String name) {
        if (!initializationFinished)
            LOGGER.warn("Trying to access a registry object (of type block) before finishing initialization! Please move this access after the registry phase: " + name);
        return (B)BLOCKS.get(name).get();
    }

    /**
     * only valid after register phase
     */
    public <I extends Item> I getItem(String name) {
        if (!initializationFinished)
            LOGGER.warn("Trying to access a registry object (of type item) before finishing initialization! Please move this access after the registry phase: " + name);
        return (I)ITEMS.get(name).get();
    }

    /**
     * only valid after register phase
     */
    public <F extends Fluid> F getFluid(String name) {
        if (!initializationFinished)
            LOGGER.warn("Trying to access a registry object (of type fluid) before finishing initialization! Please move this access after the registry phase: " + name);
        return (F)FLUIDS.get(name).get();
    }

    /**
     * only valid after register phase
     */
    public <T extends net.minecraft.tileentity.TileEntityType<?>> T getBlockEntityType(String name) {
        if (!initializationFinished)
            LOGGER.warn("Trying to access a registry object (of type tile entity type) before finishing initialization! Please move this access after the registry phase: " + name);
        return (T) TILE_ENTITY_TYPES.get(name).get();
    }

    /**
     * only valid after register phase
     */
    public <T extends ContainerType<?>> T getContainerType(String name) {
        if (!initializationFinished)
            LOGGER.warn("Trying to access a registry object (of type container type) before finishing initialization! Please move this access after the registry phase: " + name);
        return (T)CONTAINER_TYPES.get(name).get();
    }

    /**
     * only valid after register phase
     */
    public <T extends EntityType<?>> T getEntityType(String name) {
        if (!initializationFinished)
            LOGGER.warn("Trying to access a registry object (of type entity type) before finishing initialization! Please move this access after the registry phase: " + name);
        return (T) ENTITY_TYPES.get(name).get();
    }
}
