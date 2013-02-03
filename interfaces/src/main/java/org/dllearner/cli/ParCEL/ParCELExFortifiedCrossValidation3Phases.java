package org.dllearner.cli.ParCEL;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.dllearner.algorithms.ParCEL.ParCELAbstract;
import org.dllearner.algorithms.ParCEL.ParCELExtraNode;
import org.dllearner.algorithms.ParCEL.ParCELPosNegLP;
import org.dllearner.algorithms.ParCELEx.ParCELExAbstract;
import org.dllearner.algorithms.celoe.CELOE; 
import org.dllearner.cli.CrossValidation;
import org.dllearner.cli.ParCEL.Orthogonality.FortificationResult;
import org.dllearner.core.AbstractCELA;
import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.core.ComponentInitException;
import org.dllearner.core.owl.Description;
import org.dllearner.core.owl.Individual;
import org.dllearner.core.owl.Negation;
import org.dllearner.kb.OWLFile;
import org.dllearner.learningproblems.Heuristics;
import org.dllearner.learningproblems.PosNegLP;
import org.dllearner.utilities.Files;
import org.dllearner.utilities.Helper;
import org.dllearner.utilities.owl.ConceptComparator;
import org.dllearner.utilities.statistics.Stat;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import com.clarkparsia.pellet.owlapiv3.PelletReasoner;
import com.clarkparsia.pellet.owlapiv3.PelletReasonerFactory;

/**
 * Add PDLL cross validation support to Jens Lehmann work (
 * {@link org.dllearner.cli.CrossValidation}). In this cross validation, 
 * some more addition dimensions will be investigated such as: 
 * number partial definitions, partial definition length, etc.   
 *  
 * 
 * @author actran
 * 
 */

public class ParCELExFortifiedCrossValidation3Phases extends CrossValidation {

	//pdef
	private Stat noOfPdefStat;
	private Stat noOfUsedPdefStat;
	private Stat avgUsedPartialDefinitionLengthStat;
	
	//cpdef
	private Stat noOfCpdefStat;
	private Stat noOfCpdefUsedStat;
	private Stat avgCpdefLengthStat;	
	private Stat totalCPDefLengthStat;
	private Stat avgCpdefCoverageTrainingStat;
	
	
	//learning time
	private Stat learningTime;
	
	
	//fortify strategy statistical variables
	/*
	private Stat accuracyFortifyStat;
	private Stat correctnessFortifyStat;
	private Stat completenessFortifyStat;
	private Stat fmeasureFortifyStat;
	//private Stat avgFortifiedPartialDefinitionLengthStat;
	*/

	
	//blind fortification
	private Stat accuracyBlindFortifyStat;
	private Stat correctnessBlindFortifyStat;
	private Stat completenessBlindFortifyStat;
	private Stat fmeasureBlindFortifyStat;
	
	
	//labeled fortification
	private Stat labelFortifyCpdefTrainingCoverageStat;
	private Stat noOfLabelFortifySelectedCpdefStat;
	private Stat avgLabelCpdefLengthStat;
	private Stat labelFortifiedDefinitionLengthStat;
	private Stat accuracyLabelFortifyStat;
	private Stat correctnessLabelFortifyStat;
	private Stat completenessLabelFortifyStat;
	private Stat fmeasureLabelFortifyStat;

	

	//multi-step fortification
	protected Stat[][] accuracyPercentageFortifyStepStat;		//hold the fortified accuracy at 5,10,20,30,40,50% (multi-strategies)
	protected Stat[][] completenessPercentageFortifyStepStat;	//hold the fortified completeness at 5,10,20,30,40,50% (multi-strategies)
	protected Stat[][] correctnessPercentageFortifyStepStat;	//hold the fortified correctness at 5,10,20,30,40,50% (multi-strategies)
	protected Stat[][] fmeasurePercentageFortifyStepStat;	//hold the fortified correctness at 5,10,20,30,40,50% (multi-strategies)

	protected Stat[] noOfCpdefUsedMultiStepFortStat;
	
	
	protected double[][] accuracyHalfFullStep;
	protected double[][] fmeasureHalfFullStep;
	
	protected Stat[][] accuracyFullStepStat;
	protected Stat[][] fmeasureFullStepStat;
	protected Stat[][] correctnessFullStepStat;
	protected Stat[][] completenessFullStepStat;
	
	
	Logger logger = Logger.getLogger(this.getClass());

	protected boolean interupted = false;

	/**
	 * Default constructor
	 */

	public ParCELExFortifiedCrossValidation3Phases(AbstractCELA la, PosNegLP lp, AbstractReasonerComponent rs,
			int folds, boolean leaveOneOut, int noOfRuns) {
		super(la, lp, rs, folds, leaveOneOut, noOfRuns);
	}

	/**
	 * This is for PDLL cross validation
	 * 
	 * @param la
	 * @param lp
	 * @param rs
	 * @param folds
	 * @param leaveOneOut
	 * @param noOfRuns Number of k-fold runs, i.e. the validation will run kk times of k-fold validations 
	 */
	public ParCELExFortifiedCrossValidation3Phases(AbstractCELA la, ParCELPosNegLP lp, AbstractReasonerComponent rs,
			int folds, boolean leaveOneOut, int noOfRuns) {

		super(); // do nothing

		//--------------------------
		//setting up 
		//--------------------------
		DecimalFormat df = new DecimalFormat();	

		// the training and test sets used later on
		List<Set<Individual>> trainingSetsPos = new LinkedList<Set<Individual>>();
		List<Set<Individual>> trainingSetsNeg = new LinkedList<Set<Individual>>();
		List<Set<Individual>> testSetsPos = new LinkedList<Set<Individual>>();
		List<Set<Individual>> testSetsNeg = new LinkedList<Set<Individual>>();
		List<Set<Individual>> fortificationSetsPos = new LinkedList<Set<Individual>>();
		List<Set<Individual>> fortificationSetsNeg = new LinkedList<Set<Individual>>();

		
		// get examples and shuffle them too
		Set<Individual> posExamples = lp.getPositiveExamples();
		List<Individual> posExamplesList = new LinkedList<Individual>(posExamples);
		Collections.shuffle(posExamplesList, new Random(1));			
		Set<Individual> negExamples = lp.getNegativeExamples();
		List<Individual> negExamplesList = new LinkedList<Individual>(negExamples);
		Collections.shuffle(negExamplesList, new Random(2));
		
		String baseURI = rs.getBaseURI();
		Map<String, String> prefixes = rs.getPrefixes();

		//----------------------
		//end of setting up
		//----------------------

		// sanity check whether nr. of folds makes sense for this benchmark
		if(!leaveOneOut && (posExamples.size()<folds && negExamples.size()<folds)) {
			System.out.println("The number of folds is higher than the number of "
					+ "positive/negative examples. This can result in empty test sets. Exiting.");
			System.exit(0);
		}


		// calculating where to split the sets, ; note that we split
		// positive and negative examples separately such that the 
		// distribution of positive and negative examples remains similar
		// (note that there are better but more complex ways to implement this,
		// which guarantee that the sum of the elements of a fold for pos
		// and neg differs by at most 1 - it can differ by 2 in our implementation,
		// e.g. with 3 folds, 4 pos. examples, 4 neg. examples)
		int[] splitsPos = calculateSplits(posExamples.size(),folds);
		int[] splitsNeg = calculateSplits(negExamples.size(),folds);
		
		
		//for orthogonality check
		long orthAllCheckCount[] = new long[5];
		orthAllCheckCount[0] = orthAllCheckCount[1] = orthAllCheckCount[2] = orthAllCheckCount[3] = orthAllCheckCount[4] = 0;
		
		long orthSelectedCheckCount[] = new long[5];
		orthSelectedCheckCount[0] = orthSelectedCheckCount[1] = orthSelectedCheckCount[2] = orthSelectedCheckCount[3] = orthSelectedCheckCount[4] = 0;
		
	

		//System.out.println(splitsPos[0]);
		//System.out.println(splitsNeg[0]);

		// calculating training and test sets
		for(int i=0; i<folds; i++) {
			
			//test sets
			Set<Individual> testPos = getTestingSet(posExamplesList, splitsPos, i);
			Set<Individual> testNeg = getTestingSet(negExamplesList, splitsNeg, i);
			testSetsPos.add(i, testPos);
			testSetsNeg.add(i, testNeg);
			
			//fortification training sets
			Set<Individual> fortPos = getTestingSet(posExamplesList, splitsPos, (i+1) % folds);
			Set<Individual> fortNeg = getTestingSet(negExamplesList, splitsNeg, (i+1) % folds);
			fortificationSetsPos.add(i, fortPos);
			fortificationSetsNeg.add(i, fortNeg);
			
			//training sets
			Set<Individual> trainingPos = getTrainingSet(posExamples, testPos); 
			Set<Individual> trainingNeg = getTrainingSet(negExamples, testNeg);
			
			trainingPos.removeAll(fortPos);
			trainingNeg.removeAll(fortNeg);
			
			trainingSetsPos.add(i, trainingPos);
			trainingSetsNeg.add(i, trainingNeg);		
		}	

	

		// run the algorithm
		int terminatedBypartialDefinition=0, terminatedByCounterPartialDefinitions=0;

		//---------------------------------
		//k-fold cross validation
		//---------------------------------

		Stat runtimeAvg = new Stat();
		Stat runtimeMax = new Stat();
		Stat runtimeMin = new Stat();
		Stat runtimeDev = new Stat();
		
		Stat learningTimeAvg = new Stat();
		Stat learningTimeMax = new Stat();
		Stat learningTimeMin = new Stat();
		Stat learningTimeDev = new Stat();

		Stat noOfPartialDefAvg = new Stat();
		Stat noOfPartialDefDev = new Stat();
		Stat noOfPartialDefMax = new Stat();
		Stat noOfPartialDefMin = new Stat();

		Stat avgPartialDefLenAvg = new Stat();
		Stat avgPartialDefLenDev = new Stat();
		Stat avgPartialDefLenMax = new Stat();
		Stat avgPartialDefLenMin = new Stat();

		Stat avgFortifiedPartialDefLenAvg = new Stat();
		Stat avgFortifiedPartialDefLenDev = new Stat();
		Stat avgFortifiedPartialDefLenMax = new Stat();
		Stat avgFortifiedPartialDefLenMin = new Stat();
		
		Stat defLenAvg = new Stat();
		Stat defLenDev = new Stat();
		Stat defLenMax = new Stat();
		Stat defLenMin = new Stat();

		Stat trainingAccAvg = new Stat();
		Stat trainingAccDev= new Stat();
		Stat trainingAccMax = new Stat();
		Stat trainingAccMin = new Stat();

		Stat trainingCorAvg = new Stat();
		Stat trainingCorDev = new Stat();
		Stat trainingCorMax = new Stat();
		Stat trainingCorMin = new Stat();

		Stat trainingComAvg = new Stat();
		Stat trainingComDev = new Stat();
		Stat trainingComMax = new Stat();
		Stat trainingComMin = new Stat();

		Stat testingAccAvg = new Stat();
		Stat testingAccMax = new Stat();
		Stat testingAccMin = new Stat();
		Stat testingAccDev = new Stat();
		
		Stat fortifyAccAvg = new Stat();
		Stat fortifyAccMax = new Stat();
		Stat fortifyAccMin = new Stat();
		Stat fortifyAccDev = new Stat();


		Stat testingCorAvg = new Stat();
		Stat testingCorMax = new Stat();
		Stat testingCorMin = new Stat();
		Stat testingCorDev = new Stat();
		
		Stat fortifyCorAvg = new Stat();
		Stat fortifyCorMax = new Stat();
		Stat fortifyCorMin = new Stat();
		Stat fortifyCorDev = new Stat();
		
		Stat testingComAvg = new Stat();		
		Stat testingComMax = new Stat();
		Stat testingComMin = new Stat();
		Stat testingComDev = new Stat();
				
		Stat fortifyComAvg = new Stat();
		Stat fortifyComMax = new Stat();
		Stat fortifyComMin = new Stat();
		Stat fortifyComDev = new Stat();
		
		
		Stat testingFMeasureAvg = new Stat();
		Stat testingFMeasureMax = new Stat();
		Stat testingFMeasureMin = new Stat();
		Stat testingFMeasureDev = new Stat();
		
		Stat trainingFMeasureAvg = new Stat();
		Stat trainingFMeasureMax = new Stat();
		Stat trainingFMeasureMin = new Stat();
		Stat trainingFMeasureDev = new Stat();
		
		Stat fortifyFmeasureAvg = new Stat();
		Stat fortifyFmeasureMax = new Stat();
		Stat fortifyFmeasureMin = new Stat();
		Stat fortifyFmeasureDev = new Stat();
		
		
		Stat noOfDescriptionsAgv = new Stat();
		Stat noOfDescriptionsMax = new Stat();
		Stat noOfDescriptionsMin = new Stat();
		Stat noOfDescriptionsDev = new Stat();
				
		Stat noOfCounterPartialDefinitionsAvg = new Stat();
		Stat noOfCounterPartialDefinitionsDev = new Stat();
		Stat noOfCounterPartialDefinitionsMax = new Stat();
		Stat noOfCounterPartialDefinitionsMin = new Stat();
		
		Stat noOfCounterPartialDefinitionsUsedAvg = new Stat();
		Stat noOfCounterPartialDefinitionsUsedDev = new Stat();
		Stat noOfCounterPartialDefinitionsUsedMax = new Stat();
		Stat noOfCounterPartialDefinitionsUsedMin = new Stat();

		
		/*
		long orthAllCheckCountFold[] = new long[5];
		long orthSelectedCheckCountFold[] = new long[5];

		long orthAllCheckCountTotal[] = new long[5];
		long orthSelectedCheckCountTotal[] = new long[5];
		
		
		orthAllCheckCountTotal[0] = orthAllCheckCountTotal[1] = orthAllCheckCountTotal[2] = 
			orthAllCheckCountTotal[3] = orthAllCheckCountTotal[4] = 0;
		
		orthSelectedCheckCountTotal[0] = orthSelectedCheckCountTotal[1] = orthSelectedCheckCountTotal[2] = 
			orthSelectedCheckCountTotal[3] = orthSelectedCheckCountTotal[4] = 0;
		 */
		
		
		//----------------------------------------------------------------------
		//loading ontology into Pellet reasoner for checking 
		//the orthogonality and satisfiability (fortification training strategy) 
		//----------------------------------------------------------------------
		long ontologyLoadStarttime = System.nanoTime();		
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology ontology = ((OWLFile)la.getReasoner().getSources().iterator().next()).createOWLOntology(manager);			
		outputWriter("Ontology created, axiom count: " + ontology.getAxiomCount());
		PelletReasoner pelletReasoner = PelletReasonerFactory.getInstance().createReasoner(ontology);
		outputWriter("Pellet creared and binded with the ontology: " + pelletReasoner.getReasonerName());
		long ontologyLoadDuration = System.nanoTime() - ontologyLoadStarttime;
		outputWriter("Total time for creating and binding ontology: " + ontologyLoadDuration/1000000000d + "ms");

		
		for (int kk=0; kk < noOfRuns; kk++) {

			//general statistics
			runtime = new Stat();
			learningTime = new Stat();
			length = new Stat();
			totalNumberOfDescriptions = new Stat();
			

			//pdef
			noOfPdefStat = new Stat();
			noOfUsedPdefStat = new Stat();
			avgUsedPartialDefinitionLengthStat = new Stat();			
			
			//cpdef
			noOfCpdefStat = new Stat();
			noOfCpdefUsedStat = new Stat();
			totalCPDefLengthStat = new Stat();
			avgCpdefLengthStat = new Stat();
			avgCpdefCoverageTrainingStat = new Stat();
						

			//training 
			accuracyTraining = new Stat();
			trainingCorrectnessStat= new Stat();
			trainingCompletenessStat = new Stat();
			fMeasureTraining = new Stat();
			
			//test
			accuracy = new Stat();
			fMeasure = new Stat();
			testingCorrectnessStat = new Stat();
			testingCompletenessStat = new Stat();
					
						
			//blind fortification
			accuracyBlindFortifyStat = new Stat();
			correctnessBlindFortifyStat = new Stat();
			completenessBlindFortifyStat = new Stat();
			fmeasureBlindFortifyStat = new Stat();

			
			//labled fortification
			labelFortifyCpdefTrainingCoverageStat = new Stat();
			noOfLabelFortifySelectedCpdefStat = new Stat();
			avgLabelCpdefLengthStat = new Stat();
			labelFortifiedDefinitionLengthStat = new Stat();
			accuracyLabelFortifyStat = new Stat();
			correctnessLabelFortifyStat= new Stat();
			completenessLabelFortifyStat = new Stat();
			fmeasureLabelFortifyStat = new Stat();
			
			

			//fortification strategies								
			String[] strategyNames = {"TRAINING COVERAGE", "JACCARD OVERLAP", "JACCARD DISJOIN", "FORTIFICATION TRAINING", "SIMILARITY-ALL", "SIMILARITY-NEG_POS"};
			
			//constants used to index the fortification strategies in the result array 
			final int TRAINING_COVERAGE_INDEX = 0, JACCARD_OVERLAP_INDEX = 1, JACCARD_DISJOIN_INDEX = 2,
					FORTIFICATION_TRANING_INDEX = 3, SIMILARITY_ALL_INDEX = 4, SIMILARITY_POS_NEG_INDEX = 5;
			
			int noOfStrategies = strategyNames.length;
			
			
			//fortification accuracy
			accuracyPercentageFortifyStepStat = new Stat[noOfStrategies][6];		//6 elements for six values of 5%, 10%, ..., 50%
			completenessPercentageFortifyStepStat = new Stat[noOfStrategies][6];
			correctnessPercentageFortifyStepStat = new Stat[noOfStrategies][6];
			fmeasurePercentageFortifyStepStat = new Stat[noOfStrategies][6];
			
			
			//initial fortification accuracy by PERCENTAGE
			for (int i=0; i<noOfStrategies; i++) {
				for (int j=0; j<6; j++) {
					accuracyPercentageFortifyStepStat[i][j] = new Stat();
					completenessPercentageFortifyStepStat[i][j] = new Stat();
					correctnessPercentageFortifyStepStat[i][j] = new Stat();				
					fmeasurePercentageFortifyStepStat[i][j] = new Stat();
				}
			}
			
			//number of cpdef corresponding to 5%, 10%, ..., 50% (for stat.)
			noOfCpdefUsedMultiStepFortStat = new Stat[6];
			for (int i=0; i<6; i++)
				noOfCpdefUsedMultiStepFortStat[i] = new Stat();
				
			int minOfHalfCpdef = Integer.MAX_VALUE;
			int minCpdef = Integer.MAX_VALUE;
			
			//run n-fold cross validations
			for(int currFold=0; (currFold<folds); currFold++) {
				
				
				outputWriter("//---------------\n" + "// Fold " + currFold + "/" + folds + "\n//---------------");
				outputWriter("Training: " + trainingSetsPos.get(currFold).size() + "/" + trainingSetsNeg.get(currFold).size()
						+ ", test:" + testSetsPos.get(currFold).size() + "/" + testSetsNeg.get(currFold).size()
						+ ", fort: " + fortificationSetsPos.get(currFold).size() + "/" + fortificationSetsNeg.get(currFold).size()
					);


				if (this.interupted) {
					outputWriter("Cross validation has been interupted");
					return;
				}
				
				//-----------------------------------------------------
				//	1. Learn the DEFINITIONS
				//		Both pdef and cpdef may be generated
				//-----------------------------------------------------				
				outputWriter("** Phase 1 - Learning definition");				
				outputWriter("Timeout="	+ ((ParCELExAbstract)la).getMaxExecutionTimeInSeconds() + "s");
				
				//set training example sets
				lp.setPositiveExamples(trainingSetsPos.get(currFold));
				lp.setNegativeExamples(trainingSetsNeg.get(currFold));

				try {			
					lp.init();
					la.init();
				} catch (ComponentInitException e) {
					e.printStackTrace();
				}

				long algorithmStartTime = System.nanoTime();
				try {
					la.start();
				}
				catch (OutOfMemoryError e) {
					System.out.println("Out of memory at " + (System.currentTimeMillis() - algorithmStartTime)/1000 + "s");
				}

				long algorithmDuration = System.nanoTime() - algorithmStartTime;
				runtime.addNumber(algorithmDuration/(double)1000000000);
				
				//learning time, does not include the reduction time
				long learningMili = ((ParCELAbstract)la).getLearningTime();
				learningTime.addNumber(learningMili/(double)1000);

				
				//--------------------------------
				//	FINISH learning
				//--------------------------------
				

				//cast the la into ParCELExAbstract for easier accessing
				ParCELExAbstract parcelEx = (ParCELExAbstract)la;

				
				//get the learned DEFINITION (union)
				Description concept = parcelEx.getUnionCurrenlyBestDescription(); 
				
				length.addNumber(concept.getLength());
				
				outputWriter("Learning finished.  Total number of pdefs: " + parcelEx.getPartialDefinitions().size() + ". Number of pdef used: " + parcelEx.getNoOfReducedPartialDefinition());
				
				//cpdef: some stat information
				int noOfUsedCpdef = parcelEx.getNumberOfCounterPartialDefinitionUsed();
				int noOfCpdef = parcelEx.getCounterPartialDefinitions().size();
				noOfCpdefStat.addNumber(noOfCpdef);				
				noOfCpdefUsedStat.addNumber(noOfUsedCpdef);
				
				//pdef: some stat information
				Set<ParCELExtraNode> partialDefinitions = parcelEx.getReducedPartialDefinition();
				long noOfPdef = parcelEx.getNumberOfPartialDefinitions();
				long noOfUsedPdef = parcelEx.getNoOfReducedPartialDefinition();
				double avgPdefLength = concept.getLength() / (double)noOfUsedPdef;
				noOfPdefStat.addNumber(noOfPdef);
				noOfUsedPdefStat.addNumber(noOfUsedPdef);
				avgUsedPartialDefinitionLengthStat.addNumber(avgPdefLength);
				
				//descriptions
				totalNumberOfDescriptions.addNumber(parcelEx.getTotalNumberOfDescriptionsGenerated());
				
				
				//print the coverage of the counter partial definitions
				outputWriter("Number of counter partial definitions: " + noOfCpdef);
				
				
				//--------------------------------------
				//get the COUNTER PARTIAL DEFINITIONs
				//--------------------------------------
				//sorted by training coverage by default
				TreeSet<CELOE.PartialDefinition> counterPartialDefinitions = new TreeSet<CELOE.PartialDefinition>(new CoverageComparator2());

				//-------------------------------
				//training sets
				//-------------------------------
				Set<Individual> curFoldPosTrainingSet = trainingSetsPos.get(currFold);
				Set<Individual> curFoldNegTrainingSet = trainingSetsNeg.get(currFold); 
				
				int trainingPosSize = curFoldPosTrainingSet.size() ;
				int trainingNegSize = curFoldNegTrainingSet.size();
				
				
				//-----------------------
				// 2. Check if any CPDEFs generated
				// 	Note that this algorithm generate both pdefs and cpdef 
				//	However, sometime there is no cpdef as the definition had been found "too" fast
				//	Therefore, we will reverse training set to produce some cpdef if necessary
				//-----------------------

				if (noOfCpdef < 5) {
					//================================================================
					//2. Phase 2: Learn Counter Partial Definitions
					// 		Reverse the pos/neg and let the learner start
					//================================================================
					
					outputWriter("* Number of counter partial definitions is too small, reverse the examples and learn again!!!");
					
					//reverse the pos/neg examples
					lp.setPositiveExamples(trainingSetsNeg.get(currFold));
					lp.setNegativeExamples(trainingSetsPos.get(currFold));

					//re-initialize the learner
					try {			
						lp.init();
						la.init();
					} catch (ComponentInitException e) {
						e.printStackTrace();
					}
					
					outputWriter("\n** Phase 2 - Learning COUNTER PARTIAL DEFINITIONS");				
					outputWriter("Timeout="	+ ((ParCELExAbstract)la).getMaxExecutionTimeInSeconds() + "s");

					//start the learner
					long algorithmStartTime1 = System.nanoTime();
					try {
						la.start();
					}
					catch (OutOfMemoryError e) {
						System.out.println("out of memory at " + (System.currentTimeMillis() - algorithmStartTime1)/1000 + "s");
					}
										
					
					
					//calculate the counter partial definitions' avg. coverage 
					//(note that the positive and negative examples are swapped)
					for (ParCELExtraNode cpdef : ((ParCELExAbstract)la).getPartialDefinitions()) {
						
						int trainingCp = cpdef.getCoveredPositiveExamples().size();	//
								
						counterPartialDefinitions.add(new CELOE.PartialDefinition(new Negation(cpdef.getDescription()), trainingCp));		
						
						avgCpdefCoverageTrainingStat.addNumber(trainingCp/(double)trainingSetsNeg.get(currFold).size());						
					}
					
					outputWriter("Finish learning, number of counter partial definitions: " + counterPartialDefinitions.size());
				}				
				else {				
					//calculate the counter partial definitions' avg coverage
					for (ParCELExtraNode cpdef : parcelEx.getCounterPartialDefinitions()) {
						
						int trainingCn = cpdef.getCoveredNegativeExamples().size();
								
						counterPartialDefinitions.add(new CELOE.PartialDefinition(cpdef.getDescription(), trainingCn));		
						
						avgCpdefCoverageTrainingStat.addNumber(trainingCn/(double)trainingPosSize);						
					}

				}				


				
				

				
				/*				 
				//display the cpdef and their coverage 
				outputWriter("(CPDEF length and coverage (length, coverage)");
				int count = 1;
				String sTemp = "";
				
				for (ParCELExtraNode cpdef : counterPartialDefinitions) {
					sTemp += ("(" + cpdef.getDescription().getLength() + ", " + 
							df.format(cpdef.getCoveredNegativeExamples().size()/(double)trainingNegSize) + "); ");
					if (count % 10 == 0) {
						outputWriter(sTemp);
						sTemp = "";
					}
					count++;
				}
				*/
				
				outputWriter("------------------------------");
				
				
				//-----------------------------
				//TRAINING accuracy
				//-----------------------------
		
				//cp, cn of training sets
				Set<Individual> cpTraining = rs.hasType(concept, trainingSetsPos.get(currFold));		//positive examples covered by the learned concept
				//Set<Individual> upTraining = Helper.difference(trainingSetsPos.get(currFold), cpTraining);	//false negative (pos as neg)
				Set<Individual> cnTraining = rs.hasType(concept, trainingSetsNeg.get(currFold));		//false positive (neg as pos)

				
				//training completeness, correctness and accuracy
				int trainingCorrectPosClassified = cpTraining.size();	
				int trainingCorrectNegClassified = trainingNegSize - cnTraining.size();	//getCorrectNegClassified(rs, concept, trainingSetsNeg.get(currFold));
				int trainingCorrectExamples = trainingCorrectPosClassified + trainingCorrectNegClassified;
				
				double trainingAccuracy = 100*((double)trainingCorrectExamples/(trainingPosSize + trainingNegSize));	
				double trainingCompleteness = 100*(double)trainingCorrectPosClassified/trainingPosSize;
				double trainingCorrectness = 100*(double)trainingCorrectNegClassified/trainingNegSize;
				
				accuracyTraining.addNumber(trainingAccuracy);
				trainingCompletenessStat.addNumber(trainingCompleteness);
				trainingCorrectnessStat.addNumber(trainingCorrectness);
				
				//training F-Measure
				int negAsPosTraining = cnTraining.size();
				double precisionTraining = (trainingCorrectPosClassified + negAsPosTraining) == 0 ? 
						0 : trainingCorrectPosClassified / (double) (trainingCorrectPosClassified + negAsPosTraining);
				double recallTraining = trainingCorrectPosClassified / (double) trainingPosSize;
				double currFmeasureTraining = 100 * Heuristics.getFScore(recallTraining, precisionTraining);
				fMeasureTraining.addNumber(currFmeasureTraining);


				//----------------------
				//TEST accuracy
				//----------------------				
				
				//calculate the coverage
				Set<Individual> curFoldPosTestSet = testSetsPos.get(currFold);
				Set<Individual> curFoldNegTestSet = testSetsNeg.get(currFold);
				
				int testingPosSize = curFoldPosTestSet.size();
				int testingNegSize = curFoldNegTestSet.size();
				
				Set<Individual> cpTest = rs.hasType(concept, curFoldPosTestSet);		//positive examples covered by the learned concept
				Set<Individual> upTest = Helper.difference(curFoldPosTestSet, cpTest);	//false negative (pos as neg)
				Set<Individual> cnTest = rs.hasType(concept, curFoldNegTestSet);		//false positive (neg as pos)

			
				//calculate test accuracies
				int correctTestPosClassified = cpTest.size();	//covered pos. in test set				
				int correctTestNegClassified = testingNegSize - cnTest.size();	//uncovered neg in test set
				int correctTestExamples = correctTestPosClassified + correctTestNegClassified;

				double testingCompleteness = 100*(double)correctTestPosClassified/testingPosSize;
				double testingCorrectness = 100*(double)correctTestNegClassified/testingNegSize;				
				double currAccuracy = 100*((double)correctTestExamples/(testingPosSize + testingNegSize));

				accuracy.addNumber(currAccuracy);
				testingCompletenessStat.addNumber(testingCompleteness);
				testingCorrectnessStat.addNumber(testingCorrectness);				
	
				
				//F-Measure test set
				int negAsPos = cnTest.size();
				double testPrecision = correctTestPosClassified + negAsPos == 0 ? 
						0 : correctTestPosClassified / (double) (correctTestPosClassified + negAsPos);
				double testRecall = correctTestPosClassified / (double) testingPosSize;
				double currFmeasureTest = 100 * Heuristics.getFScore(testRecall, testPrecision); 
				
				fMeasure.addNumber(currFmeasureTest);
				

				//---------------------------------------
				// FORTIFICATION 				
				//---------------------------------------

				FortificationResult[] multiStepFortificationResult = new FortificationResult[noOfStrategies];
				

				
				//check the f-measure, accuracy, completeness, correctness calculations between the methods 
				//	Orthogonality.fortifyAccuracyMultiSteps and inside this class
				
				/*
				outputWriter("********* check the calculation ***************");
				outputWriter("fmeasure: " + currFmeasureTest+ " // " + multiStepFortificationCoverage.fortificationFmeasure[0]);
				outputWriter("accuracy: " + currAccuracy + " // " + multiStepFortificationCoverage.fortificationAccuracy[0]);
				outputWriter("correctness: " + testingCorrectness + " // " + multiStepFortificationCoverage.fortificationCorrectness[0]);
				outputWriter("comleteness: " + testingCompleteness + " // " + multiStepFortificationCoverage.fortificationCompleteness[0]);
				*/
				
				
				//---------------------------------
				// Fortification - ALL CPDEFs
				//  (BLIND Fortification)
				//---------------------------------
				//NOTE: 
				//Since this will iterate all cpdef, we will calculate score for all other fortification strategies
				// training coverage (done), jaccard, fortification training, 

				outputWriter("---------------------------------------------------------------");
				outputWriter("BLIND fortification - All counter partial defintions are used");
				outputWriter("---------------------------------------------------------------");
				
				
				//get the set of pos and neg (in the test set) covered by counter partial definition
				Set<Individual> cpdefPositiveCovered = new HashSet<Individual>();
				Set<Individual> cpdefNegativeCovered = new HashSet<Individual>();
				
				long totalCPDefLength = 0;
				
				//some variables for Jaccard statistical info
				int c = 1;
				Map<Long, Integer> jaccardValueCount = new TreeMap<Long, Integer>();

				//variables for fortification training
				Set<Individual> fortificationTrainingPos = fortificationSetsPos.get(currFold);
				Set<Individual> fortificationTrainingNeg= fortificationSetsNeg.get(currFold);
				
				Set<Individual> allFortificationExamples = new HashSet<Individual>();
				
				allFortificationExamples.addAll(fortificationTrainingPos);
				allFortificationExamples.addAll(fortificationTrainingNeg);	//duplicate will be remove automatically
				
				ConceptSimilarity similarityCheckerAll = new ConceptSimilarity(rs, allFortificationExamples);
				ConceptSimilarity similarityCheckerPos= new ConceptSimilarity(rs, fortificationTrainingPos);
				ConceptSimilarity similarityCheckerNeg = new ConceptSimilarity(rs, fortificationTrainingNeg);
				
		
				//start the BLIND fortification and calculate the scores
				int tmp_id = 1;
				for (CELOE.PartialDefinition negCpdef : counterPartialDefinitions) {
					
					Description cpdef = negCpdef.getDescription().getChild(0);
					
					//assign id for cpdef for debugging purpose
					negCpdef.setId("#" + tmp_id++);

					//--------------------
					//Orthogonality check
					//--------------------
					//int orthoCheck = Orthogonality.orthogonalityCheck(pelletReasoner, ontology, concept, cpdef.getDescription());
					int orthoCheck = 0;	//currently, disable this value as the reasoner often gets stuck in checking the satisfiability
					
					//count ortho check values for stat purpose
					orthAllCheckCount[orthoCheck]++;
					
					
					//--------------------
					//BLIND fortification
					//--------------------
					
					//cp and cn of the current cpdef
					Set<Individual> cpdefCp = rs.hasType(cpdef, curFoldPosTestSet);
					Set<Individual> cpdefCn = rs.hasType(cpdef, curFoldNegTestSet);
					

					//-----------------
					//JACCARD distance
					//-----------------
					double jaccardDistance = Orthogonality.jaccardDistance(concept, cpdef);		//calculate jaccard distance between the learned concept and the cpdef	
					
					//count the jaccard values, for stat purpose
					long tmp_key = Math.round(jaccardDistance * 1000);
					if (jaccardValueCount.containsKey(tmp_key))
						jaccardValueCount.put(tmp_key, jaccardValueCount.get(tmp_key)+1);					
					else
						jaccardValueCount.put(tmp_key, 1);
					
					
					//------------------------
					//fortification TRAINNING
					//------------------------
					Set<Individual> fortCp = rs.hasType(cpdef, fortificationTrainingPos);
					Set<Individual> fortCn = rs.hasType(cpdef, fortificationTrainingNeg);
					
					int cp = fortCp.size();
					int cn = fortCn.size();
					
					//this need to be revised (calculate once)
					fortCp.removeAll(rs.hasType(concept, fortificationTrainingPos));
					fortCn.removeAll(rs.hasType(concept, fortificationTrainingNeg));
					
					double fortificationTrainingScore = Orthogonality.fortificationScore(pelletReasoner, cpdef, concept, 
							cp, cn, fortificationTrainingPos.size(), fortificationTrainingNeg.size(), 
							cp-fortCp.size(), cn-fortCn.size());
					
					
					//--------------
					//similarity
					//--------------
					double similarityScoreAll = similarityCheckerAll.disjunctiveSimilarityEx(partialDefinitions, cpdef);
					double similarityScorePos = similarityCheckerPos.disjunctiveSimilarityEx(partialDefinitions, cpdef);
					double similarityScoreNeg = similarityCheckerNeg.disjunctiveSimilarityEx(partialDefinitions, cpdef);
					double similarityCombineScore = similarityScoreNeg*1.5 - similarityScorePos*0.5;
					
					//---------------------------
					//assign score for the cpdef
					//---------------------------
					negCpdef.setAdditionValue(0, orthoCheck);
					negCpdef.setAdditionValue(1, cpdefCn.size());	//no of neg. examples in test set covered by the cpdef					
					negCpdef.setAdditionValue(2, jaccardDistance);
					negCpdef.setAdditionValue(3, fortificationTrainingScore);	
					negCpdef.setAdditionValue(4, similarityScoreAll);						
					negCpdef.setAdditionValue(5, similarityScorePos);
					negCpdef.setAdditionValue(6, similarityScoreNeg);
					negCpdef.setAdditionValue(7, similarityCombineScore);
					
					//------------------------
					//BLIND fortification
					//------------------------
					cpdefPositiveCovered.addAll(cpdefCp);
					cpdefNegativeCovered.addAll(cpdefCn);

					
					totalCPDefLength += cpdef.getLength();					
					
					//print the cpdef which covers some pos. examples
					//if (cpdefCp.size() > 0)								
					outputWriter(c++ + ". " + getCpdefString(negCpdef, baseURI, prefixes)
							+ ", cp=" + rs.hasType(cpdef, curFoldPosTestSet)
							+ ", cn=" + rs.hasType(cpdef, curFoldNegTestSet));	
				}
								
				outputWriter( " * Blind fortifcation summary: cp=" + cpdefPositiveCovered + " --- cn=" + cpdefNegativeCovered);
				
				
				outputWriter("test set errors pos (" + upTest.size() + "): " + upTest);
				outputWriter("test set errors neg (" + cnTest.size() + "): " + cnTest);
				
				//-----------------------------------------				
				//calculate BLIND fortification accuracy
				//-----------------------------------------
				
				//fortify definition length: total length of all cpdef
				totalCPDefLengthStat.addNumber(totalCPDefLength);
				double avgCPDefLength = totalCPDefLength/(double)counterPartialDefinitions.size();
				avgCpdefLengthStat.addNumber(avgCPDefLength);
				
				//accuracy, completeness, correctness
				int oldSizePosFort = cpdefPositiveCovered.size();
				int oldSizeNegFort = cpdefNegativeCovered.size();
				
				cpdefPositiveCovered.removeAll(cpTest);
				cpdefNegativeCovered.removeAll(cnTest);
				
				int commonPos = oldSizePosFort - cpdefPositiveCovered.size();
				int commonNeg = oldSizeNegFort - cpdefNegativeCovered.size();
				
				
				int cpFort = cpTest.size() - commonPos;	//positive examples covered by fortified definition
				int cnFort = cnTest.size() - commonNeg;	//negative examples covered by fortified definition
				
				//correctness = un/negSize
				double blindFortificationCorrectness = 100 *  (curFoldNegTestSet.size() - cnFort)/(double)(curFoldNegTestSet.size());
				
				//completeness = cp/posSize
				double blindFortificationCompleteness = 100 * (cpFort)/(double)curFoldPosTestSet.size();
				
				//accuracy = (cp + un)/(pos + neg)
				double blindFortificationAccuracy = 100 * (cpFort + (curFoldNegTestSet.size() - cnFort))/
						(double)(curFoldPosTestSet.size() + curFoldNegTestSet.size());
				
				//precision = right positive classified / total positive classified
				//          = cp / (cp + negAsPos)
				double blindPrecission = (cpFort + cnFort) == 0 ? 0 : cpFort / (double)(cpFort + cnFort);
				
				//recall = right positive classified / total positive
				double blindRecall = cpFort / (double)curFoldPosTestSet.size();
				
				double blindFmeasure = 100 * Heuristics.getFScore(blindRecall, blindPrecission);
				
				//STAT values for Blind fortification
				correctnessBlindFortifyStat.addNumber(blindFortificationCorrectness);
				completenessBlindFortifyStat.addNumber(blindFortificationCompleteness);
				accuracyBlindFortifyStat.addNumber(blindFortificationAccuracy);
				fmeasureBlindFortifyStat.addNumber(blindFmeasure);
				
				//end of blind fortification
				
				
				//---------------------------------------
				/// Fortification - TRAINGING COVERAGE				
				//---------------------------------------
				outputWriter("---------------------------------------------");
				outputWriter("Fortification - TRAINING COVERAGE");
				outputWriter("---------------------------------------------");
				//counter partial definition is sorted by training coverage by default ==> don't need to sort the cpdef set 
				multiStepFortificationResult[TRAINING_COVERAGE_INDEX] = Orthogonality.fortifyAccuracyMultiSteps(
						rs, concept, counterPartialDefinitions, curFoldPosTestSet, curFoldNegTestSet, true);
				
				for (int i=0; i<6; i++) {
					accuracyPercentageFortifyStepStat[TRAINING_COVERAGE_INDEX][i].
							addNumber(multiStepFortificationResult[TRAINING_COVERAGE_INDEX].fortificationAccuracy[i+1]);
					completenessPercentageFortifyStepStat[TRAINING_COVERAGE_INDEX][i].
							addNumber(multiStepFortificationResult[TRAINING_COVERAGE_INDEX].fortificationCompleteness[i+1]);
					correctnessPercentageFortifyStepStat[TRAINING_COVERAGE_INDEX][i].
							addNumber(multiStepFortificationResult[TRAINING_COVERAGE_INDEX].fortificationCorrectness[i+1]);
					fmeasurePercentageFortifyStepStat[TRAINING_COVERAGE_INDEX][i].
							addNumber(multiStepFortificationResult[TRAINING_COVERAGE_INDEX].fortificationFmeasure[i+1]);
				}
				
				
				//-----------------------------------------------------------------------------
				// JACCARD fortification
				// use Jaccard score to set the priority for the counter partial definitions
				//-----------------------------------------------------------------------------
				outputWriter("---------------------------------------------");
				outputWriter("Fortification - JACCARD DISJOINT (Distance)");
				outputWriter("---------------------------------------------");
				
				SortedSet<CELOE.PartialDefinition> jaccardDistanceFortificationCpdef = new TreeSet<CELOE.PartialDefinition>(new AdditionalValueComparator(2));
				jaccardDistanceFortificationCpdef.addAll(counterPartialDefinitions);
				
				outputWriter("Jaccard distance cpdef size: " + jaccardDistanceFortificationCpdef.size());
				
				//visit all counter partial definitions
				/*
				c = 1;
				for (CELOE.PartialDefinition cpdef : jaccardFortificationCpdef) {
					outputWriter(c++ + ". " + this.getCpdefString(cpdef)
							+ ", cp=" + rs.hasType(cpdef.getDescription(), curFoldPosTestSet)
							+ ", cn=" + rs.hasType(cpdef.getDescription(), curFoldNegTestSet));					
				}
				*/
				
				
				outputWriter("*** Jaccard distance values count:");
				for (Long value : jaccardValueCount.keySet()) 
					outputWriter(df.format(value/1000d) + ": " + jaccardValueCount.get(value));

				
				//calculate jaccard fortification
				multiStepFortificationResult[JACCARD_DISJOIN_INDEX] = Orthogonality.fortifyAccuracyMultiSteps(
						rs, concept, jaccardDistanceFortificationCpdef, curFoldPosTestSet, curFoldNegTestSet, true);
				
				for (int i=0; i<6; i++) {
					accuracyPercentageFortifyStepStat[JACCARD_DISJOIN_INDEX][i].
							addNumber(multiStepFortificationResult[JACCARD_DISJOIN_INDEX].fortificationAccuracy[i+1]);
					completenessPercentageFortifyStepStat[JACCARD_DISJOIN_INDEX][i].
							addNumber(multiStepFortificationResult[JACCARD_DISJOIN_INDEX].fortificationCompleteness[i+1]);
					correctnessPercentageFortifyStepStat[JACCARD_DISJOIN_INDEX][i].	
							addNumber(multiStepFortificationResult[JACCARD_DISJOIN_INDEX].fortificationCorrectness[i+1]);					
					fmeasurePercentageFortifyStepStat[JACCARD_DISJOIN_INDEX][i].
							addNumber(multiStepFortificationResult[JACCARD_DISJOIN_INDEX].fortificationFmeasure[i+1]);
				}

				
				//-----------------------------------------------------------------------------
				// JACCARD SCORE (overlap) fortification
				// Use Jaccard score to set the priority for the counter partial definitions
				//-----------------------------------------------------------------------------
				outputWriter("---------------------------------------------");
				outputWriter("Fortification - JACCARD OVERLAP (Score)");
				outputWriter("---------------------------------------------");

				//distance = 1 - score ==> sort by distance ascendingly = sort by score descendingly (more overlap to less overlap)
				SortedSet<CELOE.PartialDefinition> jaccardScoreFortificationCpdef = new TreeSet<CELOE.PartialDefinition>(new AdditionalValueComparator(2, false));	//sort ascendingly
				jaccardScoreFortificationCpdef.addAll(counterPartialDefinitions);
				
				outputWriter("Jaccard score cpdef size: " + jaccardScoreFortificationCpdef.size());

				//calculate jaccard fortification
				multiStepFortificationResult[JACCARD_OVERLAP_INDEX]= Orthogonality.fortifyAccuracyMultiSteps(
						rs, concept, jaccardScoreFortificationCpdef, curFoldPosTestSet, curFoldNegTestSet, true);
				
				for (int i=0; i<6; i++) {
					accuracyPercentageFortifyStepStat[JACCARD_OVERLAP_INDEX][i].
							addNumber(multiStepFortificationResult[JACCARD_OVERLAP_INDEX].fortificationAccuracy[i+1]);
					completenessPercentageFortifyStepStat[JACCARD_OVERLAP_INDEX][i].
							addNumber(multiStepFortificationResult[JACCARD_OVERLAP_INDEX].fortificationCompleteness[i+1]);
					correctnessPercentageFortifyStepStat[JACCARD_OVERLAP_INDEX][i].	
							addNumber(multiStepFortificationResult[JACCARD_OVERLAP_INDEX].fortificationCorrectness[i+1]);					
					fmeasurePercentageFortifyStepStat[JACCARD_OVERLAP_INDEX][i].
							addNumber(multiStepFortificationResult[JACCARD_OVERLAP_INDEX].fortificationFmeasure[i+1]);
				}
				
				
				//---------------------------------------
				// Fortification - VALIDATION SET
				//---------------------------------------	

				outputWriter("---------------------------------------------");
				outputWriter("Fortification VALIDATION SET");
				outputWriter("---------------------------------------------");

				
				SortedSet<CELOE.PartialDefinition> trainingFortificationCpdef = new TreeSet<CELOE.PartialDefinition>(new AdditionalValueComparator(3));

				trainingFortificationCpdef.addAll(counterPartialDefinitions);

				//print the counter partial definitions with score
				/*
				c = 1;
				for (CELOE.PartialDefinition cpdef : trainingFortificationCpdef) {
					outputWriter(c++ + ". " + this.getCpdefString(cpdef)
							+ ", cp=" + rs.hasType(cpdef.getDescription(), curFoldPosTestSet)
							+ ", cn=" + rs.hasType(cpdef.getDescription(), curFoldNegTestSet));
				}
				*/
				
				//calculate the multi-step fortification accuracy
				multiStepFortificationResult[FORTIFICATION_TRANING_INDEX] = Orthogonality.fortifyAccuracyMultiSteps(
						rs, concept, trainingFortificationCpdef, curFoldPosTestSet, curFoldNegTestSet, true);
				for (int i=0; i<6; i++) {
					accuracyPercentageFortifyStepStat[FORTIFICATION_TRANING_INDEX][i].
							addNumber(multiStepFortificationResult[FORTIFICATION_TRANING_INDEX].fortificationAccuracy[i+1]);
					completenessPercentageFortifyStepStat[FORTIFICATION_TRANING_INDEX][i].
							addNumber(multiStepFortificationResult[FORTIFICATION_TRANING_INDEX].fortificationCompleteness[i+1]);
					correctnessPercentageFortifyStepStat[FORTIFICATION_TRANING_INDEX][i].
							addNumber(multiStepFortificationResult[FORTIFICATION_TRANING_INDEX].fortificationCorrectness[i+1]);						
					fmeasurePercentageFortifyStepStat[FORTIFICATION_TRANING_INDEX][i].
							addNumber(multiStepFortificationResult[FORTIFICATION_TRANING_INDEX].fortificationFmeasure[i+1]);
				}
				
				
				
				//------------------------------------------
				// Fortification - SIMILARITY_ALL Examples
				//------------------------------------------				
				outputWriter("---------------------------------------------");
				outputWriter("Fortification - SIMILARITY");
				outputWriter("---------------------------------------------");

				
				SortedSet<CELOE.PartialDefinition> similarityFortificationCpdef = new TreeSet<CELOE.PartialDefinition>(new AdditionalValueComparator(4));
				similarityFortificationCpdef.addAll(counterPartialDefinitions);

				
				//print the counter partial definitions with score
				/*
				c = 1;
				for (CELOE.PartialDefinition cpdef : similarityFortificationCpdef) {
					outputWriter(c++ + ". " + this.getCpdefString(cpdef)
							+ ", cp=" + rs.hasType(cpdef.getDescription(), curFoldPosTestSet)
							+ ", cn=" + rs.hasType(cpdef.getDescription(), curFoldNegTestSet));
				}
				*/
				
				//calculate the multi-step fortification accuracy
				multiStepFortificationResult[SIMILARITY_ALL_INDEX] = Orthogonality.fortifyAccuracyMultiSteps(
						rs, concept, similarityFortificationCpdef, curFoldPosTestSet, curFoldNegTestSet, true);
				for (int i=0; i<6; i++) {
					accuracyPercentageFortifyStepStat[SIMILARITY_ALL_INDEX][i].
							addNumber(multiStepFortificationResult[SIMILARITY_ALL_INDEX].fortificationAccuracy[i+1]);
					completenessPercentageFortifyStepStat[SIMILARITY_ALL_INDEX][i].
							addNumber(multiStepFortificationResult[SIMILARITY_ALL_INDEX].fortificationCompleteness[i+1]);
					correctnessPercentageFortifyStepStat[SIMILARITY_ALL_INDEX][i].
							addNumber(multiStepFortificationResult[SIMILARITY_ALL_INDEX].fortificationCorrectness[i+1]);						
					fmeasurePercentageFortifyStepStat[SIMILARITY_ALL_INDEX][i].
							addNumber(multiStepFortificationResult[SIMILARITY_ALL_INDEX].fortificationFmeasure[i+1]);
				}
								
				
				//---------------------------------------
				// Fortification - SIMILARITY_POS-NEG
				//---------------------------------------				
				outputWriter("---------------------------------------------");
				outputWriter("Fortification - SIMILARITY_POS-NEG");
				outputWriter("---------------------------------------------");

				
				SortedSet<CELOE.PartialDefinition> similarityCombineFortificationCpdef = new TreeSet<CELOE.PartialDefinition>(new AdditionalValueComparator(7));
				similarityCombineFortificationCpdef.addAll(counterPartialDefinitions);
				
				//calculate the multi-step fortification accuracy
				multiStepFortificationResult[SIMILARITY_POS_NEG_INDEX] = Orthogonality.fortifyAccuracyMultiSteps(
						rs, concept, similarityCombineFortificationCpdef, curFoldPosTestSet, curFoldNegTestSet, true);
				for (int i=0; i<6; i++) {
					accuracyPercentageFortifyStepStat[SIMILARITY_POS_NEG_INDEX][i].
							addNumber(multiStepFortificationResult[SIMILARITY_POS_NEG_INDEX].fortificationAccuracy[i+1]);
					completenessPercentageFortifyStepStat[SIMILARITY_POS_NEG_INDEX][i].
							addNumber(multiStepFortificationResult[SIMILARITY_POS_NEG_INDEX].fortificationCompleteness[i+1]);
					correctnessPercentageFortifyStepStat[SIMILARITY_POS_NEG_INDEX][i].
							addNumber(multiStepFortificationResult[SIMILARITY_POS_NEG_INDEX].fortificationCorrectness[i+1]);						
					fmeasurePercentageFortifyStepStat[SIMILARITY_POS_NEG_INDEX][i].
							addNumber(multiStepFortificationResult[SIMILARITY_POS_NEG_INDEX].fortificationFmeasure[i+1]);
				}

				
				//------------------------------
				// Fortification - LABEL DATA				
				// 	LABLED TEST DATA
				//------------------------------
				//if there exists covered negative examples ==> check if there are any counter partial definitions 
				//can be used to remove covered negative examples
				
				int fixedNeg = 0;
				int fixedPos = 0;
				int noOfSelectedCpdef = 0;
				int totalSelectedCpdefLength = 0;
				double avgTrainingCoverageSelectedCpdef = 0;
				
				/**
				 * selected cpdef which are selected based on the test labled data
				 * given a set of wrong classified neg., select a set of cpdef to remove the wrong classified neg examples 
				 * the cpdef are sorted based on the training neg. example coverage
				 */
				TreeSet<CELOE.PartialDefinition> selectedCounterPartialDefinitions = new TreeSet<CELOE.PartialDefinition>(new CoverageComparator2());

				outputWriter("---------------------------------------------------------------");
				outputWriter("BLIND fortification - All counter partial defintions are used");
				outputWriter("---------------------------------------------------------------");

				
				if (cnTest.size() > 0) {
					
					TreeSet<Individual> tempCoveredNeg = new TreeSet<Individual>(new URIComparator());
					tempCoveredNeg.addAll(cnTest);
					
					TreeSet<Individual> tempUncoveredPos = new TreeSet<Individual>(new URIComparator());
					tempUncoveredPos.addAll(upTest);
					
					//check each counter partial definitions
					for (CELOE.PartialDefinition negCpdef : counterPartialDefinitions) {
						
						Description cpdef = negCpdef.getDescription().getChild(0);
						
						//set of neg examples covered by the counter partial definition
						Set<Individual> desCoveredNeg = new HashSet<Individual>(rs.hasType(cpdef, curFoldNegTestSet));
						
						//if the current counter partial definition can help to remove some neg examples
						//int oldNoOfCoveredNeg=tempCoveredNeg.size();
						if (tempCoveredNeg.removeAll(desCoveredNeg)) {
							
							//assign cn on test set to additionalValue
							selectedCounterPartialDefinitions.add(negCpdef);
							
							//check if it may remove some positive examples or not
							Set<Individual> desCoveredPos = new HashSet<Individual>(rs.hasType(cpdef, curFoldPosTestSet));
							tempUncoveredPos.addAll(desCoveredPos);
							
							//count the total number of counter partial definition selected and their total length
							noOfSelectedCpdef++;
							totalSelectedCpdefLength += cpdef.getLength();			
							avgTrainingCoverageSelectedCpdef += negCpdef.getCoverage();
						}
						
						if (tempCoveredNeg.size() == 0)
							break;
					}
					
					fixedNeg = cnTest.size() - tempCoveredNeg.size();
					fixedPos = tempUncoveredPos.size() - upTest.size();	
					avgTrainingCoverageSelectedCpdef /= noOfSelectedCpdef;
					

				}
				
				noOfLabelFortifySelectedCpdefStat.addNumber(noOfSelectedCpdef);
				labelFortifyCpdefTrainingCoverageStat.addNumber(avgTrainingCoverageSelectedCpdef);
				
				
				//-------------------------------
				//Labeled fortification
				//	stat calculation
				//-------------------------------
				
				//def length
				double labelFortifyDefinitionLength = concept.getLength() + totalSelectedCpdefLength + noOfSelectedCpdef;	//-1 from the selected cpdef and +1 for NOT
				labelFortifiedDefinitionLengthStat.addNumber(labelFortifyDefinitionLength);
				
				double avgLabelFortifyDefinitionLength = 0;
				
				if (noOfSelectedCpdef > 0) {
					avgLabelFortifyDefinitionLength = (double)totalSelectedCpdefLength/noOfSelectedCpdef;				
					avgLabelCpdefLengthStat.addNumber(totalSelectedCpdefLength/(double)noOfSelectedCpdef);
				}
				
				//accuracy = test accuracy + fortification adjustment
				double labelFortifyAccuracy = 100 * ((double)(correctTestExamples + fixedNeg - fixedPos)/
						(curFoldPosTestSet.size() + curFoldNegTestSet.size()));				
				accuracyLabelFortifyStat.addNumber(labelFortifyAccuracy);
				
				//completeness
				double labelFortifyCompleteness = 100 * ((double)(correctTestPosClassified - fixedPos)/curFoldPosTestSet.size());
				completenessLabelFortifyStat.addNumber(labelFortifyCompleteness);
				
				//correctness
				double labelFortifyCorrectness = 100 * ((double)(correctTestNegClassified + fixedNeg)/curFoldNegTestSet.size());				
				correctnessLabelFortifyStat.addNumber(labelFortifyCorrectness);
								
				//precision, recall, f-measure
				double labelFortifyPrecision = 0.0;	//percent of correct pos examples in total pos examples classified (= correct pos classified + neg as pos)
				if (((correctTestPosClassified - fixedPos) + (cnTest.size() - fixedNeg)) > 0)
					labelFortifyPrecision = (double)(correctTestPosClassified - fixedPos)/
							(correctTestPosClassified - fixedPos + cnTest.size() - fixedNeg);	//tmp3: neg as pos <=> false pos
				
				double labelFortifyRecall = (double)(correctTestPosClassified - fixedPos) / curFoldPosTestSet.size();
				
				double labelFortifyFmeasure = 100 * Heuristics.getFScore(labelFortifyRecall, labelFortifyPrecision);
				fmeasureLabelFortifyStat.addNumber(labelFortifyFmeasure);
				
				
				jaccardValueCount.clear();
				outputWriter("---------------------------------------------");								
				outputWriter("LABEL fortify counter partial definitions: ");			
				outputWriter("---------------------------------------------");			
				c = 1;
				//output the selected counter partial definition information				
				if (noOfSelectedCpdef > 0) {										
					for (CELOE.PartialDefinition cpdef : selectedCounterPartialDefinitions) {
						
						outputWriter(c++ + cpdef.getId() + ". " + cpdef.getId() + " " + this.getCpdefString(cpdef, baseURI, prefixes)
								+ ", cp=" + rs.hasType(cpdef.getDescription(), curFoldPosTestSet)
								+ ", cn=" + rs.hasType(cpdef.getDescription(), curFoldNegTestSet));
						
						
						long tmp_key = Math.round(cpdef.getAdditionValue(1) * 1000);	//jaccard value is hold by the 2nd element
						
						if (jaccardValueCount.containsKey(tmp_key)) {
							jaccardValueCount.put(tmp_key, jaccardValueCount.get(tmp_key)+1);
						}
						else
							jaccardValueCount.put(tmp_key, 1);
					}
					
					outputWriter("\n*** Jaccard distance values count for selected cpdefs:");
					for (Long value : jaccardValueCount.keySet()) 
						outputWriter(df.format(value/1000d) + ": " + jaccardValueCount.get(value));
					
				}				

				
				outputWriter("----------------------");
				
				int[] noOfCpdefMultiStep = Orthogonality.getMultiStepFortificationStep(counterPartialDefinitions.size());
				for (int i=0; i<6; i++) {
					noOfCpdefUsedMultiStepFortStat[i].addNumber(noOfCpdefMultiStep[i]);
					
					//minimal value of 50% of the cpdef used in the fortification
					//NOTE: no of cpdef descreases after added into other sets for fortification 
					//	Cause has not been investigated
					minOfHalfCpdef = (minOfHalfCpdef > noOfCpdefMultiStep[i])? noOfCpdefMultiStep[i] : minOfHalfCpdef;
					
					//minimal number of counter partial definitions till the current run
					//the above problem happens for this case as well
					minCpdef = (minCpdef > multiStepFortificationResult[i].fortificationAccuracyStepByStep.length)? 
							multiStepFortificationResult[i].fortificationAccuracyStepByStep.length : minCpdef;		
				}

				
				//create data structure to hold the fortification result				
				if (currFold == 0) {	//have  not initiallised
					accuracyHalfFullStep = new double[strategyNames.length][minOfHalfCpdef];	//4 strategies
					fmeasureHalfFullStep = new double[strategyNames.length][minOfHalfCpdef];
					
					accuracyFullStepStat = new Stat[noOfStrategies][minCpdef];
					fmeasureFullStepStat = new Stat[noOfStrategies][minCpdef];
					correctnessFullStepStat = new Stat[noOfStrategies][minCpdef];
					completenessFullStepStat = new Stat[noOfStrategies][minCpdef];
					
					//run a loop to create a set of Stat objects
					for (int i=0; i < noOfStrategies; i++) {
						for (int j = 0; j < minCpdef; j++) {
							accuracyFullStepStat[i][j] = new Stat();
							fmeasureFullStepStat[i][j] = new Stat();
							correctnessFullStepStat[i][j] = new Stat();
							completenessFullStepStat[i][j] = new Stat();
						}
					}
				}

				
				//sum up the accuracy and fmeasure directly, do not use Stat for simplicity 
				outputWriter("*** Calculate full step accuracy: minCpdef = " + minCpdef);

				outputWriter("\tcounter partial deifnition size=" + 
						counterPartialDefinitions.size());
				
				//calculate accuracy, fmeasure  of the cpdef HALF FULL STEP
				for (int i=0; i<strategyNames.length; i++) { 
					for (int j=0; j<minOfHalfCpdef; j++) {
						//calculate the accuracy and fmeasure of full step fortification
						accuracyHalfFullStep[i][j] += multiStepFortificationResult[i].fortificationAccuracyStepByStep[j];
						fmeasureHalfFullStep[i][j] += multiStepFortificationResult[i].fortificationFmeasureStepByStep[j];
					}
				}
				
				//calculate accuracy, fmeasure FULL STEP by STEP
				for (int i=0; i<strategyNames.length; i++) { 
					for (int j=0; j<minCpdef; j++) {
						//calculate the accuracy and fmeasure of full step fortification
						accuracyFullStepStat[i][j].addNumber(multiStepFortificationResult[i].fortificationAccuracyStepByStep[j]);
						fmeasureFullStepStat[i][j].addNumber(multiStepFortificationResult[i].fortificationFmeasureStepByStep[j]);
						correctnessFullStepStat[i][j].addNumber(multiStepFortificationResult[i].fortificationCorrectnessStepByStep[j]);
						completenessFullStepStat[i][j].addNumber(multiStepFortificationResult[i].fortificationCompletenessStepByStep[j]);
					}
				}
				
				
				
				//--------------------------------
				//output fold stat. information
				// of the CURRENT fold
				//--------------------------------
				outputWriter("Fold " + currFold + "/" + folds + ":");
				outputWriter("  concept: " + concept);
				
				outputWriter("  training: " + trainingCorrectPosClassified + "/" + trainingPosSize + 
						" positive and " + trainingCorrectNegClassified + "/" + trainingNegSize + " negative examples");				
				outputWriter("  testing: " + correctTestPosClassified + "/" + testingPosSize + " correct positives, " 
						+ correctTestNegClassified + "/" + testingNegSize + " correct negatives");
				
				
				//general learning statistics
				outputWriter("  runtime: " + df.format(algorithmDuration/(double)1000000000) + "s");
				outputWriter("  learning time: " + df.format(learningMili/(double)1000) + "s");
				outputWriter("  total number of descriptions: " + la.getTotalNumberOfDescriptionsGenerated());
				outputWriter("  total number pdef: " + noOfPdef + " (used by parcelex: " + noOfUsedPdef + ")");
				outputWriter("  total number of cpdef: " + noOfCpdef + " (used by parcelex: " + noOfUsedCpdef + ")");
				
				
				//pdef + cpdef
				outputWriter("  def. length: " + df.format(concept.getLength()));
				outputWriter("  def. length label fortify: " + df.format(labelFortifyDefinitionLength));
				outputWriter("  avg. def. length label fortify: " + df.format(avgLabelFortifyDefinitionLength));				
				outputWriter("  total cpdef. length: " + df.format(totalCPDefLength));
				outputWriter("  avg. pdef length: " + df.format(avgPdefLength));
				outputWriter("  avg. cpdef. length: " + df.format(avgCPDefLength));				
				outputWriter("  avg. cpdef training coverage: " + statOutput(df, avgCpdefCoverageTrainingStat, "%"));

				outputWriter("  no of cpdef used in the multi-step fortification: " + arrayToString(noOfCpdefMultiStep));
				
				//f-measure
				outputWriter("  f-measure training set: " + df.format(currFmeasureTraining));
				outputWriter("  f-measure test set: " + df.format(currFmeasureTest));
				outputWriter("  f-measure on test set label fortification: " + df.format(labelFortifyFmeasure)); 
				outputWriter("  f-measure on test set blind fortification: " + df.format(blindFmeasure));
				
				
				//accuracy
				outputWriter("  accuracy test: " + df.format(currAccuracy) +  
						"% (corr:"+ df.format(testingCorrectness) + 
						"%, comp:" + df.format(testingCompleteness) + "%) --- " + 
						df.format(trainingAccuracy) + "% (corr:"+ trainingCorrectness + 
						"%, comp:" + df.format(trainingCompleteness) + "%) on training set");
				
				outputWriter("  accuracy label fortification: " + df.format(labelFortifyAccuracy) +
						"%, correctness: " + df.format(labelFortifyCorrectness) +
						"%, completeness: " + df.format(labelFortifyCompleteness) + "%");
				
				outputWriter("  accuracy blind fortification: " + df.format(blindFortificationAccuracy) +
						"%, correctness: " + df.format(blindFortificationCorrectness) +
						"%, completeness: " + df.format(blindFortificationCompleteness) + "%");
				
				
				//output the fortified accuracy at 5%, 10%, ..., 50%				
				for (int i=0; i<strategyNames.length; i++) {
					outputWriter("  multi-step fortified accuracy by " + strategyNames[i] + ": " 
							+ arrayToString(df, multiStepFortificationResult[i].fortificationAccuracy)
							+ " -- correctness: " + arrayToString(df, multiStepFortificationResult[i].fortificationCorrectness)
							+ " -- completeness: " + arrayToString(df, multiStepFortificationResult[i].fortificationCompleteness)
						);
				}	//output fortified accuracy at 5%, 10%, ..., 50%
				
				
				outputWriter("  number of cpdef use in the label fortification: " + noOfSelectedCpdef);
				outputWriter("  avg. training coverage of the selected cpdef. in the label fortification: " + df.format(avgTrainingCoverageSelectedCpdef));

				//----------------------------------------------
				//output fold accumulative stat. information
				//----------------------------------------------
				outputWriter("----------");
				outputWriter("Aggregate data from fold 0 to fold " + currFold + "/" + folds);
				outputWriter("  runtime: " + statOutput(df, runtime, "s"));
				outputWriter("  learning time parcelex: " + statOutput(df, learningTime, "s"));
				outputWriter("  no of descriptions: " + statOutput(df, totalNumberOfDescriptions, ""));
				
				outputWriter("  no of total pdef: " + statOutput(df, noOfPdefStat, ""));
				outputWriter("  no of used pdef: " + statOutput(df, noOfUsedPdefStat, ""));
				outputWriter("  avg pdef length: " + statOutput(df, avgUsedPartialDefinitionLengthStat, ""));
				outputWriter("  no of cpdef: " + statOutput(df, noOfCpdefStat, ""));
				outputWriter("  avg cpdef length: " + statOutput(df, avgCpdefLengthStat, ""));				
				outputWriter("  avg. def. length: " + statOutput(df, length, ""));
				
				outputWriter("  avg. label fortified def. length: " + statOutput(df, labelFortifiedDefinitionLengthStat, ""));
				outputWriter("  avg. length of the cpdefs used in the label fortification: " + statOutput(df, avgLabelCpdefLengthStat, ""));
				
				outputWriter("  F-Measure on training set: " + statOutput(df, fMeasureTraining, "%"));
				outputWriter("  F-Measure on test set: " + statOutput(df, fMeasure, "%"));
				outputWriter("  F-Measure on test set fortified: " + statOutput(df, fmeasureLabelFortifyStat, "%"));
				outputWriter("  predictive accuracy on training set: " + statOutput(df, accuracyTraining, "%") + 
						" -- correctness: " + statOutput(df, trainingCorrectnessStat, "%") +
						"-- completeness: " + statOutput(df, trainingCompletenessStat, "%"));
				outputWriter("  predictive accuracy on test set: " + statOutput(df, accuracy, "%") +
						" -- correctness: " + statOutput(df, testingCorrectnessStat, "%") +
						"-- completeness: " + statOutput(df, testingCompletenessStat, "%"));				
				
				outputWriter("  label fortification accuracy on test set: " + statOutput(df, accuracyLabelFortifyStat, "%") +
						" -- fortified correctness: " + statOutput(df, correctnessLabelFortifyStat, "%") +
						"-- fortified completeness: " + statOutput(df, completenessLabelFortifyStat, "%"));

				outputWriter("  blind fortification accuracy on test set: " + statOutput(df, accuracyBlindFortifyStat, "%") +
						" -- fortified correctness: " + statOutput(df, correctnessBlindFortifyStat, "%") +
						"-- fortified completeness: " + statOutput(df, completenessBlindFortifyStat, "%"));
				
				for (int i=0; i< strategyNames.length; i++) {					
					outputWriter("  multi-step fortified accuracy by " + strategyNames[i] + ":");
					
					outputWriter("\t 5%: " + statOutput(df, accuracyPercentageFortifyStepStat[i][0], "%")
							+ " -- correctness: " + statOutput(df, correctnessPercentageFortifyStepStat[i][0], "%")
							+ " -- completeness: " + statOutput(df, completenessPercentageFortifyStepStat[i][0], "%")
							);
					outputWriter("\t 10%: " + statOutput(df, accuracyPercentageFortifyStepStat[i][1], "%")
							+ " -- correctness: " + statOutput(df, correctnessPercentageFortifyStepStat[i][1], "%")
							+ " -- completeness: " + statOutput(df, completenessPercentageFortifyStepStat[i][1], "%")
							);
					outputWriter("\t 20%: " + statOutput(df, accuracyPercentageFortifyStepStat[i][2], "%")
							+ " -- correctness: " + statOutput(df, correctnessPercentageFortifyStepStat[i][2], "%")
							+ " -- completeness: " + statOutput(df, completenessPercentageFortifyStepStat[i][2], "%")
							);
					outputWriter("\t 30%: " + statOutput(df, accuracyPercentageFortifyStepStat[i][3], "%")
							+ " -- correctness: " + statOutput(df, correctnessPercentageFortifyStepStat[i][3], "%")
							+ " -- completeness: " + statOutput(df, completenessPercentageFortifyStepStat[i][3], "%")
							);
					outputWriter("\t 40%: " + statOutput(df, accuracyPercentageFortifyStepStat[i][4], "%")
							+ " -- correctness: " + statOutput(df, correctnessPercentageFortifyStepStat[i][4], "%")
							+ " -- completeness: " + statOutput(df, completenessPercentageFortifyStepStat[i][4], "%")
							);
					outputWriter("\t 50%: " + statOutput(df, accuracyPercentageFortifyStepStat[i][5], "%")
							+ " -- correctness: " + statOutput(df, correctnessPercentageFortifyStepStat[i][5], "%")
							+ " -- completeness: " + statOutput(df, completenessPercentageFortifyStepStat[i][5], "%")
							);
					
				}

				outputWriter("  avg. no of counter partial definition: " + statOutput(df, noOfCpdefStat, ""));
				outputWriter("  avg. no of counter partial definition used in label fortification: " + statOutput(df, noOfLabelFortifySelectedCpdefStat,""));
				
				outputWriter("  no of cpdef used in multi-step fortification:");
				outputWriter("\t5%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[0], ""));
				outputWriter("\t10%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[1], ""));
				outputWriter("\t20%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[2], ""));
				outputWriter("\t30%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[3], ""));
				outputWriter("\t40%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[4], ""));
				outputWriter("\t50%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[5], ""));
					
							
				outputWriter("----------------------");
				

				//sleep after each run (fer MBean collecting information purpose)
				try {
					Thread.sleep(5000);
				}
				catch (InterruptedException e) {
					e.printStackTrace();
				}

			}	//for k folds


			//---------------------------------
			//end of k-fold cross validation
			//output result of the k-fold 
			//---------------------------------

			//final cumulative statistical data of a run
			outputWriter("");
			outputWriter("Finished the " + (kk+1) + "/" + noOfRuns + " of " + folds + "-folds cross-validation.");
			outputWriter("  runtime: " + statOutput(df, runtime, "s"));
			outputWriter("  learning time parcelex: " + statOutput(df, learningTime, "s"));
			outputWriter("  no of descriptions: " + statOutput(df, totalNumberOfDescriptions, ""));
			
			outputWriter("  no of total pdef: " + statOutput(df, noOfPdefStat, ""));
			outputWriter("  no of used pdef: " + statOutput(df, noOfUsedPdefStat, ""));
			outputWriter("  avg pdef length: " + statOutput(df, avgUsedPartialDefinitionLengthStat, ""));
			outputWriter("  no of cpdef: " + statOutput(df, noOfCpdefStat, ""));
			outputWriter("  avg cpdef length: " + statOutput(df, avgCpdefLengthStat, ""));									
			outputWriter("  avg. def. length: " + statOutput(df, length, ""));
			
			outputWriter("  avg. label fortified def. length: " + statOutput(df, labelFortifiedDefinitionLengthStat, ""));
			outputWriter("  avg. cpdef used in the label fortification: " + statOutput(df, avgLabelCpdefLengthStat, ""));
			outputWriter("  F-Measure on training set: " + statOutput(df, fMeasureTraining, "%"));
			outputWriter("  F-Measure on test set: " + statOutput(df, fMeasure, "%"));
			outputWriter("  F-Measure on test set fortified: " + statOutput(df, fmeasureLabelFortifyStat, "%"));
			outputWriter("  predictive accuracy on training set: " + statOutput(df, accuracyTraining, "%") + 
					"\n\t-- correctness: " + statOutput(df, trainingCorrectnessStat, "%") +
					"\n\t-- completeness: " + statOutput(df, trainingCompletenessStat, "%"));
			outputWriter("  predictive accuracy on test set: " + statOutput(df, accuracy, "%") +
					"\n\t-- correctness: " + statOutput(df, testingCorrectnessStat, "%") +
					"\n\t-- completeness: " + statOutput(df, testingCompletenessStat, "%"));				
			
			outputWriter("  fortified accuracy on test set: " + statOutput(df, accuracyLabelFortifyStat, "%") +
					"\n\t-- fortified correctness: " + statOutput(df, correctnessLabelFortifyStat, "%") +
					"\n\t-- fortified completeness: " + statOutput(df, completenessLabelFortifyStat, "%"));

			outputWriter("  blind fortified accuracy on test set: " + statOutput(df, accuracyBlindFortifyStat, "%") +
					"\n\t-- fortified correctness: " + statOutput(df, correctnessBlindFortifyStat, "%") +
					"\n\t-- fortified completeness: " + statOutput(df, completenessBlindFortifyStat, "%"));

			for (int i=0; i< strategyNames.length; i++) {
				
				outputWriter("  multi-step fortified accuracy by " + strategyNames[i] + ":");
				
				outputWriter("\t 5%: " + statOutput(df, accuracyPercentageFortifyStepStat[i][0], "%")
						+ " -- correctness: " + statOutput(df, correctnessPercentageFortifyStepStat[i][0], "%")
						+ " -- completeness: " + statOutput(df, completenessPercentageFortifyStepStat[i][0], "%")
						);
				outputWriter("\t 10%: " + statOutput(df, accuracyPercentageFortifyStepStat[i][1], "%")
						+ " -- correctness: " + statOutput(df, correctnessPercentageFortifyStepStat[i][1], "%")
						+ " -- completeness: " + statOutput(df, completenessPercentageFortifyStepStat[i][1], "%")
						);
				outputWriter("\t 20%: " + statOutput(df, accuracyPercentageFortifyStepStat[i][2], "%")
						+ " -- correctness: " + statOutput(df, correctnessPercentageFortifyStepStat[i][2], "%")
						+ " -- completeness: " + statOutput(df, completenessPercentageFortifyStepStat[i][2], "%")
						);
				outputWriter("\t 30%: " + statOutput(df, accuracyPercentageFortifyStepStat[i][3], "%")
						+ " -- correctness: " + statOutput(df, correctnessPercentageFortifyStepStat[i][3], "%")
						+ " -- completeness: " + statOutput(df, completenessPercentageFortifyStepStat[i][3], "%")
						);
				outputWriter("\t 40%: " + statOutput(df, accuracyPercentageFortifyStepStat[i][4], "%")
						+ " -- correctness: " + statOutput(df, correctnessPercentageFortifyStepStat[i][4], "%")
						+ " -- completeness: " + statOutput(df, completenessPercentageFortifyStepStat[i][4], "%")
						);
				outputWriter("\t 50%: " + statOutput(df, accuracyPercentageFortifyStepStat[i][5], "%")
						+ " -- correctness: " + statOutput(df, correctnessPercentageFortifyStepStat[i][5], "%")
						+ " -- completeness: " + statOutput(df, completenessPercentageFortifyStepStat[i][5], "%")
						);
				
			}

			outputWriter("  total no of counter partial definition: " + statOutput(df, noOfCpdefStat, ""));
			outputWriter("  avg. no of counter partial definition used in label fortification: " + statOutput(df, noOfLabelFortifySelectedCpdefStat,""));
			
			outputWriter("  no of cpdef used in multi-step fortification:");
			outputWriter("\t5%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[0], ""));
			outputWriter("\t10%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[1], ""));
			outputWriter("\t20%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[2], ""));
			outputWriter("\t30%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[3], ""));
			outputWriter("\t40%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[4], ""));
			outputWriter("\t50%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[5], ""));
			
			
			
			//-----------------------------------------
			//this is for copying to word document
			//-----------------------------------------
			outputWriter("======= RESULT SUMMARY PERCENTAGE (5%, 10%, 20%, 30%, 40%, 50%) =======");
			
			//fmeasure			
			outputWriter("\n***f-measure test/blind");
			outputWriter(df.format(fMeasure.getMean()) + "  " + df.format(fMeasure.getStandardDeviation())
				+ "\n" + df.format(fmeasureBlindFortifyStat.getMean()) + "  " + df.format(fmeasureBlindFortifyStat.getStandardDeviation())
				);
			
			//for each strategy: strategy name, f-measure (5-50%)
			for (int i=0; i<strategyNames.length; i++) {				
				outputWriter("fmeasure - " + strategyNames[i] + " by percentage (5%, 10%, 20%, 30%, 40%, 50%)");				
				for (int j=0; j<6; j++)
					outputWriter(df.format(fmeasurePercentageFortifyStepStat[i][j].getMean()) 
							+ "\n" + df.format(fmeasurePercentageFortifyStepStat[i][j].getStandardDeviation()));
			}
			
			
			//accuracy
			outputWriter("\n***accuracy test/blind");
			outputWriter(df.format(accuracy.getMean()) + "  " + df.format(accuracy.getStandardDeviation())
				+ "\n" + df.format(accuracyBlindFortifyStat.getMean()) + "  " + df.format(accuracyBlindFortifyStat.getStandardDeviation())
				);
			
			//for each strategy: strategy name, accuracy (5-50%)
			for (int i=0; i<strategyNames.length; i++) {				
				outputWriter("accuracy - " + strategyNames[i] + " by percentage (5%, 10%, 20%, 30%, 40%, 50%)");				
				for (int j=0; j<6; j++)
					outputWriter(df.format(accuracyPercentageFortifyStepStat[i][j].getMean()) 
							+ "\n" + df.format(accuracyPercentageFortifyStepStat[i][j].getStandardDeviation()));
			}
			
			//correctness
			outputWriter("\n***correctness test/blind");
			outputWriter(df.format(testingCorrectnessStat.getMean()) + "  " + df.format(testingCorrectnessStat.getStandardDeviation())
				+ "\n" + df.format(correctnessBlindFortifyStat.getMean()) + "  " + df.format(correctnessBlindFortifyStat.getStandardDeviation())
				);
			
			//for each strategy: strategy name, accuracy (5-50%)
			for (int i=0; i<strategyNames.length; i++) {				
				outputWriter("correctness - " + strategyNames[i] + " by percentage (5%, 10%, 20%, 30%, 40%, 50%)");				
				for (int j=0; j<6; j++)
					outputWriter(df.format(correctnessPercentageFortifyStepStat[i][j].getMean()) 
							+ "\n" + df.format(correctnessPercentageFortifyStepStat[i][j].getStandardDeviation()));
			}
			
			//completeness
			outputWriter("\n***completeness test/blind");
			outputWriter(df.format(testingCompletenessStat.getMean()) + "  " + df.format(testingCompletenessStat.getStandardDeviation())
				+ "\n" + df.format(completenessBlindFortifyStat.getMean()) + "  " + df.format(completenessBlindFortifyStat.getStandardDeviation())
				);
			
			//for each strategy: strategy name, accuracy (5-50%)
			for (int i=0; i<strategyNames.length; i++) {				
				outputWriter("completeness - " + strategyNames[i] + " by percentage (5%, 10%, 20%, 30%, 40%, 50%)");				
				for (int j=0; j<6; j++)
					outputWriter(df.format(completenessPercentageFortifyStepStat[i][j].getMean()) 
							+ "\n" + df.format(completenessPercentageFortifyStepStat[i][j].getStandardDeviation()));
			}
			
			outputWriter("======= RESULT SUMMARY FULL STEP =======");
			outputWriter("======= Fmeasure full steps =======");
			for (int i=0; i<strategyNames.length; i++) {	//6 strategies
				outputWriter(strategyNames[i] + "(" + minCpdef + "/" + fmeasureFullStepStat[0].length + ")");	//fmeasureFullStepStat[0].length == minCpdef???
				for (int j=0; j<minCpdef; j++) {
					outputWriter(df.format(fmeasureFullStepStat[i][j].getMean()) + "\t"
							+ df.format(fmeasureFullStepStat[i][j].getStandardDeviation()));
				}
				outputWriter("\n");
			}
			
			
			outputWriter("======= Accuracy full steps =======");
			for (int i=0; i<strategyNames.length; i++) {	//6 strategies
				outputWriter(strategyNames[i] + "(" + minCpdef + "/" + accuracyFullStepStat[0].length + ")");
				for (int j=0; j<minCpdef; j++) {
					outputWriter(df.format(accuracyFullStepStat[i][j].getMean()) + "\t"
							+ df.format(accuracyFullStepStat[i][j].getStandardDeviation()));
				}
				outputWriter("\n");
			}

			

			outputWriter("======= Correctness full steps =======");
			for (int i=0; i<strategyNames.length; i++) {	//6 strategies
				outputWriter(strategyNames[i] + "(" + minCpdef + "/" + correctnessFullStepStat[0].length +")");
				for (int j=0; j<minCpdef; j++) {
					outputWriter(df.format(correctnessFullStepStat[i][j].getMean()) + "\t"
							+ df.format(correctnessFullStepStat[i][j].getStandardDeviation()));
				}
				outputWriter("\n");
			}
			
			
			outputWriter("======= Completeness full steps =======");
			for (int i=0; i<strategyNames.length; i++) {	//4 strategies
				outputWriter(strategyNames[i] + "(" + minCpdef + "/" + completenessFullStepStat[0].length +")");
				for (int j=0; j<minCpdef; j++) {
					outputWriter(df.format(completenessFullStepStat[i][j].getMean()) + "\t"
							+ df.format(completenessFullStepStat[i][j].getStandardDeviation()));
				}
				outputWriter("\n");
			}
			
			
			//---------------------------------------------------
			// this is used to copy into EXCEL to draw charts 
			//---------------------------------------------------			
			outputWriter("======= FULL STEP SUMMARY ALL strategies & dimentions=======");
			//accuracy(6), correctness(6), completeness(6), fmeasure(6)
			
			String strategies = "Strategies: ";
			
			String noCpdefFortificationAcc = "";
			String noCpdefFortificationCor = "";
			String noCpdefFortificationComp = "";
			String noCpdefFortificationFm = "";
			
			
			String allCpdefFortificationAcc = "";
			String allCpdefFortificationCor = "";
			String allCpdefFortificationComp = "";
			String allCpdefFortificationFm = "";
			
			for (int i=0; i<strategyNames.length; i++) {	//6 strategies										
				strategies += strategyNames[i] + ", ";
				
				//test data (no fortification
				noCpdefFortificationAcc += df.format(accuracy.getMean()) + "\t" + df.format(accuracy.getStandardDeviation()) + "\t";
				noCpdefFortificationCor += df.format(testingCorrectnessStat.getMean()) + "\t" + df.format(testingCorrectnessStat.getStandardDeviation()) + "\t";
				noCpdefFortificationComp += df.format(testingCompletenessStat.getMean()) + "\t" + df.format(testingCompletenessStat.getStandardDeviation()) + "\t";
				noCpdefFortificationFm += df.format(fMeasure.getMean()) + "\t" + df.format(fMeasure.getStandardDeviation()) + "\t";
				
				//all cpdef fortification
				allCpdefFortificationAcc += df.format(accuracyBlindFortifyStat.getMean()) + "\t" + df.format(accuracyBlindFortifyStat.getStandardDeviation()) + "\t";
				allCpdefFortificationCor += df.format(correctnessBlindFortifyStat.getMean()) + "\t" + df.format(correctnessBlindFortifyStat.getStandardDeviation()) + "\t";
				allCpdefFortificationComp += df.format(completenessBlindFortifyStat.getMean()) + "\t" + df.format(completenessBlindFortifyStat.getStandardDeviation()) + "\t";
				allCpdefFortificationFm += df.format(fmeasureBlindFortifyStat.getMean()) + "\t" + df.format(fmeasureBlindFortifyStat.getStandardDeviation()) + "\t";
			}
			
			outputWriter(strategies);
			
			outputWriter(noCpdefFortificationAcc + "\t" + noCpdefFortificationCor + "\t" + noCpdefFortificationComp + "\t" + noCpdefFortificationFm);
			
			for (int j=0; j<minCpdef; j++) {	//all cpdefs	
				String allResult = "";	//contains all data of one cpdef
				
				String bestAcc = "\t", bestCor = "\t", bestComp = "\t", bestFm = "";
				
				if ((j == Math.round(Math.ceil(noOfLabelFortifySelectedCpdefStat.getMin()))) || 
						(j == Math.round(Math.ceil(noOfLabelFortifySelectedCpdefStat.getMax()))) || 
						(j == Math.round(Math.ceil(noOfLabelFortifySelectedCpdefStat.getMean())))) {
					
					 bestAcc = df.format(accuracyLabelFortifyStat.getMean()) + "\t";
					 bestCor = df.format(correctnessLabelFortifyStat.getMean()) + "\t";
					 bestComp = df.format(completenessLabelFortifyStat.getMean()) + "\t";
					 bestFm = df.format(fmeasureLabelFortifyStat.getMean()) + "\t";
				}
				
				//accuracy
				for (int i=0; i<strategyNames.length; i++) {	//6 strategies										
					allResult += df.format(accuracyFullStepStat[i][j].getMean()) + "\t"
						+ df.format(accuracyFullStepStat[i][j].getStandardDeviation()) + "\t";
				}		
				
				allResult += bestAcc;
				
				//correctness
				for (int i=0; i<strategyNames.length; i++) {	//6 strategies										
					allResult += df.format(correctnessFullStepStat[i][j].getMean()) + "\t"
						+ df.format(correctnessFullStepStat[i][j].getStandardDeviation()) + "\t";
				}

				allResult += bestCor;
				
				//completeness
				for (int i=0; i<strategyNames.length; i++) {	//6 strategies										
					allResult += df.format(completenessFullStepStat[i][j].getMean()) + "\t"
						+ df.format(completenessFullStepStat[i][j].getStandardDeviation()) + "\t";
				}
				
				allResult += bestComp;
				
				//f-measure
				for (int i=0; i<strategyNames.length; i++) {	//6 strategies										
					allResult += df.format(fmeasureFullStepStat[i][j].getMean()) + "\t"
						+ df.format(fmeasureFullStepStat[i][j].getStandardDeviation()) + "\t";
				}
				
				allResult += bestFm;
				
				outputWriter(allResult);
				
			}

			//blind fortification data
			outputWriter(allCpdefFortificationAcc + "\t" + allCpdefFortificationCor + "\t" + allCpdefFortificationComp + "\t" + allCpdefFortificationFm);
		
			//---------------------------------
			//end of DATA for GRAPH generation
			//---------------------------------
			


			//aggrerate stat information for multi-run
			//TODO: need to be revised
			if (noOfRuns > 1) {		
				//runtime
				runtimeAvg.addNumber(runtime.getMean());
				runtimeMax.addNumber(runtime.getMax());
				runtimeMin.addNumber(runtime.getMin());
				runtimeDev.addNumber(runtime.getStandardDeviation());
				
				//learning time
				learningTimeAvg.addNumber(learningTime.getMean());
				learningTimeDev.addNumber(learningTime.getStandardDeviation());
				learningTimeMax.addNumber(learningTime.getMax());
				learningTimeMin.addNumber(learningTime.getMin());		
	
				//number of partial definitions			
				noOfPartialDefAvg.addNumber(noOfPdefStat.getMean());
				noOfPartialDefMax.addNumber(noOfPdefStat.getMax());
				noOfPartialDefMin.addNumber(noOfPdefStat.getMin());
				noOfPartialDefDev.addNumber(noOfPdefStat.getStandardDeviation());
						
				//avg partial definition length
				avgPartialDefLenAvg.addNumber(avgUsedPartialDefinitionLengthStat.getMean());
				avgPartialDefLenMax.addNumber(avgUsedPartialDefinitionLengthStat.getMax());
				avgPartialDefLenMin.addNumber(avgUsedPartialDefinitionLengthStat.getMin());
				avgPartialDefLenDev.addNumber(avgUsedPartialDefinitionLengthStat.getStandardDeviation());
				
				avgFortifiedPartialDefLenAvg.addNumber(labelFortifiedDefinitionLengthStat.getMean());
				avgFortifiedPartialDefLenMax.addNumber(labelFortifiedDefinitionLengthStat.getMax());
				avgFortifiedPartialDefLenMin.addNumber(labelFortifiedDefinitionLengthStat.getMin());
				avgFortifiedPartialDefLenDev.addNumber(labelFortifiedDefinitionLengthStat.getStandardDeviation());
				
				
				defLenAvg.addNumber(length.getMean());			
				defLenMax.addNumber(length.getMax());
				defLenMin.addNumber(length.getMin());
				defLenDev.addNumber(length.getStandardDeviation());
				
				//counter partial definitions
				noOfCounterPartialDefinitionsAvg.addNumber(noOfCpdefStat.getMean());
				noOfCounterPartialDefinitionsDev.addNumber(noOfCpdefStat.getStandardDeviation());
				noOfCounterPartialDefinitionsMax.addNumber(noOfCpdefStat.getMax());
				noOfCounterPartialDefinitionsMin.addNumber(noOfCpdefStat.getMin());
				
				noOfCounterPartialDefinitionsUsedAvg.addNumber(noOfCpdefUsedStat.getMean());
				noOfCounterPartialDefinitionsUsedDev.addNumber(noOfCpdefUsedStat.getStandardDeviation());
				noOfCounterPartialDefinitionsUsedMax.addNumber(noOfCpdefUsedStat.getMax());
				noOfCounterPartialDefinitionsUsedMin.addNumber(noOfCpdefUsedStat.getMin());			
							
				//training accuracy
				trainingAccAvg.addNumber(accuracyTraining.getMean());			
				trainingAccDev.addNumber(accuracyTraining.getStandardDeviation());
				trainingAccMax.addNumber(accuracyTraining.getMax());
				trainingAccMin.addNumber(accuracyTraining.getMin());
				
				trainingCorAvg.addNumber(trainingCorrectnessStat.getMean());			
				trainingCorDev.addNumber(trainingCorrectnessStat.getStandardDeviation());
				trainingCorMax.addNumber(trainingCorrectnessStat.getMax());
				trainingCorMin.addNumber(trainingCorrectnessStat.getMin());
				
				trainingComAvg.addNumber(trainingCompletenessStat.getMean());
				trainingComDev.addNumber(trainingCompletenessStat.getStandardDeviation());
				trainingComMax.addNumber(trainingCompletenessStat.getMax());
				trainingComMin.addNumber(trainingCompletenessStat.getMin());
				
				testingAccAvg.addNumber(accuracy.getMean());
				testingAccMax.addNumber(accuracy.getMax());
				testingAccMin.addNumber(accuracy.getMin());
				testingAccDev.addNumber(accuracy.getStandardDeviation());
				
				//fortify accuracy
				fortifyAccAvg.addNumber(accuracyLabelFortifyStat.getMean());
				fortifyAccMax.addNumber(accuracyLabelFortifyStat.getMax());
				fortifyAccMin.addNumber(accuracyLabelFortifyStat.getMin());
				fortifyAccDev.addNumber(accuracyLabelFortifyStat.getStandardDeviation());
				
				
				testingCorAvg.addNumber(testingCorrectnessStat.getMean());
				testingCorDev.addNumber(testingCorrectnessStat.getStandardDeviation());
				testingCorMax.addNumber(testingCorrectnessStat.getMax());
				testingCorMin.addNumber(testingCorrectnessStat.getMin());
				
				//fortify correctness
				fortifyCorAvg.addNumber(correctnessLabelFortifyStat.getMean());
				fortifyCorMax.addNumber(correctnessLabelFortifyStat.getMax());
				fortifyCorMin.addNumber(correctnessLabelFortifyStat.getMin());
				fortifyCorDev.addNumber(correctnessLabelFortifyStat.getStandardDeviation());
								
				testingComAvg.addNumber(testingCompletenessStat.getMean());
				testingComDev.addNumber(testingCompletenessStat.getStandardDeviation());
				testingComMax.addNumber(testingCompletenessStat.getMax());
				testingComMin.addNumber(testingCompletenessStat.getMin());
				
				//fortify completeness (level 1 fixing does not change the completeness
				fortifyComAvg.addNumber(completenessLabelFortifyStat.getMean());
				fortifyComMax.addNumber(completenessLabelFortifyStat.getMax());
				fortifyComMin.addNumber(completenessLabelFortifyStat.getMin());
				fortifyComDev.addNumber(completenessLabelFortifyStat.getStandardDeviation());
				
				
				testingFMeasureAvg.addNumber(fMeasure.getMean());
				testingFMeasureDev.addNumber(fMeasure.getStandardDeviation());
				testingFMeasureMax.addNumber(fMeasure.getMax());
				testingFMeasureMin.addNumber(fMeasure.getMin());	
							
				trainingFMeasureAvg.addNumber(fMeasureTraining.getMean());
				trainingFMeasureDev.addNumber(fMeasureTraining.getStandardDeviation());
				trainingFMeasureMax.addNumber(fMeasureTraining.getMax());
				trainingFMeasureMin.addNumber(fMeasureTraining.getMin());
				
				fortifyFmeasureAvg.addNumber(fmeasureLabelFortifyStat.getMean());
				fortifyFmeasureMax.addNumber(fmeasureLabelFortifyStat.getMax());
				fortifyFmeasureMin.addNumber(fmeasureLabelFortifyStat.getMin());
				fortifyFmeasureDev.addNumber(fmeasureLabelFortifyStat.getStandardDeviation());
								
				noOfDescriptionsAgv.addNumber(totalNumberOfDescriptions.getMean());
				noOfDescriptionsMax.addNumber(totalNumberOfDescriptions.getMax());
				noOfDescriptionsMin.addNumber(totalNumberOfDescriptions.getMin());
				noOfDescriptionsDev.addNumber(totalNumberOfDescriptions.getStandardDeviation());
			}
			
		}	//for kk folds
		
		if (noOfRuns > 1) {
	
			//TODO: this needs to be revised using a loop instead if multi-run is used
			outputWriter("");
			outputWriter("Finished " + noOfRuns + " time(s) of the " + folds + "-folds cross-validations");
			
			outputWriter("runtime: " + 
					"\n\t avg.: " + statOutput(df, runtimeAvg, "s") +
					"\n\t dev.: " + statOutput(df, runtimeDev, "s") +
					"\n\t max.: " + statOutput(df, runtimeMax, "s") +
					"\n\t min.: " + statOutput(df, runtimeMin, "s"));
			
			outputWriter("learning time: " + 
					"\n\t avg.: " + statOutput(df, learningTimeAvg, "s") +
					"\n\t dev.: " + statOutput(df, learningTimeDev, "s") +
					"\n\t max.: " + statOutput(df, learningTimeMax, "s") +
					"\n\t min.: " + statOutput(df, learningTimeMin, "s"));
					
			outputWriter("no of descriptions: " + 
					"\n\t avg.: " + statOutput(df, noOfDescriptionsAgv, "") +
					"\n\t dev.: " + statOutput(df, noOfDescriptionsDev, "") +
					"\n\t max.: " + statOutput(df, noOfDescriptionsMax, "") +
					"\n\t min.: " + statOutput(df, noOfDescriptionsMin, ""));
			
			outputWriter("number of partial definitions: " + 
					"\n\t avg.: " + statOutput(df, noOfPartialDefAvg, "") +
					"\n\t dev.: " + statOutput(df, noOfPartialDefDev, "") +
					"\n\t max.: " + statOutput(df, noOfPartialDefMax, "") +
					"\n\t min.: " + statOutput(df, noOfPartialDefMin, ""));
			
			outputWriter("avg. partial definition length: " + 
					"\n\t avg.: " + statOutput(df, avgPartialDefLenAvg, "") + 				
					"\n\t dev.: " + statOutput(df, avgPartialDefLenDev, "") +
					"\n\t max.: " + statOutput(df, avgPartialDefLenMax, "") +
					"\n\t min.: " + statOutput(df, avgPartialDefLenMin, ""));
			
			outputWriter("definition length: " + 
					"\n\t avg.: " + statOutput(df, defLenAvg, "") +
					"\n\t dev.: " + statOutput(df, defLenDev, "") +
					"\n\t max.: " + statOutput(df, defLenMax, "") +
					"\n\t min.: " + statOutput(df, defLenMin, ""));
			
			outputWriter("number of counter partial definitions: " + 
					"\n\t avg.: " + statOutput(df, noOfCounterPartialDefinitionsAvg, "") +
					"\n\t dev.: " + statOutput(df, noOfCounterPartialDefinitionsDev, "") +
					"\n\t max.: " + statOutput(df, noOfCounterPartialDefinitionsMax, "") +
					"\n\t min.: " + statOutput(df, noOfCounterPartialDefinitionsMin, ""));
			
			outputWriter("number of counter partial definitions used: " + 
					"\n\t avg.: " + statOutput(df, noOfCounterPartialDefinitionsUsedAvg, "") +
					"\n\t dev.: " + statOutput(df, noOfCounterPartialDefinitionsUsedDev, "") +
					"\n\t max.: " + statOutput(df, noOfCounterPartialDefinitionsUsedMax, "") +
					"\n\t min.: " + statOutput(df, noOfCounterPartialDefinitionsUsedMin, ""));
			
			outputWriter("accuracy on training set:" + 
					"\n\t avg.: " + statOutput(df, trainingAccAvg, "%") +
					"\n\t dev.: " + statOutput(df, trainingAccDev, "%") + 
					"\n\t max.: " + statOutput(df, trainingAccMax, "%") +
					"\n\t min.: " + statOutput(df, trainingAccMin, "%"));
			
			outputWriter("correctness on training set: " + 
					"\n\t avg.: " + statOutput(df, trainingCorAvg, "%") +
					"\n\t dev.: " + statOutput(df, trainingCorDev, "%") +
					"\n\t max.: " + statOutput(df, trainingCorMax, "%") +
					"\n\t min.: " + statOutput(df, trainingCorMin, "%"));
			
			outputWriter("completeness on training set: " + 
					"\n\t avg.: " + statOutput(df, trainingComAvg, "%") +
					"\n\t dev.: " + statOutput(df, trainingCorDev, "%") +
					"\n\t max.: " + statOutput(df, trainingComMax, "%") +
					"\n\t min.: " + statOutput(df, trainingComMin, "%"));
			
			outputWriter("FMeasure on training set: " + 
					"\n\t avg.: " + statOutput(df, trainingFMeasureAvg, "%") +
					"\n\t dev.: " + statOutput(df, trainingFMeasureDev, "%") +
					"\n\t max.: " + statOutput(df, trainingFMeasureMax, "%") +
					"\n\t min.: " + statOutput(df, trainingFMeasureMin, "%"));
			
			outputWriter("accuracy on testing set: " + 
					"\n\t avg.: " + statOutput(df, testingAccAvg, "%") +
					"\n\t dev.: " + statOutput(df, testingAccDev, "%") +
					"\n\t max.: " + statOutput(df, testingAccMax, "%") +
					"\n\t min.: " + statOutput(df, testingAccMin, "%"));
			
			outputWriter("correctness on testing set: " + 
					"\n\t avg.: " + statOutput(df, testingCorAvg, "%") +
					"\n\t dev.: " + statOutput(df, testingCorDev, "%") +
					"\n\t max.: " + statOutput(df, testingCorMax, "%") +
					"\n\t min.: " + statOutput(df, testingCorMin, "%"));
			
			outputWriter("completeness on testing set: " + 
					"\n\t avg.: " + statOutput(df, testingComAvg, "%") +
					"\n\t dev.: " + statOutput(df, testingComDev, "%") +
					"\n\t max.: " + statOutput(df, testingComMax, "%") +
					"\n\t min.: " + statOutput(df, testingComMin, "%"));
			
			outputWriter("FMeasure on testing set: " + 
					"\n\t avg.: " + statOutput(df, testingFMeasureAvg, "%") +
					"\n\t dev.: " + statOutput(df, testingFMeasureDev, "%") +
					"\n\t max.: " + statOutput(df, testingFMeasureMax, "%") +
					"\n\t min.: " + statOutput(df, testingFMeasureMin, "%"));
			

		}

		if (la instanceof ParCELExAbstract)
			outputWriter("terminated by: partial def.: " + terminatedBypartialDefinition + "; counter partial def.: " + terminatedByCounterPartialDefinitions);
		
		
		//reset the set of positive and negative examples for the learning problem for further experiment if any 
		lp.setPositiveExamples(posExamples);
		lp.setNegativeExamples(negExamples);
		

	}	//constructor


	/*
	private String getOrderUnit(int order) {
		switch (order) {
			case 1: return "st";
			case 2: return "nd";
			case 3: return "rd";
			default: return "th";
		}
	}
	*/

	@Override
	protected void outputWriter(String output) {
		logger.info(output);

		if (writeToFile)
			Files.appendToFile(outputFile, output + "\n");
	}
	
	
	class URIComparator implements Comparator<Individual> {

		@Override
		public int compare(Individual o1, Individual o2) {
			return o1.getURI().compareTo(o2.getURI());
		}
		
	}
	
	
	/*	
	class ParCELExtraNodeNegCoverageComparator implements Comparator<ParCELExtraNode> {

		@Override
		public int compare(ParCELExtraNode node1, ParCELExtraNode node2) {
			int coverage1 = node1.getCoveredNegativeExamples().size();
			int coverage2 = node2.getCoveredNegativeExamples().size();
			
			if (coverage1 > coverage2)
				return -1;
			else if (coverage1 < coverage2)
				return 1;
			else
				return new ConceptComparator().compare(node1.getDescription(), node2.getDescription());
				
		}
		
	}
	*/



	class CoverageComparator2 implements Comparator<CELOE.PartialDefinition> { 
		@Override
		public int compare(CELOE.PartialDefinition p1, CELOE.PartialDefinition p2) {
			if (p1.getCoverage() > p2.getCoverage())
				return -1;
			else if (p1.getCoverage() < p2.getCoverage())
				return 1;
			else
				return new ConceptComparator().compare(p1.getDescription(), p2.getDescription());
				
		}
	}
	
	
	/**
	 * Sort descreasingly 
	 * 
	 * @author An C. Tran
	 *
	 */
	class AdditionalValueComparator implements Comparator<CELOE.PartialDefinition> {		
		int index = 0;
		boolean descending;
		
		public AdditionalValueComparator(int index) {
			this.index = index;
			this.descending = true;
		}
		

		public AdditionalValueComparator(int index, boolean descending) {
			this.index = index;
			this.descending = descending;
		}

		
		@Override
		public int compare(CELOE.PartialDefinition pdef1, CELOE.PartialDefinition pdef2) {
			if (pdef1.getAdditionValue(index) > pdef2.getAdditionValue(index)) {
				if (this.descending)
					return -1;
				else
					return 1;
			}
			else if (pdef1.getAdditionValue(index) < pdef2.getAdditionValue(index)) {
				if (this.descending)
					return 1;
				else
					return -1;
			}
			else
				return new ConceptComparator().compare(pdef1.getDescription(), pdef2.getDescription());
			
		}
		
	}

	
	
	private String getCpdefString(CELOE.PartialDefinition cpdef, String baseURI, Map<String, String> prefixes) {
		DecimalFormat df = new DecimalFormat();
		
		String result = cpdef.getDescription().toKBSyntaxString(baseURI, prefixes)  
				+ "(l=" + cpdef.getDescription().getLength() 
				+ ", cn_training=" + df.format(cpdef.getCoverage())
				+ ", ortho=" + df.format(cpdef.getAdditionValue(0))
				+ ", cn_test=" + Math.round(cpdef.getAdditionValue(1))
				+ ", jaccard=" + df.format(cpdef.getAdditionValue(2))				
				+ ", fort_training_score(cn_test)=" + df.format(cpdef.getAdditionValue(3))
				+ ", simAll=" + df.format(cpdef.getAdditionValue(4))
				+ ", simPos=" + df.format(cpdef.getAdditionValue(5))
				+ ", simNeg=" + df.format(cpdef.getAdditionValue(6))
				+ ")"; 
				
		return result;
	}

	
	/**
	 * Convert an array of double that contains accuracy/completeness/correctness into a string (from the 1st..6th elements)
	 * 
	 * @param df Decimal formatter 
	 * @param arr Array of double (7 elements)
	 * 
	 * @return A string of double values
	 */
	private String arrayToString(int[] arr) {
		String result = "[" + arr[0];
		
		for (int i=1; i<arr.length; i++)
			result += (";" + arr[i]);
		
		result += "]";
		
		return result;
	}
	
	
	/**
	 * Convert an array of double that contains accuracy/completeness/correctness into a string (from the 1st..6th elements)
	 * 
	 * @param df Decimal formatter 
	 * @param arr Array of double (7 elements)
	 * 
	 * @return A string of double values
	 */
	private String arrayToString(DecimalFormat df, double[] arr) {
		String result = "[" + df.format(arr[0]);
		
		for (int i=1; i<arr.length; i++)
			result += (";" + df.format(arr[i]));
		
		result += "]";
		
		return result;
	}
}

