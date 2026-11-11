import com.jumper.client.RangeDome;
import java.util.*;

/**
 * Offline check of the range dome's maths (client/RangeDome.java, plain Java): for radii 10, 64, 256 and 2048 and many
 * camera places, every ring and upright line within view is drawn, nothing far outside the view is, and how many lines
 * one frame draws and how long it takes. Run from the project folder:
 *   javac -d /tmp/domecheck src/main/java/com/jumper/client/RangeDome.java scripts/DomeCheck.java
 *   java -cp /tmp/domecheck DomeCheck
 */
public class DomeCheck {
    static List<float[]> lines = new ArrayList<>();
    public static void main(String[] a) {
        Random rnd = new Random(1);
        double[] radii = {64, 256, 2048, 10};
        int worst = 0;
        for (double r : radii) {
            for (int t = 0; t < 40; t++) {
                double view = (t % 3 == 0) ? 512 : 192;
                // camera somewhere from the middle to well outside
                double dist = r * (t % 10) / 6.0 + rnd.nextDouble() * 5;
                double th = Math.acos(2 * rnd.nextDouble() - 1), ph = rnd.nextDouble() * 2 * Math.PI;
                if (t % 4 == 0) th = Math.PI / 2 + (rnd.nextDouble() - 0.5) * 0.2; // near the anchor's height
                double camx = dist * Math.sin(th) * Math.cos(ph), camy = dist * Math.cos(th), camz = dist * Math.sin(th) * Math.sin(ph);
                lines.clear();
                int n = RangeDome.draw(-camx, -camy, -camz, r, view, 1, 2, (x1,y1,z1,x2,y2,z2,nx,ny,nz,c) -> lines.add(new float[]{x1,y1,z1,x2,y2,z2}));
                worst = Math.max(worst, n);
                // 1) drawn lines: at least one end within view + a strip's size
                for (float[] l : lines) {
                    double e1 = cyl(l[0],l[1],l[2]), e2 = cyl(l[3],l[4],l[5]);
                    if (Math.min(e1, e2) > view + 33) throw new RuntimeException("far line r=" + r + " " + Math.min(e1,e2));
                }
                // 2) sample the sphere near the camera on the grid's rings: every ring point within view - 2 must be
                //    within 0.6 blocks of a drawn line
                Map<Long, List<float[]>> hash = new HashMap<>();
                for (float[] l : lines) { double len = Math.sqrt(Math.pow(l[3]-l[0],2)+Math.pow(l[4]-l[1],2)+Math.pow(l[5]-l[2],2)); int steps = (int) Math.ceil(len / 0.5); for (int s = 0; s <= steps; s++) {
                    float f = s / (float) steps; float x = l[0]+(l[3]-l[0])*f, y = l[1]+(l[4]-l[1])*f, z = l[2]+(l[5]-l[2])*f;
                    hash.computeIfAbsent(key(x,y,z), k -> new ArrayList<>()).add(new float[]{x,y,z});
                }}
                int bands = Math.max(4, 2 * (int) Math.round(Math.PI * r / 16.0));
                int missing = 0, checked = 0;
                for (int i = 1; i < bands; i++) {
                    double theta = i * Math.PI / bands, rho = r * Math.sin(theta), y = r * Math.cos(theta);
                    int pts = (int) Math.max(16, 2 * Math.PI * rho / 2);
                    for (int j = 0; j < pts; j++) {
                        double lam = 2 * Math.PI * j / pts;
                        double x = rho * Math.cos(lam) - camx, yy = y - camy, z = rho * Math.sin(lam) - camz;
                        if (cyl(x,yy,z) > view - 2) continue;
                        checked++;
                        if (!near(hash, x, yy, z, 0.6)) missing++;
                    }
                }
                // upright lines near the anchor's height (no thinning there): points on every grid meridian
                double around = 2 * Math.PI * r / 8.0;
                int levels = Math.max(0, (int) Math.floor(Math.log(around / 6.0) / Math.log(2.0)));
                int maxThin = 1 << levels;
                int meridians = (int) Math.max(6, Math.round(around / maxThin)) * maxThin;
                for (int k = 0; k < meridians; k++) {
                    double lam = 2 * Math.PI * k / meridians;
                    for (int q = -6; q <= 6; q++) {
                        double theta = Math.PI / 2 + q * 0.37 * Math.PI / bands;
                        double x = r*Math.sin(theta)*Math.cos(lam) - camx, yy = r*Math.cos(theta) - camy, z = r*Math.sin(theta)*Math.sin(lam) - camz;
                        if (cyl(x,yy,z) > view - 2) continue;
                        checked++;
                        if (!near(hash, x, yy, z, 0.6)) missing++;
                    }
                }
                if (missing > 0) throw new RuntimeException("drawn " + n + " missing " + missing + "/" + checked + " r=" + r + " t=" + t + " dist=" + dist);
            }
            System.out.println("radius " + r + ": ok, most lines in one frame so far " + worst);
        }
        // full view from the middle of a 64 dome, and a long walk along the edge of a 2048 dome
        System.out.println("64 from the middle: " + RangeDome.draw(0,0,0,64,192,1,2,(x1,y1,z1,x2,y2,z2,nx,ny,nz,c)->{}));
        System.out.println("2048 standing on the edge at ground level: " + RangeDome.draw(-2040,0,0,2048,192,1,2,(x1,y1,z1,x2,y2,z2,nx,ny,nz,c)->{}));
        System.out.println("2048 at render distance 32: " + RangeDome.draw(-2040,0,0,2048,512,1,2,(x1,y1,z1,x2,y2,z2,nx,ny,nz,c)->{}));
        long t0 = System.nanoTime(); int frames = 2000;
        for (int f = 0; f < frames; f++) RangeDome.draw(-2040 + f % 7,0,0,2048,192,1,2,(x1,y1,z1,x2,y2,z2,nx,ny,nz,c)->{});
        System.out.printf("time per frame (2048, edge, rd 12): %.3f ms%n", (System.nanoTime()-t0)/1e6/frames);
    }
    static double cyl(double x, double y, double z) { return Math.max(Math.sqrt(x*x+z*z), Math.abs(y)); }
    static long key(double x, double y, double z) { return (((long)Math.floor(x)) * 73856093L) ^ (((long)Math.floor(y)) * 19349663L) ^ (((long)Math.floor(z)) * 83492791L); }
    static boolean near(Map<Long, List<float[]>> hash, double x, double y, double z, double tol) {
        for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
            List<float[]> l = hash.get(key(x+dx,y+dy,z+dz)); if (l == null) continue;
            for (float[] p : l) { double ex=p[0]-x, ey=p[1]-y, ez=p[2]-z; if (ex*ex+ey*ey+ez*ez <= tol*tol*4) return true; }
        }
        return false;
    }
}
