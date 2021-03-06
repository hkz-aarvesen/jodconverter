package com.mydocket.pdfserver;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.artofsolving.jodconverter.office.DefaultOfficeManagerConfiguration;
import org.artofsolving.jodconverter.office.OfficeManager;

public class Console {

	public static final String OPT_HELP       = "help";
	public static final String OPT_HELP_SHORT = "h";
	
	public static final String OPT_PORT       = "port";
	public static final String OPT_PORT_SHORT = "p";
	public static final int    DFLT_PORT      = 7001;

	public static final String OPT_OO_PORT     = "ooport";
	public static final int    DFLT_OO_PORT    = 7007;
	
	public static final String OPT_OO_HOME     = "oohome";
	
	
	
	public static void main(String[] args) throws Exception {
		Console c = new Console();
		int exitCode = 0;
		try {
			c.execute(args);
		} catch (Exception e) {
			if (!( e instanceof HarmlessException)) {
				if (! (e instanceof QuietException)) {
					e.printStackTrace();
				}
				exitCode = 1;
			}
		}
		System.exit(exitCode);
	}
	
	public int execute(String[] args) throws Exception {
		MoreOptions opts = buildOptions();
		CommandLine cmd  = buildCommandLine(opts, args);
		
		int serverPort = getInt(cmd, OPT_PORT, DFLT_PORT);
		int ooPort     = getInt(cmd, OPT_OO_PORT, DFLT_OO_PORT);

		// spin up open office server
		DefaultOfficeManagerConfiguration ooconfig = new DefaultOfficeManagerConfiguration();
		ooconfig.setPortNumber(ooPort);
		ooconfig.setOfficeHome(cmd.getOptionValue(OPT_OO_HOME));

		final OfficeManager manager = ooconfig.buildOfficeManager();
		manager.start();
		execute(manager, serverPort);
//		addHook(manager);
//		
//		Server server = new Server(serverPort, manager);
//		// TODO: so... are we going to zombify OO all the time here?
//		server.run();
		return 0;
	}
	
	protected void execute(OfficeManager manager, int serverPort) throws Exception {
		addHook(manager);
		
		Server server = new Server(serverPort, manager);
		// TODO: so... are we going to zombify OO all the time here?
		server.run();
	}
	
	private void addHook(final OfficeManager manager) {
		Thread t = new Thread() {
			@Override
			public void run() {
				manager.stop();
			}
		};
		Runtime.getRuntime().addShutdownHook(t);
	}
	
	
	private MoreOptions buildOptions() {
		MoreOptions opts = new MoreOptions();
		
		opts.addOption(OPT_HELP, OPT_HELP_SHORT, false, "This messsage");
		opts.addOption(OPT_PORT, OPT_PORT_SHORT, true, "Server port to listen on (" + DFLT_PORT + ")");
		
		opts.addOption(OPT_OO_PORT, true, "Local port to run the OpenOffice server on (" + DFLT_OO_PORT + ")");
		opts.addRequired(OPT_OO_HOME, "Local path soffice directory -- this is the OpenOffice install");
		
		return opts;
	}
	
	
	private CommandLine buildCommandLine(MoreOptions opts, String[] args) throws Exception {
		GnuParser parser = new GnuParser();
		CommandLine cmd = parser.parse(opts, args);
		if (cmd == null) {
			throw new Exception("Could not parse command line");
		}
		if (cmd.hasOption(OPT_HELP_SHORT)) {
			this.usage(opts, null);
			throw new HarmlessException();
		}
		StringBuilder missing = new StringBuilder();
		for (String o : opts.getRequired() ) {
			if (! cmd.hasOption(o)) {
				if (missing.length() > 0) {
					missing.append(", ");
				}
				missing.append(o);
			}
		}
		if (missing.length() > 0) {
			this.usage(opts, "Missing required param(s): " + missing.toString());
			throw new QuietException();
		}
		
		
		return cmd;
	}
	
	private void usage(MoreOptions opts, String msg) {
		String usage = "Doc to OO/PDF converter server";
		if (msg != null) {
			usage = msg + "\n" + usage;
		}
		HelpFormatter fmt = new HelpFormatter();
		fmt.printHelp(usage, opts);
	}
	
	public int getInt(CommandLine cmd, String option, int dflt) throws Exception {
		int value = 0;
		if (! cmd.hasOption(option)) {
			value = dflt;
		} else {
			String oValue = cmd.getOptionValue(option);
			try {
				value = Integer.parseInt(cmd.getOptionValue(option));
			} catch (NumberFormatException nfe) {
				throw new Exception("Option '" + option + "' expects a number but was passed '" + oValue + "'");				
			}
		}
		return value;
	}
	
	
	// exceptions of this type will cause you to exit with a zero rather than 1 exit status
	class HarmlessException extends Exception {
		private static final long serialVersionUID = 1L;}
	
	// return non-zero, but don't print the stack
	class QuietException extends Exception {
		private static final long serialVersionUID = 1L;}
}
