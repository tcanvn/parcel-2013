package org.dllearner.cli.ParCEL;

import java.io.File;
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
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.dllearner.algorithms.ParCEL.ParCELAbstract;
import org.dllearner.algorithms.ParCEL.ParCELCorrectnessComparator;
import org.dllearner.algorithms.ParCEL.ParCELExtraNode;
import org.dllearner.algorithms.ParCEL.ParCELPosNegLP;
import org.dllearner.algorithms.ParCELEx.ParCELExAbstract;
import org.dllearner.algorithms.celoe.CELOE;
import org.dllearner.cli.CrossValidation;
import org.dllearner.core.AbstractCELA;
import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.core.ComponentInitException;
import org.dllearner.core.owl.Description;
import org.dllearner.core.owl.Individual;
import org.dllearner.core.owl.Intersection;
import org.dllearner.core.owl.Union;
import org.dllearner.kb.OWLFile;
import org.dllearner.learningproblems.Heuristics;
import org.dllearner.learningproblems.PosNegLP;
import org.dllearner.utilities.Files;
import org.dllearner.utilities.Helper;
import org.dllearner.utilities.owl.ConceptComparator;
import org.dllearner.utilities.owl.OWLAPIConverter;
import org.dllearner.utilities.statistics.Stat;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLClassExpression;
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

public class ParCELExFortifiedCrossValidationOrtho extends CrossValidation {

	protected Stat noOfPartialDef;
	protected Stat avgPartialDefinitionLength;
	protected Stat noOfCounterPartialDefinitions;
	protected Stat noOfCounterPartialDefinitionsUsed;
	protected Stat learningTime;
	
	//fortify strategy statistical variables
	protected Stat accuracyFortifyStat;
	protected Stat correctnessFortifyStat;
	protected Stat completenessFortifyStat;
	protected Stat fmeasureFortifyStat;
	protected Stat avgFortifiedPartialDefinitionLengthStat;
	protected Stat avgFortifyDefinitionsLengthStat;
	protected Stat avgFortifyCoverageTrainingStat;
	protected Stat avgFortifyCoverageTestStat;

	
	//orthogonality check
	/*
	protected Stat orthogonalityCheckAllType1Stat, orthogonalityCheckAllType2Stat, orthogonalityCheckAllType3Stat, 
			orthogonalityCheckAllType4Stat, orthogonalityCheckAllType5Stat;
	protected Stat orthogonalityCheckSelectedType1Stat, orthogonalityCheckSelectedType2Stat,
			orthogonalityCheckSelectedType3Stat, orthogonalityCheckSelectedType4Stat, orthogonalityCheckSelectedType5Stat;	
	*/
	
	protected Stat avgNoOfFortifiedDefinitions;

	Logger logger = Logger.getLogger(this.getClass());

	protected boolean interupted = false;

	/**
	 * Default constructor
	 */

	public ParCELExFortifiedCrossValidationOrtho(AbstractCELA la, PosNegLP lp, AbstractReasonerComponent rs,
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
	public ParCELExFortifiedCrossValidationOrtho(AbstractCELA la, ParCELPosNegLP lp, AbstractReasonerComponent rs,
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

		//System.out.println(splitsPos[0]);
		//System.out.println(splitsNeg[0]);

		// calculating training and test sets
		for(int i=0; i<folds; i++) {
			Set<Individual> testPos = getTestingSet(posExamplesList, splitsPos, i);
			Set<Individual> testNeg = getTestingSet(negExamplesList, splitsNeg, i);
			testSetsPos.add(i, testPos);
			testSetsNeg.add(i, testNeg);
			trainingSetsPos.add(i, getTrainingSet(posExamples, testPos));
			trainingSetsNeg.add(i, getTrainingSet(negExamples, testNeg));				
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

		
		long orthAllCheckCountFold[] = new long[5];
		long orthSelectedCheckCountFold[] = new long[5];

		long orthAllCheckCountTotal[] = new long[5];
		long orthSelectedCheckCountTotal[] = new long[5];
		
		
		orthAllCheckCountTotal[0] = orthAllCheckCountTotal[1] = orthAllCheckCountTotal[2] = 
			orthAllCheckCountTotal[3] = orthAllCheckCountTotal[4] = 0;
		
		orthSelectedCheckCountTotal[0] = orthSelectedCheckCountTotal[1] = orthSelectedCheckCountTotal[2] = 
			orthSelectedCheckCountTotal[3] = orthSelectedCheckCountTotal[4] = 0;

		
		long ontologyLoadStarttime = System.nanoTime();		
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology ontology = ((OWLFile)la.getReasoner().getSources().iterator().next()).createOWLOntology(manager);			
		outputWriter("Ontology created, axiom count: " + ontology.getAxiomCount());
		PelletReasoner pelletReasoner = PelletReasonerFactory.getInstance().createReasoner(ontology);
		outputWriter("Pellet creared and binded with the ontology: " + pelletReasoner.getReasonerName());
		long ontologyLoadDuration = System.nanoTime() - ontologyLoadStarttime;
		outputWriter("Total time for creating and binding ontology: " + ontologyLoadDuration/1000000000d + "ms");

		
		for (int kk=0; kk < noOfRuns; kk++) {

			//runtime
			runtime = new Stat();
			fMeasure = new Stat();
			fMeasureTraining = new Stat();

			noOfPartialDef = new Stat();
			avgPartialDefinitionLength = new Stat();
			length = new Stat();			
			accuracyTraining = new Stat();
			trainingCorrectnessStat= new Stat();
			trainingCompletenessStat = new Stat();
			accuracy = new Stat();
			testingCorrectnessStat = new Stat();
			testingCompletenessStat = new Stat();
						
			noOfCounterPartialDefinitions = new Stat();
			noOfCounterPartialDefinitionsUsed = new Stat();
			learningTime = new Stat();
			
			//fortify strategy statistical variables
			accuracyFortifyStat = new Stat();
			correctnessFortifyStat = new Stat();			
			completenessFortifyStat = new Stat();
			fmeasureFortifyStat = new Stat();
			avgFortifiedPartialDefinitionLengthStat = new Stat();
			avgFortifyCoverageTrainingStat = new Stat();
			avgFortifyCoverageTestStat = new Stat();
			avgFortifyDefinitionsLengthStat = new Stat();
			
			avgNoOfFortifiedDefinitions = new Stat();
			
			totalNumberOfDescriptions = new Stat();
			
			//ortho check
			/*
			orthogonalityCheckAllType1Stat = new Stat();
			orthogonalityCheckAllType2Stat = new Stat();
			orthogonalityCheckAllType3Stat = new Stat();
			orthogonalityCheckAllType4Stat = new Stat();
			orthogonalityCheckAllType5Stat = new Stat();
			
			orthogonalityCheckSelectedType1Stat = new Stat();
			orthogonalityCheckSelectedType2Stat = new Stat();
			orthogonalityCheckSelectedType3Stat = new Stat();
			orthogonalityCheckSelectedType4Stat = new Stat();
			orthogonalityCheckSelectedType5Stat = new Stat();
			*/
			
			
			for(int currFold=0; (currFold<folds); currFold++) {

				if (this.interupted) {
					outputWriter("Cross validation has been interupted");
					return;
				}
				

				//Set<String> pos = Datastructures.individualSetToStringSet(trainingSetsPos.get(currFold));
				//Set<String> neg = Datastructures.individualSetToStringSet(trainingSetsNeg.get(currFold));
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
					System.out.println("out of memory at " + (System.currentTimeMillis() - algorithmStartTime)/1000 + "s");
				}

				long algorithmDuration = System.nanoTime() - algorithmStartTime;
				runtime.addNumber(algorithmDuration/(double)1000000000);
				
				//learning time, does not include the reduction time
				long learningMili = ((ParCELAbstract)la).getLearningTime();
				learningTime.addNumber(learningMili/(double)1000);

				// calculate training accuracies
				Set<Individual> posTestSet = testSetsPos.get(currFold);
				Set<Individual> negTestSet = testSetsNeg.get(currFold);
				Set<Individual> posTrainingSet = trainingSetsPos.get(currFold);
				Set<Individual> negTrainingSet = trainingSetsNeg.get(currFold);
				
				int trainingPosSize = posTrainingSet.size();
				int trainingNegSize = negTrainingSet.size();
				int testingPosSize = posTestSet.size();
				int testingNegSize = negTestSet.size();
				
				
				//no of counter partial definition
				TreeSet<ParCELExtraNode> counterPartialDefinitions = new TreeSet<ParCELExtraNode>(((ParCELExAbstract)la).getCounterPartialDefinitions());
				
				int noOfCounterPartialDefinitions = counterPartialDefinitions.size();
				int noOfCounterPartialDefinitionsUsed = ((ParCELExAbstract)la).getNumberOfCounterPartialDefinitionUsed();
				this.noOfCounterPartialDefinitions.addNumber(noOfCounterPartialDefinitions);
				this.noOfCounterPartialDefinitionsUsed.addNumber(noOfCounterPartialDefinitionsUsed);				
				
				
				//print the coverage of the counter partial definitions
				outputWriter("Learninh finish, number of counter partial definitions: " + noOfCounterPartialDefinitions);
				outputWriter("(CPDEF length and coverage (length, coverage)");
				int count = 1;
				String sTemp = "";
				
				Set<Individual> positiveCoveredByCpdef;
				Set<Individual> negativeCoveredByCpdef;
				
				for (ParCELExtraNode cpdef : counterPartialDefinitions) {
					sTemp += ("(" + cpdef.getDescription().getLength() + ", " + 
							df.format(cpdef.getCoveredNegativeExamples().size()/(double)trainingNegSize) + "); ");
					if (count % 10 == 0) {
						outputWriter(sTemp);
						sTemp = "";
					}
					count++;
				}
				
				outputWriter("------------------------------");
				
				
				//cast the la into ParCELExAbstract for easier accessing
				ParCELExAbstract parcelEx = (ParCELExAbstract)la;

				//get the target (learned) definition
				Description concept = parcelEx.getUnionCurrenlyBestDescription(); 

				//for the training accuracy
				Set<Individual> tmp = rs.hasType(concept, trainingSetsPos.get(currFold));		//positive examples covered by the learned concept
				Set<Individual> tmp2 = Helper.difference(trainingSetsPos.get(currFold), tmp);	//false negative (pos as neg)
				Set<Individual> tmp3 = rs.hasType(concept, trainingSetsNeg.get(currFold));		//false positive (neg as pos)


				outputWriter("training set errors pos (" + tmp2.size() + "): " + tmp2);
				outputWriter("training set errors neg (" + tmp3.size() + "): " + tmp3);
				
				
				//calculate training completeness, correctness and accuracy
				int trainingCorrectPosClassified = tmp.size();	//getCorrectPosClassified(rs, concept, trainingSetsPos.get(currFold));
				int trainingCorrectNegClassified = trainingNegSize - tmp3.size();	//getCorrectNegClassified(rs, concept, trainingSetsNeg.get(currFold));
				int trainingCorrectExamples = trainingCorrectPosClassified + trainingCorrectNegClassified;
				
				double trainingAccuracy = 100*((double)trainingCorrectExamples/(trainingPosSize + trainingNegSize));	
				double trainingCompleteness = 100*(double)trainingCorrectPosClassified/trainingPosSize;
				double trainingCorrectness = 100*(double)trainingCorrectNegClassified/trainingNegSize;
				
				accuracyTraining.addNumber(trainingAccuracy);
				trainingCompletenessStat.addNumber(trainingCompleteness);
				trainingCorrectnessStat.addNumber(trainingCorrectness);
				
				// fortify training F-Score
				int negAsPosTraining = tmp3.size();
				double precisionTraining = trainingCorrectPosClassified + negAsPosTraining == 0 ? 
						0 : trainingCorrectPosClassified / (double) (trainingCorrectPosClassified + negAsPosTraining);
				double recallTraining = trainingCorrectPosClassified / (double) trainingPosSize;
				double currFmeasureTraining = 100*Heuristics.getFScore(recallTraining, precisionTraining);
				fMeasureTraining.addNumber(currFmeasureTraining);


				//calculate the accuracy on the test set
				tmp = rs.hasType(concept, testSetsPos.get(currFold));		//positive examples covered by the learned concept
				tmp2 = Helper.difference(testSetsPos.get(currFold), tmp);	//false negative (pos as neg)
				tmp3 = rs.hasType(concept, testSetsNeg.get(currFold));		//false positive (neg as pos)

				outputWriter("test set errors pos (" + tmp2.size() + "): " + tmp2);
				outputWriter("test set errors neg (" + tmp3.size() + "): " + tmp3);

				// calculate test accuracies
				//int correctPosClassified = getCorrectPosClassified(rs, concept, testSetsPos.get(currFold));
				//int correctNegClassified = getCorrectNegClassified(rs, concept, testSetsNeg.get(currFold));
				//int correctExamples = correctPosClassified + correctNegClassified;
				int testingCorrectPosClassified = testingPosSize - tmp2.size();	//tmp2: wrong classify of pos. examples				
				int testingCorrectNegClassified = testingNegSize - tmp3.size();	//tmp3: wrong classify of neg. examples
				int testingCorrectExamples = testingCorrectPosClassified + testingCorrectNegClassified;

				double testingCompleteness = 100*(double)testingCorrectPosClassified/testingPosSize;
				double testingCorrectness = 100*(double)testingCorrectNegClassified/testingNegSize;				
				double currAccuracy = 100*((double)testingCorrectExamples/(testingPosSize + testingNegSize));

				accuracy.addNumber(currAccuracy);
				testingCompletenessStat.addNumber(testingCompleteness);
				testingCorrectnessStat.addNumber(testingCorrectness);				
	
				
				//test F-Score
				//precision: 
				int negAsPos = tmp3.size();	//rs.hasType(concept, testSetsNeg.get(currFold)).size();	//tmp3=covered negative
				double precision = testingCorrectPosClassified + negAsPos == 0 ? 
						0 : testingCorrectPosClassified / (double) (testingCorrectPosClassified + negAsPos);
				double recall = testingCorrectPosClassified / (double) testingPosSize;
				double currFmeasureTest = 100*Heuristics.getFScore(recall, precision); 
				fMeasure.addNumber(currFmeasureTest);
				
				
				//--------------------------------------------
				// FORTIFICATION
				//--------------------------------------------
				Set<ParCELExtraTestingNode> fortifyCounterPartilDefinitions = new TreeSet<ParCELExtraTestingNode>(new ParCELTestingCorrectnessComparator());
				int totalFortifyDefinitionsLength = 0;
				
				int fortifyPositiveCovered = 0;		//positive examples covered by fortify definitions
				int fortifyNegativeCovered = 0;		//negative examples covered by fortify definitions	
				
				int noOfFortifyDefinitions = 0;
				
				Stat fortifyCoverageTrainingStat = new Stat();		//avg. training coverage of the fortify definitions in each fold
				Stat fortifyCoverageTestStat = new Stat();			//avg. test coverage of the fortify definitions in each fold
				
				//----------------------------
				//fortification checking
				//
				//----------------------------
				if (tmp3.size() > 0) {
					
					//SortedSet<ParCELExtraNode> counterPartialDefinitions = parcelEx.getCounterPartialDefinitions();
					//SortedSet<ParCELExtraNode> reducedPartialDefinitions = parcelEx.getReducedPartialDefinition();
					
					//tmp3 contains negative examples covered by the learned concept
					//==> find a set of counter partial definitions that can help to remove this set of negative examples
					
					//create a temp set of covered neg. examples for the fortification to avoid modifying the tmp3 set
					TreeSet<Individual> tempCoveredNeg = new TreeSet<Individual>(new URIComparator());
					tempCoveredNeg.addAll(tmp3);
					
					TreeSet<Individual> tempCoveredPos = new TreeSet<Individual>(new URIComparator());					
					
					for (ParCELExtraNode cpdef : counterPartialDefinitions) {
						Set<Individual> cpdefNegCovered = new HashSet<Individual>(rs.hasType(cpdef.getDescription().getChild(0), negTestSet));
						
						//check if the current counter partial definition can remove any of the covered negative examples
						if (tempCoveredNeg.removeAll(cpdefNegCovered)) {
							Set<Individual> cpdefPosCovered = new HashSet<Individual>(
									rs.hasType(cpdef.getDescription().getChild(0), testSetsPos.get(currFold)));
							
							//hold both covered pos. and neg. for calculating the new completeness and correctness
							fortifyCounterPartilDefinitions.add(new ParCELExtraTestingNode(cpdef, cpdefPosCovered, cpdefNegCovered));
							
							outputWriter("*fortify counter definition: " + cpdef.getDescription() + "(" + cpdefPosCovered + ", " + cpdefNegCovered + ")");
							
							totalFortifyDefinitionsLength += cpdef.getDescription().getLength();
							tempCoveredPos.addAll(cpdefPosCovered);
							
							//coverage
							double trainingCoverage = cpdef.getCoveredNegativeExamples().size()/(double)trainingNegSize;
							double testCoverage = cpdefNegCovered.size()/(double)testingNegSize;
							
							fortifyCoverageTrainingStat.addNumber(trainingCoverage);
							fortifyCoverageTestStat.addNumber(testCoverage);
						}
						
						//if all negative examples have been removed, stop the fortification
						if (tempCoveredNeg.size() <= 0)
							break;
							
					}  //for each counter partial definition 
					
					fortifyPositiveCovered = tempCoveredPos.size();
					fortifyNegativeCovered = tmp3.size() - tempCoveredNeg.size();
					
				}	//if tmp3.size > 0, i.e. wrong classification of negative examples
				
				
				noOfFortifyDefinitions = fortifyCounterPartilDefinitions.size();
				avgNoOfFortifiedDefinitions.addNumber(noOfFortifyDefinitions);

				
				//definition length
				int noOfPdef = ((ParCELAbstract)la).getNoOfReducedPartialDefinition(); 
				this.noOfPartialDef.addNumber(noOfPdef);
				
				double avgPdefLength = concept.getLength()/(double)noOfPdef;
				this.avgPartialDefinitionLength.addNumber(avgPdefLength);
								
				
				//fortify definitions length
				double avgFortifyDefinitionsLength = (noOfFortifyDefinitions == 0? 
						0 : totalFortifyDefinitionsLength / (double)noOfFortifyDefinitions);
				avgFortifyDefinitionsLengthStat.addNumber(avgFortifyDefinitionsLength);
				
				
				//fortified definitions length (TODO: this should be checked) 
				double avgFortifiedPartialDefinitionLength = avgPdefLength + avgFortifyDefinitionsLengthStat.getMean()*noOfFortifyDefinitions;
				avgFortifiedPartialDefinitionLengthStat.addNumber(avgFortifiedPartialDefinitionLength);				
				
				
				//fortify accuracy
				double testingCompletenessFortify = 100*(double)(testingCorrectPosClassified - fortifyPositiveCovered)/testingPosSize;				
				double testingCorrectnessFortify = 100*(double)(testingCorrectNegClassified + fortifyNegativeCovered)/testingNegSize;			
				double testingAccuracyFortify = 100*((double)(testingCorrectExamples - fortifyPositiveCovered + fortifyNegativeCovered)/
						(testingPosSize + testingNegSize));

				accuracyFortifyStat.addNumber(testingAccuracyFortify);
				correctnessFortifyStat.addNumber(testingCorrectnessFortify);
				completenessFortifyStat.addNumber(testingCompletenessFortify);				
		

				//fortify F-measure TODO: this calculation is wrong. Check is needed
				int positiveClassifiedFortify = testingCorrectPosClassified + negAsPos - fortifyPositiveCovered - fortifyNegativeCovered;
				double precisionFortify = (positiveClassifiedFortify == 0 ? 
						0 : (testingCorrectPosClassified - fortifyPositiveCovered)/ (double) (positiveClassifiedFortify));
				double recallFortify = (testingCorrectPosClassified - fortifyPositiveCovered) / (double) testingPosSize;
				
				double testingFmeasureFortiafy = 100*Heuristics.getFScore(recallFortify, precisionFortify);				
				fmeasureFortifyStat.addNumber(testingFmeasureFortiafy);
					
				
				length.addNumber(concept.getLength());
				totalNumberOfDescriptions.addNumber(parcelEx.getTotalNumberOfDescriptionsGenerated());

				outputWriter("Fold " + currFold + "/" + folds + ":");
				outputWriter("  training: " + trainingCorrectPosClassified + "/" + trainingSetsPos.get(currFold).size() + 
						" positive and " + trainingCorrectNegClassified + "/" + trainingSetsNeg.get(currFold).size() + " negative examples");
				outputWriter("  testing: " + testingCorrectPosClassified + "/" + testSetsPos.get(currFold).size() + " correct positives, " 
						+ testingCorrectNegClassified + "/" + testSetsNeg.get(currFold).size() + " correct negatives");
				
				
				outputWriter("  runtime: " + df.format(algorithmDuration/(double)1000000000) + "s");
				outputWriter("  learning time: " + df.format(learningMili/(double)1000) + "s");
				outputWriter("  definition length: " + df.format(concept.getLength()));
				
				outputWriter("  concept: " + concept);
				
				outputWriter("  f-measure test: " + df.format(currFmeasureTest) +	 
						"% (" + df.format(currFmeasureTraining) + "% on training set)");

				outputWriter("  f-measure fortify: " + df.format(testingFmeasureFortiafy)); 
 
				
				outputWriter("  accuracy test: " + df.format(currAccuracy) +  
						"% (corr:"+ df.format(testingCorrectness) + 
						"%, comp:" + df.format(testingCompleteness) + "%) --- " + 
						df.format(trainingAccuracy) + "% (corr:"+ trainingCorrectness + 
						"%, comp:" + df.format(trainingCompleteness) + "%) on training set");
				
				outputWriter("  fortified accuracy: " + df.format(testingAccuracyFortify) +
						"%, correctness: " + df.format(testingCorrectnessFortify) +
						"%, completeness: " + df.format(testingCompleteness) + "%");	
				
				outputWriter("  avg. fortified training coverage: " + statOutput(df, fortifyCoverageTrainingStat, "%"));
				outputWriter("  avg. fortified test coverage: " + statOutput(df, fortifyCoverageTestStat, "%"));

				outputWriter("  total number of descriptions: " + la.getTotalNumberOfDescriptionsGenerated());				

				
				if (la instanceof ParCELAbstract) {	//this check is redundant

					outputWriter("  number of partial definition used: " + noOfPdef + "/" + ((ParCELAbstract)la).getNumberOfPartialDefinitions());

					outputWriter("  average partial definition length: " + df.format(avgPdefLength));	
					
					outputWriter("  average fortified partial definition length: " + df.format(avgFortifiedPartialDefinitionLength));
					outputWriter("  average fortify partial definition length: " + df.format(avgFortifyDefinitionsLength));

					//show more information on counter partial definitions				
					if (la instanceof ParCELExAbstract) {
						ParCELExAbstract pdllexla = (ParCELExAbstract)la;
						outputWriter("  number of partial definitions for each type: 1:" + pdllexla.getNumberOfPartialDefinitions(1) + 
								"; 2:" + pdllexla.getNumberOfPartialDefinitions(2) + 
								"; 3:" + pdllexla.getNumberOfPartialDefinitions(3) +
								"; 4:" + pdllexla.getNumberOfPartialDefinitions(4));
						outputWriter("  number of counter partial definition used: " + pdllexla.getNumberOfCounterPartialDefinitionUsed() + "/" + pdllexla.getNumberOfCounterPartialDefinitions());
						
						outputWriter("  number of fortify definition: " + (fortifyCounterPartilDefinitions.size()));
						
						//check how did the learner terminate: by partial definition or counter partial definition
						if (pdllexla.terminatedByCounterDefinitions()) {
							outputWriter("  terminated by counter partial definitions");
							terminatedByCounterPartialDefinitions++;
						}
						else if (pdllexla.terminatedByPartialDefinitions()) {
							outputWriter("  terminated by partial definitions");
							terminatedBypartialDefinition++;
						}
						else
							outputWriter("  neither terminated by partial definition nor counter partial definition");
					}
				}
				
				//cumulative statistical data
				outputWriter("----------");
				outputWriter("Aggregate data from fold 0 to fold " + currFold + "/" + folds);
				outputWriter("  runtime: " + statOutput(df, runtime, "s"));
				outputWriter("  no of descriptions: " + statOutput(df, totalNumberOfDescriptions, ""));
				outputWriter("  length: " + statOutput(df, length, ""));
				outputWriter("  avg. no of partial definitions: " + statOutput(df, noOfPartialDef, ""));
				outputWriter("  avg. partial definition length: " + statOutput(df, avgPartialDefinitionLength, ""));
				outputWriter("  avg. no of fortify definitions: " + statOutput(df, avgNoOfFortifiedDefinitions, ""));
				outputWriter("  avg. fortified partial definition length: " + statOutput(df, avgFortifiedPartialDefinitionLengthStat, ""));				
				outputWriter("  avg. fortify definition length: " + statOutput(df, avgFortifyDefinitionsLengthStat, ""));
				outputWriter("  F-Measure on training set: " + statOutput(df, fMeasureTraining, "%"));
				outputWriter("  F-Measure on test set: " + statOutput(df, fMeasure, "%"));
				outputWriter("  F-Measure fortify: " + statOutput(df, fmeasureFortifyStat, "%"));
				outputWriter("  predictive accuracy on training set: " + statOutput(df, accuracyTraining, "%") + 
						" -- correctness: " + statOutput(df, trainingCorrectnessStat, "%") +
						"-- completeness: " + statOutput(df, trainingCompletenessStat, "%"));
				outputWriter("  predictive accuracy on test set: " + statOutput(df, accuracy, "%") +
						" -- correctness: " + statOutput(df, testingCorrectnessStat, "%") +
						"-- completeness: " + statOutput(df, testingCompletenessStat, "%"));

				outputWriter("  accuracy fortify: " + statOutput(df, accuracyFortifyStat, "%") +
						" -- correctness: " + statOutput(df, correctnessFortifyStat, "%") +
						"-- completeness: " + statOutput(df, completenessFortifyStat, "%"));

				outputWriter("  coverage fortify: + training:" + 
						statOutput(df, avgFortifyCoverageTrainingStat, "%") +
						", +test: " + statOutput(df, avgFortifyCoverageTestStat, "%"));


				orthAllCheckCountFold[0] = orthAllCheckCountFold[1] = orthAllCheckCountFold[2] = orthAllCheckCountFold[3] = orthAllCheckCountFold[4] = 0;
				orthSelectedCheckCountFold[0] = orthSelectedCheckCountFold[1] = orthSelectedCheckCountFold[2] = orthSelectedCheckCountFold[3] = orthSelectedCheckCountFold[4] = 0;

				outputWriter("----------------------");
				outputWriter("ORTHOGONALITY testing");
				outputWriter("	[description (length, training completeness, neg examples removed), orthogonality check]");
				outputWriter("----------------------");
				outputWriter("Learned concept: " + concept);
				outputWriter("*** All counter partial definitions: ");
				
				//convert the learned concept into OWLAPI expression
				OWLClassExpression conceptOWLAPI = OWLAPIConverter.getOWLAPIDescription(concept); 
					
				int c = 1;
				//visit all counter partial definitions and check for the orthogonality
				for (ParCELExtraNode cpdef : counterPartialDefinitions) {
					OWLClassExpression pdefExpr = OWLAPIConverter.getOWLAPIDescription(cpdef.getDescription().getChild(0));
					
					//for each counter partial definition, combine it with the learned concept and check for the satisfiability
					System.out.print("checking...");
					int orthCheck = Orthogonality.orthogonalityCheck(pelletReasoner, ontology, conceptOWLAPI, pdefExpr);
					System.out.println("; check result: " + orthCheck);
					
					orthAllCheckCountFold[orthCheck]++;
					
					if (currFold < 5)
						outputWriter(c++ + ". " + cpdef.getDescription() + " (" 
								+ cpdef.getDescription().getLength() + ", " + df.format(cpdef.getCompleteness()) 
								+ ", " + cpdef.getCoveredNegativeExamples().size() + ")" 	//no of neg. examples removed in test set
								+ ", orthogonality check: " + orthCheck);					
				}
				
				
				outputWriter("\n*** Selected counter partial definitions: ");				
				c = 1;
				
				//output the selected counter partial definition information				
				if (fortifyCounterPartilDefinitions.size() > 0) {										
					for (ParCELExtraTestingNode cpdef : fortifyCounterPartilDefinitions) {
						OWLClassExpression pdefExpr = OWLAPIConverter.getOWLAPIDescription(cpdef.getExtraNode().getDescription());
						
						int orthCheck = Orthogonality.orthogonalityCheck(pelletReasoner, ontology, conceptOWLAPI, pdefExpr);
						
						orthSelectedCheckCountFold[orthCheck]++;
						
						outputWriter(c++ + ". " + cpdef.getExtraNode().getDescription() + "(" 
								+ cpdef.getExtraNode().getDescription().getLength() + ", " + cpdef.getCoveredNegativeExamplestestSet().size()
								+ ", orthogonality check: " + orthCheck);					
					}
				}
				
				outputWriter("------orthogonality check-------");
				outputWriter("   all cpdef check resutl: " 
						+ orthAllCheckCountFold[0] + ", " + orthAllCheckCountFold[1] + ", "
						+ orthAllCheckCountFold[2] + ", " + orthAllCheckCountFold[3] + ", "
						+ orthAllCheckCountFold[4]);
				
				outputWriter("   selected cpdef check resutl: " 
						+ orthSelectedCheckCountFold[0] + ", " + orthSelectedCheckCountFold[1] + ", "
						+ orthSelectedCheckCountFold[2] + ", " + orthSelectedCheckCountFold[3] + ", "
						+ orthSelectedCheckCountFold[4]);	
								
				outputWriter("----------------------");
				
				orthAllCheckCountTotal[0] += orthAllCheckCountFold[0];
				orthAllCheckCountTotal[1] += orthAllCheckCountFold[1];
				orthAllCheckCountTotal[2] += orthAllCheckCountFold[2];
				orthAllCheckCountTotal[3] += orthAllCheckCountFold[3];
				orthAllCheckCountTotal[4] += orthAllCheckCountFold[4];
				
				orthSelectedCheckCountTotal[0] += orthSelectedCheckCountFold[0];
				orthSelectedCheckCountTotal[1] += orthSelectedCheckCountFold[1];
				orthSelectedCheckCountTotal[2] += orthSelectedCheckCountFold[2];
				orthSelectedCheckCountTotal[3] += orthSelectedCheckCountFold[3];
				orthSelectedCheckCountTotal[4] += orthSelectedCheckCountFold[4];
				
							
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
			outputWriter("  learning time: " + statOutput(df, learningTime, "s"));
			outputWriter("  no of descriptions: " + statOutput(df, totalNumberOfDescriptions, ""));
			outputWriter("  no of partial definitions: " + statOutput(df, noOfPartialDef, ""));
			outputWriter("  avg. partial definition length: " + statOutput(df, avgPartialDefinitionLength, ""));
			outputWriter("  avg. no of fortify definitions: " + statOutput(df, avgNoOfFortifiedDefinitions, ""));
			outputWriter("  definition length: " + statOutput(df, length, ""));
			outputWriter("  avg. fortified partial definition length: " + statOutput(df, avgFortifiedPartialDefinitionLengthStat, ""));
			outputWriter("  avg. fortify partial definition length: " + statOutput(df, avgFortifyDefinitionsLengthStat, ""));
			outputWriter("  no of counter partial definition: " + statOutput(df, noOfCounterPartialDefinitions, ""));
			outputWriter("  no of counter partial definition used: " + statOutput(df, noOfCounterPartialDefinitionsUsed, ""));
			outputWriter("  F-Measure on training set: " + statOutput(df, fMeasureTraining, "%"));		
			outputWriter("  F-Measure on test set: " + statOutput(df, fMeasure, "%"));
			outputWriter("  F-Measure fortify 1: " + statOutput(df, fmeasureFortifyStat, "%"));
			outputWriter("  predictive accuracy on training set: " + statOutput(df, accuracyTraining, "%") + 
					" - corr: " + statOutput(df, trainingCorrectnessStat, "%") + 
					", comp: " + statOutput(df, trainingCompletenessStat, "%"));		
			outputWriter("  predictive accuracy on test set: " + statOutput(df, accuracy, "%") + 
					" - corr: " + statOutput(df, testingCorrectnessStat, "%") + 
					", comp: " + statOutput(df, testingCompletenessStat, "%"));

			outputWriter("  accuracy fortify: " + statOutput(df, accuracyFortifyStat, "%") +
					" -- correctness: " + statOutput(df, correctnessFortifyStat, "%") +
					"-- completeness: " + statOutput(df, completenessFortifyStat, "%"));

			
			if (la instanceof ParCELExAbstract)
				outputWriter("  terminated by: partial def.: " + terminatedBypartialDefinition + "; counter partial def.: " + terminatedByCounterPartialDefinitions);

			
			//this is for copying to word document
			//f-measure, accuracy, correctness, completeness, avg pdef length, no of pdef, time, no of des, no of cpdef
			outputWriter("***without fortify (f-measure, accuracy, correctness, completeness, avg. pdef length)***\n"
					+ df.format(fMeasure.getMean()) + "\n" + df.format(fMeasure.getStandardDeviation()) + "\n"
					+ df.format(accuracy.getMean()) + "\n" + df.format(accuracy.getStandardDeviation()) + "\n"
					+ df.format(testingCorrectnessStat.getMean()) + "\n" + df.format(testingCorrectnessStat.getStandardDeviation()) + "\n"
					+ df.format(testingCompletenessStat.getMean()) + "\n" + df.format(testingCompletenessStat.getStandardDeviation()) + "\n"
					+ df.format(avgPartialDefinitionLength.getMean()) + "\n" + df.format(avgPartialDefinitionLength.getStandardDeviation()) + "\n"
					);
			
			outputWriter("***with fortify (f-measure, accuracy, correctness, completeness, avg. fortified pdef length)***\n"
					+ df.format(fmeasureFortifyStat.getMean()) + "\n" + df.format(fmeasureFortifyStat.getStandardDeviation()) + "\n"
					+ df.format(accuracyFortifyStat.getMean()) + "\n" + df.format(accuracyFortifyStat.getStandardDeviation()) + "\n"
					+ df.format(correctnessFortifyStat.getMean()) + "\n" + df.format(correctnessFortifyStat.getStandardDeviation()) + "\n"
					+ df.format(completenessFortifyStat.getMean()) + "\n" + df.format(completenessFortifyStat.getStandardDeviation()) + "\n"
					+ df.format(avgFortifiedPartialDefinitionLengthStat.getMean()) + "\n" + df.format(avgFortifiedPartialDefinitionLengthStat.getStandardDeviation()) + "\n"
					);
			
			outputWriter("***Common dimensionss (no of pdef., learning time, no of des., no of cpdef., no of fdef.)***\n"
					+ df.format(noOfPartialDef.getMean()) + "\n" + df.format(noOfPartialDef.getStandardDeviation()) + "\n"
					+ df.format(learningTime.getMean()) + "\n" + df.format(learningTime.getStandardDeviation()) + "\n"
					+ df.format(totalNumberOfDescriptions.getMean()) + "\n" + df.format(totalNumberOfDescriptions.getStandardDeviation()) + "\n"
					+ df.format(noOfCounterPartialDefinitions.getMean()) + "\n" + df.format(noOfCounterPartialDefinitions.getStandardDeviation()) + "\n"
					+ df.format(avgNoOfFortifiedDefinitions.getMean()) + "\n" + df.format(avgNoOfFortifiedDefinitions.getStandardDeviation()) + "\n"
					);


			outputWriter("***Orthogonality result: ");
			outputWriter("\tAll counter partial definitions: " 
					+ orthAllCheckCountTotal[0] + ", " + orthAllCheckCountTotal[1] + ", "
					+ orthAllCheckCountTotal[2] + ", " + orthAllCheckCountTotal[3] + ", "
					+ orthAllCheckCountTotal[4]);
										
			outputWriter("\tFortify counter partial definitions: "
					+ orthSelectedCheckCountTotal[0] + ", " + orthSelectedCheckCountTotal[1] + ", "
					+ orthSelectedCheckCountTotal[2] + ", " + orthSelectedCheckCountTotal[3] + ", "
					+ orthSelectedCheckCountTotal[4]);
			
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
				noOfPartialDefAvg.addNumber(noOfPartialDef.getMean());
				noOfPartialDefMax.addNumber(noOfPartialDef.getMax());
				noOfPartialDefMin.addNumber(noOfPartialDef.getMin());
				noOfPartialDefDev.addNumber(noOfPartialDef.getStandardDeviation());
						
				//avg partial definition length
				avgPartialDefLenAvg.addNumber(avgPartialDefinitionLength.getMean());
				avgPartialDefLenMax.addNumber(avgPartialDefinitionLength.getMax());
				avgPartialDefLenMin.addNumber(avgPartialDefinitionLength.getMin());
				avgPartialDefLenDev.addNumber(avgPartialDefinitionLength.getStandardDeviation());
				
				avgFortifiedPartialDefLenAvg.addNumber(avgFortifiedPartialDefinitionLengthStat.getMean());
				avgFortifiedPartialDefLenMax.addNumber(avgFortifiedPartialDefinitionLengthStat.getMax());
				avgFortifiedPartialDefLenMin.addNumber(avgFortifiedPartialDefinitionLengthStat.getMin());
				avgFortifiedPartialDefLenDev.addNumber(avgFortifiedPartialDefinitionLengthStat.getStandardDeviation());
				
				
				defLenAvg.addNumber(length.getMean());			
				defLenMax.addNumber(length.getMax());
				defLenMin.addNumber(length.getMin());
				defLenDev.addNumber(length.getStandardDeviation());
				
				//counter partial definitions
				noOfCounterPartialDefinitionsAvg.addNumber(noOfCounterPartialDefinitions.getMean());
				noOfCounterPartialDefinitionsDev.addNumber(noOfCounterPartialDefinitions.getStandardDeviation());
				noOfCounterPartialDefinitionsMax.addNumber(noOfCounterPartialDefinitions.getMax());
				noOfCounterPartialDefinitionsMin.addNumber(noOfCounterPartialDefinitions.getMin());
				
				noOfCounterPartialDefinitionsUsedAvg.addNumber(noOfCounterPartialDefinitionsUsed.getMean());
				noOfCounterPartialDefinitionsUsedDev.addNumber(noOfCounterPartialDefinitionsUsed.getStandardDeviation());
				noOfCounterPartialDefinitionsUsedMax.addNumber(noOfCounterPartialDefinitionsUsed.getMax());
				noOfCounterPartialDefinitionsUsedMin.addNumber(noOfCounterPartialDefinitionsUsed.getMin());			
							
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
				fortifyAccAvg.addNumber(accuracyFortifyStat.getMean());
				fortifyAccMax.addNumber(accuracyFortifyStat.getMax());
				fortifyAccMin.addNumber(accuracyFortifyStat.getMin());
				fortifyAccDev.addNumber(accuracyFortifyStat.getStandardDeviation());
				
				
				testingCorAvg.addNumber(testingCorrectnessStat.getMean());
				testingCorDev.addNumber(testingCorrectnessStat.getStandardDeviation());
				testingCorMax.addNumber(testingCorrectnessStat.getMax());
				testingCorMin.addNumber(testingCorrectnessStat.getMin());
				
				//fortify correctness
				fortifyCorAvg.addNumber(correctnessFortifyStat.getMean());
				fortifyCorMax.addNumber(correctnessFortifyStat.getMax());
				fortifyCorMin.addNumber(correctnessFortifyStat.getMin());
				fortifyCorDev.addNumber(correctnessFortifyStat.getStandardDeviation());
								
				testingComAvg.addNumber(testingCompletenessStat.getMean());
				testingComDev.addNumber(testingCompletenessStat.getStandardDeviation());
				testingComMax.addNumber(testingCompletenessStat.getMax());
				testingComMin.addNumber(testingCompletenessStat.getMin());
				
				//fortify completeness (level 1 fixing does not change the completeness
				fortifyComAvg.addNumber(completenessFortifyStat.getMean());
				fortifyComMax.addNumber(completenessFortifyStat.getMax());
				fortifyComMin.addNumber(completenessFortifyStat.getMin());
				fortifyComDev.addNumber(completenessFortifyStat.getStandardDeviation());
				
				
				testingFMeasureAvg.addNumber(fMeasure.getMean());
				testingFMeasureDev.addNumber(fMeasure.getStandardDeviation());
				testingFMeasureMax.addNumber(fMeasure.getMax());
				testingFMeasureMin.addNumber(fMeasure.getMin());	
							
				trainingFMeasureAvg.addNumber(fMeasureTraining.getMean());
				trainingFMeasureDev.addNumber(fMeasureTraining.getStandardDeviation());
				trainingFMeasureMax.addNumber(fMeasureTraining.getMax());
				trainingFMeasureMin.addNumber(fMeasureTraining.getMin());
				
				fortifyFmeasureAvg.addNumber(fmeasureFortifyStat.getMean());
				fortifyFmeasureMax.addNumber(fmeasureFortifyStat.getMax());
				fortifyFmeasureMin.addNumber(fmeasureFortifyStat.getMin());
				fortifyFmeasureDev.addNumber(fmeasureFortifyStat.getStandardDeviation());
								
				noOfDescriptionsAgv.addNumber(totalNumberOfDescriptions.getMean());
				noOfDescriptionsMax.addNumber(totalNumberOfDescriptions.getMax());
				noOfDescriptionsMin.addNumber(totalNumberOfDescriptions.getMin());
				noOfDescriptionsDev.addNumber(totalNumberOfDescriptions.getStandardDeviation());
			}
			
		}	//for kk folds
		
		if (noOfRuns > 1) {
	
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
			
			
			//this is for copying to word document
			//f-measure, accuracy, correctness, completeness, avg pdef length, no of pdef, time, no of des, no of cpdef
			outputWriter("***without fortify (f-measure, accuracy, correctness, completeness, avg. pdef length)***\n"
					+ df.format(testingFMeasureAvg.getMean()) + "\n" + df.format(testingFMeasureDev.getMean())+ "\n"
					+ df.format(testingAccAvg.getMean()) + "\n" + df.format(testingAccDev.getMean()) + "\n"
					+ df.format(testingCorAvg.getMean()) + "\n" + df.format(testingCorDev.getMean()) + "\n"
					+ df.format(testingComAvg.getMean()) + "\n" + df.format(testingComDev.getMean()) + "\n"
					+ df.format(avgPartialDefLenAvg.getMean()) + "\n" + df.format(avgPartialDefLenDev.getMean()) + "\n");
			

			outputWriter("***with fortify (f-measure, accuracy, correctness, completeness, avg. fortified pdef length)***\n"
					+ df.format(fortifyFmeasureAvg.getMean()) + "\n" + df.format(fortifyFmeasureDev.getMean()) + "\n"
					+ df.format(fortifyAccAvg.getMean()) + "\n" + df.format(fortifyAccDev.getMean()) + "\n"
					+ df.format(fortifyCorAvg.getMean()) + "\n" + df.format(fortifyCorDev.getMean()) + "\n"
					+ df.format(fortifyComAvg.getMean()) + "\n" + df.format(fortifyComDev.getMean()) + "\n"
					+ df.format(avgFortifiedPartialDefLenAvg.getMean()) + "\n" + df.format(avgFortifiedPartialDefLenDev.getMean()) + "\n");

			
			outputWriter("***Common dimensions (no of pdef., learning time, no of des., no of cpdef., avg. no of fdef.)***\n"
					+ df.format(noOfPartialDefAvg.getMean()) + "\n" + df.format(noOfPartialDefDev.getMean()) + "\n"
					+ df.format(learningTime.getMean()) + "\n" + df.format(learningTime.getStandardDeviation()) + "\n"
					+ df.format(noOfDescriptionsAgv.getMean()) + "\n" + df.format(noOfDescriptionsDev.getMean()) + "\n"
					+ df.format(noOfCounterPartialDefinitionsAvg.getMean()) + "\n" + df.format(noOfCounterPartialDefinitionsDev.getMean()) + "\n"
					+ df.format(avgNoOfFortifiedDefinitions.getMean()) + "\n" + df.format(avgNoOfFortifiedDefinitions.getStandardDeviation()) + "\n"
					);


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
	

}