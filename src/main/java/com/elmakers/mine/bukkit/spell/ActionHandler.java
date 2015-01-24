package com.elmakers.mine.bukkit.spell;

import com.elmakers.mine.bukkit.api.action.GeneralAction;
import com.elmakers.mine.bukkit.api.action.BlockAction;
import com.elmakers.mine.bukkit.api.action.EntityAction;
import com.elmakers.mine.bukkit.api.action.SpellAction;
import com.elmakers.mine.bukkit.api.magic.Mage;
import com.elmakers.mine.bukkit.api.spell.MageSpell;
import com.elmakers.mine.bukkit.api.spell.Spell;
import com.elmakers.mine.bukkit.api.spell.SpellResult;
import com.elmakers.mine.bukkit.utility.ConfigurationUtils;
import com.elmakers.mine.bukkit.utility.Target;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ActionHandler
{
    private static final String ACTION_BUILTIN_CLASSPATH = "com.elmakers.mine.bukkit.action.builtin";

    private List<SpellAction> allActions = new ArrayList<SpellAction>();
    private List<GeneralAction> generalActions = new ArrayList<GeneralAction>();
    private List<BlockAction> blockActions = new ArrayList<BlockAction>();
    private List<EntityAction> entityActions = new ArrayList<EntityAction>();

    private final Spell spell;
    private boolean undoable = false;
    private boolean usesBrush = false;

    private ConfigurationSection parameters = null;

    public ActionHandler(Spell spell)
    {
        this.spell = spell;
    }

    public void setParameters(ConfigurationSection parameters) {
        this.parameters = parameters;
    }

    public void load(ConfigurationSection root, String key)
    {
        undoable = false;
        usesBrush = false;
        Collection<ConfigurationSection> actionNodes = ConfigurationUtils.getNodeList(root, key);

        if (actionNodes != null)
        {
            for (ConfigurationSection actionConfiguration : actionNodes)
            {
                if (actionConfiguration.contains("class"))
                {
                    String actionClassName = actionConfiguration.getString("class");
                    try
                    {
                        if (!actionClassName.contains("."))
                        {
                            actionClassName = ACTION_BUILTIN_CLASSPATH + "." + actionClassName;
                        }
                        Class<?> genericClass = Class.forName(actionClassName);
                        if (!BaseSpellAction.class.isAssignableFrom(genericClass)) {
                            throw new Exception("Must extend SpellAction");
                        }

                        @SuppressWarnings("unchecked")
                        Class<? extends BaseSpellAction> actionClass = (Class<? extends BaseSpellAction>)genericClass;
                        BaseSpellAction action = actionClass.newInstance();
                        actionConfiguration.set("class", null);
                        if (actionConfiguration.getKeys(false).size() == 0) {
                            actionConfiguration = null;
                        }
                        loadAction(action, actionConfiguration);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    public void loadAction(SpellAction action) {
        loadAction(action, new MemoryConfiguration());
    }

    public void loadAction(SpellAction action, ConfigurationSection parameters) {
        action.load(spell, parameters);
        allActions.add(action);
        usesBrush = usesBrush || action.usesBrush();
        undoable = undoable || action.isUndoable();
        if (action instanceof GeneralAction) {
            generalActions.add((GeneralAction)action);
        }
        if (action instanceof EntityAction) {
            entityActions.add((EntityAction)action);
        }
        if (action instanceof BlockAction) {
            blockActions.add((BlockAction)action);
        }
    }

    public SpellResult perform(ConfigurationSection parameters)
    {
        spell.target();
        Location targetLocation = spell.getTargetLocation();
        List<Entity> targetEntities = new ArrayList<Entity>();

        Entity targetedEntity = spell.getTargetEntity();
        int targetCount = parameters.getInt("target_count", 1);
        if (targetedEntity != null && targetCount != 0)
        {
            targetEntities.add(targetedEntity);
        }
        if (targetCount != 1 && spell instanceof TargetingSpell)
        {
            List<Target> entities = ((TargetingSpell)spell).getAllTargetEntities();
            if (targetCount < 0) {
                targetCount = entities.size();
            }
            // The first entry is the one we already added above, the primary target
            for (int i = 1; i < targetCount && i < entities.size(); i++)
            {
                targetEntities.add(entities.get(i).getEntity());
            }
        }
        return perform(parameters, targetLocation, targetEntities);
    }

    public SpellResult perform(ConfigurationSection parameters, Location targetLocation, Collection<Entity> targetEntities)
    {
        Entity sourceEntity = null;
        Location sourceLocation = spell.getLocation();
        if (spell instanceof MageSpell)
        {
            Mage mage = ((MageSpell)spell).getMage();
            sourceEntity = mage.getEntity();
        }

        return perform(parameters, sourceLocation, sourceEntity, targetLocation, targetEntities);
    }

    public SpellResult perform(Location targetLocation, Entity targetEntity)
    {
        Entity sourceEntity = null;
        Location sourceLocation = spell.getLocation();
        if (spell instanceof MageSpell)
        {
            Mage mage = ((MageSpell)spell).getMage();
            sourceEntity = mage.getEntity();
        }
        List<Entity> targetEntities = new ArrayList<Entity>();
        targetEntities.add(targetEntity);
        return perform(null, sourceLocation, sourceEntity, targetLocation, targetEntities);
    }

    public SpellResult perform(Location sourceLocation, Entity sourceEntity, Location targetLocation, Entity targetEntity)
    {
        List<Entity> targetEntities = new ArrayList<Entity>();
        targetEntities.add(targetEntity);
        return perform(null, sourceLocation, sourceEntity, targetLocation, targetEntities);
    }

   public SpellResult perform(ConfigurationSection parameters, Location sourceLocation, Entity sourceEntity, Location targetLocation, Collection<Entity> targetEntities)
    {
        SpellResult result = SpellResult.NO_ACTION;
        if (this.parameters != null)
        {
            parameters = ConfigurationUtils.addConfigurations(this.parameters, parameters, true);
        }

        for (SpellAction action : allActions)
        {
            action.prepare(parameters);
        }

        for (GeneralAction generalAction : generalActions)
        {
            SpellResult actionResult = generalAction.perform(generalAction.getParameters(parameters));
            result = result.min(actionResult);
        }

        if (targetLocation != null)
        {
            for (BlockAction action : blockActions)
            {
                SpellResult actionResult = action.perform(action.getParameters(parameters), targetLocation.getBlock());
                result = result.min(actionResult);
            }
        }

        if (targetEntities.size() == 0 && entityActions.size() > 0)
        {
            result = SpellResult.NO_TARGET.min(result);
        }

        for (Entity entity : targetEntities)
        {
            for (EntityAction action : entityActions)
            {
                SpellResult actionResult = action.perform(action.getParameters(parameters), entity);
                if (actionResult.ordinal() < result.ordinal())
                {
                    result = actionResult;
                }
            }
        }

        for (SpellAction action : allActions)
        {
            action.finish(parameters);
        }

        return result;
    }

    public boolean isUndoable()
    {
        return undoable;
    }

    public boolean usesBrush()
    {
        return usesBrush;
    }

    public void getParameterNames(Collection<String> parameters)
    {
        for (SpellAction action : allActions)
        {
            action.getParameterNames(parameters);
        }
    }

    public void getParameterOptions(Collection<String> examples, String parameterKey)
    {
        for (SpellAction action : allActions)
        {
            action.getParameterOptions(examples, parameterKey);
        }
    }

    public String transformMessage(String message)
    {
        for (SpellAction action : allActions)
        {
            message = action.transformMessage(message);
        }
        return message;
    }
}
