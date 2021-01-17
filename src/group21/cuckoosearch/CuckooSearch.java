package group21.cuckoosearch;

import genius.core.Bid;
import genius.core.Domain;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.ValueDiscrete;
import genius.core.uncertainty.UserModel;

import java.util.*;

public class CuckooSearch {

    public static final double BETA     = 1.5; // Preferred value.
    public static final double INV_BETA = 1 / 1.5;
    public static final double SIGMA_U  = 0.6966; // Dependent on BETA, see equation on paper (requires Gamma Distribution lib..).
    public static final double SIGMA_V  = 1;   // Always == 1.
    //
    public static final double P_A = 0.25; // Proffered value.

    private final CSNest csNest;

    public CuckooSearch(int popSize, double[][] xRanges, CSEgg.CSEggFitness csEggFitness) {
        this(popSize, xRanges, csEggFitness, Comparator.naturalOrder());
    }

    public CuckooSearch(int popSize, double[][] xRanges, CSEgg.CSEggFitness csEggFitness, Comparator<CSEgg> ranker) {
        csNest = new CSNest(popSize, xRanges, csEggFitness, ranker);
    }

    public static CuckooSearch newPerOptionSearch(int popSize, Domain domain, UserModel userModel) {
        // Calculate the ranges.
        final int totOptionNumber;
        final List<IssueDiscrete> issues = new ArrayList<>(domain.getIssues().size());
        {
            int tmpTotOptionNumber = 0;
            //
            for (Issue i : domain.getIssues()) {
                if (i instanceof IssueDiscrete) {
                    IssueDiscrete issueDiscrete = (IssueDiscrete) i;
                    tmpTotOptionNumber += issueDiscrete.getNumberOfValues();
                    issues.add(issueDiscrete);
                } else
                    System.err.println("[CuckooSearch]: Expected issue to be of type 'IssueDiscrete' but got type: " + i.getClass());
            }
            //
            totOptionNumber = tmpTotOptionNumber;
        }
        //
        final double[][] xRanges = new double[totOptionNumber][2];{
            int i = 0;
            for (IssueDiscrete issue : issues) {
                for (int j = 0; j < issue.getNumberOfValues(); ++j) {
                    xRanges[i + j][0] = 0;
                    xRanges[i + j][1] = 1;
                }
                i += issue.getNumberOfValues();
            }
        }

        // Fitness function as Spearman’s rank correlation.
        final List<Bid> bidOrder = userModel.getBidRanking().getBidOrder();
        //
        final CSEgg.CSEggFitness csEggFitness = xs -> {
            //
            // Here, xs are weights for all options of all issues.
            // Eg: given issues A with options (a1, a2, a3), and B with (b1, b2),
            //     xs is of the form: (wa1, wa2, wa3, wb1, wb2).
            //                          0    1    2    3    4
            PriorityQueue<Map.Entry<Bid, Double>> eggRawRank = new PriorityQueue<>(bidOrder.size(), Map.Entry.comparingByValue()); {
                int ixs = 0;
                for (Bid b : bidOrder) {
                    double eggScore = 0;
                    for (IssueDiscrete i : issues) {
                        eggScore += xs[ixs + i.getValueIndex((ValueDiscrete) b.getValue(i))];
                        ixs += i.getNumberOfValues();
                    }
                    eggRawRank.add(new AbstractMap.SimpleImmutableEntry<>(b, eggScore));

                    ixs = 0;
                }
            }
            return spearmanRankScore(bidOrder, perOptionWeightsRankAll(eggRawRank));
        };

        return new CuckooSearch(
                popSize,
                xRanges,
                csEggFitness,
                Comparator.reverseOrder()
        );
    }

    public synchronized void runGeneration() {
        csNest.nextGen();
    }

    public synchronized void runGenerations(int n) {
        for (int i = 0; i < n; ++i)
            csNest.nextGen();
    }

    public synchronized int autorunGenerations() {
        int numGens = 0;

        int consecutiveNoImprovements = 0;
        double bestSoFar = -2;
        //
        CSEgg nextBest = getBest();
        while (consecutiveNoImprovements < 3) {
            double nextScore = Math.round(nextBest.score() * 4d) / 4d;
            //
            if (nextScore > bestSoFar) {
                consecutiveNoImprovements = 0;
                bestSoFar = nextScore;
            } else ++consecutiveNoImprovements;
            //
            runGenerations(20);
            numGens += 20;
            //
            nextBest = getBest();
        }
        return numGens;
    }

    public synchronized CSEgg getBest() {
        return csNest.getBest();
    }

    public synchronized CSEgg[] getBestSubsetOfMaxMembers(int n) {
        return csNest.getBestSubsetOfMaxMembers(n);
    }

    public static double[] getNormalizersPerOptionWeights(CSEgg egg, Domain domain) {
        double[] wsEgg = egg.getWeights();

        double[] perIssue = new double[domain.getIssues().size()];
        double sumPerIssue = 0;
        {
            int i_perIssue = 0;
            int j_perOption = 0;
            for (Issue i : domain.getIssues()) {
                //
                IssueDiscrete id = (IssueDiscrete) i;
                for (ValueDiscrete option : id.getValues()) {
                    perIssue[i_perIssue] = Math.max(perIssue[i_perIssue], wsEgg[j_perOption]);
                    ++j_perOption;
                }
                //
                sumPerIssue += perIssue[i_perIssue];
                ++i_perIssue;
            }
        }

        if (sumPerIssue > 0)
            for (int i = 0; i < perIssue.length; ++i)
                perIssue[i] /= sumPerIssue;

        return perIssue;
    }

    public static double expUtilityOfPerOptionWeights(Bid bid, Domain domain, CSEgg egg, double[] normalizers) {
        double u = 0;
        //
        int j = 0;
        int i_issue = 0;
        for (Issue i : domain.getIssues()) {
            IssueDiscrete id = (IssueDiscrete) i;
            ValueDiscrete vd = (ValueDiscrete) bid.getValue(id);
            int ivd = id.getValueIndex(vd);
            //
            u += normalizers[i_issue] * egg.getWeights()[j + ivd];
            //
            j += id.getNumberOfValues();
            ++i_issue;
        }
        //
        return u;
    }

    public static double scorePerOptionWeights(Domain domain, UserModel userModel, CSEgg model, double[] normalizers) {
        final List<Bid> bidOrder = userModel.getBidRanking().getBidOrder();

        PriorityQueue<Map.Entry<Bid, Double>> eggRawRank = new PriorityQueue<>(bidOrder.size(), Map.Entry.comparingByValue());
        bidOrder.forEach(b -> eggRawRank.add(new AbstractMap.SimpleImmutableEntry<>(b, expUtilityOfPerOptionWeights(b, domain, model, normalizers))));

        return spearmanRankScore(bidOrder, perOptionWeightsRankAll(eggRawRank));
    }

    private static Map<Bid, Double> perOptionWeightsRankAll(PriorityQueue<Map.Entry<Bid, Double>> eggRawRank) {
        Map<Bid, Double> eggRank = new HashMap<>(eggRawRank.size()); {
            int index = 1;
            //
            double lastSameScore = -1;
            double indexesSum = 0;
            Stack<Bid> queueWithSameScore = new Stack<>();
            //
            while (!eggRawRank.isEmpty()) {
                Map.Entry<Bid, Double> bidDoubleEntry = eggRawRank.poll();
                // Check if queue can be flushed.
                if (bidDoubleEntry.getValue() != lastSameScore) {
                    // First dump the queue into the rank.
                    if (queueWithSameScore.size() > 0) {
                        // Compute the index for all items in queue.
                        final double qIndex = indexesSum / queueWithSameScore.size();
                        while (!queueWithSameScore.isEmpty())
                            eggRank.put(queueWithSameScore.pop(), qIndex);
                    }
                    //
                    // Update queue values.
                    lastSameScore = bidDoubleEntry.getValue();
                    indexesSum = 0;
                }
                // Update the queue.
                indexesSum += index;
                queueWithSameScore.push(bidDoubleEntry.getKey());
                //
                ++index;
            }
            // Flush last items in queue.
            if (queueWithSameScore.size() > 0) {
                // Compute the index for all items in queue.
                final double qIndex = indexesSum / queueWithSameScore.size();
                while (!queueWithSameScore.isEmpty())
                    eggRank.put(queueWithSameScore.pop(), qIndex);
            }
        }
        return eggRank;
    }

    private static double spearmanRankScore(List<Bid> bidOrder, Map<Bid, Double> eggRank) {
        final double ss; {
            double d2sum = 0; {
                double i = 1;
                for (Bid b : bidOrder) {
                    final double d = i - eggRank.get(b);
                    d2sum += d * d;
                    //
                    ++i;
                }
            }
            //
            final int bidOrderLen = bidOrder.size();
            ss = 1 - ((6 * d2sum) / (Math.pow(bidOrderLen, 3) - bidOrderLen));
        }
        //
        return ss;
    }

}
