package dev.caecorthus.sparkwitch.roles.killer.saboteur;

final class SaboteurLightGeometry {
    private SaboteurLightGeometry() {
    }

    static boolean containsBlockCenter(
            double centerX,
            double centerY,
            double centerZ,
            int blockX,
            int blockY,
            int blockZ,
            double radius
    ) {
        double offsetX = blockX + 0.5D - centerX;
        double offsetY = blockY + 0.5D - centerY;
        double offsetZ = blockZ + 0.5D - centerZ;
        return offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ <= radius * radius;
    }
}
