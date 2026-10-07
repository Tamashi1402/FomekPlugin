package __RENDERAPI_PACKAGE__;

/**
 * Vec3 helper — simple x/y/z holder for position, rotation, and scale inputs.
 * Used by the vec3 block system (pos, rot, scale) and by BEWRL render methods.
 */
public class BEWRLVars {

    public static class Vec3 {
        public final float x, y, z;
        
        public Vec3(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        public static Vec3 of(float x, float y, float z) {
            return new Vec3(x, y, z);
        }
        
        public static Vec3 zero() { return new Vec3(0, 0, 0); }
        public static Vec3 one() { return new Vec3(1, 1, 1); }
        
        @Override
        public String toString() { return "(" + x + ", " + y + ", " + z + ")"; }
    }
}
