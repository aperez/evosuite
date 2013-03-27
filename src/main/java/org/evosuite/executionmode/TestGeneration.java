package org.evosuite.executionmode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.evosuite.ClientProcess;
import org.evosuite.EvoSuite;
import org.evosuite.Properties;
import org.evosuite.Properties.StoppingCondition;
import org.evosuite.Properties.Strategy;
import org.evosuite.javaagent.InstrumentingClassLoader;
import org.evosuite.rmi.MasterServices;
import org.evosuite.rmi.service.ClientNodeRemote;
import org.evosuite.statistics.SearchStatistics;
import org.evosuite.utils.ClassPathHacker;
import org.evosuite.utils.ExternalProcessHandler;
import org.evosuite.utils.LoggingUtils;
import org.evosuite.utils.ResourceList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestGeneration {

	private static Logger logger = LoggerFactory.getLogger(TestGeneration.class);
	
	public static Object executeTestGeneration(Options options, List<String> javaOpts,
			CommandLine line, String cp) {
		
		Strategy strategy = getChosenStrategy(javaOpts, line);
		
		if (strategy == null) {
			strategy = Strategy.EVOSUITE;
		} 

		if (line.hasOption("class")) {
			if (line.hasOption("extend")) {
				javaOpts.add("-Djunit_extend="
						+ line.getOptionValue("extend"));
			}
			return generateTests(strategy, line.getOptionValue("class"), javaOpts, cp);
		} else if (line.hasOption("prefix")){
			generateTestsPrefix(strategy, line.getOptionValue("prefix"),javaOpts, cp);
		} else if (line.hasOption("target")) {
			String target = line.getOptionValue("target");
			if (cp.isEmpty()) {
				cp = target;
			} else if (!cp.contains(target)) {
				cp = cp + File.pathSeparator + target;
			}
			generateTestsTarget(strategy, target, javaOpts, cp);
		} else if (EvoSuite.hasLegacyTargets()){
			generateTestsLegacy(strategy, javaOpts, cp);
		} else {
			LoggingUtils.getEvoLogger().error("Please specify either target class ('-target' option), prefix ('-prefix' option), or classpath entry ('-class' option)");
			Help.execute(options);
		}
		return null;
	}


	private static void generateTestsLegacy(Properties.Strategy strategy,
	        List<String> args, String cp) {
		LoggingUtils.getEvoLogger().info("* Using .task files in "
		                                         + Properties.OUTPUT_DIR
		                                         + " [deprecated]");
		File directory = new File(Properties.OUTPUT_DIR);
		String[] extensions = { "task" };
		for (File file : FileUtils.listFiles(directory, extensions, false)) {
			generateTests(strategy, file.getName().replace(".task", ""), args, cp);
		}
	}
	
	public static Option[] getOptions(){
		return new Option[]{
				new Option("generateSuite", "use whole suite generation. This is the default behavior"),
				new Option("generateTests", "use individual test generation (old approach for reference purposes)"),
				new Option("generateRandom", "use random test generation"),
				new Option("generateNumRandom",true, "generate fixed number of random tests"),	
				new Option("regressionSuite", "generate a regression test suite")
		};
	}

	private static Strategy getChosenStrategy(List<String> javaOpts, CommandLine line) {
		Strategy strategy = null;
		if (line.hasOption("generateTests")) {
			strategy = Strategy.ONEBRANCH;
		} else if (line.hasOption("generateSuite")) {
			strategy = Strategy.EVOSUITE;
		} else if (line.hasOption("generateRandom")) {
			strategy = Strategy.RANDOM;
		} else if (line.hasOption("regressionSuite")) {
			strategy = Strategy.REGRESSION;
		} else if (line.hasOption("generateNumRandom")) {
			strategy = Strategy.RANDOM_FIXED;
			javaOpts.add("-Dnum_random_tests="
					+ line.getOptionValue("generateNumRandom"));
		}
		return strategy;
	}
	
	private static void generateTestsPrefix(Properties.Strategy strategy, String prefix,
	        List<String> args, String cp) {

		Pattern pattern = Pattern.compile(prefix.replace("\\.", File.separator)
		        + "[^\\$]*.class");
		Set<String> resources = new HashSet<String>();
		for (String classPathElement : cp.split(File.pathSeparator)) {
			resources.addAll(ResourceList.getResources(pattern, classPathElement));
			try {
				ClassPathHacker.addFile(classPathElement);
			} catch (IOException e) {
				// Ignore?
			}
		}
		try {
			if (Properties.INHERITANCE_FILE.isEmpty()) {
				String inheritanceFile = EvoSuite.generateInheritanceTree(cp);
				args.add("-Dinheritance_file=" + inheritanceFile);
			}
		} catch (IOException e) {
			LoggingUtils.getEvoLogger().info("* Error while traversing classpath: " + e);
			return;
		}
		LoggingUtils.getEvoLogger().info("* Found " + resources.size()
		                                         + " matching classes for prefix "
		                                         + prefix);
		for (String resource : resources) {
			try {
				if (EvoSuite.isInterface(resource)) {
					LoggingUtils.getEvoLogger().info("* Skipping interface: "
					                                         + resource.replace(".class",
					                                                            "").replace(File.separatorChar,
					                                                                        '.'));
					continue;
				}
			} catch (IOException e) {
				LoggingUtils.getEvoLogger().info("Could not load class: " + resource);
				continue;
			}
			LoggingUtils.getEvoLogger().info("* Current class: "
			                                         + resource.replace(".class", "").replace(File.separatorChar,
			                                                                                  '.'));
			generateTests(Strategy.EVOSUITE,
			              resource.replace(".class", "").replace(File.separatorChar, '.'),
			              args, cp);
		}

	}
	
	private static boolean findTargetClass(String target, String cp) {

		String oldCP = Properties.CP;

		Properties.CP = cp;
		if (Properties.CP != null && !Properties.CP.isEmpty()
		        && ResourceList.hasClass(target)) {
			return true;
		}

		Properties.CP = oldCP;
		if (Properties.CP != null && !Properties.CP.isEmpty()
		        && ResourceList.hasClass(target)) {
			return true;
		}

		Properties.CP = System.getProperty("java.class.path");
		if (Properties.CP != null && !Properties.CP.isEmpty()
		        && ResourceList.hasClass(target)) {
			return true;
		}

		Properties.CP = System.getenv("CLASSPATH");
		if (Properties.CP != null && !Properties.CP.isEmpty()
		        && ResourceList.hasClass(target)) {
			return true;
		}

		LoggingUtils.getEvoLogger().info("* Unknown class: " + target);

		return false;
	}
	
	private static Object generateTests(Properties.Strategy strategy, String target,
	        List<String> args, String cp) {
		String classPath = System.getProperty("java.class.path");
		if (!EvoSuite.evosuiteJar.equals("")) {
			classPath += File.pathSeparator + EvoSuite.evosuiteJar;
		}

		if (!findTargetClass(target, cp)) {
			return null;
		}

		if (!classPath.isEmpty())
			classPath += File.pathSeparator;
		classPath += Properties.CP;

		if (!InstrumentingClassLoader.checkIfCanInstrument(target)) {
			throw new IllegalArgumentException(
			        "Cannot consider "
			                + target
			                + " because it belongs to one of the packages EvoSuite cannot currently handle");
		}

		ExternalProcessHandler handler = new ExternalProcessHandler();
		int port = handler.openServer();
		if (port <= 0) {
			throw new RuntimeException("Not possible to start RMI service");
		}

		List<String> cmdLine = new ArrayList<String>();
		cmdLine.add(EvoSuite.JAVA_CMD);
		cmdLine.add("-cp");
		cmdLine.add(classPath);
		if (cp.isEmpty()) {
			cmdLine.add("-DCP=" + classPath);
		} else {
			cmdLine.add("-DCP=" + cp);
		}

		if (Properties.VIRTUAL_FS) {
			LoggingUtils.getEvoLogger().info("* Setting up virtual FS for testing");
			String stringToBePrependedToBootclasspath = locateEvoSuiteIOClasses();
			if (stringToBePrependedToBootclasspath == null)
				throw new IllegalStateException(
				        "Could not prepend needed classes for VFS functionality to bootclasspath of client!");
			cmdLine.add("-Xbootclasspath/p:" + stringToBePrependedToBootclasspath);
			cmdLine.add("-Dvirtual_fs=true");
		}

		cmdLine.add("-Dprocess_communication_port=" + port);
		cmdLine.add("-Dinline=true");
		cmdLine.add("-Djava.awt.headless=true");
		cmdLine.add("-Dlogback.configurationFile=logback-evosuite.xml");
		cmdLine.add("-Dlog.level=" + Properties.LOG_LEVEL);
		cmdLine.add("-Dlog.target=" + Properties.LOG_TARGET);
		cmdLine.add("-Djava.library.path=lib");
		// cmdLine.add("-Dminimize_values=true");

		if (Properties.DEBUG) {
			// enabling debugging mode to e.g. connect the eclipse remote debugger to the given port
			cmdLine.add("-Ddebug=true");
			cmdLine.add("-Xdebug");
			cmdLine.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address="
			        + Properties.PORT);
			LoggingUtils.getEvoLogger().info("* Waiting for remote debugger to connect on port "
			                                         + Properties.PORT + "..."); // TODO find the right
			// place for this
		}

		for (String arg : args) {
			if (!arg.startsWith("-DCP=")) {
				cmdLine.add(arg);
			}
		}

		switch (strategy) {
		case EVOSUITE:
			cmdLine.add("-Dstrategy=EvoSuite");
			break;
		case ONEBRANCH:
			cmdLine.add("-Dstrategy=OneBranch");
			break;
		case RANDOM:
			cmdLine.add("-Dstrategy=Random");
			break;
		case RANDOM_FIXED:
			cmdLine.add("-Dstrategy=Random_Fixed");
			break;
		case REGRESSION:
			cmdLine.add("-Dstrategy=Regression");
			break;
		default:
			throw new RuntimeException("Unsupported strategy: " + strategy);
		}
		cmdLine.add("-DTARGET_CLASS=" + target);
		if (Properties.PROJECT_PREFIX != null) {
			cmdLine.add("-DPROJECT_PREFIX=" + Properties.PROJECT_PREFIX);
		}

		cmdLine.add("org.evosuite.ClientProcess");

		/*
		 * TODO: here we start the client with several properties that are set through -D. These properties are not visible to the master process (ie
		 * this process), when we access the Properties file. At the moment, we only need few parameters, so we can hack them
		 */
		Properties.getInstance();// should force the load, just to be sure
		Properties.TARGET_CLASS = target;
		Properties.PROCESS_COMMUNICATION_PORT = port;

		/*
		 * The use of "assertions" in the client is pretty tricky, as those properties need to be transformed into JVM options before starting the
		 * client. Furthermore, the properties in the property file might be overwritten from the commands coming from shell
		 */

		String definedEAforClient = null;
		String definedEAforSUT = null;

		final String DISABLE_ASSERTIONS_EVO = "-da:org...";
		final String ENABLE_ASSERTIONS_EVO = "-ea:org...";
		final String DISABLE_ASSERTIONS_SUT = "-da:" + Properties.PROJECT_PREFIX + "...";
		final String ENABLE_ASSERTIONS_SUT = "-ea:" + Properties.PROJECT_PREFIX + "...";

		for (String s : cmdLine) {
			// first check client
			if (s.startsWith("-Denable_asserts_for_evosuite")) {
				if (s.endsWith("false")) {
					definedEAforClient = DISABLE_ASSERTIONS_EVO;
				} else if (s.endsWith("true")) {
					definedEAforClient = ENABLE_ASSERTIONS_EVO;
				}
			}
			// then check SUT
			if (s.startsWith("-Denable_asserts_for_sut")) {
				if (s.endsWith("false")) {
					definedEAforSUT = DISABLE_ASSERTIONS_SUT;
				} else if (s.endsWith("true")) {
					definedEAforSUT = ENABLE_ASSERTIONS_SUT;
				}
			}
		}

		/*
		 * the assertions might not be defined in the command line, but they might be in the property file, or just use default values. NOTE: if those
		 * are defined in the command line, then they overwrite whatever we had in the conf file
		 */

		if (definedEAforSUT == null) {
			if (Properties.ENABLE_ASSERTS_FOR_SUT) {
				definedEAforSUT = ENABLE_ASSERTIONS_SUT;
			} else {
				definedEAforSUT = DISABLE_ASSERTIONS_SUT;
			}
		}

		if (definedEAforClient == null) {
			if (Properties.ENABLE_ASSERTS_FOR_EVOSUITE) {
				definedEAforClient = ENABLE_ASSERTIONS_EVO;
			} else {
				definedEAforClient = DISABLE_ASSERTIONS_EVO;
			}
		}

		/*
		 * We add them in first position, after the java command To avoid confusion, we only add them if they are enabled. NOTE: this might have side
		 * effects "if" in the future we have something like a generic "-ea"
		 */
		if (definedEAforClient.equals(ENABLE_ASSERTIONS_EVO)) {
			cmdLine.add(1, definedEAforClient);
		}
		if (definedEAforSUT.equals(ENABLE_ASSERTIONS_SUT)) {
			cmdLine.add(1, definedEAforSUT);
		}

		LoggingUtils logUtils = new LoggingUtils();

		if (!Properties.CLIENT_ON_THREAD) {
			/*
			 * We want to completely mute the SUT. So, we block all outputs from client, and use a remote logging
			 */
			boolean logServerStarted = logUtils.startLogServer();
			if (!logServerStarted) {
				logger.error("Cannot start the log server");
				return null;
			}
			int logPort = logUtils.getLogServerPort(); //
			cmdLine.add(1, "-Dmaster_log_port=" + logPort);
			cmdLine.add(1, "-Devosuite.log.appender=CLIENT");
		}

		String[] newArgs = cmdLine.toArray(new String[cmdLine.size()]);

		for (String entry : Properties.CP.split(File.pathSeparator)) {
			try {
				ClassPathHacker.addFile(entry);
			} catch (IOException e) {
				LoggingUtils.getEvoLogger().info("* Error while adding classpath entry: "
				                                         + entry);
			}
		}

		handler.setBaseDir(EvoSuite.base_dir_path);
		Object result = null;
		if (handler.startProcess(newArgs)) {

			Set<ClientNodeRemote> clients = null;
			try {
				clients = MasterServices.getInstance().getMasterNode().getClientsOnceAllConnected(10000);
			} catch (InterruptedException e) {
			}
			if (clients == null) {
				logger.error("Not possible to access to clients");
			} else {
				/*
				 * The clients have started, and connected back to Master.
				 * So now we just need to tell them to start a search
				 */
				for (ClientNodeRemote client : clients) {
					try {
						client.startNewSearch();
					} catch (RemoteException e) {
						logger.error("Error in starting clients", e);
					}
				}

				int time = calculateHowLongSearchShouldWaitInSeconds();
				result = handler.waitForResult(time * 1000); // FIXXME: search timeout plus 100 seconds?
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
			}
			handler.killProcess();
		} else {
			LoggingUtils.getEvoLogger().info("* Could not connect to client process");
		}

		handler.closeServer();

		if (Properties.CLIENT_ON_THREAD) {
			/*
			 * FIXME: this is done only to avoid current problems with serialization
			 */
			result = ClientProcess.geneticAlgorithmStatus;
		}

		if (!Properties.CLIENT_ON_THREAD) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
			logUtils.closeLogServer();
		}

		logger.debug("Master process has finished to wait for client");
		if (Properties.NEW_STATISTICS)
			SearchStatistics.getInstance().writeStatistics();
		return result;
	}

	private static int calculateHowLongSearchShouldWaitInSeconds() {
		int time = Properties.EXTRA_TIMEOUT;
		if (Properties.STOPPING_CONDITION == StoppingCondition.MAXTIME) {
			time += Math.max(Properties.GLOBAL_TIMEOUT, Properties.SEARCH_BUDGET);
		} else {
			time += Properties.GLOBAL_TIMEOUT;
		}
		if (Properties.MINIMIZE) {
			time += Properties.MINIMIZATION_TIMEOUT;
		}
		if (Properties.ASSERTIONS) {
			time += Properties.ASSERTION_TIMEOUT;
		}
		return time;
	}
	
	/**
	 * Locates the resources that have to be prepended to bootclasspath in order
	 * to have all classes in the Client JVM that are needed for VFS
	 * functionality. Extracts and creates it if necessary.
	 * 
	 * @return a string denoting one or more with the system's pathSeparator
	 *         separated pathes to one or more jars containing evosuite-io,
	 *         commons-vfs2 and commons-logging; <code>null</code> if one or
	 *         more resources could not be found or accessed
	 */
	private static String locateEvoSuiteIOClasses() {
		String stringToBePrependedToBootclasspath = null;

		// try to find it inside the jar // FIXME this does still not seem to be the golden solution
		InputStream evosuiteIOjarInputStream = EvoSuite.class.getClassLoader().getResourceAsStream("evosuite-io.jar"); // created by maven with the
		// jar-minimal.xml assembly
		// file - contains the
		// evosuite-io classes
		// plus the needed
		// commons-vfs2 and
		// commons-logging
		// dependencies
		if (evosuiteIOjarInputStream != null) {
			// extract evosuite-io.jar into the system-default temporary directory
			String tmpFilePath = System.getProperty("java.io.tmpdir") + File.separator
			        + "evosuite-io.jar";
			File tmpFile = new File(tmpFilePath);
			tmpFile.deleteOnExit();

			try {
				IOUtils.copy(evosuiteIOjarInputStream, new FileOutputStream(tmpFile));
				stringToBePrependedToBootclasspath = tmpFilePath;
			} catch (IOException e) {
				throw new IllegalStateException(
				        "Error while extracing the evosuite-io JAR file!", e);
			}
		} else {
			// if not found try to locate all needed jars in classpath
			logger.info("\"evosuite-io.jar\" could not be found by EvoSuite.class.getClassLoader().getResource. "
			        + "EvoSuite is likely not executing out of an executable jar file at the moment. "
			        + "Now trying to locate all needed jars for VFS functionality in classpath instead...");
			URL[] urls = ((URLClassLoader) ClassLoader.getSystemClassLoader()).getURLs();
			URL evosuiteIOjar = null;
			URL commonsVFSjar = null;
			URL commonsLoggingjar = null;
			for (URL url : urls) {
				if (evosuiteIOjar != null && commonsVFSjar != null
				        && commonsLoggingjar != null)
					break;

				if (url.getPath().matches(".*evosuite-io.*\\.jar")) {
					evosuiteIOjar = url;
					continue;
				}

				if (url.getPath().matches(".*commons-vfs2.*\\.jar")) {
					commonsVFSjar = url;
					continue;
				}

				if (url.getPath().matches(".*commons-logging.*\\.jar")) {
					commonsLoggingjar = url;
					continue;
				}
			}

			if (evosuiteIOjar == null || !(new File(evosuiteIOjar.getPath())).canRead()) {
				throw new IllegalStateException("The evosuite-io JAR cannot be read!");
			} else if (commonsVFSjar == null
			        || !(new File(commonsVFSjar.getPath())).canRead()) {
				throw new IllegalStateException("The commons-vfs2 JAR cannot be read!");
			} else if (commonsLoggingjar == null
			        || !(new File(commonsLoggingjar.getPath())).canRead()) {
				throw new IllegalStateException("The commons-logging JAR cannot be read!");
			} else {
				logger.info("All needed jars for VFS functionality are in classpath and readable!");
				stringToBePrependedToBootclasspath = evosuiteIOjar.getPath()
				        + File.pathSeparator + commonsVFSjar.getPath()
				        + File.pathSeparator + commonsLoggingjar.getPath();
			}
		}

		return stringToBePrependedToBootclasspath;
	}
	
	private static void generateTestsTarget(Properties.Strategy strategy, String target,
	        List<String> args, String cp) {

		Pattern pattern = Pattern.compile("[^\\$]*.class");
		Collection<String> resources = ResourceList.getResources(pattern, target);
		LoggingUtils.getEvoLogger().info("* Found " + resources.size()
		                                         + " matching classes in target "
		                                         + target);
		try {
			ClassPathHacker.addFile(target);
		} catch (IOException e) {
			// Ignore?
		}
		try {
			if (Properties.INHERITANCE_FILE.isEmpty()) {
				String inheritanceFile = EvoSuite.generateInheritanceTree(cp);
				args.add("-Dinheritance_file=" + inheritanceFile);
			}
		} catch (IOException e) {
			LoggingUtils.getEvoLogger().info("* Error while traversing classpath: " + e);
			return;
		}

		for (String resource : resources) {
			try {
				if (EvoSuite.isInterface(resource)) {
					LoggingUtils.getEvoLogger().info("* Skipping interface: "
					                                         + resource.replace(".class",
					                                                            "").replace(File.separatorChar,
					                                                                        '.'));
					continue;
				}
			} catch (IOException e) {
				LoggingUtils.getEvoLogger().info("Could not load class: " + resource);
				continue;
			}
			LoggingUtils.getEvoLogger().info("* Current class: "
			                                         + resource.replace(".class", "").replace(File.separatorChar,
			                                                                                  '.'));
			generateTests(Strategy.EVOSUITE,
			              resource.replace(".class", "").replace(File.separatorChar, '.'),
			              args, cp);
		}
	}
}