package com.elmakers.mine.bukkit.action.builtin;

import com.elmakers.mine.bukkit.api.action.CastContext;
import com.elmakers.mine.bukkit.api.magic.Mage;
import com.elmakers.mine.bukkit.api.magic.MageController;
import com.elmakers.mine.bukkit.api.spell.Spell;
import com.elmakers.mine.bukkit.api.spell.SpellResult;
import com.elmakers.mine.bukkit.api.wand.Wand;
import com.elmakers.mine.bukkit.spell.BaseSpell;
import com.elmakers.mine.bukkit.utility.CompatibilityUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;

import java.util.Arrays;
import java.util.Collection;

public class MountArmorStandAction extends RideEntityAction
{
    private boolean armorStandInvisible;
    private boolean armorStandSmall;
    private boolean armorStandMarker;
    private boolean armorStandGravity;
    private boolean mountWand;
    private double armorStandPitch = 0;
    private CreatureSpawnEvent.SpawnReason armorStandSpawnReason = CreatureSpawnEvent.SpawnReason.CUSTOM;

    private ItemStack item;
    private int slotNumber;
    private ArmorStand armorStand;
    private boolean mountTarget = false;

    @Override
    public void reset(CastContext context)
    {
        super.reset(context);
        item = null;
        if (armorStand != null && !mountTarget) {
            armorStand.remove();
        }
        armorStand = null;
    }
    
    @Override
    public void prepare(CastContext context, ConfigurationSection parameters)
    {
        super.prepare(context, parameters);
        mountTarget = parameters.getBoolean("mount_target", false);
        armorStandInvisible = parameters.getBoolean("armor_stand_invisible", true);
        armorStandSmall = parameters.getBoolean("armor_stand_small", false);
        armorStandMarker = parameters.getBoolean("armor_stand_marker", true);
        armorStandGravity = parameters.getBoolean("armor_stand_gravity", true);
        armorStandPitch = parameters.getDouble("armor_stand_pitch", 0.0);
        mountWand = parameters.getBoolean("mount_wand", false);
        if (parameters.contains("armor_stand_reason")) {
            String reasonText = parameters.getString("armor_stand_reason").toUpperCase();
            try {
                armorStandSpawnReason = CreatureSpawnEvent.SpawnReason.valueOf(reasonText);
            } catch (Exception ex) {
                context.getMage().sendMessage("Unknown spawn reason: " + reasonText);
            }
        }
    }

    @Override
    protected Entity remount(CastContext context) {
        if (mountTarget) {
            return null;
        }

        // This seems to happen occasionally... guess we'll work around it for now.
        if (armorStand != null) {
            armorStand.remove();
        }
        if (!mountNewArmorStand(context)) {
            return null;
        }

        org.bukkit.Bukkit.getLogger().info("   REMOUNTED!");

        return armorStand;
    }
    
    protected void adjustHeading(CastContext context) {
        super.adjustHeading(context);

        Location targetLocation = context.getEntity().getLocation();
        float targetPitch = targetLocation.getPitch();
        if (armorStandPitch != 0) {
            armorStand.setHeadPose(new EulerAngle(armorStandPitch * targetPitch / 180 * Math.PI, 0, 0));
        }
    }
    
    protected SpellResult mount(CastContext context) {
        Mage mage = context.getMage();
        Player player = mage.getPlayer();
        if (player == null && mountWand)
        {
            return SpellResult.PLAYER_REQUIRED;
        }

        item = null;
        if (mountWand) {
            Wand wand = context.getWand();

            if (wand == null) {
                return SpellResult.NO_TARGET;
            }
            wand.deactivate();

            item = wand.getItem();
            if (item == null || item.getType() == Material.AIR)
            {
                return SpellResult.FAIL;
            }
            slotNumber = wand.getHeldSlot();
        }

        if (!mountTarget && !mountNewArmorStand(context)) {
            return SpellResult.FAIL;
        }
        if (mountWand) {
            player.getInventory().setItem(slotNumber, new ItemStack(Material.AIR));
        }
        
        return super.mount(context);
	}
	
	protected boolean mountNewArmorStand(CastContext context) {
        Mage mage = context.getMage();
        Entity entity = context.getEntity();
        armorStand = CompatibilityUtils.spawnArmorStand(mage.getLocation());

        if (armorStandInvisible) {
            CompatibilityUtils.setInvisible(armorStand, true);
        }
        if (armorStandMarker) {
            armorStand.setMarker(true);
        }
        if (!armorStandGravity) {
            armorStand.setGravity(false);
        }
        CompatibilityUtils.setDisabledSlots(armorStand, 2039552);
        if (armorStandSmall) {
            armorStand.setSmall(true);
        }

        MageController controller = context.getController();
        controller.setForceSpawn(true);
        try {
            CompatibilityUtils.addToWorld(entity.getWorld(), armorStand, armorStandSpawnReason);
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
        controller.setForceSpawn(false);

        if (mountWand) {
            armorStand.setHelmet(item);
        }
        context.setTargetEntity(armorStand);

        return true;
    }
	
	@Override
    public void finish(CastContext context) {
        if (!mountTarget && armorStand != null) {
            armorStand.remove();
        }
        armorStand = null;

        super.finish(context);

        Mage mage = context.getMage();
        Player player = mage.getPlayer();
        if (player == null || item == null) return;

        ItemStack currentItem = player.getInventory().getItem(slotNumber);
        if (currentItem != null) {
            context.getMage().giveItem(item);
        } else {
            player.getInventory().setItem(slotNumber, item);
        }
        mage.checkWand();
        
        item = null;
    }

    @Override
    public void getParameterNames(Spell spell, Collection<String> parameters)
    {
        super.getParameterNames(spell, parameters);
        parameters.add("armor_stand_invisible");
        parameters.add("armor_stand_small");
        parameters.add("armor_stand_marker");
        parameters.add("armor_stand_gravity");
        parameters.add("armor_stand_reason");
        parameters.add("armor_stand_pitch");
        parameters.add("mount_wand");
        parameters.add("mount_target");
    }

    @Override
    public void getParameterOptions(Spell spell, String parameterKey, Collection<String> examples)
    {
        if (parameterKey.equals("armor_stand_invisible")
                || parameterKey.equals("armor_stand_marker") 
                || parameterKey.equals("armor_stand_small")
                || parameterKey.equals("armor_stand_gravity")
                || parameterKey.equals("mount_target")
                || parameterKey.equals("mount_wand")) {
            examples.addAll(Arrays.asList(BaseSpell.EXAMPLE_BOOLEANS));
        } else if (parameterKey.equals("armor_stand_pitch")) {
            examples.addAll(Arrays.asList(BaseSpell.EXAMPLE_VECTOR_COMPONENTS));
        } else if (parameterKey.equals("armor_stand_reason")) {
            for (CreatureSpawnEvent.SpawnReason reason : CreatureSpawnEvent.SpawnReason.values()) {
                examples.add(reason.name().toLowerCase());
            }
        } else {
            super.getParameterOptions(spell, parameterKey, examples);
        }
    }
}
