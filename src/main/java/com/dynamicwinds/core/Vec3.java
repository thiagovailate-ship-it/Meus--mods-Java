package com.dynamicwinds.core;

/**
 * Immutable 3D vector used by the pure-physics core. No Minecraft dependency.
 */
public final class Vec3 {
    public final double x;
    public final double y;
    public final double z;

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }
    public Vec3 sub(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); }
    public Vec3 mul(double s) { return new Vec3(x * s, y * s, z * s); }
    public double dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }
    public double length() { return Math.sqrt(x * x + y * y + z * z); }
    public Vec3 normalize() {
        double l = length();
        if (l == 0.0) return ZERO;
        return new Vec3(x / l, y / l, z / l);
    }

    @Override
    public String toString() { return "Vec3(" + x + "," + y + "," + z + ")"; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vec3)) return false;
        Vec3 v = (Vec3) o;
        return Double.compare(v.x, x) == 0 && Double.compare(v.y, y) == 0 && Double.compare(v.z, z) == 0;
    }

    @Override
    public int hashCode() {
        long bits = Double.doubleToLongBits(x) * 31 + Double.doubleToLongBits(y);
        bits = bits * 31 + Double.doubleToLongBits(z);
        return (int) (bits ^ (bits >>> 32));
    }
}
