package net.skullian;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EasyFilters implements ModInitializer {
	public static final Logger LOGGER = LogManager.getLogger("easyfilters");

	private static Map<PlayerEntity, Item> itemType = new HashMap<>();
	private static Map<PlayerEntity, Boolean> enabledPlayers = new HashMap<>();
	private static Map<PlayerEntity, Integer> firstSlotCount = new HashMap<>();
	private static Map<PlayerEntity, Boolean> fillFirstSlot = new HashMap<>();



	@Override
	public void onInitialize() {

		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {

			if (!level.isClient()) {
				if (!enabledPlayers.containsKey(player) || !enabledPlayers.get(player)) {
					return ActionResult.PASS;
				}

				if (!firstSlotCount.containsKey(player)) {
					sendMessage(player, "§cYou must specify a filter type first! (§6/easyfilters filtertype ss2/ss3§c)");
					return ActionResult.PASS;
				}
				if (!itemType.containsKey(player)) {
					sendMessage(player, "§cYou must specify an item type first! (§6/easyfilters item <choose from the list!>)");
					return ActionResult.PASS;
				}

				BlockPos blockPos = hitResult.getBlockPos();
				BlockState blockState = level.getBlockState(blockPos);

				if (blockState.getBlock() instanceof HopperBlock) {
					doHopperFill(player, blockPos);
				}
			}

			return ActionResult.PASS;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
			dispatcher.register(CommandManager.literal("easyfilters")
					.then(CommandManager.literal("item")
							.then(CommandManager.argument("presetOptions", StringArgumentType.word())
									.suggests((context, builder) -> {
										String[] options = getStackableItemNames(); // Add more options as needed
										return CommandSource.suggestMatching(options, builder);
									})
									.executes(context -> {
										ServerCommandSource source = context.getSource();
										String presetOptions = StringArgumentType.getString(context, "presetOptions");

										Item translated = translationKeyToItem(presetOptions);
										if (translated != Items.AIR || translated != null) {


											itemType.put((PlayerEntity) source.getPlayer(), translated);
											sendMessage((PlayerEntity) source.getPlayer(), "§aSuccessfully set filter item to: §6" + translated);
											return 1;
										}

										sendMessage((PlayerEntity) source.getPlayer(), "§cThat is not a valid item (or an error occurred).");
										return 1;
									})))
					.then(CommandManager.literal("toggleFillFirstSlot")
							.executes(context -> {
								ServerCommandSource source = context.getSource();

								if (fillFirstSlot.containsKey((PlayerEntity) source.getPlayer())) {
									sendMessage((PlayerEntity) source.getPlayer(), "§7Fill first slot mode has been toggled §c§lOFF.");
									fillFirstSlot.remove((PlayerEntity) source.getPlayer());
								} else {
									fillFirstSlot.put((PlayerEntity) source.getPlayer(), true);
									sendMessage((PlayerEntity) source.getPlayer(), "§7Fill first slot mode has been toggled §a§lON.");
								}

								return 1;
							}))
					.then(CommandManager.literal("toggle")
							.executes(context -> {
								ServerCommandSource source = context.getSource();

								if (enabledPlayers.containsKey((PlayerEntity) source.getPlayer())) {
									sendMessage((PlayerEntity) source.getPlayer(), "§7Filter mode has been toggled §c§lOFF.");
									enabledPlayers.remove((PlayerEntity) source.getPlayer());
								} else {
									enabledPlayers.put((PlayerEntity) source.getPlayer(), true);
									sendMessage((PlayerEntity) source.getPlayer(), "§7Filter mode has been toggled §a§lON.");
								}

								return 1;
							}))
					.then(CommandManager.literal("filtertype")
							.then(CommandManager.argument("presetOptions", StringArgumentType.greedyString())
									.suggests((context, builder) -> {
										String[] options = {"ss2", "ss3"}; // Add more options as needed
										return CommandSource.suggestMatching(options, builder);
									})
									.executes(context -> {
										ServerCommandSource source = context.getSource();
										String presetOptions = StringArgumentType.getString(context, "presetOptions");

										if (presetOptions.equals("ss2")) {
											firstSlotCount.put((PlayerEntity) source.getPlayer(), 18);
											sendMessage((PlayerEntity) source.getPlayer(), "§7Filter type has been set to§a§l SS2.");
											return 1;
										} else if (presetOptions.equals("ss3")) {
											firstSlotCount.put((PlayerEntity) source.getPlayer(), 1);
											sendMessage((PlayerEntity) source.getPlayer(), "§7Filter type has been set to§a§l SS3.");
											return 1;
										}

										return 1;
									}))));
		});


	}

	private String[] getStackableItemNames() {
		List<String> options = new ArrayList<>();
		int index = 0;
		for (Item item : Registry.ITEM) {
			if (item.isDamageable() || item.getMaxCount() == 64) {
				String refactoredName = item.getTranslationKey();
				if (item.getTranslationKey().startsWith("block.minecraft.")) {
					refactoredName = item.getTranslationKey().replace("block.minecraft.", "");
				}
				if (item.getTranslationKey().startsWith("item.minecraft.")) {
					refactoredName = item.getTranslationKey().replace("item.minecraft.", "");
				}
				options.add(refactoredName);
			}
		}

		return options.toArray(new String[0]);
	}

	public static ItemStack findFirstItem(PlayerEntity player, Item itemToFind, int count) {

		ItemStack ironNuggets = ItemStack.EMPTY;
		int remainingCount = count;
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack itemStack = player.getInventory().getStack(i);
			if (!itemStack.isEmpty() && itemStack.getItem() == itemToFind) {
				int stackCount = itemStack.getCount();
				if (stackCount <= remainingCount) {
					remainingCount -= stackCount;
					ironNuggets = itemStack.copy();
					player.getInventory().setStack(i, ItemStack.EMPTY);
				} else {
					ironNuggets = itemStack.copy();
					ironNuggets.setCount(count);
					itemStack.setCount((stackCount - count));
					remainingCount = 0;
				}
				if (remainingCount == 0) {
					break;
				}
			}
		}


		return ironNuggets;
	}

	public static void doHopperFill(PlayerEntity player, BlockPos hopperPos) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		BlockEntity hopperEntity = world.getBlockEntity(hopperPos);

		if (hopperEntity instanceof HopperBlockEntity) {
			HopperBlockEntity hopper = (HopperBlockEntity) hopperEntity;

			fillSlots(player, hopper, itemType.get(player), firstSlotCount.get(player));
		}
	}

	private static void fillSlots(PlayerEntity player, HopperBlockEntity hopperBlock, Item type, int firstSlotCount) {
		if (fillFirstSlot.containsKey(player)) {
			if (alreadyHasItemInIt(player, hopperBlock.getPos(), 0)) {
				ItemStack itemStack = findFirstItem(player, type, 1);
				if (itemStack == null || itemStack == ItemStack.EMPTY) {
					sendMessage(player, "§cCould not find 1 or more of configured item [§e§l" + itemType.get(player) + "§c] in your inventory.");
				} else {
					hopperBlock.setStack(0, itemStack);
				}

			}
		}

		if (alreadyHasItemInIt(player, hopperBlock.getPos(), 1)) {
			ItemStack itemStack = findFirstItem(player, type, firstSlotCount);
			if (itemStack == null || itemStack == ItemStack.EMPTY) {
				sendMessage(player, "§cCould not 18 or more of configured item [§e§l" + itemType.get(player) + "§c] in your inventory.");
			} else {
				hopperBlock.setStack(1, itemStack);
			}
		}
		if (alreadyHasItemInIt(player, hopperBlock.getPos(), 2)) {
			ItemStack itemStack = findFirstItem(player, type, 1);
			if (itemStack == null || itemStack == ItemStack.EMPTY) {
				sendMessage(player, "§cCould not find 1 or more of configured item [§e§l" + itemType.get(player) + "§c] in your inventory.");
			} else {
				hopperBlock.setStack(2, itemStack);
			}
		}
		if (alreadyHasItemInIt(player, hopperBlock.getPos(), 3)) {
			ItemStack itemStack = findFirstItem(player, type, 1);
			if (itemStack == null || itemStack == ItemStack.EMPTY) {
				sendMessage(player, "§cCould not find 1 or more of configured item [§e§l" + itemType.get(player) + "§c] in your inventory.");
			} else {
				hopperBlock.setStack(3, itemStack);
			}
		}
		if (alreadyHasItemInIt(player, hopperBlock.getPos(), 4)) {
			ItemStack itemStack = findFirstItem(player, type, 1);
			if (itemStack == null || itemStack == ItemStack.EMPTY) {
				sendMessage(player, "§cCould not find 1 or more of configured item [§e§l" + itemType.get(player) + "§c] in your inventory.");
			} else {
				hopperBlock.setStack(4, itemStack);
			}
		}
	}

	private static boolean alreadyHasItemInIt(PlayerEntity player, BlockPos hopperPos, int slot) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		BlockEntity hopperEntity = world.getBlockEntity(hopperPos);
		//test

		if (hopperEntity instanceof HopperBlockEntity) {
			HopperBlockEntity hopper = (HopperBlockEntity) hopperEntity;

			if (!hopper.getStack(slot).isEmpty()) {
				sendMessage(player, "§cFailed to insert items into one or more slots! Is it already full?");
			}
			return hopper.getStack(slot).isEmpty();
		}

		return true;
	}

	private static void sendMessage(PlayerEntity player, String message) {
		player.sendMessage(new LiteralText(message), true);
	}

	private static Item translationKeyToItem(String key) {
		return Registry.ITEM.get(new Identifier(key));
	}
}