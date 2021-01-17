package group21.cuckoosearch;

import java.util.Random;

public class CSEgg implements Comparable<CSEgg> {

    public interface CSEggFitness {
        double score(double[] xs);
    }

    public static final double R = 0.01;

    private final CSEggFitness csEggFitness;
    private final double[][] xRanges;
    private final double[] xs;

    private boolean isScoreCacheObsolete;
    private double scoreCache;

    public CSEgg(CSEggFitness csEggFitness, double[][] xRanges) {
        this.csEggFitness = csEggFitness;
        this.xRanges = xRanges;
        this.xs = new double[xRanges.length];

        isScoreCacheObsolete = true;
    }

    public void randomInit() {
        Random r = new Random();
        for (int i = 0; i < xs.length; ++i) {
            double min = Math.min(xRanges[i][0], xRanges[i][1]);
            double max = Math.max(xRanges[i][0], xRanges[i][1]);
            xs[i] = min + (r.nextDouble() * (max - min));
        }
    }

    public double score() {
        if (isScoreCacheObsolete) {
            this.scoreCache = csEggFitness.score(this.xs);
            isScoreCacheObsolete = false;
        }
        return this.scoreCache;
    }

    public void resetScore() {
        this.scoreCache = csEggFitness.score(this.xs);
        isScoreCacheObsolete = false;
    }

    public CSEgg genNew(CSEgg bestEgg) {
        CSEgg n = new CSEgg(csEggFitness, xRanges);
        //
        for (int i = 0; i < this.xs.length; ++i) {
            Random r = new Random();
            final double u = 0 + (r.nextDouble() * CuckooSearch.SIGMA_U);
            final double v = 0 + (r.nextDouble() * CuckooSearch.SIGMA_V);
            final double step = u / Math.pow(Math.abs(v), CuckooSearch.INV_BETA);
            final double rnd = r.nextDouble();
            //
            double diff = this.xs[i] - bestEgg.xs[i];
            n.xs[i] = this.xs[i] + (rnd * R * step * diff);
            //
            final double min = Math.min(xRanges[i][0], xRanges[i][1]);
            final double max = Math.max(xRanges[i][0], xRanges[i][1]);
            //
            if (n.xs[i] < min)
                n.xs[i] = min;
            else if (n.xs[i] > max)
                n.xs[i] = max;
        }
        //
        return n;
    }

    public CSEgg genNew(boolean[] isSubAttempted, CSEgg egg0, CSEgg egg1) {
        CSEgg n = new CSEgg(csEggFitness, xRanges);
        //
        for (int i = 0; i < this.xs.length; ++i) {
            if (isSubAttempted[i]) {
                double diff = egg0.xs[i] - egg1.xs[i];
                double r = new Random().nextDouble();
                //
                n.xs[i] = this.xs[i] + (r * diff);
                //
                final double min = Math.min(xRanges[i][0], xRanges[i][1]);
                final double max = Math.max(xRanges[i][0], xRanges[i][1]);
                //
                if (n.xs[i] < min)
                    n.xs[i] = min;
                else if (n.xs[i] > max)
                    n.xs[i] = max;
            }
        }
        //
        return n;
    }

    public CSEgg deepCopy() {
        CSEgg copy = new CSEgg(csEggFitness, xRanges);
        System.arraycopy(xs, 0, copy.xs, 0, xs.length);
        copy.isScoreCacheObsolete = isScoreCacheObsolete;
        copy.scoreCache = scoreCache;
        return copy;
    }

    public double[] getWeights() {
        return xs.clone();
    }

    @Override
    public int compareTo(CSEgg o) {
        return Double.compare(score(), o.score());
    }

    @Override
    public String toString() {
        double score = score();
        StringBuilder sb = new StringBuilder();
        sb.append("[  ");
        for (double x : xs)
            sb.append(x >= 0 ? " " : "").append(String.format("%.4f", x)).append("  ");
        sb.append("]-> ").append(score >= 0 ? " " : "").append(String.format("%.4f", score())).append("\n");
        return sb.toString();
    }
}
