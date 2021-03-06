package com.elmakers.mine.bukkit.magic.command;

import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.elmakers.mine.bukkit.automata.Automaton;
import com.elmakers.mine.bukkit.magic.MagicController;

public class AutomatonSelectionManager extends SelectionManager<Automaton> {
    public AutomatonSelectionManager(MagicController controller) {
        super(controller);
    }

    @Override
    @Nonnull
    public Collection<Automaton> getAll() {
        return controller.getAutomata();
    }

    @Override
    @Nonnull
    protected String getTypeNamePlural() {
        return "automata";
    }

    @Override
    protected void showListItem(CommandSender sender, Automaton automaton, ListType listType) {
        String effectsKey;
        switch (listType) {
            case TARGET:
                effectsKey = "blocktarget";
                break;
            case SELECTED:
                effectsKey = "blockselected";
                break;
            case INACTIVE:
                return;
            default:
                effectsKey = "blockitem";
                break;
        }
        playEffects(sender, automaton, effectsKey);
    }

    @Override
    @Nullable
    protected Automaton getTarget(CommandSender sender, List<Automaton> sorted) {
        return sorted.isEmpty() ? null : sorted.get(0);
    }

    public void playEffects(CommandSender sender, Automaton automaton, String effectsKey) {
        int maxRangeSquared = 64 * 64;
        if (sender instanceof Player) {
            Location location = ((Player)sender).getLocation();
            Location automatonLocation = automaton.getLocation();
            if (location.getWorld().equals(automatonLocation.getWorld())) {
                double distance = location.distanceSquared(automatonLocation);
                if (distance < maxRangeSquared) {
                    controller.playEffects(effectsKey, location, automatonLocation);
                }
            }
        }
    }
}
