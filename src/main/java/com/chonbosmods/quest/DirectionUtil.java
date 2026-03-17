package com.chonbosmods.quest;

public class DirectionUtil {

    private static final String[] DIRECTIONS = {
        "north", "north-east", "east", "south-east",
        "south", "south-west", "west", "north-west"
    };

    public static String computeHint(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        int roundedDistance = ((int) (distance / 50)) * 50;
        if (roundedDistance < 50) roundedDistance = 50;

        double angle = Math.toDegrees(Math.atan2(dz, dx));
        angle = (90 - angle + 360) % 360;

        int index = (int) ((angle + 22.5) / 45) % 8;
        return DIRECTIONS[index] + ", about " + roundedDistance + " blocks";
    }

    public static String computeDirection(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        angle = (90 - angle + 360) % 360;
        int index = (int) ((angle + 22.5) / 45) % 8;
        return DIRECTIONS[index];
    }
}
