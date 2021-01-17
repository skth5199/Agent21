import org.apache.commons.math3.stat.inference.ChiSquareTest;
import java.lang.reflect.Type;
import java.util.*;
import java.lang.*;
import java.util.stream.Collectors;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import genius.core.AgentID;
import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.EndNegotiation;
import genius.core.actions.Offer;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;

public class MyAgent extends AbstractNegotiationParty {
	protected Bid lastOffer;

	protected List<ExpBid> allOrdBids;
	// { Issue number, {Option value, option frequency} }
	// Values are hashed by their value (String), by the framework itself.
	protected Map<Integer, Map<String, Double>> issuesOptionsFreq;
	protected Map<Integer, Map<String, Double>> issuesOptionsFreqWinCurr;
	protected Map<Integer, Map<String, Double>> issuesOptionsFreqWinNext;
	protected Map<Integer, Map<String, Double>> issuesOptionsValueWinCurr;
	protected Map<Integer, Map<String, Double>> issuesOptionsValueWinNext;
	protected double totNumOfBids;
	protected Bid bestOffer;
	public static double[] maxfreiss = new double[1000];
	public static double[] maxfreissC = new double[1000];
	public static double[] maxfreissN = new double[1000];
	protected double agreedValue;
	protected double R = 1.1;
	protected static final double CARE = 0.4;
	protected int cou = 0;
	public List<Issue> issues;
	public double maxweight = 0;
	public int gic,winsize=5,beta=5,alpha=10;
	public Boolean chi = Boolean.TRUE, concession = Boolean.FALSE;
	public static double weights[] = new double[200];
	//////////////////////normalize weights to be done
	public static double norweights[] = new double[200];


	/*public static<K,V> Map<K,V> clone(Map<K,V> original) {
		return original.entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getKey,
						Map.Entry::getValue));
	}*/

	@Override
	public void init(NegotiationInfo info) {
		super.init(info);
		AbstractUtilitySpace utilitySpace = info.getUtilitySpace();
		AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;
		issues = additiveUtilitySpace.getDomain().getIssues();
		this.allOrdBids = getAllBids(additiveUtilitySpace);
		Collections.sort(this.allOrdBids);
		System.out.println(allOrdBids);

		// Init number of bids.
		this.totNumOfBids = 0;

		// Agreed Value;
		agreedValue = allOrdBids.get(0).u;

		// Init frequency state.
		this.issuesOptionsFreq = new HashMap<>(issues.size());
		this.issuesOptionsFreqWinCurr = new HashMap<>(issues.size());
		this.issuesOptionsFreqWinNext = new HashMap<>(issues.size());
		this.issuesOptionsValueWinCurr = new HashMap<>(issues.size());
		this.issuesOptionsValueWinNext = new HashMap<>(issues.size());
		final int[] j = {0};
		issues.forEach(i -> {
			if (i instanceof IssueDiscrete) {
				IssueDiscrete issueDiscrete = (IssueDiscrete) i;
				Map<String, Double> thisIssueFreqs = new HashMap<>(issueDiscrete.getNumberOfValues());
				Map<String, Double> thisIssueFreqsC = new HashMap<>(issueDiscrete.getNumberOfValues());
				Map<String, Double> thisIssueFreqsN = new HashMap<>(issueDiscrete.getNumberOfValues());
				issueDiscrete.getValues().forEach(v -> thisIssueFreqs.put(v.getValue(), 0d));
				issueDiscrete.getValues().forEach(v -> thisIssueFreqsC.put(v.getValue(), 0d));
				issueDiscrete.getValues().forEach(v -> thisIssueFreqsN.put(v.getValue(), 0d));
				maxfreiss[j[0]] = 0;
				maxfreissC[j[0]] = 0;
				maxfreissN[j[0]] = 0;

				j[0]++;
				this.issuesOptionsFreq.put(issueDiscrete.getNumber(), thisIssueFreqs);
				this.issuesOptionsFreqWinCurr.put(issueDiscrete.getNumber(), thisIssueFreqsC);
				this.issuesOptionsFreqWinNext.put(issueDiscrete.getNumber(), thisIssueFreqsN);

			} else
				System.err.println("[init]: Expected issue to be of type 'IssueDiscrete' but got type: " + i.getClass());
		});

		printFrequencyState();
		gic=j[0];
		Bid bid = generateRandomBid();
		for (int issueIndex = 0; issueIndex < bid.getIssues().size(); issueIndex++) {
			Issue issue = bid.getIssues().get(issueIndex);
			Value issueValue = findValue(bid, issueIndex);
			System.out.println(issue);
			System.out.println(issueValue);
		}

		//Init weights
		for(int i =0; i< issues.size(); i++){
			weights[i] = 1d/Double.valueOf(issues.size());
		}
		maxweight = 1d/Double.valueOf(issues.size());
	}

	@Override
	public Action chooseAction(List<Class<? extends Action>> possibleActions) {
		// Check for acceptance if we have received an offer
		if (lastOffer != null) {
			double utilLastOffer = getUtility(lastOffer);
			if (timeline.getTime() >= 0.99) {
				if (utilLastOffer >= agreedValue)
					return new Accept(getPartyId(), lastOffer);
				else
					return new EndNegotiation(getPartyId());
			} else {
				if(utilLastOffer > agreedValue)
					return new Accept(getPartyId(), lastOffer);
				else {
					Bid newBid = getBidAboveTargets();
					if (newBid == null)
						newBid = getBidAboveAgreedValue();

					return new Offer(getPartyId(), newBid);
				}
			}
		}

		// Otherwise, send out a random offer above the target utility
		System.err.println("[chooseAction] had to default to generate random bid.");
		return new Offer(getPartyId(), generateRandomBid());
	}

	@Override
	public void receiveMessage(AgentID sender, Action action) {
		if (action instanceof Offer) {
			// Update records.
			this.lastOffer = ((Offer) action).getBid();
			this.totNumOfBids += 1;
			if (bestOffer != null) {
				if (getUtility(bestOffer) < getUtility(this.lastOffer))
					this.bestOffer = lastOffer;
			} else bestOffer = this.lastOffer;

			// Update frequencies.
			updateFrequencyStateOnLastOffer();
			printFrequencyState();

			// Update agreedValue every 10 rounds.
			if (this.totNumOfBids % 10 == 0) {
				this.R *= 0.995D;
				this.agreedValue = utilitySpace.getUtility(this.bestOffer) * R;
				if (this.agreedValue >= 1.0D) {
					this.agreedValue = 0.99D;
				}
			}
		}
	}

	@Override
	public String getDescription() {
		return "Formidable";
	}

	@Override
	public AbstractUtilitySpace estimateUtilitySpace() {
		return super.estimateUtilitySpace();
	}

	private Value findValue(Bid bid, int issueIndex) {
		Value issueValue = null;
		try {
			issueValue = bid.getValue(issueIndex+1);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return issueValue;
	}

	private void updateFrequencyStateOnLastOffer() {
		final int[] j = {0};
		cou++;
		this.lastOffer.getIssues().forEach(i -> {
			if (i instanceof IssueDiscrete) {
				IssueDiscrete issueDiscrete = (IssueDiscrete) i;
				Value genBidValue = this.lastOffer.getValue(issueDiscrete);
				if (genBidValue instanceof ValueDiscrete) {
					String bidValue = ((ValueDiscrete) genBidValue).getValue();
					Map<String, Double> freqOfIssue = this.issuesOptionsFreq.getOrDefault(issueDiscrete.getNumber(), new HashMap<>());
					Map<String, Double> freqOfIssueN = this.issuesOptionsFreqWinNext.getOrDefault(issueDiscrete.getNumber(), new HashMap<>());
					System.out.println("Calculated frequency:");
					System.out.println(freqOfIssue.put(bidValue, (freqOfIssue.getOrDefault(bidValue, 0d) + 1)));
					System.out.println(freqOfIssueN.put(bidValue, (freqOfIssueN.getOrDefault(bidValue, 0d) + 1)));
					if (maxfreiss[j[0]] < (freqOfIssue.getOrDefault(bidValue, 0d)))
						maxfreiss[j[0]] = (freqOfIssue.getOrDefault(bidValue, 0d));

					if (maxfreissN[j[0]] < (freqOfIssueN.getOrDefault(bidValue, 0d)))
						maxfreissN[j[0]] = (freqOfIssueN.getOrDefault(bidValue, 0d));
					//System.out.println("Max frequency:");
					//System.out.println(maxfreiss[j[0]]);
					//System.out.println(maxfreissN[j[0]]);

					j[0]++;

				} else

					System.err.println("[updateFrequencyState]: Expected value to be of type 'ValueDiscrete' but got type: " + genBidValue.getClass());


			} else
				System.err.println("[updateFrequencyState]: Expected issue to be of type 'IssueDiscrete' but got type: " + i.getClass());
		});

		if (cou == winsize){
			cou = 0;
			System.out.println("Window end");
			/*for(int i =0; i< gic;i++){
				System.out.println("mf");
				System.out.println(maxfreiss[i]);
				System.out.println(maxfreissC[i]);
				System.out.println(maxfreissN[i]);
			}*/
			//issuesOptionsValueWinCurr=clone(issuesOptionsFreqWinCurr);
			//issuesOptionsValueWinNext=clone(issuesOptionsFreqWinNext);
			Gson gson = new Gson();
			String jsonString = gson.toJson(issuesOptionsFreqWinCurr);
			Type type = new TypeToken<HashMap<Integer, Map<String, Double>>>(){}.getType();
			issuesOptionsValueWinCurr = gson.fromJson(jsonString, type);
			jsonString = gson.toJson(issuesOptionsFreqWinNext);
			type = new TypeToken<HashMap<Integer, Map<String, Double>>>(){}.getType();
			issuesOptionsValueWinNext = gson.fromJson(jsonString, type);
			//issuesOptionsValueWinCurr.putAll(issuesOptionsFreqWinCurr);
			//issuesOptionsValueWinNext.putAll(issuesOptionsFreqWinNext);
			//issuesOptionsValueWinCurr= new HashMap<Integer, Map<String, Double>>(issuesOptionsFreqWinCurr);
			//issuesOptionsValueWinNext= new HashMap<Integer, Map<String, Double>>(issuesOptionsFreqWinNext);
			printFrequencyState();
			printFrequencyStateC();
			printFrequencyStateN();
			// Smoothing current and next window frequency tables
			j[0] = 0;
			issuesOptionsValueWinCurr.forEach((y,x) -> {
				//System.out.println("Smoothing issue frequencies");
				x.forEach((k,v) -> {
					v = (v+1)/(maxfreissC[j[0]]+1);
					x.replace(k,v);
				});
				j[0]++;
			});
			//System.out.println("mfc");
			//printFrequencyStateC();
			j[0] = 0;
			issuesOptionsValueWinNext.forEach((y,x) -> {
				//System.out.println("Smoothing issue frequencies");
				x.forEach((k,v) -> {
					v = (v+1)/(maxfreissN[j[0]]+1);
					x.replace(k,v);
				});
				j[0]++;
			});
			//weights tend to vanish
			printValueStateC();
			printValueStateN();
			final int[] z = {0};


////////////weight update steps here
			double[] expected = new double[issues.size()];
			long[] observed = new long[issues.size()];
			double[] expectedval = new double[issues.size()];
			double[] observedval = new double[issues.size()];
			int[] e = new int[issues.size()];
			for(int y = 0;y<e.length;y++){
				e[y] = -1;
			}

			issuesOptionsFreqWinCurr.forEach((y,x) -> {
				HashMap<String,Double> fwnh1 = new HashMap<String,Double>(issuesOptionsValueWinNext.get(y));
				HashMap<String,Double> fwnh2 = new HashMap<String,Double>(issuesOptionsValueWinCurr.get(y));
				j[0] = 0;
				fwnh2.forEach((k,v) -> {
					expectedval[j[0]] = v;
					j[0]++;
				});
				j[0]=0;
				fwnh1.forEach((k,v) -> {
					observedval[j[0]] = v;
					j[0]++;
				});

				//System.out.println("Finding issue frequencies using given formula");
				HashMap<String,Double> fwnh = new HashMap<String,Double>(issuesOptionsFreqWinNext.get(y));
				j[0] = 0;
				x.forEach((k,v) -> {
					expected[j[0]] = v;
					j[0]++;
				});
				j[0]=0;
				fwnh.forEach((k,v) -> {
					observed[j[0]] = v.longValue();
					j[0]++;
				});
				// do chi squared test without formula -- Done
				chi = chisqtest(expected,observed);
				if (chi){
					e[z[0]++] = y;
				}
				else{
					double sumcur = 0;
					double sumnxt = 0;
					for(int i = 0; i < expectedval.length; i++){
						sumcur = sumcur + expected[i]*expectedval[i];
						sumnxt = sumnxt + observed[i]*observedval[i];
						if (sumnxt>sumcur)
							concession = Boolean.TRUE;
						else concession = Boolean.FALSE;
					}
					}
			});
			/*for (int i=0;i<issues.size();i++){
				System.out.println("eval");
				System.out.println(e[i]);
			}*/
			System.out.println("Opponent condeding: "+concession);
			if ((e[0]!= -1) && (concession == Boolean.TRUE)) {
				for (int i = 0; i < issues.size(); i++) {
					double time = getTimeLine().getTime();
					//System.out.println(time);
					double dt = alpha * (1 - Math.pow(time, beta));
					//System.out.println("containsss");
					boolean test = false;
					for (int element : e) {
						if (element == (i+1)) {
							test = true;
							break;
						}
					}
					if (test)
						weights[i] = weights[i] + dt;
					System.out.println("Weight "+(i+1)+": "+weights[i]);
				}

				System.out.println();

			}
			for(int i =0; i<issues.size();i++){
				if (weights[i] > maxweight) maxweight=weights[i];
			}


/////////////weight update end


			maxfreissC = maxfreissN;
			this.issuesOptionsFreqWinCurr = this.issuesOptionsFreqWinNext;
			this.issuesOptionsFreqWinNext = new HashMap<>(issues.size());
			j[0] = 0;
			issues.forEach(i -> {
				if (i instanceof IssueDiscrete) {
					IssueDiscrete issueDiscrete = (IssueDiscrete) i;
					Map<String, Double> thisIssueFreqs = new HashMap<>(issueDiscrete.getNumberOfValues());
					issueDiscrete.getValues().forEach(v -> thisIssueFreqs.put(v.getValue(), 0d));
					maxfreissN[j[0]] = 0;
					j[0]++;
					this.issuesOptionsFreqWinNext.put(issueDiscrete.getNumber(), thisIssueFreqs);
				} else
					System.err.println("[init]: Expected issue to be of type 'IssueDiscrete' but got type: " + i.getClass());
			});
			System.out.println("After Updation");
			printFrequencyStateC();
			printFrequencyStateN();
		}
		/*Bid bid = generateRandomBid();
		System.out.println("lmao");
		System.out.println(getOppUtility(bid));*/
	}
/////////////////////Chi squared test
	public static boolean chisqtest(double[] expected, long[] observed)
	{
		double alpha = 0.05; // confidence level 95%
		ChiSquareTest t = new ChiSquareTest();
		for(int i =0; i<expected.length; i++){
			if (expected[i] == 0) expected[i] = 1;
			if (observed[i] == 0) observed[i] = 1;
		}
		boolean rejected = t.chiSquareTest(expected, observed, alpha);
		return (!rejected);
	}

//////////////////Get Opponent Utility
	public double getOppUtility(Bid bid) {
		double utility = 0;
		for (int issueIndex = 0; issueIndex < bid.getIssues().size(); issueIndex++) {
			Value issueValue = findValue(bid, issueIndex);
			Map<String,Double> valueMap = issuesOptionsFreq.get(issueIndex+1);
			double weight = (weights[issueIndex]/maxweight)*(valueMap.get(issueValue.toString()) / maxfreiss[issueIndex]);
			utility += weight;

		}
		return utility/issues.size();
	}


/////////////////////Printing Function
	private void printFrequencyState() {
		System.out.println("[ New frequency table ]");
		for (Map.Entry<Integer, Map<String, Double>> issue : this.issuesOptionsFreq.entrySet()) {
			System.out.println("ISSUE[ " + issue.getKey() + " ]:");
			for (Map.Entry<String, Double> value : issue.getValue().entrySet())
				System.out.println("\tOPTION[ " + value.getKey() + " ]: \t" + value.getValue());
		}
		System.out.println();
	}

	private void printFrequencyStateC() {
		System.out.println("[ Current frequency table ]");
		for (Map.Entry<Integer, Map<String, Double>> issue : this.issuesOptionsFreqWinCurr.entrySet()) {
			System.out.println("ISSUE[ " + issue.getKey() + " ]:");
			for (Map.Entry<String, Double> value : issue.getValue().entrySet())
				System.out.println("\tOPTION[ " + value.getKey() + " ]: \t" + value.getValue());
		}
		System.out.println();
	}

	private void printFrequencyStateN() {
		System.out.println("[ Next frequency table ]");
		for (Map.Entry<Integer, Map<String, Double>> issue : this.issuesOptionsFreqWinNext.entrySet()) {
			System.out.println("ISSUE[ " + issue.getKey() + " ]:");
			for (Map.Entry<String, Double> value : issue.getValue().entrySet())
				System.out.println("\tOPTION[ " + value.getKey() + " ]: \t" + value.getValue());
		}
		System.out.println();
	}

	private void printValueStateC() {
		System.out.println("[ Current Value table ]");
		for (Map.Entry<Integer, Map<String, Double>> issue : this.issuesOptionsValueWinCurr.entrySet()) {
			System.out.println("ISSUE[ " + issue.getKey() + " ]:");
			for (Map.Entry<String, Double> value : issue.getValue().entrySet())
				System.out.println("\tOPTION[ " + value.getKey() + " ]: \t" + value.getValue());
		}
		System.out.println();
	}

	private void printValueStateN() {
		System.out.println("[ Next Value table ]");
		for (Map.Entry<Integer, Map<String, Double>> issue : this.issuesOptionsValueWinNext.entrySet()) {
			System.out.println("ISSUE[ " + issue.getKey() + " ]:");
			for (Map.Entry<String, Double> value : issue.getValue().entrySet())
				System.out.println("\tOPTION[ " + value.getKey() + " ]: \t" + value.getValue());
		}
		System.out.println();
	}
///////////////////////////

	private void printWeights() {
		for(int i = 0; i<issues.size(); i++) {
			System.out.println("Weight for Issue "+i+": "+ weights[i]);

		}
	}


	private double weightOfIssue(Integer issueNumber) {
		Map<String, Double> optionFreqs = this.issuesOptionsFreq.getOrDefault(issueNumber, new HashMap<>());
		double w = 0; {
			double t2 = Math.pow(this.totNumOfBids, 2);
			for (Double f : optionFreqs.values())
				w += Math.pow(f, 2) / t2;
		}
		return w;
	}

	private double normalizedWeightOfIssue(Integer issueNumber) {
		// Calculate the weight of the issue.
		double w = weightOfIssue(issueNumber);

		// Normalise the weight.
		double wNorm; {
			double sumAllWs = 0;
			for (Integer i : this.issuesOptionsFreq.keySet())
				sumAllWs += weightOfIssue(i);
			wNorm = w / sumAllWs;
		}

		return wNorm;
	}

	private double valueOfOption(Integer issueNumber, String option) {
		Map<String, Double> optionFreqs = this.issuesOptionsFreq.getOrDefault(issueNumber, new HashMap<>());
		// Sort ascending.
		List<String> ascOptions = new ArrayList<>(optionFreqs.size());
		optionFreqs.entrySet()
				.stream()
				.sorted(Map.Entry.comparingByValue())
				.forEachOrdered(it -> ascOptions.add(it.getKey()));
		// Calculate option value.
		double v; {
			double k = ascOptions.size();// number of options.
			double n0 = ascOptions.indexOf(option); // rank of option.
			v = (k - n0 + 1) / k;
		}
		return v;
	}

	private double valuationOfConfig(Map<Integer, String> issueOptionMap) {
		double est = 0;
		for (Map.Entry<Integer, String> issueOption : issueOptionMap.entrySet()) {
			double v = valueOfOption(issueOption.getKey(), issueOption.getValue());
			double w = normalizedWeightOfIssue(issueOption.getKey());
			est += v*w;
		}
		System.out.println("EST[ " + issueOptionMap + " ] = " + est);
		return est;
	}

	private Bid getBidAboveTargets() {
		for (ExpBid eb : this.allOrdBids) {
			double opUtil = valuationOfConfig(bidToMap(eb.bid));
			if (eb.u >= agreedValue && opUtil >= CARE)
				return eb.bid;
		}
		//
		System.err.println("[generateRandomBidAboveTarget]: \"Failed to generate one below: " + agreedValue + " and " + CARE + "\"");
		return generateRandomBid();
	}

	private Bid getBidAboveAgreedValue() {
		for (ExpBid eb : this.allOrdBids) {
			if (eb.u >= this.agreedValue)
				return eb.bid;
		}
		//
		System.err.println("[generateRandomBidAboveTarget]: \"Failed to generate one below: " + this.agreedValue + "\"");
		return generateRandomBid();
	}

	private static Map<Integer, String> bidToMap(Bid bid) {
		Map<Integer, String> map = new HashMap<>(bid.getIssues().size());
		bid.getIssues().forEach(i -> {
			map.put(i.getNumber(), ((ValueDiscrete) bid.getValue(i)).getValue()); // TODO: check before casting(?)...
		});
		return map;
	}

	private List<ExpBid> getAllBids(AdditiveUtilitySpace additiveUtilitySpace) {
		List<ExpBid> bhs = new LinkedList<>();
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

		allCmbs.forEach(config -> {
			Bid b = new Bid(additiveUtilitySpace.getDomain(), config);
			double u = utilitySpace.getUtility(b);
			bhs.add(new ExpBid(b, u));
		});

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

	private class ExpBid implements Comparable<ExpBid> {
		public final Bid bid;
		public final double u;

		public ExpBid(Bid bid, double u) {
			this.bid = bid;
			this.u = u;
		}

		@Override
		public String toString() {
			return "ExpBid {" + bid + ", " + u + "}";
		}

		@Override
		public int compareTo(ExpBid o) {
			return Double.compare(o.u, u);
		}
	}

}

