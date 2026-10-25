package de.raindancer.modules.worldutils.listener;

import de.raindancer.modules.worldutils.WorldUtilsSettings;
import io.papermc.paper.event.world.border.WorldBorderBoundsChangeEvent;
import io.papermc.paper.event.world.border.WorldBorderCenterChangeEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.event.world.WorldLoadEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DimensionBorderListenerTest {

    private final Map<String, World> worlds = new HashMap<>();
    private World overworld;
    private WorldBorder overworldBorder;
    private WorldBorder netherBorder;
    private WorldBorder endBorder;

    private World world(String name, World.Environment environment, WorldBorder border) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        when(world.getEnvironment()).thenReturn(environment);
        when(world.getWorldBorder()).thenReturn(border);
        worlds.put(name, world);
        return world;
    }

    private DimensionBorderListener listener(WorldUtilsSettings settings) {
        return new DimensionBorderListener(() -> settings, worlds::get, Runnable::run);
    }

    @BeforeEach
    void setUp() {
        overworldBorder = mock(WorldBorder.class);
        when(overworldBorder.getSize()).thenReturn(10_000.0);
        when(overworldBorder.getCenter()).thenReturn(new Location(null, 100, 64, -200));
        when(overworldBorder.getDamageBuffer()).thenReturn(5.0);
        when(overworldBorder.getDamageAmount()).thenReturn(0.2);
        when(overworldBorder.getWarningDistance()).thenReturn(5);
        when(overworldBorder.getWarningTimeTicks()).thenReturn(300);
        netherBorder = mock(WorldBorder.class);
        endBorder = mock(WorldBorder.class);
        overworld = world("world", World.Environment.NORMAL, overworldBorder);
        world("world_nether", World.Environment.NETHER, netherBorder);
        world("world_the_end", World.Environment.THE_END, endBorder);
    }

    @Test
    @DisplayName("on start the Nether and the End get the overworld's border — same size, centre, damage, warning")
    void matchedOnStart() {
        listener(WorldUtilsSettings.DEFAULTS).matchAll(List.copyOf(worlds.values()));

        for (WorldBorder border : List.of(netherBorder, endBorder)) {
            verify(border).setCenter(100, -200);
            verify(border).setSize(10_000.0);
            verify(border).setDamageBuffer(5.0);
            verify(border).setDamageAmount(0.2);
            verify(border).setWarningDistance(5);
            verify(border).setWarningTimeTicks(300);
        }
    }

    @Test
    @DisplayName("a Nether loaded later takes its overworld's border")
    void matchedWhenTheNetherLoads() {
        listener(WorldUtilsSettings.DEFAULTS).onLoad(new WorldLoadEvent(worlds.get("world_nether")));

        verify(netherBorder).setSize(10_000.0);
        verify(endBorder, never()).setSize(anyDouble());
    }

    @Test
    @DisplayName("the overworld's border shrinking over time shrinks the others over the same time")
    void followsAResize() {
        listener(WorldUtilsSettings.DEFAULTS).onResize(new WorldBorderBoundsChangeEvent(overworld, overworldBorder,
                WorldBorderBoundsChangeEvent.Type.STARTED_MOVE, 10_000.0, 2_000.0, 1_200));

        verify(netherBorder).changeSize(2_000.0, 1_200);
        verify(endBorder).changeSize(2_000.0, 1_200);
    }

    @Test
    @DisplayName("an instant resize is instant in the others too")
    void followsAnInstantResize() {
        listener(WorldUtilsSettings.DEFAULTS).onResize(new WorldBorderBoundsChangeEvent(overworld, overworldBorder,
                WorldBorderBoundsChangeEvent.Type.INSTANT_MOVE, 10_000.0, 4_000.0, 0));

        verify(netherBorder).setSize(4_000.0);
        verify(endBorder).setSize(4_000.0);
    }

    @Test
    @DisplayName("moving the overworld's centre moves the others'")
    void followsTheCentre() {
        listener(WorldUtilsSettings.DEFAULTS).onCentre(new WorldBorderCenterChangeEvent(overworld, overworldBorder,
                new Location(overworld, 100, 0, -200), new Location(overworld, 7, 0, 9)));

        verify(netherBorder).setCenter(7, 9);
        verify(endBorder).setCenter(7, 9);
    }

    @Test
    @DisplayName("a change to the Nether's own border is not pushed anywhere")
    void netherIsNeverTheSource() {
        listener(WorldUtilsSettings.DEFAULTS).onResize(new WorldBorderBoundsChangeEvent(worlds.get("world_nether"),
                netherBorder, WorldBorderBoundsChangeEvent.Type.INSTANT_MOVE, 10_000.0, 50.0, 0));

        verify(overworldBorder, never()).setSize(anyDouble());
        verify(endBorder, never()).setSize(anyDouble());
    }

    @Test
    @DisplayName("an overworld without a nether or end of its own is left alone")
    void noFamily() {
        worlds.remove("world_nether");
        worlds.remove("world_the_end");

        listener(WorldUtilsSettings.DEFAULTS).matchAll(List.of(overworld));

        verify(netherBorder, never()).setSize(anyDouble());
    }

    @Test
    @DisplayName("switched off, nothing is touched")
    void off() {
        DimensionBorderListener off = listener(WorldUtilsSettings.DEFAULTS.withMatchDimensionBorders(false));

        off.matchAll(List.copyOf(worlds.values()));
        off.onResize(new WorldBorderBoundsChangeEvent(overworld, overworldBorder,
                WorldBorderBoundsChangeEvent.Type.INSTANT_MOVE, 10_000.0, 4_000.0, 0));

        verify(netherBorder, never()).setSize(anyDouble());
        verify(endBorder, never()).setSize(anyDouble());
    }
}
