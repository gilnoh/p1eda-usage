package eu.excitement.example.p1edausage;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.uima.jcas.JCas;
import org.apache.commons.io.FileUtils;

import eu.excitementproject.eop.alignmentedas.p1eda.P1EDATemplate;
import eu.excitementproject.eop.alignmentedas.p1eda.TEDecisionWithAlignment;
import eu.excitementproject.eop.alignmentedas.p1eda.instances.SimpleWordCoverageIT;
import eu.excitementproject.eop.common.EDAException;
import eu.excitementproject.eop.lap.LAPAccess;
import eu.excitementproject.eop.lap.LAPException;
import eu.excitementproject.eop.lap.dkpro.TreeTaggerIT;

/**
 * The class shows how you can access P1EDA (the alignment EDA, phase-1) when 
 * you are using the EOP as a library. 
 * 
 * Note: 
 * The class only shows usage examples of official instances  (that of SimpleWordCoverage classes). 
 * 
 * But P1EDA is designed to be extended by extend the template (by adding additional 
 * aligners / feature extractors). This is done by extending P1EDATemplate base class. 
 * For working examples, please check EOP sources --- especially the instances in  
 * EOP alignmentedas module (see package eu.excitementproject.eop.alignmentedas.p1eda.sandbox) 
 *  
 * @author Tae-Gil Noh
 *
 */
public class SimpleUsageExampleIT {
	
	// NOTE: before running the example --- 
	// please set correct Italian WordNet path
	// modify the following line for your own environment. 
	private static final String itWordNetDirPath = "/Users/tailblues/eop-resources-1.1.4/ontologies/ItalianWordNet-dict";
	
	public static void main (String[] args)
	{
		BasicConfigurator.configure(); 
		Logger.getRootLogger().setLevel(Level.INFO);  // set INFO to hide Debug info. 

		try {
			// this method shows an example of training .. 
			trainExampleIT(); // takes some minutes. 
			
			// once a model is trained, you can process single pairs ... this method 
			// shows how you can do that. 
			singlePairTestExampleIT();  
			
			// or you can evaluate the model on a test set. 
			evaluationExampleIT(); // also takes some minutes. 
		} catch (Exception e)
		{
			e.printStackTrace();
			System.exit(1); 
		}
	}
		
	/**
	 * This method shows how you can train with an instance of P1EDA. 
	 */
	public static void trainExampleIT() throws EDAException, LAPException, IOException
	{
		//
		// 1. prepare one LAP(linguistic analysis pipeline, preprocessing) and 
		//    one EDA (Entailment Decision Algorihthm), with P1EDA instance.  
		
		// here, we use a TreeTagger based pipeline ... 
		LAPAccess lap = new TreeTaggerIT(); 
		
		// initialize P1EDA instance (that of SimpleWordCoverage), with the two paths 
		P1EDATemplate p1eda = new SimpleWordCoverageIT(itWordNetDirPath); // Put your (configured, instance) P1EDA here... 

		//
		// 2. now we have LAP and EDA. Time to do a training. 
		// 		
    	File rteTrainingXML = new File("src/main/resources/Italian_dev.xml");  // the source holds RTE3 cases here...
    	
    	// first, we do the pre-processing of the training data set, (running TreeTagger) 
    	File trainXmiDir = new File("target/trainingXmis/");   // each annotated pair will be stored here... 
		runLAPForXmis(lap, rteTrainingXML, trainXmiDir); 
		
		// now we are ready to ask the P1EDA instance for training. 
		// this takes some time 
		File classifierModel = new File ("target/p1eda.trained.model"); 
		p1eda.startTraining(trainXmiDir, classifierModel); // first argument: where is the annotated training data? 
														   // second argument: the new model file to be generated. 
		
		// now the training is over, and new model trained from the data has been 
		// generated on the specified path. next two examples will use that model file. 
		System.out.println("The training is complete, and the resulting trained-model file has been stored in the given path"); 
	}
	
	/**
	 * this method shows how you can load a trained model, and ask the EDA to process 
	 * one pair of Text-Hypothesis. 
	 */
	public static void singlePairTestExampleIT() throws EDAException, LAPException, IOException
	{
		// prepare LAP and P1EDA instance ... 
		LAPAccess lap = new TreeTaggerIT(); 
		P1EDATemplate p1eda = new SimpleWordCoverageIT(itWordNetDirPath); 
		
		// Load the pre-trained model... 
		p1eda.initialize(new File("target/p1eda.trained.model"));
		
		// Okay, time generate an annotated T-H pair. Note that, the P1EDA (SimpleWordCoverage) 
		// minimally requires "Token", "Lemma", and "POS" annotations. TreeTagger lap does 
		// provide all of them. So... 
		
		JCas aJCas = lap.generateSingleTHPairCAS("Tripontium era inizialmente un posto di frontiera militare, fondato subito dopo l'invasione romana della Britannia intorno al 50 a.C.", "I romani arrivarono in Britannia intorno al 50 a.C."); 
		TEDecisionWithAlignment decision = p1eda.process(aJCas); // this call makes EDA to do the decision.  
		
		System.out.println("The decision was: " + decision.getDecision().toString()); 
		System.out.println("The confidence was: " + decision.getConfidence()); 
	}
	
	public static void evaluationExampleIT() throws EDAException, LAPException, IOException
	{
		// prepare LAP and P1EDA instance, and load model. 
		LAPAccess lap = new TreeTaggerIT(); 
		P1EDATemplate p1eda = new SimpleWordCoverageIT(itWordNetDirPath); 
		p1eda.initialize(new File("target/p1eda.trained.model"));

		// prepare the test set ... 
    	File rteTestingXML = new File("src/main/resources/Italian_test.xml");  // this file holds RTE IT test 800 pairs. 
    	
    	// pre-processing of the testing set data (this takes some time) 
    	File testingXmiDir = new File("target/testingXmis/");   // each annotated pair will be stored here... 
    	runLAPForXmis(lap, rteTestingXML, testingXmiDir); 

    	// okay, time to ask EDA to do evaluation on train data. 
		List<Double> evalResult = p1eda.evaluateModelWithGoldXmis(testingXmiDir); 
		
		System.out.println("(accuracy, f1, prec, recall, true positive ratio, true negative ratio)"); 
		System.out.println(evalResult.toString()); 		
	}

	// utility, which will run preprocessing (LAP) on train/test data and 
	// make "annotated" pairs on a target directory. 
    public static void runLAPForXmis(LAPAccess lap, File rteInputXML, File xmiDir) throws LAPException, IOException
    {
    	if (xmiDir.exists()) {
    		// delete all contents, if the target directory already exists 
    		FileUtils.deleteDirectory(xmiDir); 
    	}
    	xmiDir.mkdirs();     	
    	lap.processRawInputFormat(rteInputXML, xmiDir); 
    }
}
