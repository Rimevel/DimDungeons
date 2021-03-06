package com.catastrophe573.dimdungeons.feature;

import java.util.Random;
import java.util.function.Function;

import com.catastrophe573.dimdungeons.DimDungeons;
import com.catastrophe573.dimdungeons.block.BlockRegistrar;
import com.catastrophe573.dimdungeons.block.TileEntityPortalKeyhole;
import com.catastrophe573.dimdungeons.dimension.DungeonDimensionType;
import com.catastrophe573.dimdungeons.item.ItemPortalKey;
import com.catastrophe573.dimdungeons.structure.DungeonBuilderLogic;
import com.catastrophe573.dimdungeons.structure.DungeonBuilderLogic.DungeonRoom;
import com.catastrophe573.dimdungeons.structure.DungeonBuilderTestShapes;
import com.mojang.datafixers.Dynamic;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ILivingEntityData;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.state.properties.StructureMode;
import net.minecraft.tileentity.BarrelTileEntity;
import net.minecraft.tileentity.ChestTileEntity;
import net.minecraft.tileentity.DispenserTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Mirror;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IWorld;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.GenerationSettings;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.feature.template.PlacementSettings;
import net.minecraft.world.gen.feature.template.Template;
import net.minecraft.world.gen.feature.template.Template.BlockInfo;
import net.minecraft.world.gen.feature.template.TemplateManager;

public class AdvancedDungeonFeature extends Feature<NoFeatureConfig>
{
    public static String FEATURE_ID = "feature_advanced_dungeon";

    public AdvancedDungeonFeature(Function<Dynamic<?>, ? extends NoFeatureConfig> function)
    {
	super(function);
    }

    @Override
    // vanilla worldgen calls this function at some point in the complicated decoration process
    public boolean place(IWorld world, ChunkGenerator<? extends GenerationSettings> chunkGenerator, Random rand, BlockPos pos, NoFeatureConfig config)
    {
	// only put dungeons on the right chunks, and only in the dungeon dimension
	if (world.getDimension().getType() != DungeonDimensionType.getDimensionType())
	{
	    DimDungeons.LOGGER.info("DIMDUNGEONS WEIRD ERROR: why is there a dungeon biome outside of the dungeon dimension?");
	    return false;
	}

	ChunkPos cpos = new ChunkPos(pos);
	if (isDungeonChunk(cpos.x, cpos.z))
	{
	    DungeonRoom room = getRoomForChunk(cpos, world);
	    if (room != null)
	    {
		boolean success = putRoomHere(cpos, world, room);
		if (!success)
		{
		    DimDungeons.LOGGER.info("DIMDUNGEONS STRUCTURE ERROR: failed to place structure " + room.structure + " at " + cpos.x + ", " + cpos.z);
		}
		return true;
	    }
	}

	return false;
    }

    // also used to by the DungeonChunkGenerator, note that the dimension check is not done here
    public static boolean isDungeonChunk(long x, long z)
    {
	if (x < 0 || z > 0)
	{
	    return false; // advanced dungeons only spawn in the +x/-z quadrant
	}

	long plotX = x % 16;
	long plotZ = z % 16;
	return plotX > 3 && plotX < 12 && plotZ < -4 && plotZ > -13;
    }

    // also used to by the DungeonChunkGenerator, note that the dimension check is not done here
    public static boolean isEntranceChunk(long x, long z)
    {
	if (x < 0 || z > 0)
	{
	    return false; // advanced dungeons only spawn in the +x/-z quadrant
	}

	long plotX = x % 16;
	long plotZ = z % 16;
	return plotX == 8 && plotZ == -5;
    }

    // used by the place() function to actually place rooms
    public static boolean putRoomHere(ChunkPos cpos, IWorld world, DungeonRoom room)
    {
	MinecraftServer minecraftserver = world.getWorld().getServer();
	TemplateManager templatemanager = minecraftserver.getWorld(world.getDimension().getType()).getStructureTemplateManager();

	Template template = templatemanager.getTemplate(new ResourceLocation(DimDungeons.RESOURCE_PREFIX + room.structure));
	PlacementSettings placementsettings = (new PlacementSettings()).setMirror(Mirror.NONE).setRotation(Rotation.NONE).setIgnoreEntities(false).setChunk(cpos);
	placementsettings.setBoundingBox(placementsettings.getBoundingBox());

	placementsettings.setRotation(room.rotation);
	BlockPos position = new BlockPos(cpos.getXStart(), 50, cpos.getZStart());

	if (template == null)
	{
	    DimDungeons.LOGGER.info("DIMDUNGEONS FATAL ERROR: Structure does not exist (" + room.structure + ")");
	    return false;
	}

	// next if the structure is to be rotated then it must also be offset, because rotating a structure also moves it
	if (room.rotation == Rotation.COUNTERCLOCKWISE_90)
	{
	    // west: rotate CCW and push +Z
	    placementsettings.setRotation(Rotation.COUNTERCLOCKWISE_90);
	    position = position.add(0, 0, template.getSize().getZ() - 1);
	}
	else if (room.rotation == Rotation.CLOCKWISE_90)
	{
	    // east rotate CW and push +X
	    placementsettings.setRotation(Rotation.CLOCKWISE_90);
	    position = position.add(template.getSize().getX() - 1, 0, 0);
	}
	else if (room.rotation == Rotation.CLOCKWISE_180)
	{
	    // south: rotate 180 and push both +X and +Z
	    placementsettings.setRotation(Rotation.CLOCKWISE_180);
	    position = position.add(template.getSize().getX() - 1, 0, template.getSize().getZ() - 1);
	}
	else //if (nextRoom.rotation == Rotation.NONE)
	{
	    // north: no rotation
	    placementsettings.setRotation(Rotation.NONE);
	}
	//DimDungeons.LOGGER.info("Placing a room: " + room.structure);
	boolean success = template.addBlocksToWorld(world, position, placementsettings, 2);

	// handle data blocks - this code block is copied from TemplateStructurePiece
	//Map<BlockPos, String> map = template.getDataBlocks(position, placementsettings); // 1.12 / 1.13 version
	//List<Template.BlockInfo> dblocks = template.func_215386_a(position, placementsettings, Blocks.STRUCTURE_BLOCK, true); // my old 1.14.2 method
	for (Template.BlockInfo template$blockinfo : template.func_215381_a(position, placementsettings, Blocks.STRUCTURE_BLOCK))
	{
	    if (template$blockinfo.nbt != null)
	    {
		StructureMode structuremode = StructureMode.valueOf(template$blockinfo.nbt.getString("mode"));
		if (structuremode == StructureMode.DATA)
		{
		    handleDataBlock(template$blockinfo.nbt.getString("metadata"), template$blockinfo.pos, world, world.getRandom(), placementsettings.getBoundingBox());
		}
	    }
	}
	
	// replace all red carpet in entrance rooms with green carpet
	for (BlockInfo info : template.func_215381_a(position, placementsettings, Blocks.RED_CARPET))
	{
	    world.setBlockState(info.pos, Blocks.GREEN_CARPET.getDefaultState(), 3);
	}
	
	return success;
    }

    // this function assumes the chunk isDungeonChunk() and may return null if the dungeon doesn't have a room at that position
    public static DungeonRoom getRoomForChunk(ChunkPos cpos, IWorld world)
    {
	// start by calculating the position of the entrance chunk for this dungeon
	int entranceX = cpos.x;
	int entranceZ = cpos.z;
	int distToEntranceX = 8 - (entranceX % 16);
	int distToEntranceZ = 5 + (entranceZ % 16);
	entranceX += distToEntranceX;
	entranceZ -= distToEntranceZ;

	// assert that my math is not bad
	if (!isEntranceChunk(entranceX, entranceZ))
	{
	    DimDungeons.LOGGER.info("DIMDUNGEONS MAJOR ERROR: attempting to generate a dungeon at a chunk which isn't an entrance chunk! (" + entranceX + ", " + entranceZ + ")");
	    return null;
	}

	// this is the date structure for an entire dungeon
	DungeonBuilderLogic dbl = new DungeonBuilderLogic(world.getSeed(), entranceX, entranceZ);

	// trigger some debug code for test layouts
	if (world.getWorldInfo().getWorldName().equalsIgnoreCase("DimDungeonsDebugOne"))
	{
	    DungeonBuilderTestShapes.MakeTestDungeonEnds(dbl);
	}
	else if (world.getWorldInfo().getWorldName().equalsIgnoreCase("DimDungeonsDebugTwo"))
	{
	    DungeonBuilderTestShapes.MakeTestDungeonTwos(dbl);
	}
	else if (world.getWorldInfo().getWorldName().equalsIgnoreCase("DimDungeonsDebugThree"))
	{
	    DungeonBuilderTestShapes.MakeTestDungeonThreesAndFours(dbl);
	}
	else
	{
	    // generate the entire dungeon, an advanced dungeon
	    dbl.calculateDungeonShape(52);
	}

	// pick the room we want, for example the entrance room is at [4][7] in this array
	int i = (cpos.x % 16) - 4;
	int j = (cpos.z % 16) + 12;
	DungeonRoom nextRoom = dbl.finalLayout[i][j];
	if (!nextRoom.hasRoom())
	{
	    return null; // no room here after all
	}
	return nextRoom;
    }

    // another debugging function
    public void printMap(DungeonBuilderLogic dbl)
    {
	for (int j = 0; j < 8; j++)
	{
	    String dungeonRowShape = "";
	    for (int i = 0; i < 8; i++)
	    {
		dungeonRowShape += dbl.finalLayout[i][j].hasRoom() ? "*" : ".";
	    }
	    System.out.println(dungeonRowShape);
	}
    }

    // resembles TemplateStructurePiece.handleDataMarker()
    protected static void handleDataBlock(String name, BlockPos pos, IWorld world, Random rand, MutableBoundingBox bb)
    {
	//DimDungeons.LOGGER.info("DATA BLOCK NAME: " + name);

	if ("LockIt".equals(name))
	{
	    LockDispensersAround(world, pos);
	    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2); // erase this data block 
	}
	else if ("LockItStoneBrick".equals(name))
	{
	    LockDispensersAround(world, pos);
	    world.setBlockState(pos, Blocks.STONE_BRICKS.getDefaultState(), 2); // erase this data block 
	}
	else if ("ReturnPortal".equals(name))
	{
	    LockDispensersAround(world, pos);
	    world.setBlockState(pos, BlockRegistrar.block_gold_portal.getDefaultState(), 2); // erase this data block 
	}
	else if ("FortuneTeller".equals(name))
	{
	    world.setBlockState(pos, Blocks.STONE_BRICKS.getDefaultState(), 2); // erase this data block 
	    faceContainerTowardsAir(world, pos.down());
	    LockDispensersAround(world, pos.down());

	    // put a message inside the dispenser
	    TileEntity te = world.getTileEntity(pos.down());
	    if (te instanceof DispenserTileEntity)
	    {
		((DispenserTileEntity) te).clear();
		ItemStack message = generateLuckyMessage(rand);
		((DispenserTileEntity) te).addItemStack(message);
	    }
	    else
	    {
		DimDungeons.LOGGER.info("DIMDUNGEONS TILE ENTITY ERROR: unable to place a fortune teller block.");
	    }
	}
	else if ("ChestLoot1".equals(name))
	{
	    // 80% loot_1, 20% loot_2
	    int lucky = rand.nextInt(100);
	    if (lucky < 80)
	    {
		fillChestBelow(pos, new ResourceLocation(DimDungeons.RESOURCE_PREFIX + "chests/chestloot_3"), world, rand);
	    }
	    else
	    {
		fillChestBelow(pos, new ResourceLocation(DimDungeons.RESOURCE_PREFIX + "chests/chestloot_4"), world, rand);
	    }
	}
	else if ("ChestLoot2".equals(name))
	{
	    fillChestBelow(pos, new ResourceLocation(DimDungeons.RESOURCE_PREFIX + "chests/chestloot_4"), world, rand);
	}
	else if ("ChestLootLucky".equals(name))
	{
	    // 70% nothing, 30% random minecraft loot table that isn't an end city
	    int lucky = rand.nextInt(100);
	    if (lucky < 30)
	    {
		fillChestBelow(pos, new ResourceLocation(DimDungeons.RESOURCE_PREFIX + "chests/chestloot_crazy"), world, rand);
	    }
	    else
	    {
		world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2); // erase this data block 
		world.setBlockState(pos.down(), Blocks.AIR.getDefaultState(), 2); // and erase the chest below it
	    }
	}
	else if ("SetTrappedLoot".equals(name))
	{
	    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2); // erase this data block
	    ChestTileEntity te = (ChestTileEntity) world.getTileEntity(pos.down());
	    if (te != null)
	    {
		te.clear();
		te.setLootTable(new ResourceLocation(DimDungeons.RESOURCE_PREFIX + "chests/chestloot_3"), rand.nextLong());
	    }
	}
	else if ("BarrelLoot1".equals(name))
	{
	    // 80% loot_1, 20% loot_2
	    int lucky = rand.nextInt(100);
	    if (lucky < 80)
	    {
		fillBarrelBelow(pos, new ResourceLocation(DimDungeons.RESOURCE_PREFIX + "chests/chestloot_3"), world, rand);
	    }
	    else
	    {
		fillBarrelBelow(pos, new ResourceLocation(DimDungeons.RESOURCE_PREFIX + "chests/chestloot_4"), world, rand);
	    }
	}
	else if ("PlaceL2Key".equals(name))
	{
	    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2); // erase this data block
	    TileEntityPortalKeyhole te = (TileEntityPortalKeyhole)world.getTileEntity(pos.down());
	    if (te != null)
	    {
		ItemStack key = te.getObjectInserted();
		if ( key.getItem() instanceof ItemPortalKey )
		{
		    ((ItemPortalKey)key.getItem()).activateKeyLevel2(key);
		    te.setContents(key);
		    //te.updateContainingBlockInfo();
		}
	    }
	}
	else if ("SummonWitch".equals(name))
	{
	    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2); // erase this data block
	    spawnEnemyHere(pos, "witch", world);
	}
	else if ("SummonWaterEnemy".equals(name))
	{
	    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2); // erase this data block
	    spawnEnemyHere(pos, "guardian", world);
	}
	else if ("SummonEnderman".equals(name))
	{
	    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2); // erase this data block
	    spawnEnemyHere(pos, "enderman", world);
	}
	else if ("SummonEnemy1".equals(name))
	{
	    // 50% chance of a weak enemy OR BLAZE
	    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2); // erase this data block
	    int chance = rand.nextInt(100);
	    if (chance < 16)
	    {
		spawnEnemyHere(pos, "zombie", world);
	    }
	    else if (chance < 32)
	    {
		spawnEnemyHere(pos, "husk", world);
	    }
	    else if (chance < 48)
	    {
		spawnEnemyHere(pos, "drowned", world);
	    }
	    else if (chance < 74)
	    {
		spawnEnemyHere(pos, "blaze", world);
	    }
	    else
	    {
		spawnEnemyHere(pos, "spider", world);
	    }
	}
	else if ("SummonEnemy2".equals(name))
	{
	    // 80% chance of a strong enemy
	    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2); // erase this data block
	    int chance = rand.nextInt(100);
	    if (chance < 20)
	    {
		spawnEnemyHere(pos, "wither_skeleton", world);
	    }
	    else if (chance < 40)
	    {
		spawnEnemyHere(pos, "stray", world);
	    }
	    else if (chance < 60)
	    {
		spawnEnemyHere(pos, "skeleton", world);
	    }
	    else if (chance < 80)
	    {
		spawnEnemyHere(pos, "pillager", world);
	    }
	}
	else
	{
	    DimDungeons.LOGGER.info("UNHANDLED DATA BLOCK WITH name = " + name);
	    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2); // erase this data block
	}
    }

    private static void spawnEnemyHere(BlockPos pos, String casualName, IWorld world)
    {
	MobEntity mob = null;

	if ("witch".contentEquals(casualName))
	{
	    mob = EntityType.WITCH.create(world.getWorld());
	    mob.setPosition(pos.getX(), pos.getY() + 1, pos.getZ());
	    mob.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.35f);
	    mob.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(40.0f);
	    mob.setHealth(40.0f);
	}
	else if ("enderman".contentEquals(casualName))
	{
	    mob = EntityType.ENDERMAN.create(world.getWorld());
	    mob.setPosition(pos.getX(), pos.getY() + 2, pos.getZ());
	    mob.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.4f);
	    mob.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(50.0f);
	    mob.setHealth(50.0f);
	}
	else if ("guardian".contentEquals(casualName))
	{
	    mob = EntityType.GUARDIAN.create(world.getWorld());
	    mob.setPosition(pos.getX(), pos.getY() + 1, pos.getZ());
	    mob.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.4f);
	    mob.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(40.0f);
	    mob.setHealth(40.0f);
	}
	else if ("zombie".contentEquals(casualName))
	{
	    mob = EntityType.ZOMBIE.create(world.getWorld());
	    mob.setPosition(pos.getX(), pos.getY() + 1, pos.getZ());
	    mob.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.35f);
	    mob.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(30.0f);
	    mob.setHealth(32.0f);
	}
	else if ("husk".contentEquals(casualName))
	{
	    mob = EntityType.HUSK.create(world.getWorld());
	    mob.setPosition(pos.getX(), pos.getY() + 1, pos.getZ());
	    mob.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.35f);
	    mob.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(30.0f);
	    mob.setHealth(32.0f);
	}
	else if ("drowned".contentEquals(casualName))
	{
	    mob = EntityType.DROWNED.create(world.getWorld());
	    mob.setPosition(pos.getX(), pos.getY() + 1, pos.getZ());
	    mob.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.35f);
	    mob.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(30.0f);
	    mob.setHealth(32.0f);
	}
	else if ("skeleton".contentEquals(casualName))
	{
	    mob = EntityType.SKELETON.create(world.getWorld());
	    mob.setPosition(pos.getX(), pos.getY() + 1, pos.getZ());
	    mob.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.4f);
	    mob.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(30.0f);
	    mob.setHealth(40.0f);
	}
	else if ("wither_skeleton".contentEquals(casualName))
	{
	    mob = EntityType.WITHER_SKELETON.create(world.getWorld());
	    mob.setPosition(pos.getX(), pos.getY() + 1, pos.getZ());
	    mob.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.35f);
	    mob.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(30.0f);
	    mob.setHealth(30.0f);
	}
	else if ("stray".contentEquals(casualName))
	{
	    mob = EntityType.STRAY.create(world.getWorld());
	    mob.setPosition(pos.getX(), pos.getY() + 1, pos.getZ());
	    mob.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.45f);
	    mob.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(30.0f);
	    mob.setHealth(40.0f);
	}
	else if ("spider".contentEquals(casualName))
	{
	    mob = EntityType.SPIDER.create(world.getWorld());
	    mob.setPosition(pos.getX(), pos.getY() + 1, pos.getZ());
	    mob.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.4f);
	    mob.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(36.0f);
	    mob.setHealth(36.0f);
	}
	else if ("pillager".contentEquals(casualName))
	{
	    mob = EntityType.PILLAGER.create(world.getWorld());
	    mob.setPosition(pos.getX(), pos.getY() + 1, pos.getZ());
	    mob.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.35f);
	    mob.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(36.0f);
	    mob.setHealth(36.0f);
	}
	else if ("blaze".contentEquals(casualName))
	{
	    mob = EntityType.BLAZE.create(world.getWorld());
	    mob.setPosition(pos.getX(), pos.getY() + 1, pos.getZ());
	    mob.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.35f);
	    mob.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(30.0f);
	    mob.setHealth(30.0f);
	}
	else
	{
	    System.out.println("DungeonChunkGenerator: Attempting to spawn unrecognized enemy: " + casualName);
	    return;
	}

	mob.setCanPickUpLoot(false);
	//mob.setCustomName(new StringTextComponent(I18n.format("enemy.dimdungeons." + casualName)));
	mob.setCustomName(new TranslationTextComponent("enemy.dimdungeons." + casualName + "2"));
	mob.setHomePosAndDistance(pos, 8);
	mob.moveToBlockPosAndAngles(pos, 0.0F, 0.0F);
	mob.enablePersistence();
	mob.addPotionEffect(new EffectInstance(Effects.FIRE_RESISTANCE, 9999999));
	mob.addPotionEffect(new EffectInstance(Effects.JUMP_BOOST, 9999999, 3));

	mob.onInitialSpawn(world, world.getDifficultyForLocation(pos), SpawnReason.STRUCTURE, (ILivingEntityData) null, (CompoundNBT) null);
	world.addEntity(mob);
    }

    private static void fillChestBelow(BlockPos pos, ResourceLocation lootTable, IWorld world, Random rand)
    {
	world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2); // erase this data block
	faceContainerTowardsAir(world, pos.down());

	// set the loot table
	TileEntity te = world.getTileEntity(pos.down());
	if (te instanceof ChestTileEntity)
	{
	    ((ChestTileEntity) te).clear();
	    ((ChestTileEntity) te).setLootTable(lootTable, rand.nextLong());
	}
	else
	{
	    DimDungeons.LOGGER.info("DIMDUNGEONS: FAILED TO PLACE CHEST IN DUNGEON. pos = " + pos.getX() + ", " + pos.getZ());
	}
    }

    private static void fillBarrelBelow(BlockPos pos, ResourceLocation lootTable, IWorld world, Random rand)
    {
	world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2); // erase this data block

	// set the loot table
	TileEntity te = world.getTileEntity(pos.down());
	if (te instanceof BarrelTileEntity)
	{
	    ((BarrelTileEntity) te).clear();
	    ((BarrelTileEntity) te).setLootTable(lootTable, rand.nextLong());
	}
	else
	{
	    DimDungeons.LOGGER.info("DIMDUNGEONS: FAILED TO PLACE BARREL IN DUNGEON. pos = " + pos.getX() + ", " + pos.getZ());
	}
    }

    // I was originally thinking that this would contain direct hints about the dungeon, but that would involve a post generation step
    private static ItemStack generateLuckyMessage(Random rand)
    {
	ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
	stack.setTag(new CompoundNBT());

	// randomize book contents
	int messageVariation = rand.nextInt(8) + 1;

	String title = new TranslationTextComponent("book.dimdungeons.title_4").getString();
	String body = new TranslationTextComponent("book.dimdungeons.advanced_message_" + messageVariation).getString();

	// create the complicated NBT tag list for the list of pages in the book
	ListNBT pages = new ListNBT();
	ITextComponent text = new TranslationTextComponent(body);
	String json = ITextComponent.Serializer.toJson(text);
	//pages.appendTag(new NBTTagString(json)); // 1.12
	//pages.add(0, new NBTTagString(json)); // 1.13
	//pages.add(0, new StringNBT(json)); // 1.14
	pages.add(0, StringNBT.valueOf(json)); // 1.15

	// actually set all the bookish NBT on the item
	stack.getTag().putBoolean("resolved", false);
	stack.getTag().putInt("generation", 0);
	stack.getTag().put("pages", pages);
	stack.getTag().putString("title", title);
	stack.getTag().putString("author", new TranslationTextComponent("book.dimdungeons.author").getString());
	return stack;
    }

    // this function might not be needed unless the DungeonDimension::canMineBlock() returns true for Dispensers
    private static void LockDispensersAround(IWorld world, BlockPos pos)
    {
	//	Random r = new Random((world.getSeed() + (long) (pos.getX() * pos.getX() * 4987142) + (long) (pos.getX() * 5947611) + (long) (pos.getZ() * pos.getZ()) * 4392871L + (long) (pos.getZ() * 389711) ^ world.getSeed()));
	//
	//	// make sure the player cannot be holding an item with this name
	//	LockCode code = new LockCode("ThisIsIntentionallyLongerThanCanNormallyBePossiblePlus" + r.nextLong());
	//
	//	if (world.getBlockState(pos.up()).getBlock() == Blocks.DISPENSER)
	//	{
	//	    //((DispenserTileEntity) world.getTileEntity(pos.up())).setLockCode(code);
	//	}
	//	if (world.getBlockState(pos.down()).getBlock() == Blocks.DISPENSER)
	//	{
	//	    //((DispenserTileEntity) world.getTileEntity(pos.down())).setLockCode(code);
	//	}
	//	if (world.getBlockState(pos.north()).getBlock() == Blocks.DISPENSER)
	//	{
	//	    //((DispenserTileEntity) world.getTileEntity(pos.north())).setLockCode(code);
	//	}
	//	if (world.getBlockState(pos.south()).getBlock() == Blocks.DISPENSER)
	//	{
	//	    //((DispenserTileEntity) world.getTileEntity(pos.south())).setLockCode(code);
	//	}
	//	if (world.getBlockState(pos.west()).getBlock() == Blocks.DISPENSER)
	//	{
	//	    //((DispenserTileEntity) world.getTileEntity(pos.west())).setLockCode(code);
	//	}
	//	if (world.getBlockState(pos.east()).getBlock() == Blocks.DISPENSER)
	//	{
	//	    //((DispenserTileEntity) world.getTileEntity(pos.east())).setLockCode(code);
	//	}
    }

    // used on dispensers and chests, particularly ones created by data blocks
    // this function might not be needed in versions later thaN 1.13
    private static void faceContainerTowardsAir(IWorld world, BlockPos pos)
    {
	BlockState bs = world.getBlockState(pos);

	if (bs.getBlock() == Blocks.DISPENSER || bs.getBlock() == Blocks.CHEST)
	{
	    if (world.getBlockState(pos.north()).getBlock() == Blocks.AIR)
	    {
		//bs.with(DispenserBlock.FACING, Direction.NORTH);
	    }
	    if (world.getBlockState(pos.south()).getBlock() == Blocks.AIR)
	    {
		//bs.with(DispenserBlock.FACING, Direction.SOUTH);
	    }
	    if (world.getBlockState(pos.west()).getBlock() == Blocks.AIR)
	    {
		//bs.with(DispenserBlock.FACING, Direction.WEST);
	    }
	    if (world.getBlockState(pos.east()).getBlock() == Blocks.AIR)
	    {
		//bs.with(DispenserBlock.FACING, Direction.EAST);
	    }
	    world.setBlockState(pos, bs, 2);
	}
    }
}