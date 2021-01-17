package group21;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.utility.AbstractUtilitySpace;
import java.util.List;



/**
 * A simple example agent that makes random bids above a minimum target utility.
 */
public class Agent21 extends AbstractNegotiationParty
{
    private Bid lastRecievedBid;
    double time = 0.2D;
    private double maximumUtility = 1.0;


    /**
     * Initializes a new instance of the agent.
     */
    @Override
    public void init(NegotiationInfo info)
    {
        super.init(info);
    }

    /**
     * Makes a random offer above the minimum utility target
     */
    @Override
    public Action chooseAction(List<Class<? extends Action>> possibleActions) {
        double lastReceivedBidUtility = getUtility(lastRecievedBid);

        if (t())
            return (lastReceivedBidUtility >= 0.75) ?
                    new Accept(getPartyId(), lastRecievedBid) :
                    new Offer(getPartyId(), firstBids());

        if (lastRecievedBid != null) {
            if (timeline.getTime() >= 0.99) {
                return new Accept(getPartyId(), lastRecievedBid);
            }
            if (timeline.getTime() >= 0.90) {
                if (lastReceivedBidUtility >= 0.65)
                    return new Accept(getPartyId(), lastRecievedBid);
            } else {
                if (lastReceivedBidUtility > 0.75)
                    return new Accept(getPartyId(), lastRecievedBid);
                else {
                    Bid newBid = generateRandomBidAboveTarget(f());

                    return new Offer(getPartyId(), newBid);
                }
            }
        }
        // Otherwise, send out a random offer above the target utility
//        System.err.println("[chooseAction] had to default to generate random bid.");
        return new Offer(getPartyId(), generateRandomBidAboveTarget(f()));

    }
    private Bid firstBids(){
        Bid bid;
        double util;
        do{
            bid= generateRandomBid();
            util= utilitySpace.getUtility(bid);
        } while (util<=0.9 || util>= 0.95);
        return bid;
    }


    private boolean t() {
        return getTimeLine().getTime() < this.time;
    }

    private Bid generateRandomBidAboveTarget(double f)
    {
        Bid bid;
        double util;

        do {
            bid = generateRandomBid();
            util = utilitySpace.getUtility(bid);
        } while (util<= f);

        return bid;
    }

    public double f(){
        double TimeRatio = 1 - timeline.getCurrentTime()/ timeline.getTotalTime();
        double minimumUtility = 0;

        if (TimeRatio > 0.5) {
            minimumUtility = 0.8;
        } else if (TimeRatio > 0.1) {
            minimumUtility = 0.6;
        } else {
            minimumUtility = 0.3;
        }

        return exponential(timeline.getCurrentTime(), timeline.getTotalTime(), minimumUtility, maximumUtility);
    }

    public double exponential(double currentTime, double totalTime, double minimumUtility, double maximumUtility){
        double alpha= Math.pow(1 - Math.min(currentTime, totalTime)/totalTime, 1/Math.E);
        double concedingVariable= minimumUtility + (maximumUtility - minimumUtility) * alpha;

        return concedingVariable;
    }

    /**
     *   Remember the offers received by the opponent
     */
    @Override
    public void receiveMessage(AgentID sender, Action action)
    {
        super.receiveMessage(sender, action);
        if (action instanceof Offer)
            this.lastRecievedBid = ((Offer) action).getBid();
    }


    @Override
    public String getDescription()
    {
        return "Terminator2020";
    }

    /**
     * This stub can be expanded to deal with preference uncertainty in a more sophisticated way than the default behavior.
     */
    @Override
    public AbstractUtilitySpace estimateUtilitySpace()
    {
        return super.estimateUtilitySpace();
    }

}
