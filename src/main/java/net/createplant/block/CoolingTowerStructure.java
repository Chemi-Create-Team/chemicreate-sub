package net.createplant.block;

import java.util.ArrayList;
import java.util.List;
import net.createplant.registry.CEPlantSystems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class CoolingTowerStructure {
    public static final int RADIUS = 12, HEIGHT = 36, SHELL_START = 4, THROAT_Y = 26;
    public static final double THROAT_RADIUS = 7, TOP_RADIUS = 8.5;
    public static final List<Integer> PORT_X = List.of(-9, -7, -5, -3, 3, 5, 7, 9);
    public record Size(int radius, int height) {
        public int width() { return radius * 2 + 1; }
        public int throatY() { return (int) Math.round(height * 26d / 36); }
        public double throatRadius() { return radius * 7d / 12; }
        public double topRadius() { return radius * 8.5 / 12; }
    }
    public static final List<Size> SIZES = List.of(new Size(8, 24), new Size(RADIUS, HEIGHT), new Size(16, 48));
    public static final List<Integer> WIDTHS = SIZES.stream().map(Size::width).toList();
    public static final int DEFAULT_SIZE = 1;
    public enum Role { CLEAR, BASIN, SHELL, FILL, DISTRIBUTOR, CONTROLLER, HOT, COLD, MAKEUP }
    public record Cell(BlockPos offset, Role role) {
        public Block block() {
            return switch (role) {
                case CLEAR -> Blocks.AIR;
                case BASIN -> CEPlantSystems.TOWER_BASIN.get();
                case SHELL -> CEPlantSystems.TOWER_SHELL.get();
                case FILL -> CEPlantSystems.TOWER_FILL.get();
                case DISTRIBUTOR -> CEPlantSystems.TOWER_DISTRIBUTOR.get();
                case CONTROLLER -> CEPlantSystems.COOLING_TOWER.get();
                case HOT -> CEPlantSystems.TOWER_HOT_PORT.get();
                case COLD -> CEPlantSystems.TOWER_COLD_PORT.get();
                case MAKEUP -> CEPlantSystems.TOWER_MAKEUP_PORT.get();
            };
        }
    }
    private static final List<List<Cell>> CELLS = SIZES.stream().map(CoolingTowerStructure::build).toList();
    private CoolingTowerStructure() { }
    public static List<Cell> cells() { return cells(DEFAULT_SIZE); }
    public static List<Cell> cells(int index) { return CELLS.get(index); }
    public static BlockPos centre(BlockPos controller) { return centre(controller, DEFAULT_SIZE); }
    public static BlockPos centre(BlockPos controller, int index) { return controller.offset(0, -1, SIZES.get(index).radius()); }
    public static double radius(int y) {
        return radius(y, SIZES.get(DEFAULT_SIZE));
    }
    public static double radius(int y, Size size) {
        double dy = y - size.throatY();
        double lowerHeight = size.throatY() - SHELL_START, upperHeight = size.height() - 1 - size.throatY();
        double slope = y <= size.throatY() ? (size.radius() * size.radius() - size.throatRadius() * size.throatRadius()) / (lowerHeight * lowerHeight)
            : (size.topRadius() * size.topRadius() - size.throatRadius() * size.throatRadius()) / (upperHeight * upperHeight);
        return Math.sqrt(size.throatRadius() * size.throatRadius() + slope * dy * dy);
    }
    private static boolean support(int x, int y, int z, Size size) {
        double radius = size.radius() - 1 + (y - 2) / 2d, spread = (SHELL_START - y) * .6;
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6, cx = Math.cos(a) * radius, cz = Math.sin(a) * radius;
            for (int sign : new int[] {-1, 1}) if (Math.hypot(x - cx + Math.sin(a) * spread * sign, z - cz - Math.cos(a) * spread * sign) < .75) return true;
        }
        return false;
    }
    private static List<Cell> build(Size size) {
        List<Cell> cells = new ArrayList<>();
        for (int y = 0; y < size.height(); y++) {
            double radius = y < SHELL_START ? size.radius() : radius(y, size);
            int extent = (int) Math.ceil(radius);
            for (int x = -extent; x <= extent; x++) for (int z = -extent; z <= extent; z++) {
                double distance = Math.hypot(x, z);
                if (distance > radius + .35) continue;
                Role role = Role.CLEAR;
                if (y == 0 || y == 1 && distance >= size.radius() - 1) role = Role.BASIN;
                else if (y >= SHELL_START && distance >= radius - 1) role = Role.SHELL;
                else if (y > 1 && y < SHELL_START && distance >= size.radius() - 2 && support(x, y, z, size)) role = Role.SHELL;
                else if (y >= 3 && y <= 5 && distance < size.radius() - 2) role = Role.FILL;
                else if (y == 6 && distance < size.radius() - 2 && (Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0)) role = Role.DISTRIBUTOR;
                if (y == 1 && x == 0 && z == -size.radius()) role = Role.CONTROLLER;
                if (y == 1 && Math.abs(x) >= 3 && Math.abs(x) <= size.radius() - 3 && (x & 1) != 0
                    && Math.abs(z) == (int) Math.floor(Math.sqrt((size.radius() + .35) * (size.radius() + .35) - x * x))) role = z < 0 ? Role.HOT : Role.COLD;
                if (y == 1 && x == size.radius() && z == 0) role = Role.MAKEUP;
                cells.add(new Cell(new BlockPos(x, y, z), role));
            }
        }
        return List.copyOf(cells);
    }
}
