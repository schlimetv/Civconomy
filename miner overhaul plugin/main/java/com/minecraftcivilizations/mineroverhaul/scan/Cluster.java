package com.minecraftcivilizations.mineroverhaul.scan;

import org.bukkit.Material;

import java.util.Arrays;

/**
 * Sealed ore cluster, ready to persist. Outline coordinates are packed int32:
 *   bits  0-8  : x within region (0..511)
 *   bits  9-17 : z within region (0..511)
 *   bits 18-29 : (y + 64) (0..383)
 */
public class Cluster {

    public static final int REGION_SIZE = 512;
    public static final int Y_OFFSET = 64;

    private final Material primaryMaterial;
    private final int totalSize;
    private final int[] outlinePacked;
    private final int regionX;
    private final int regionZ;

    private int cachedMinX, cachedMaxX, cachedMinZ, cachedMaxZ;
    private boolean bboxComputed = false;

    public Cluster(Material primaryMaterial, int totalSize, int[] outlinePacked, int regionX, int regionZ) {
        this.primaryMaterial = primaryMaterial;
        this.totalSize = totalSize;
        this.outlinePacked = outlinePacked;
        this.regionX = regionX;
        this.regionZ = regionZ;
    }

    public Material primaryMaterial() { return primaryMaterial; }
    public int totalSize() { return totalSize; }
    public int[] outlinePacked() { return outlinePacked; }
    public int regionX() { return regionX; }
    public int regionZ() { return regionZ; }

    public static int pack(int xWithinRegion, int yAbsolute, int zWithinRegion) {
        int yPacked = yAbsolute + Y_OFFSET;
        return (xWithinRegion & 0x1FF)
                | ((zWithinRegion & 0x1FF) << 9)
                | ((yPacked & 0xFFF) << 18);
    }

    public static int unpackX(int packed) { return packed & 0x1FF; }
    public static int unpackZ(int packed) { return (packed >> 9) & 0x1FF; }
    public static int unpackY(int packed) { return ((packed >> 18) & 0xFFF) - Y_OFFSET; }

    /** Min squared 3D distance from any outline block to (px, py, pz) in world coordinates. */
    public double minSquaredDistanceTo(double px, double py, double pz) {
        double minSq = Double.MAX_VALUE;
        int rxOrigin = regionX * REGION_SIZE;
        int rzOrigin = regionZ * REGION_SIZE;
        for (int packed : outlinePacked) {
            double dx = (rxOrigin + unpackX(packed)) + 0.5 - px;
            double dy = unpackY(packed) + 0.5 - py;
            double dz = (rzOrigin + unpackZ(packed)) + 0.5 - pz;
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq < minSq) minSq = sq;
        }
        return minSq;
    }

    /** Find the world-space coordinate of the outline block closest to (px,py,pz). */
    public int[] closestOutlineWorldCoord(double px, double py, double pz) {
        double minSq = Double.MAX_VALUE;
        int bestPacked = outlinePacked.length > 0 ? outlinePacked[0] : 0;
        int rxOrigin = regionX * REGION_SIZE;
        int rzOrigin = regionZ * REGION_SIZE;
        for (int packed : outlinePacked) {
            double dx = (rxOrigin + unpackX(packed)) + 0.5 - px;
            double dy = unpackY(packed) + 0.5 - py;
            double dz = (rzOrigin + unpackZ(packed)) + 0.5 - pz;
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq < minSq) {
                minSq = sq;
                bestPacked = packed;
            }
        }
        return new int[]{
                rxOrigin + unpackX(bestPacked),
                unpackY(bestPacked),
                rzOrigin + unpackZ(bestPacked)
        };
    }

    public void computeBbox() {
        if (bboxComputed) return;
        if (outlinePacked.length == 0) {
            cachedMinX = cachedMaxX = cachedMinZ = cachedMaxZ = 0;
            bboxComputed = true;
            return;
        }
        int rxOrigin = regionX * REGION_SIZE;
        int rzOrigin = regionZ * REGION_SIZE;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int packed : outlinePacked) {
            int wx = rxOrigin + unpackX(packed);
            int wz = rzOrigin + unpackZ(packed);
            if (wx < minX) minX = wx;
            if (wx > maxX) maxX = wx;
            if (wz < minZ) minZ = wz;
            if (wz > maxZ) maxZ = wz;
        }
        cachedMinX = minX; cachedMaxX = maxX;
        cachedMinZ = minZ; cachedMaxZ = maxZ;
        bboxComputed = true;
    }

    public boolean bboxIntersectsXz(int qMinX, int qMinZ, int qMaxX, int qMaxZ) {
        computeBbox();
        return cachedMaxX >= qMinX && cachedMinX <= qMaxX
            && cachedMaxZ >= qMinZ && cachedMinZ <= qMaxZ;
    }

    @Override
    public String toString() {
        return "Cluster{mat=" + primaryMaterial + ", size=" + totalSize
                + ", outline=" + outlinePacked.length + " rx=" + regionX + " rz=" + regionZ + "}";
    }
}
