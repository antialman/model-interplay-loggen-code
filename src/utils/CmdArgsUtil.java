package utils;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class CmdArgsUtil {

	private CmdArgsUtil() {
		//Private constructor to avoid unnecessary instantiation of the class
	}

	//Handling cmd arguments
	public static CommandLine handleArgs(String[] args) {
		//TODO Review after implementation is done
		Options options = new Options();
		
		Option declareParam = new Option("c", "costsFile", true, "Input model files and violation costs. Model and corresponding violation cost separated by comma. Each model on separate line. File extension defines the model type (decl - Declare; pnml - Data Petri Net; ltl - same format as decl file, but using raw formulas instead of Declare templates) ");
		declareParam.setRequired(true);
		options.addOption(declareParam);

		Option logParam = new Option("o", "outputFile", true, "Output event log path");
		logParam.setRequired(true);
		options.addOption(logParam);

		Option posTracesParam = new Option("p", "posTraces", true, "Number of positive traces to generate");
		posTracesParam.setRequired(true);
		options.addOption(posTracesParam);

		Option negTracesParam = new Option("n", "negTraces", true, "Number of negative traces to generate");
		negTracesParam.setRequired(true);
		options.addOption(negTracesParam);

		Option violProbParam = new Option("e", "violProb", true, "Probability of considering violating next states at each step. Default: 5");
		violProbParam.setRequired(false);
		options.addOption(violProbParam);

		CommandLineParser parser = new org.apache.commons.cli.DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		CommandLine cmd = null;

		try {
			cmd = parser.parse(options, args, true);
		} catch (ParseException e) {
			System.out.println(e.getMessage());
			formatter.printHelp("java -jar .\\InterplayLogGen.jar ", options);
			System.exit(1);
		}

		return cmd;
	}
}
