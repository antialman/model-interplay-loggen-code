package main;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.ltl2automaton.plugins.automaton.State;
import org.processmining.ltl2automaton.plugins.automaton.Transition;
import org.processmining.plugins.declareminer.ExecutableAutomaton;
import org.processmining.plugins.declareminer.PossibleNodes;

import model.AbstractModel;
import proposition.PropositionData;
import proposition.attribute.FloatAttribute;
import proposition.attribute.IntegerAttribute;
import proposition.attribute.StringAttribute;
import utils.AutomatonUtils;
import utils.CmdArgsUtil;
import utils.LogUtils;
import utils.ModelUtils;
import utils.enums.MonitoringState;

public class MainCmd {
	
	private static String logHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\r\n" + 
			"<!-- This file has been generated with the OpenXES library. It conforms -->\r\n" + 
			"<!-- to the XML serialization of the XES standard for log storage and -->\r\n" + 
			"<!-- management. -->\r\n" + 
			"<!-- XES standard version: 1.0 -->\r\n" + 
			"<!-- OpenXES library version: 1.0RC7 -->\r\n" + 
			"<!-- OpenXES is available from http://www.openxes.org/ -->\r\n" + 
			"<log xes.version=\"1.0\" xes.features=\"nested-attributes\" openxes.version=\"1.0RC7\" xmlns=\"http://www.xes-standard.org/\">\r\n" + 
			"	<extension name=\"Organizational\" prefix=\"org\" uri=\"http://www.xes-standard.org/org.xesext\"/>\r\n" + 
			"	<extension name=\"Time\" prefix=\"time\" uri=\"http://www.xes-standard.org/time.xesext\"/>\r\n" + 
			"	<extension name=\"Lifecycle\" prefix=\"lifecycle\" uri=\"http://www.xes-standard.org/lifecycle.xesext\"/>\r\n" + 
			"	<extension name=\"Semantic\" prefix=\"semantic\" uri=\"http://www.xes-standard.org/semantic.xesext\"/>\r\n" + 
			"	<extension name=\"Concept\" prefix=\"concept\" uri=\"http://www.xes-standard.org/concept.xesext\"/>\r\n" + 
			"	<global scope=\"trace\">\r\n" + 
			"		<string key=\"concept:name\" value=\"__INVALID__\"/>\r\n" + 
			"	</global>\r\n" + 
			"	<global scope=\"event\">\r\n" + 
			"		<string key=\"concept:name\" value=\"__INVALID__\"/>\r\n" + 
			"	</global>\r\n" + 
			"	<classifier name=\"Event Name\" keys=\"concept:name\"/>\r\n" + 
			"	<string key=\"concept:name\" value=\"Artificial Log\"/>\r\n" + 
			"	<string key=\"lifecycle:model\" value=\"standard\"/>\r\n" + 
			"	<string key=\"source\" value=\"custom_loggen\"/>\r\n";
	
	private static String logEnd = "</log>\r\n";
	
	
	private static String traceStart = "\t<trace>\r\n" + 
			"\t\t<string key=\"concept:name\" value=\"Case No. 01\"/>\r\n";
	
	private static String eventStart = "\t\t<event>\r\n" + 
			"			<string key=\"concept:name\" value=\"";
	private static String activityEnd = "\"/>\r\n" + 
			"			<string key=\"lifecycle:transition\" value=\"complete\"/>\r\n";
	
	private static String strAttributeStart = "			<string key=\"";
	private static String intAttributeStart = "			<int key=\"";
	private static String floatAttributeStart = "			<float key=\"";
	private static String attributeValueStart = "\" value=\"";
	private static String attributeEnd = "\"/>\r\n";
	
	private static String eventEnd = "		</event>\r\n";

	private static String traceEnd = "\t</trace>\r\n";
	
	
	private static String outputFile;
	private static ExecutableAutomaton globalAutomaton;
	private static Map<State, Integer> costBestMap;
	private static PropositionData propositionData = new PropositionData();
	static Set<String> allPropositions;
	
	
	

	public static void main(String[] args) {
		//Data structures
		List<AbstractModel> processModels;
		Map<State, Map<AbstractModel, MonitoringState>> globalAutomatonColours;
		Map<State, Integer> costCurrMap;
		int numberOfPosTraces = 0;
		int numberOfNegTraces = 0;
		int violProbability = 0;

		System.out.println("Start: Handling parameters");
		CommandLine cmd = CmdArgsUtil.handleArgs(args);
		String costsFilePath = cmd.getOptionValue("costsFile");
		outputFile = cmd.getOptionValue("outputFile");
		try {
			numberOfPosTraces = Integer.parseInt(cmd.getOptionValue("posTraces"));
			numberOfNegTraces = Integer.parseInt(cmd.getOptionValue("negTraces"));
			violProbability = 5;
			if (cmd.hasOption("violProb")) {
				violProbability = Integer.parseInt(cmd.getOptionValue("violProb"));
			}
		} catch (Exception e) {
			System.err.println("Error parsing parameters: " + e.getMessage());
			System.exit(1);
		}
		
		if (numberOfPosTraces < 0 || numberOfNegTraces < 0 || violProbability < 0 || violProbability > 100) {
			System.err.println("Invalid parameter value(s). Number of traces must be positive. violProb must be between 0 and 100 (both included)");
			System.exit(1);
		}
		

		//String costsFilePath = "input/costModel.txt";
		//String eventLogPath = "input/eventlog.xes";
		System.out.println("Done: Handling parameters\n");


		System.out.println("PROCESSING INPUT MODELS");
		System.out.println("======================================================");

		System.out.println("Start: Loading input models");
		processModels = ModelUtils.loadProcessModels(costsFilePath);
		if (processModels == null || processModels.isEmpty()) {
			System.err.println("No models found from the costs file");
			System.exit(-1);
		}
		System.out.println("Done: Loading input models\n");


		System.out.println("Start: Populating propositionalization data structure");
		for (AbstractModel processModel : processModels) {
			propositionData.addActivities(processModel);
			propositionData.addAttributePropositions(processModel);
		}
		System.out.println("Done: Populating propositionalization data structure\n");


		//Creating a propositionalized automaton of each input model
		System.out.println("Start: Creating a propositionalized automaton of each input model");
		for (AbstractModel processModel : processModels) {
			processModel.initializeAutomaton(propositionData);
		}
		System.out.println("Done: Creating a propositionalized automaton of each input model\n");


		System.out.println("Start: Creating the global automaton");
		globalAutomaton = AutomatonUtils.createGlobalAutomaton(processModels);
		System.out.println("Done: Creating the global automaton\n");

		System.out.println("Start: Calculating colors for each state of the global automaton");
		globalAutomatonColours = AutomatonUtils.getGlobalAutomatonColours(processModels, globalAutomaton);
		System.out.println("Done: Calculating colors for each state of the global automaton\n");

		System.out.println("Start: Calculating cost_curr and cost_best values for each state of the global automaton");
		costCurrMap = AutomatonUtils.getCostCurrMap(processModels, globalAutomaton, globalAutomatonColours);
		costBestMap = AutomatonUtils.getCostBestMap(globalAutomaton, costCurrMap);
		System.out.println("Done: Calculating cost_curr and cost_best values for each state of the global automaton\n");


		System.out.println("Log generation start - " + "pos=" + numberOfPosTraces + " neg=" + numberOfNegTraces + " violProb=" + violProbability);
		long startTime = System.nanoTime();
		generateEventLog(numberOfPosTraces, numberOfNegTraces, violProbability);
		long logGenTime = System.nanoTime() - startTime;
		System.out.println("Log Generation time (ms)    : " + TimeUnit.MILLISECONDS.convert(logGenTime, TimeUnit.NANOSECONDS));
		System.out.println("Done!");
	}
	
	
	
	
	private static void generateEventLog(int positiveTraces, int negativeTraces, int violProbability) {
		allPropositions = propositionData.getAllPropositions();
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile))) {
			writer.write(logHeader);
		} catch (IOException e) {
			System.err.println("Unable to write to event log file");
		}
		
		for (int i = 0; i < positiveTraces; i++) {
			
			generatePositiveTrace(i);
		}
		
		for (int i = 0; i < negativeTraces; i++) {
			generateNegativeTrace(positiveTraces + i, violProbability);
		}
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile), StandardOpenOption.APPEND)) {
			writer.write(logEnd);
			writer.flush();
		} catch (IOException e) {
			System.err.println("Unable to write to event log file");
		}
	}
	
	
	
	private static void generatePositiveTrace(int id) {
		
		LocalDate startDate = createRandomDate(Year.now().getValue()-5, Year.now().getValue()-1);
		Timestamp eventTimestamp = Timestamp.valueOf(startDate.atStartOfDay());
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile), StandardOpenOption.APPEND)) {
			writer.write(traceStart.replace("Case No. 01", "Positive No. " + id));
		} catch (IOException e) {
			System.err.println("Unable to write to event log file");
		}
		
		globalAutomaton.ini();
		PossibleNodes currentState = globalAutomaton.currentState();
		
		while (!currentState.isAccepting()) {
			List<Transition> suitableTransitions = new ArrayList<Transition>();
			for (State state : currentState) {
				for (Transition t : state.getOutput()) {
					if ((t.isPositive() || (t.isNegative() && t.getSource() != t.getTarget())) && costBestMap.get(t.getTarget()) == 0) {
						suitableTransitions.add(t);
					}
				}
			}

			int duration = ((14 * createRandomIntBetween(30, 60)) + 59) * 10000;
			eventTimestamp.setTime(eventTimestamp.getTime()+duration);
			
			String transitionLabel = selectTransition(suitableTransitions);
			
			writeTransition(transitionLabel, eventTimestamp);
			currentState = globalAutomaton.next(transitionLabel);
		}
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile), StandardOpenOption.APPEND)) {
			writer.write(traceEnd);
		} catch (IOException e) {
			System.err.println("Unable to write to event log file");
		}
	}

	private static void generateNegativeTrace(int id, int violProbability) {
		
		LocalDate startDate = createRandomDate(Year.now().getValue()-5, Year.now().getValue()-1);
		Timestamp eventTimestamp = Timestamp.valueOf(startDate.atStartOfDay());
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile), StandardOpenOption.APPEND)) {
			writer.write(traceStart.replace("Case No. 01", "Negative No. " + id));
		} catch (IOException e) {
			System.err.println("Unable to write to event log file");
		}
		
		globalAutomaton.ini();
		PossibleNodes currentState = globalAutomaton.currentState();
		
		while (!isSinkState(currentState)) {
			List<Transition> suitableTransitions = new ArrayList<Transition>();
			for (State state : currentState) {
				boolean allowNewViol = (int)(Math.random() * 100) < violProbability;
				
				if (!allowNewViol) {
					for (Transition t : state.getOutput()) {
						if ((t.isPositive() || (t.isNegative() && t.getSource() != t.getTarget())) && costBestMap.get(t.getTarget()) == costBestMap.get(t.getSource())) {
							suitableTransitions.add(t);
						}
					}
				}
				
				if (allowNewViol || suitableTransitions.isEmpty()) {
					for (Transition t : state.getOutput()) {
						if ((t.isPositive() || (t.isNegative() && t.getSource() != t.getTarget()))) {
							suitableTransitions.add(t);
						}
					}
				}
			}

			int duration = ((14 * createRandomIntBetween(30, 60)) + 59) * 10000;
			eventTimestamp.setTime(eventTimestamp.getTime()+duration);
			
			String transitionLabel = selectTransition(suitableTransitions);
			
			writeTransition(transitionLabel, eventTimestamp);
			currentState = globalAutomaton.next(transitionLabel);
		}
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile), StandardOpenOption.APPEND)) {
			writer.write(traceEnd);
		} catch (IOException e) {
			System.err.println("Unable to write to event log file");
		}
		
	}
	
	
	public static LocalDate createRandomDate(int startYear, int endYear) {
        int day = createRandomIntBetween(1, 28);
        int month = createRandomIntBetween(1, 12);
        int year = createRandomIntBetween(startYear, endYear);
        return LocalDate.of(year, month, day);
    }
	
	public static int createRandomIntBetween(int start, int end) {
        return start + (int) Math.round(Math.random() * (end - start));
    }
	
	private static boolean isSinkState(PossibleNodes currentState) {
		for (State state : currentState) {
			if (state.getOutputSize() == 1) {
				for (Transition transition : state.getOutput()) {
					if (transition.isAll() && transition.getTarget() == state) {
						return true;
					}
				}
			}
		}
		return false;
		
	}
	
	private static String selectTransition(List<Transition> suitableTransitions) {
		Transition selectedTransition = suitableTransitions.get((int)(Math.random() * suitableTransitions.size()));
		
		String transitionLabel = null;
		if (selectedTransition.isPositive()) {
			transitionLabel = selectedTransition.getPositiveLabel();
		} else if (selectedTransition.isNegative()) {
			List<String> candidatePropositions = new ArrayList<String>(allPropositions);
			candidatePropositions.removeAll(selectedTransition.getNegativeLabels());
			transitionLabel = candidatePropositions.get((int)(Math.random() * candidatePropositions.size()));
		}
		String activityString = propositionData.propositionToActivityString(transitionLabel, true);
		
		if (activityString.indexOf(" [") != -1) {
			String attributeName = activityString.substring(activityString.indexOf(" [")+2, activityString.indexOf("="));
			if (propositionData.getAttribute(attributeName) instanceof IntegerAttribute) { //Integer constants c1 and c2, such that c1-c2=1, result in intervals (c1,c2) which contain no values  
				String attributeValue = activityString.substring(activityString.indexOf("=")+1, activityString.indexOf("]"));
				if (attributeValue.contains(",") && !attributeValue.contains("inf")) {
					attributeValue = attributeValue.substring(1, attributeValue.length()-1);
					if (Integer.parseInt(attributeValue.split(",")[1]) - Integer.parseInt(attributeValue.split(",")[0]) <= 1) {
						suitableTransitions.remove(selectedTransition);
						transitionLabel = selectTransition(suitableTransitions);
					}
				}
			}
		}
		return transitionLabel;
	}
	
	private static void writeTransition(String transitionLabel, Timestamp eventTimestamp) {
		String activityString = propositionData.propositionToActivityString(transitionLabel, true);
		
		String activityName;
		String attributeName = null;
		String attributeValue = null;
		if (activityString.indexOf(" [") == -1) {
			activityName = activityString;
		} else {
			activityName = activityString.substring(0, activityString.indexOf(" ["));
			attributeName = activityString.substring(activityString.indexOf(" [")+2, activityString.indexOf("="));
			attributeValue = activityString.substring(activityString.indexOf("=")+1, activityString.indexOf("]"));
			if (attributeValue.contains(",") && propositionData.getAttribute(attributeName) instanceof IntegerAttribute) { //This does not account for the allowed value range in the decl model
				attributeValue = attributeValue.substring(1, attributeValue.length()-1);
				String valueLb = attributeValue.split(",")[0];
				String valueUb = attributeValue.split(",")[1];
				if (valueLb.equals("-inf") && valueUb.equals("inf")) {
					attributeValue = String.valueOf(createRandomIntBetween(-5, 5));
				} else {
					if (valueLb.equals("-inf")) { 
						valueLb = String.valueOf(Integer.parseInt(valueUb) - 5);
					}
					if (valueUb.equals("inf")) {
						valueUb = String.valueOf(Integer.parseInt(valueLb) + 5);
					}
					attributeValue = String.valueOf(createRandomIntBetween(Integer.parseInt(valueLb), Integer.parseInt(valueUb)));
				}
			}
		}
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile), StandardOpenOption.APPEND)) {
			writer.write(eventStart);
			writer.write(activityName);
			writer.write(activityEnd);
			if (attributeName != null && attributeValue != null) {
				if (propositionData.getAttribute(attributeName) instanceof StringAttribute) {
					writer.write(strAttributeStart);
				} else if (propositionData.getAttribute(attributeName) instanceof IntegerAttribute) {
					writer.write(intAttributeStart);
				} else if (propositionData.getAttribute(attributeName) instanceof FloatAttribute) {
					writer.write(floatAttributeStart);
				}
				writer.write(attributeName);
				writer.write(attributeValueStart);
				writer.write(attributeValue);
				writer.write(attributeEnd);
			}
			
			writer.write("			<date key=\"time:timestamp\" value=\""+ String.format("%1$TFT%1$TT", eventTimestamp) + "\"/>\r\n");
			
			writer.write(eventEnd);
		} catch (IOException e) {
			System.err.println("Unable to write to event log file");
		}
	}
}
