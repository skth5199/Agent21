package group21.cuckoosearch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class CSNest {

    private final List<CSEgg> eggs;
    private final Comparator<CSEgg> ranker;

    private final int xsSize;

    public CSNest(int popSize, double[][] xRanges, CSEgg.CSEggFitness csEggFitness, Comparator<CSEgg> ranker) {
        this.eggs = new ArrayList<>(popSize);
        for (int i = 0; i < popSize; ++i)
            this.eggs.add(new CSEgg(csEggFitness, xRanges));
        //
        this.ranker = ranker;
        //
        this.xsSize = xRanges.length;
        //
        randomInit();
        //
//        System.out.println(this);
    }

    public void randomInit() {
        eggs.forEach(CSEgg::randomInit);
        rank();
    }

    private void rank() {
        eggs.sort(ranker);
    }

    private void runPerEggMutation() {
        // Get the best.
        final CSEgg bestEgg = eggs.get(0);
//        System.out.println("[runPerEggMutation] Best:\n" + bestEgg);

        // Per egg mutation.
        for (int i = 1; i < eggs.size(); ++i) {
            CSEgg currEgg = eggs.get(i);
//            System.out.println("[runPerEggMutation] Current:\n" + currEgg);

            CSEgg newEgg = currEgg.genNew(bestEgg);
//            System.out.println("[runPerEggMutation] New:\n" + newEgg);

            boolean isWeakImprovement = ranker.compare(newEgg, currEgg) <= 0;
//            System.out.println("[runPerEggMutation] Is new Weak Improvement: " + isWeakImprovement);

            if (isWeakImprovement)
                eggs.set(i, newEgg);
        }

//        System.out.println("[runPerEggMutation] After perEggMutation:\n" + this);
    }

    private void runEggSubstitutions() {
        for (int i = 0; i < eggs.size(); ++i) {
            CSEgg currEgg = eggs.get(i);

            boolean isSomeToUpdate = false;
            boolean[] isSubAttempted = new boolean[xsSize]; {
                Random r = new Random();
                for (int j = 0; j < xsSize; ++j) {
                    boolean tmp = r.nextDouble() <= CuckooSearch.P_A;
                    isSubAttempted[j] = tmp;
                    isSomeToUpdate |= tmp;
                }
            }
            if (isSomeToUpdate) {
                Random r = new Random();

                CSEgg newEgg = currEgg.genNew(
                        isSubAttempted,
                        this.eggs.get(r.nextInt(this.eggs.size())),
                        this.eggs.get(r.nextInt(this.eggs.size())));
//                System.out.println("[runEggSubstitutions] New:\n" + newEgg);

                boolean isWeakImprovement = ranker.compare(newEgg, currEgg) <= 0;
//                System.out.println("[runEggSubstitutions] Is new Weak Improvement: " + isWeakImprovement);

                if (isWeakImprovement)
                    eggs.set(i, newEgg);
            } //else System.out.println("[runEggSubstitutions] No mutations for " + i);
        }
    }

    public void nextGen() {
        runPerEggMutation();
        runEggSubstitutions();
        rank();
//        System.out.println("[nextGen] After mutation:\n" + this);
    }

    public CSEgg getBest() {
        return this.eggs.get(0).deepCopy();
    }

    public CSEgg[] getBestSubsetOfMaxMembers(int n) {
        int size = Math.max(0, Math.min(n, this.eggs.size()));
        CSEgg[] bests = new CSEgg[size];
        for (int i = 0; i < size; ++i)
            bests[i] = this.eggs.get(i).deepCopy();
        return bests;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        eggs.forEach(e -> sb.append(e.toString()));
        return sb.toString();
    }
}
