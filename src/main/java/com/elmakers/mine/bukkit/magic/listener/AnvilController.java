package com.elmakers.mine.bukkit.magic.listener;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.elmakers.mine.bukkit.api.magic.Mage;
import com.elmakers.mine.bukkit.magic.MagicController;
import com.elmakers.mine.bukkit.wand.Wand;

public class AnvilController implements Listener {
	private final MagicController controller;
	private boolean combiningEnabled = false;
	private boolean organizingEnabled = false;
    private boolean clearDescriptionOnRename = false;

	public AnvilController(MagicController controller) {
		this.controller = controller;
	}
	
	public void load(ConfigurationSection properties) {
		combiningEnabled = properties.getBoolean("enable_combining", combiningEnabled);
		organizingEnabled = properties.getBoolean("enable_organizing", organizingEnabled);
        clearDescriptionOnRename = properties.getBoolean("anvil_rename_clears_description", clearDescriptionOnRename);
    }
	
	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		if (event.isCancelled()) return;
		if (!(event.getWhoClicked() instanceof Player)) return;
	
		InventoryType inventoryType = event.getInventory().getType();
		SlotType slotType = event.getSlotType();
		Player player = (Player)event.getWhoClicked();
		Mage mage = controller.getMage(player);
		
		if (inventoryType == InventoryType.ANVIL)
		{
			ItemStack cursor = event.getCursor();
			ItemStack current = event.getCurrentItem();
			Inventory anvilInventory = event.getInventory();
			InventoryAction action = event.getAction();
			ItemStack firstItem = anvilInventory.getItem(0);
			ItemStack secondItem = anvilInventory.getItem(1);
			
			//org.bukkit.Bukkit.getLogger().info("cursor? " + Wand.isWand(cursor) + " current?" + Wand.isWand(current) + " action: " + action + " slot: " + slotType + " first: " + firstItem + ", second: " + secondItem);
			
			// Handle direct movement
			if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY)
			{
				if (!Wand.isWand(current)) return;
				// Moving from anvil back to inventory
				if (slotType == SlotType.CRAFTING) {
					Wand wand = new Wand(controller, current);
					wand.updateName(true);
				} else if (slotType == SlotType.RESULT) {
					// Don't allow combining
					if (!combiningEnabled) {
						if (firstItem != null && secondItem != null) {
							event.setCancelled(true);
							return;
						}
					}
					// Taking from result slot
					ItemMeta meta = current.getItemMeta();
					String newName = meta.getDisplayName();

					Wand wand = new Wand(controller, current);
					if (!wand.canUse(player)) {
						event.setCancelled(true);
						mage.sendMessage(controller.getMessages().get("wand.bound").replace("$name", wand.getOwner()));
						return;
					}
					wand.setName(newName);
					if (organizingEnabled) {
						wand.organizeInventory(controller.getMage(player));
					}
					wand.tryToOwn(player);
				} else {
					// Moving from inventory to anvil
					Wand wand = new Wand(controller, current);
					wand.updateName(false);
				}
				return;
			}
			
			// Set/unset active names when starting to craft
			if (slotType == SlotType.CRAFTING) {
				// Putting a wand into the anvil's crafting slot
				if (Wand.isWand(cursor)) {
					Wand wand = new Wand(controller, cursor);
					wand.updateName(false);
				} 
				// Taking a wand out of the anvil's crafting slot
				if (Wand.isWand(current)) {
					Wand wand = new Wand(controller, current);
                    if (clearDescriptionOnRename) {
                        wand.setDescription("");
                    }
					wand.updateName(true);
					if (event.getWhoClicked() instanceof Player) {
						wand.tryToOwn((Player)event.getWhoClicked());
					}
				}
				
				return;
			}
			
			// Rename wand when taking from result slot
			if (slotType == SlotType.RESULT && Wand.isWand(current)) {
				if (!combiningEnabled) {
					if (firstItem != null && secondItem != null) {
						event.setCancelled(true);
						return;
					}
				}
				ItemMeta meta = current.getItemMeta();
				String newName = meta.getDisplayName();
				
				Wand wand = new Wand(controller, current);
				if (!wand.canUse(player)) {
					event.setCancelled(true);
					mage.sendMessage(controller.getMessages().get("wand.bound").replace("$name", wand.getOwner()));
					return;
				}
				wand.setName(newName);
				if (organizingEnabled) {
					wand.organizeInventory(controller.getMage(player));
				}
				wand.tryToOwn(player);
				return;
			}

			if (combiningEnabled && slotType == SlotType.RESULT) {
				// Check for wands in both slots
				// ...... arg. So close.. and yet, not.
				// I guess I need to wait for the long-awaited anvil API?
				if (Wand.isWand(firstItem) && Wand.isWand(secondItem)) 
				{
					Wand firstWand = new Wand(controller, firstItem);
					Wand secondWand = new Wand(controller, secondItem);
					if (!firstWand.isModifiable() || !secondWand.isModifiable()) {
						mage.sendMessage("One of your wands can not be combined");
						return;
					}
					if (!firstWand.canUse(player) || !secondWand.canUse(player)) {
						mage.sendMessage("One of those wands is not bound to you");
						return;
					}
					
					if (!firstWand.add(secondWand)) {
						mage.sendMessage("These wands can not be combined with each other");
						return;
					}
					anvilInventory.setItem(0,  null);
					anvilInventory.setItem(1,  null);
					cursor.setType(Material.AIR);

					if (organizingEnabled) {
						firstWand.organizeInventory(mage);
					}
					firstWand.tryToOwn(player);
					player.getInventory().addItem(firstWand.getItem());
					mage.sendMessage("Your wands have been combined!");
					
					// This seems to work in the debugger, but.. doesn't do anything.
					// InventoryUtils.setInventoryResults(anvilInventory, newWand.getItem());
				} else if (organizingEnabled && Wand.isWand(firstItem)) {
					Wand firstWand = new Wand(controller, firstItem);
					// TODO: Can't get the anvil's text from here.
					anvilInventory.setItem(0,  null);
					anvilInventory.setItem(1,  null);
					cursor.setType(Material.AIR);
					firstWand.organizeInventory(mage);
					firstWand.tryToOwn(player);
					player.getInventory().addItem(firstWand.getItem());
					mage.sendMessage("Your wand has been organized!");
				}
				
				return;
			}
		}
	}
	
	public boolean isCombiningEnabled()
	{
		return combiningEnabled;
	}
	
	public boolean isOrganizingEnabled()
	{
		return organizingEnabled;
	}
}
