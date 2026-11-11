package com.jumper.client;

/**
 * The range dome's shape: the sphere an anchor reaches (centred on the middle of the anchor block) drawn as a cage of
 * rings (like lines of latitude) and upright lines (like longitude), about every 8 blocks along its surface, so the
 * edge is easy to see when you walk up to it. The ring at the anchor's own height is brighter.
 * <p>
 * Only the part of the cage within view distance of the camera is drawn (the game's fog hides the rest), so a
 * 2048-block dome costs about the same as a 64-block one. Near the top and bottom the upright lines are thinned out (every 2nd, 4th, ...) so they don't bunch
 * up. No allocations per frame: the grid's counts are kept for the last radius and every line goes straight to the
 * {@link LineSink}. Plain maths, no game classes: the same file on 1.20.1 and 1.21.1.
 */
public final class RangeDome {
    /** Where the dome's lines go (the game's line buffer). */
    public interface LineSink {
        /**
         * One straight line, in blocks relative to the camera. (nx, ny, nz) is its direction (length 1); argb is its
         * colour with alpha.
         */
        void line(float x1, float y1, float z1, float x2, float y2, float z2, float nx, float ny, float nz, int argb);
    }

    /** Distance between neighbouring lines along the surface, in blocks. */
    public static final double SPACING = 8.0;
    /** How far a ring's straight piece may stray from the true circle, in blocks (too little to see). */
    private static final double SAG = 0.1;
    /** Shortest and longest straight piece of a ring, in blocks. */
    private static final double MIN_PIECE = 4.0;
    private static final double MAX_PIECE = 32.0;
    /** Largest turn between two straight pieces of a ring (keeps small rings round). */
    private static final double MAX_RING_STEP = Math.PI / 16;
    /** Upright lines closer than this (as a share of SPACING) are thinned out. */
    private static final double THIN_BELOW = 0.7;
    /** Safety limit on lines per frame. */
    public static final int MAX_LINES = 30000;

    private static double gridRadius = -1;
    /** Strips between the top and bottom (even, so one ring sits at the anchor's height). */
    private static int bands;
    /** Upright lines around the full circle (a multiple of maxThin). */
    private static int meridians;
    /** Most the upright lines are thinned near the top and bottom (a power of two; leaves at least 6). */
    private static int maxThin;
    private static int drawn;
    /** Lines whose every point is further than this from the camera (as the game's fog measures it) are skipped. */
    private static double cullDistance;

    private RangeDome() {
    }

    /**
     * Draws the part of the dome within view of the camera.
     *
     * @param cx           the dome's middle minus the camera position (blocks)
     * @param radius       the dome's radius in blocks (nothing is drawn if it isn't above 0)
     * @param viewDistance where the game's fog ends (the render distance in blocks); lines beyond it are skipped
     * @param color        the cage's colour (argb)
     * @param bright       the colour of the ring at the anchor's height
     * @return the number of lines drawn
     */
    public static int draw(double cx, double cy, double cz, double radius, double viewDistance, int color, int bright,
            LineSink sink) {
        drawn = 0;
        if (!(radius > 0) || !(viewDistance > 0)) {
            return 0;
        }
        setGrid(radius);
        cullDistance = viewDistance;
        // The fog is a cylinder (horizontal distance or height, whichever is larger), so the part of the sphere worth
        // looking at is within view * sqrt(2) of the camera; emit() then skips lines outside the cylinder itself.
        double view = viewDistance * Math.sqrt(2.0);
        // v: the camera seen from the dome's middle. A point X on the sphere is within view when |X - v| <= view,
        // which is the same as X.v >= k0.
        double vx = -cx;
        double vy = -cy;
        double vz = -cz;
        double d2 = vx * vx + vy * vy + vz * vz;
        double d = Math.sqrt(d2);
        double k0 = (radius * radius + d2 - view * view) / 2.0;
        if (k0 > radius * d) {
            return 0;
        }
        double dTheta = Math.PI / bands;
        int firstRing = 1;
        int lastRing = bands - 1;
        if (d > 1e-6 && k0 > -radius * d) {
            // Only the rings near the direction of the camera can be in view: those within angle alpha of it.
            double alpha = Math.acos(clamp(k0 / (radius * d)));
            double thetaV = Math.acos(clamp(vy / d));
            firstRing = Math.max(1, (int) Math.floor((thetaV - alpha) / dTheta));
            lastRing = Math.min(bands - 1, (int) Math.ceil((thetaV + alpha) / dTheta));
        }
        double h = Math.sqrt(vx * vx + vz * vz);
        double lambdaV = h > 1e-9 ? Math.atan2(vz, vx) : 0.0;

        for (int i = firstRing; i <= lastRing && drawn < MAX_LINES; i++) {
            double theta = i * dTheta;
            double rho = radius * Math.sin(theta);
            double y = radius * Math.cos(theta);
            double half = halfWindow(rho, y, vy, h, k0);
            if (half >= 0) {
                ring(cx, cy, cz, rho, y, lambdaV, half, i * 2 == bands ? bright : color, sink);
            }
        }

        // Upright lines, one strip at a time: strip b runs from ring b to ring b + 1 (rings 0 and `bands` are the
        // top and bottom points). Each piece is one straight line (a strip is about SPACING tall).
        double dLambda = 2.0 * Math.PI / meridians;
        int firstBand = Math.max(0, firstRing - 1);
        int lastBand = Math.min(bands - 1, lastRing);
        for (int b = firstBand; b <= lastBand && drawn < MAX_LINES; b++) {
            double t1 = b * dTheta;
            double t2 = (b + 1) * dTheta;
            double s1 = Math.sin(t1);
            double c1 = Math.cos(t1);
            double s2 = Math.sin(t2);
            double c2 = Math.cos(t2);
            double half = Math.max(halfWindow(radius * s1, radius * c1, vy, h, k0),
                    halfWindow(radius * s2, radius * c2, vy, h, k0));
            if (half < 0) {
                continue;
            }
            // Thin out by the strip's narrower end (at a top or bottom point, by its other end).
            double narrow = Math.min(s1, s2);
            if (narrow < 1e-9) {
                narrow = Math.max(s1, s2);
            }
            int step = thinning(radius * narrow * dLambda);
            int from;
            int to;
            if (half >= Math.PI - 1e-9) {
                from = 0;
                to = meridians - 1;
            } else {
                from = (int) Math.ceil((lambdaV - half) / dLambda);
                to = (int) Math.floor((lambdaV + half) / dLambda);
            }
            // First multiple of step at or after from (from may be negative).
            int k = Math.floorDiv(from + step - 1, step) * step;
            for (; k <= to && drawn < MAX_LINES; k += step) {
                double lambda = k * dLambda;
                double cl = Math.cos(lambda);
                double sl = Math.sin(lambda);
                emit(cx + radius * s1 * cl, cy + radius * c1, cz + radius * s1 * sl,
                        cx + radius * s2 * cl, cy + radius * c2, cz + radius * s2 * sl, color, sink);
            }
        }
        return drawn;
    }

    /** Draws the visible arc of one ring: radius rho, at height y above the middle, centred on angle mid. */
    private static void ring(double cx, double cy, double cz, double rho, double y, double mid, double half,
            int color, LineSink sink) {
        if (rho < 1e-6) {
            return;
        }
        double span = 2.0 * half;
        double piece = Math.max(MIN_PIECE, Math.min(MAX_PIECE, Math.sqrt(8.0 * rho * SAG)));
        int pieces = (int) Math.ceil(Math.max(span * rho / piece, span / MAX_RING_STEP));
        pieces = Math.max(1, Math.min(pieces, 4096));
        double step = span / pieces;
        double start = mid - half;
        // Walk round the ring by rotating (cos, sin) one step at a time instead of calling cos and sin per point.
        double cosStep = Math.cos(step);
        double sinStep = Math.sin(step);
        double c = Math.cos(start);
        double s = Math.sin(start);
        double px = cx + rho * c;
        double pz = cz + rho * s;
        double py = cy + y;
        for (int j = 0; j < pieces && drawn < MAX_LINES; j++) {
            double nc = c * cosStep - s * sinStep;
            double ns = s * cosStep + c * sinStep;
            c = nc;
            s = ns;
            double qx = cx + rho * c;
            double qz = cz + rho * s;
            emit(px, py, pz, qx, py, qz, color, sink);
            px = qx;
            pz = qz;
        }
    }

    private static void emit(double x1, double y1, double z1, double x2, double y2, double z2, int color,
            LineSink sink) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-6) {
            return;
        }
        // No point of the line can be nearer (by the fog's measure) than an end's distance minus the line's length.
        double near = Math.min(fogDistance(x1, y1, z1), fogDistance(x2, y2, z2));
        if (near - length > cullDistance) {
            return;
        }
        sink.line((float) x1, (float) y1, (float) z1, (float) x2, (float) y2, (float) z2, (float) (dx / length),
                (float) (dy / length), (float) (dz / length), color);
        drawn++;
    }

    /** Distance from the camera as the game's terrain fog measures it: horizontal distance or height, the larger. */
    private static double fogDistance(double x, double y, double z) {
        return Math.max(Math.sqrt(x * x + z * z), Math.abs(y));
    }

    /**
     * Half the angle (around the vertical axis, centred on the camera's direction) of a ring's part that is within
     * view: PI for the whole ring, -1 for none of it.
     */
    private static double halfWindow(double rho, double y, double vy, double h, double k0) {
        double need = k0 - y * vy;
        double most = rho * h;
        if (most < 1e-9) {
            return need <= 0 ? Math.PI : -1.0;
        }
        double ratio = need / most;
        if (ratio > 1.0) {
            return -1.0;
        }
        if (ratio <= -1.0) {
            return Math.PI;
        }
        return Math.acos(ratio);
    }

    /** Draw every step-th upright line, so neighbours are at least THIN_BELOW * SPACING apart. */
    private static int thinning(double spacing) {
        int step = 1;
        while (step < maxThin && spacing * step < SPACING * THIN_BELOW) {
            step *= 2;
        }
        return step;
    }

    /** Works out the grid's counts for this radius (only when the radius changes). */
    private static void setGrid(double radius) {
        if (radius == gridRadius) {
            return;
        }
        gridRadius = radius;
        bands = Math.max(4, 2 * (int) Math.round(Math.PI * radius / (2.0 * SPACING)));
        double around = 2.0 * Math.PI * radius / SPACING;
        int levels = Math.max(0, (int) Math.floor(Math.log(around / 6.0) / Math.log(2.0)));
        maxThin = 1 << Math.min(levels, 20);
        meridians = (int) Math.max(6, Math.round(around / maxThin)) * maxThin;
    }

    private static double clamp(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }

    /** Lines drawn by the last {@link #draw} call. */
    public static int lastDrawn() {
        return drawn;
    }
}
