package de.raindancer.modules.worldutils.listener;

import de.raindancer.core.world.manage.WorldFamily;
import de.raindancer.modules.worldutils.WorldUtilsSettings;
import io.papermc.paper.event.world.border.WorldBorderBoundsChangeEvent;
import io.papermc.paper.event.world.border.WorldBorderCenterChangeEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Gives every overworld's {@code _nether} and {@code _the_end} the overworld's border, and keeps it so.
 *
 * <p>Paper keeps one border per world, so {@code /worldborder} in the overworld leaves the other two
 * dimensions at sixty million. Only an overworld is ever the source: a change in the Nether is not
 * pushed anywhere, which is also why copying into it cannot echo back.
 *
 * <p>Same size, not size divided by eight — the Nether's border is as wide in its own blocks as the
 * overworld's, which is what an owner asking for "the same border" means.
 */
public final class DimensionBorderListener implements IWorldUtilsListener {

    private final Supplier<WorldUtilsSettings> settings;
    private final Function<String, World> lookup;
    private final Consumer<Runnable> global;

    /**
     * @param global runs a task on the global region — another world's border is not this region's to
     *               touch on Folia
     */
    public DimensionBorderListener(Supplier<WorldUtilsSettings> settings, Function<String, World> lookup,
                                   Consumer<Runnable> global) {
        this.settings = settings;
        this.lookup = lookup;
        this.global = global;
    }

    /** Matches every loaded overworld's dimensions once — on start, and after the setting is turned on. */
    public void matchAll(Collection<World> loaded) {
        if (!on()) {
            return;
        }
        List<World> overworlds = loaded.stream()
                .filter(world -> world.getEnvironment() == World.Environment.NORMAL)
                .toList();
        global.accept(() -> overworlds.forEach(this::copyFrom));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoad(WorldLoadEvent event) {
        if (!on()) {
            return;
        }
        World world = event.getWorld();
        if (world.getEnvironment() == World.Environment.NORMAL) {
            global.accept(() -> copyFrom(world));
            return;
        }
        World overworld = lookup.apply(WorldFamily.of(world.getName()).overworld());
        if (overworld != null && overworld != world
                && overworld.getEnvironment() == World.Environment.NORMAL) {
            global.accept(() -> copy(overworld.getWorldBorder(), world.getWorldBorder()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResize(WorldBorderBoundsChangeEvent event) {
        List<WorldBorder> targets = targets(event.getWorld());
        if (targets.isEmpty()) {
            return;
        }
        double size = event.getNewSize();
        long ticks = event.getDurationTicks();
        global.accept(() -> targets.forEach(border -> {
            if (ticks > 0) {
                border.changeSize(size, ticks);
            } else {
                border.setSize(size);
            }
        }));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCentre(WorldBorderCenterChangeEvent event) {
        List<WorldBorder> targets = targets(event.getWorld());
        Location centre = event.getNewCenter();
        if (targets.isEmpty() || centre == null) {
            return;
        }
        double x = centre.getX();
        double z = centre.getZ();
        global.accept(() -> targets.forEach(border -> border.setCenter(x, z)));
    }

    private boolean on() {
        return settings.get().matchDimensionBorders();
    }

    /** The loaded nether and end borders that follow {@code world}; empty unless it is an overworld. */
    private List<WorldBorder> targets(World world) {
        if (!on() || world == null || world.getEnvironment() != World.Environment.NORMAL) {
            return List.of();
        }
        WorldFamily family = WorldFamily.of(world.getName());
        List<WorldBorder> borders = new ArrayList<>(2);
        for (String name : List.of(family.nether(), family.theEnd())) {
            World dimension = lookup.apply(name);
            if (dimension != null && dimension != world && dimension.getWorldBorder() != null) {
                borders.add(dimension.getWorldBorder());
            }
        }
        return borders;
    }

    private void copyFrom(World overworld) {
        WorldBorder source = overworld.getWorldBorder();
        targets(overworld).forEach(target -> copy(source, target));
    }

    private static void copy(WorldBorder source, WorldBorder target) {
        if (source == null || target == null || source.getCenter() == null) {
            return;
        }
        target.setCenter(source.getCenter().getX(), source.getCenter().getZ());
        target.setSize(source.getSize());
        target.setDamageBuffer(source.getDamageBuffer());
        target.setDamageAmount(source.getDamageAmount());
        target.setWarningDistance(source.getWarningDistance());
        target.setWarningTimeTicks(source.getWarningTimeTicks());
    }

    @Override
    public void forget(UUID player) {
        // Nothing remembered per player.
    }

    @Override
    public String describe() {
        return "giving the Nether and the End their overworld's border";
    }
}
