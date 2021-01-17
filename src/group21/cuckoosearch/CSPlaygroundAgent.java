package group21.cuckoosearch;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.uncertainty.BidRanking;
import genius.core.utility.AdditiveUtilitySpace;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;

public class CSPlaygroundAgent extends AbstractNegotiationParty {

    private Bid lastReceivedBid = null;

    @Override
    public void init(NegotiationInfo info) {
        super.init(info);

//        if (hasPreferenceUncertainty())
//            try {
//                PrintWriter out = new PrintWriter("src/group21/cuckoosearch/CSPlaygroundAgentLog.txt");
//
//                CuckooSearch cuckooSearch = CuckooSearch.newPerOptionSearch(50, getDomain(), getUserModel());
//
//                int numGens = cuckooSearch.autorunGenerations();
//                out.println("gens: " + numGens);
//
//                CSEgg bestModel = cuckooSearch.getBest();
//                double[] normalizers = CuckooSearch.getNormalizersPerOptionWeights(bestModel, getDomain());
//                out.println(bestModel.score());
//                out.println(Arrays.toString(bestModel.getWeights()));
//                out.println(Arrays.toString(normalizers));
//
//                BidRanking bidRanking = userModel.getBidRanking();
//                List<Bid> knownOrder = bidRanking.getBidOrder();
//
//                out.println(
//                        bidRanking.getHighUtility() + " - " +
//                        CuckooSearch.expUtilityOfPerOptionWeights(
//                                bidRanking.getMaximalBid(),
//                                getDomain(),
//                                bestModel,
//                                normalizers));
//
//                out.println(
//                        bidRanking.getLowUtility() + " - " +
//                        CuckooSearch.expUtilityOfPerOptionWeights(
//                                bidRanking.getMinimalBid(),
//                                getDomain(),
//                                bestModel,
//                                normalizers));
//
//                out.println("All in raking");
//                for (Bid b : knownOrder)
//                    out.println(
//                        b + " -> " +
//                        CuckooSearch.expUtilityOfPerOptionWeights(
//                                b,
//                                getDomain(),
//                                bestModel,
//                                normalizers)
//                    );
//
//                out.flush();
//                out.close();
//            } catch (FileNotFoundException e) {
//                e.printStackTrace();
//            }

        if (hasPreferenceUncertainty())
            try {
                PrintWriter out = new PrintWriter("src/group21/cuckoosearch/CSPlaygroundAgentLog.txt");
                out.println((double)userModel.getBidRanking().getSize() / (double)userModel.getDomain().getNumberOfPossibleBids());

                CuckooSearch cuckooSearch = CuckooSearch.newPerOptionSearch(100, getDomain(), getUserModel());

                out.println(cuckooSearch.autorunGenerations());

                CSEgg bestModel = cuckooSearch.getBest();
                double[] normalizers = CuckooSearch.getNormalizersPerOptionWeights(bestModel, getDomain());

                out.println(getUserModel().getBidRanking().getSize());
                out.println(bestModel.score() + " - " + CuckooSearch.scorePerOptionWeights(getDomain(), getUserModel(), bestModel, normalizers));
                out.println(Arrays.toString(bestModel.getWeights()));
                out.println(Arrays.toString(normalizers));

                List<Bid> allBids = getAllBids((AdditiveUtilitySpace) getUtilitySpace());

                for (Bid bid : allBids) {
                    userModel = user.elicitRank(bid, userModel);
                }

                out.println(userModel.getBidRanking().getSize());
                out.println(CuckooSearch.scorePerOptionWeights(getDomain(), getUserModel(), bestModel, normalizers));

                out.flush();
                out.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

    }

    @Override
    public Action chooseAction(List<Class<? extends Action>> validActions) {
        if (lastReceivedBid == null || !validActions.contains(Accept.class) || Math.random() > 0.5) {
            return new Offer(getPartyId(), generateRandomBid());
        } else {
            return new Accept(getPartyId(), lastReceivedBid);
        }
    }

    @Override
    public void receiveMessage(AgentID sender, Action action) {
        super.receiveMessage(sender, action);
        if (action instanceof Offer) {
            lastReceivedBid = ((Offer) action).getBid();
        }
    }

    @Override
    public String getDescription() {
        return "CSPlaygroundAgent";
    }

    private List<Bid> getAllBids(AdditiveUtilitySpace additiveUtilitySpace) {
        List<Bid> bhs = new LinkedList<>();
        List<HashMap<Integer, Value>> allCmbs = new LinkedList<>();

        List<Issue> issues = additiveUtilitySpace.getDomain().getIssues();
        //
        IssueDiscrete i0 = (IssueDiscrete) issues.get(0);
        for (ValueDiscrete vi0 : i0.getValues()) {
            HashMap<Integer, Value> tmp = new HashMap<>();
            tmp.put(i0.getNumber(), vi0);
            allCmbs.add(tmp);
        }
        //
        for (int i = 1; i < issues.size(); ++i)
            allCmbs = integrateCombs(allCmbs, (IssueDiscrete) issues.get(i));

        allCmbs.forEach(config -> bhs.add(new Bid(additiveUtilitySpace.getDomain(), config)));

        return bhs;
    }

    private List<HashMap<Integer, Value>> integrateCombs(List<HashMap<Integer, Value>> cmbs, IssueDiscrete i) {
        List<HashMap<Integer, Value>> newCmbs = new LinkedList<>();
        //
        for (Map<Integer, Value> m : cmbs) {
            for (ValueDiscrete v : i.getValues()) {
                HashMap<Integer, Value> newCmb = new HashMap<>(m);
                newCmb.put(i.getNumber(), v);
                //
                newCmbs.add(newCmb);
            }
        }
        //
        return newCmbs;
    }

}
