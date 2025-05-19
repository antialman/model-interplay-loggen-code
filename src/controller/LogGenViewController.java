package controller;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.model.XLog;
import org.processmining.datapetrinets.io.DPNIOException;
import org.processmining.ltl2automaton.plugins.automaton.State;
import org.processmining.ltl2automaton.plugins.automaton.Transition;
import org.processmining.plugins.declareminer.ExecutableAutomaton;
import org.processmining.plugins.declareminer.PossibleNodes;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.AbstractModel;
import proposition.PropositionData;
import task.MonitoringTask;
import utils.AutomatonUtils;
import utils.FileUtils;
import utils.LogUtils;
import utils.ModelUtils;
import utils.enums.MonitoringState;

public class LogGenViewController {

	@FXML
	private VBox settingsPanel;
	@FXML
	private Label eventLogLabel;
	@FXML
	private Spinner<Integer> numberOfPosTracesSpinner;
	@FXML
	private Spinner<Integer> numberOfNegTracesSpinner;
	@FXML
	private Spinner<Integer> violProbabilitySpinner;
	@FXML
	private TableView<AbstractModel> modelTableView;
	@FXML
	private TableColumn<AbstractModel, String> modelNameColumn;
	@FXML
	private TableColumn<AbstractModel, String> modelTypeColumn;
	@FXML
	private TableColumn<AbstractModel, Number> modelCostColumn;
	@FXML
	private TableColumn<AbstractModel, AbstractModel> modelRemoveColumn;
	@FXML
	private Button startLogGenButton;
	@FXML
	private ListView<String> tracesListView;
	@FXML
	private ScrollPane resultsPane;

	private Stage stage;
	private int defaultViolationCost = 5;
	private ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

	//Data structures
	PropositionData propositionData = new PropositionData();
	Set<String> allPropositions;
	ExecutableAutomaton globalAutomaton;
	Map<State, Map<AbstractModel, MonitoringState>> globalAutomatonColours;
	Map<State, Integer> costCurrMap;
	Map<State, Integer> costBestMap;
	
	//statistics
	long automatonCreationTime;
	long logGenTime;
	List<Long> eventProcessingTimes = new ArrayList<Long>();

	List<VBox> resultsList;

	private XLog xlog;
	
	String outputFile;
	
	
	String logHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\r\n" + 
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
	
	String logEnd = "</log>\r\n";
	
	
	String traceStart = "\t<trace>\r\n" + 
			"\t\t<string key=\"concept:name\" value=\"Case No. 01\"/>\r\n";
	
	String eventStart = "\t\t<event>\r\n" + 
			"			<string key=\"concept:name\" value=\"";
	String activityEnd = "\"/>\r\n" + 
			"			<string key=\"lifecycle:transition\" value=\"complete\"/>\r\n";
	
	String attributeStart = "			<string key=\"";
	String attributeValueStart = "\" value=\"";
	String attributeEnd = "\"/>\r\n";
	
	String eventEnd = "		</event>\r\n";

	String traceEnd = "\t</trace>\r\n";
	
	
	
	public void setStage(Stage stage) {
		this.stage = stage;
	}

	@FXML
	private void initialize() {
		numberOfPosTracesSpinner.setValueFactory(new IntegerSpinnerValueFactory(0, Integer.MAX_VALUE, 100, 1));	// Causes NPE on empty input
		numberOfNegTracesSpinner.setValueFactory(new IntegerSpinnerValueFactory(0, Integer.MAX_VALUE, 100, 1));	// Causes NPE on empty input
		violProbabilitySpinner.setValueFactory(new IntegerSpinnerValueFactory(0, 100, 5, 1));	// Causes NPE on empty input
		
		modelTableView.setPlaceholder(new Label("No input models selected"));
		modelNameColumn.setCellFactory(TextFieldTableCell.forTableColumn());
		modelNameColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getModelName()));
		modelTypeColumn.setCellFactory(TextFieldTableCell.forTableColumn());
		modelTypeColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getModelType().toString()));
		modelCostColumn.setCellFactory(col -> new IntegerEditingCell());
		modelCostColumn.setCellValueFactory(data -> data.getValue().violationCostProperty());
		modelRemoveColumn.setCellValueFactory(
				param -> new ReadOnlyObjectWrapper<AbstractModel>(param.getValue())
				);
		modelRemoveColumn.setCellFactory(param -> new TableCell<AbstractModel, AbstractModel>() {
			private final Button removeButton = new Button("Remove");

			@Override
			protected void updateItem(AbstractModel item, boolean empty) {
				super.updateItem(item, empty);

				if (item == null) {
					setGraphic(null);
					return;
				}

				setGraphic(removeButton);
				removeButton.setOnAction(
						event -> getTableView().getItems().remove(item)
						);
			}
		});

		tracesListView.getSelectionModel().selectedIndexProperty().addListener((obs, oldIndex, newIndex) -> {
			if (newIndex.intValue() >= 0) {
				resultsPane.setContent(resultsList.get(newIndex.intValue()));
			} else {
				resultsPane.setContent(null);
			}
		});
	}

	@FXML
	private void selectOutputLog() {
		File logFile = FileUtils.showLogOutputDialog(stage);
		if (logFile != null) {
			outputFile = logFile.getAbsolutePath();
			eventLogLabel.setText(logFile.getAbsolutePath());
		}
	}

	@FXML
	private void addModel() {
		List<File> modelFiles = FileUtils.showModelOpenDialog(stage);
		if (modelFiles != null) {
			List<AbstractModel> abstractModels = new ArrayList<AbstractModel>();
			for (File modelFile : modelFiles) {
				String modelName = modelFile.getName();
				try {
					String modelExtension = modelName.substring(modelName.lastIndexOf(".")+1);
					if ("decl".equalsIgnoreCase(modelExtension)) {
						abstractModels.add(ModelUtils.loadDeclareModel(modelFile.toPath(), modelName, defaultViolationCost));
					} else if ("ltl".equalsIgnoreCase(modelExtension)) {
						abstractModels.add(ModelUtils.loadLtlModel(modelFile.toPath(), modelName, defaultViolationCost));
					} else if ("pnml".equalsIgnoreCase(modelExtension)) {
						abstractModels.add(ModelUtils.loadDpnModel(modelFile.toPath(), modelName, defaultViolationCost));
					} else {
						System.err.println("Skipping model of unknown type: " + modelExtension);
					}
				} catch (DPNIOException | IOException | IndexOutOfBoundsException e) {
					System.err.println("Unable to load model: " + modelFile.getAbsolutePath());
					e.printStackTrace();
				}
			}
			modelTableView.getItems().addAll(abstractModels);
		}
	}

	@FXML
	private void startLogGen() {
		settingsPanel.setDisable(true);
		
		long startTime = System.nanoTime();
		createLogGenDataStructures();
		automatonCreationTime = System.nanoTime() - startTime;
		System.out.println("Automaton creation time (ms): " + TimeUnit.MILLISECONDS.convert(automatonCreationTime, TimeUnit.NANOSECONDS));

		tracesListView.getItems().clear();
		resultsList = new ArrayList<VBox>();
		
		int numberOfPosTraces = numberOfPosTracesSpinner.getValue();
		int numberOfNegTraces = numberOfNegTracesSpinner.getValue();
		int violProbability = violProbabilitySpinner.getValue();
		
		System.out.println("Log generation start - " + "pos=" + numberOfPosTraces + " neg=" + numberOfNegTraces + " violProb=" + violProbability);
		startTime = System.nanoTime();
		generateEventLog(numberOfPosTraces, numberOfNegTraces, violProbability);
		logGenTime = System.nanoTime() - startTime;
		System.out.println("Log Generation time (ms)    : " + TimeUnit.MILLISECONDS.convert(logGenTime, TimeUnit.NANOSECONDS));
		System.out.println("Done!");
		

		//System.out.println("Log generation n=100");
		//startTime = System.nanoTime();
		//generateEventLog(50,50);
		//logGenTime = System.nanoTime() - startTime;
		//System.out.println("Log Generation time (ms)    : " + TimeUnit.MILLISECONDS.convert(logGenTime, TimeUnit.NANOSECONDS));
		//System.out.println("Total time (ms)    : " + (TimeUnit.MILLISECONDS.convert(automatonCreationTime, TimeUnit.NANOSECONDS) + TimeUnit.MILLISECONDS.convert(logGenTime, TimeUnit.NANOSECONDS)));
		//System.out.println();
		
		//System.out.println("Log generation n=1000");
		//startTime = System.nanoTime();
		//generateEventLog(500,500);
		//logGenTime = System.nanoTime() - startTime;
		//System.out.println("Log Generation time (ms)    : " + TimeUnit.MILLISECONDS.convert(logGenTime, TimeUnit.NANOSECONDS));
		//System.out.println("Total time (ms)    : " + (TimeUnit.MILLISECONDS.convert(automatonCreationTime, TimeUnit.NANOSECONDS) + TimeUnit.MILLISECONDS.convert(logGenTime, TimeUnit.NANOSECONDS)));
		//System.out.println();
		
		//System.out.println("Log generation n=10000");
		//startTime = System.nanoTime();
		//generateEventLog(5000,5000);
		//logGenTime = System.nanoTime() - startTime;
		//System.out.println("Log Generation time (ms)    : " + TimeUnit.MILLISECONDS.convert(logGenTime, TimeUnit.NANOSECONDS));
		//System.out.println("Total time (ms)    : " + (TimeUnit.MILLISECONDS.convert(automatonCreationTime, TimeUnit.NANOSECONDS) + TimeUnit.MILLISECONDS.convert(logGenTime, TimeUnit.NANOSECONDS)));
		//System.out.println();
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
	
	private void generateEventLog(int positiveTraces, int negativeTraces, int violProbability) {
		
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
			xlog = LogUtils.convertToXlog(outputFile);
			replayNextTrace();
		} catch (IOException e) {
			System.err.println("Unable to write to event log file");
		}
		
		settingsPanel.setDisable(false);
	}

	private void generatePositiveTrace(int id) {
		
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
			
			Transition selectedTransition = suitableTransitions.get((int)(Math.random() * suitableTransitions.size()));
			
			int duration = ((14 * createRandomIntBetween(30, 60)) + 59) * 10000;
			eventTimestamp.setTime(eventTimestamp.getTime()+duration);
			
			String transitionLabel = null;
			if (selectedTransition.isPositive()) {
				transitionLabel = selectedTransition.getPositiveLabel();
			} else if (selectedTransition.isNegative()) {
				List<String> candidatePropositions = new ArrayList<String>(allPropositions);
				candidatePropositions.removeAll(selectedTransition.getNegativeLabels());
				transitionLabel = candidatePropositions.get((int)(Math.random() * candidatePropositions.size()));
			}
			
			writeTransition(transitionLabel, eventTimestamp);
			currentState = globalAutomaton.next(transitionLabel);
		}
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile), StandardOpenOption.APPEND)) {
			writer.write(traceEnd);
		} catch (IOException e) {
			System.err.println("Unable to write to event log file");
		}
	}

	private void generateNegativeTrace(int id, int violProbability) {
		
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
			
			Transition selectedTransition = suitableTransitions.get((int)(Math.random() * suitableTransitions.size()));
			
			int duration = ((14 * createRandomIntBetween(30, 60)) + 59) * 10000;
			eventTimestamp.setTime(eventTimestamp.getTime()+duration);
			
			String transitionLabel = null;
			if (selectedTransition.isPositive()) {
				transitionLabel = selectedTransition.getPositiveLabel();
			} else if (selectedTransition.isNegative()) {
				List<String> candidatePropositions = new ArrayList<String>(allPropositions);
				candidatePropositions.removeAll(selectedTransition.getNegativeLabels());
				transitionLabel = candidatePropositions.get((int)(Math.random() * candidatePropositions.size()));
			}
			
			writeTransition(transitionLabel, eventTimestamp);
			currentState = globalAutomaton.next(transitionLabel);
		}
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile), StandardOpenOption.APPEND)) {
			writer.write(traceEnd);
		} catch (IOException e) {
			System.err.println("Unable to write to event log file");
		}
		
	}
	
	private boolean isSinkState(PossibleNodes currentState) {
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
	
	private void writeTransition(String transitionLabel, Timestamp eventTimestamp) {
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
			if (attributeValue.contains(",")) { //This does not account for the allowed value range in the decl model and does not work well for floats
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
				writer.write(attributeStart);
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
	
	
	
	
	private void replayNextTrace() {
		if (resultsList.size() < xlog.size()) {
			MonitoringTask monitoringTask = new MonitoringTask(xlog.get(resultsList.size()), modelTableView.getItems(), propositionData, globalAutomaton, globalAutomatonColours, costCurrMap, costBestMap, eventProcessingTimes);

			monitoringTask.setOnSucceeded(event -> {
				resultsList.add(monitoringTask.getValue());
				tracesListView.getItems().add(XConceptExtension.instance().extractName(xlog.get(resultsList.size()-1)));
				replayNextTrace();
			});

			monitoringTask.setOnFailed(event -> {
				resultsList.add(new VBox(new Label("Error getting trace results")));
				tracesListView.getItems().add(XConceptExtension.instance().extractName(xlog.get(resultsList.size()-1)));
				replayNextTrace();
			});
			executorService.execute(monitoringTask);

		} else {
			settingsPanel.setDisable(false);
			
			System.out.println("\n\n\n");
			System.out.println("===========================================");
			System.out.println("Replay Statistics");
			System.out.println("===========================================");
			//System.out.println("Monitoring automaton creation time (ms): " + TimeUnit.MILLISECONDS.convert(automatonCreationTime, TimeUnit.NANOSECONDS));
			//System.out.println("Monitoring automaton number of states: " + globalAutomaton.stateCount());
			//System.out.println("Monitoring automaton memory consumption (MB): " + "TODO");
			System.out.println("Event processing time (min): " + TimeUnit.MILLISECONDS.convert(Collections.min(eventProcessingTimes), TimeUnit.NANOSECONDS));
			System.out.println("Event processing time (max): " + TimeUnit.MILLISECONDS.convert(Collections.max(eventProcessingTimes), TimeUnit.NANOSECONDS));
			long sum = 0;
			for(int i = 0; i < eventProcessingTimes.size(); i++) {
		        sum += eventProcessingTimes.get(i);
			}
			long avg = sum / eventProcessingTimes.size();
			
			System.out.println("Event processing time (avg): " + TimeUnit.MILLISECONDS.convert(avg, TimeUnit.NANOSECONDS));
		}
	}

	private void createLogGenDataStructures() {
		//TODO: Avoid code duplication (following code is duplicated in MainCmd.java)
		
		long startTime = System.nanoTime();
		System.out.println("Start: Populating propositionalization data structure");
		for (AbstractModel processModel : modelTableView.getItems()) {
			propositionData.addActivities(processModel);
			propositionData.addAttributePropositions(processModel);
		}
		System.out.println("Done: Populating propositionalization data structure - " + TimeUnit.MILLISECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS) + "(ms) \n");


		//Creating a propositionalized automaton of each input model
		System.out.println("Start: Creating a propositionalized automaton of each input model");
		for (AbstractModel processModel : modelTableView.getItems()) {
			processModel.initializeAutomaton(propositionData);
		}
		System.out.println("Done: Creating a propositionalized automaton of each input model\n");


		System.out.println("Start: Creating the global automaton");
		globalAutomaton = AutomatonUtils.createGlobalAutomaton(modelTableView.getItems());
		System.out.println("Done: Creating the global automaton\n");

		System.out.println("Start: Calculating colors for each state of the global automaton");
		globalAutomatonColours = AutomatonUtils.getGlobalAutomatonColours(modelTableView.getItems(), globalAutomaton);
		System.out.println("Done: Calculating colors for each state of the global automaton\n");

		System.out.println("Start: Calculating cost_curr and cost_best values for each state of the global automaton");
		costCurrMap = AutomatonUtils.getCostCurrMap(modelTableView.getItems(), globalAutomaton, globalAutomatonColours);
		costBestMap = AutomatonUtils.getCostBestMap(globalAutomaton, costCurrMap);
		System.out.println("Done: Calculating cost_curr and cost_best values for each state of the global automaton\n");
	}


	private class IntegerEditingCell extends TableCell<AbstractModel, Number> {
		//https://stackoverflow.com/questions/27900344/how-to-make-a-table-column-with-integer-datatype-editable-without-changing-it-to
		private final TextField textField = new TextField();
		private final Pattern intPattern = Pattern.compile("\\d+");

		public IntegerEditingCell() {
			textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
				if (! isNowFocused) {
					processEdit();
				}
			});
			textField.setOnAction(event -> processEdit());
		}

		private void processEdit() {
			String text = textField.getText();
			if (intPattern.matcher(text).matches()) {
				commitEdit(Integer.parseInt(text));
			} else {
				cancelEdit();
			}
		}

		@Override
		public void updateItem(Number value, boolean empty) {
			super.updateItem(value, empty);
			if (empty) {
				setText(null);
				setGraphic(null);
			} else if (isEditing()) {
				setText(null);
				textField.setText(value.toString());
				setGraphic(textField);
			} else {
				setText(value.toString());
				setGraphic(null);
			}
		}

		@Override
		public void startEdit() {
			super.startEdit();
			Number value = getItem();
			if (value != null) {
				textField.setText(value.toString());
				setGraphic(textField);
				setText(null);
			}
		}

		@Override
		public void cancelEdit() {
			super.cancelEdit();
			setText(getItem().toString());
			setGraphic(null);
		}

		// This seems necessary to persist the edit on loss of focus; not sure why:
		@Override
		public void commitEdit(Number value) {
			super.commitEdit(value);
			((AbstractModel)this.getTableRow().getItem()).setViolationCost(value.intValue());
		}
	}
}
