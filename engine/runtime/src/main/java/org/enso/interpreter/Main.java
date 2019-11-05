package org.enso.interpreter;

import io.github.spencerpark.jupyter.kernel.BaseKernel;
import io.github.spencerpark.jupyter.kernel.LanguageInfo;
import io.github.spencerpark.jupyter.kernel.display.DisplayData;
import org.apache.commons.cli.*;
import org.enso.interpreter.instrument.ReplDebuggerInstrument;
import org.enso.interpreter.runtime.RuntimeOptions;
import org.enso.interpreter.util.ScalaConversions;
import org.enso.pkg.Package;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;
import io.github.spencerpark.jupyter.channels.JupyterConnection;
import io.github.spencerpark.jupyter.channels.JupyterSocket;
import io.github.spencerpark.jupyter.kernel.KernelConnectionProperties;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.Scanner;
import java.util.logging.Level;

/** The main CLI entry point class. */
public class Main {
  public static class EnsoKernel extends BaseKernel {

    Context context;;

    public EnsoKernel() {
      this.context = createContext("", getIO().in, getIO().out);
    }

    @Override
    public DisplayData eval(String expr) throws Exception {
      return new DisplayData(context.eval(Constants.LANGUAGE_ID, expr).toString());
    }

    @Override
    public LanguageInfo getLanguageInfo() {
      return new LanguageInfo.Builder("enso").version("1.0").build();
    }
  }

  public static void runJupyter(String connectionFileStr) throws Exception {
    Path connectionFile = Paths.get(connectionFileStr);

    if (!Files.isRegularFile(connectionFile))
      throw new IllegalArgumentException("Connection file '" + connectionFile + "' isn't a file.");

    String contents = new String(Files.readAllBytes(connectionFile));

    JupyterSocket.JUPYTER_LOGGER.setLevel(Level.WARNING);

    KernelConnectionProperties connProps = KernelConnectionProperties.parse(contents);
    JupyterConnection connection = new JupyterConnection(connProps);

    EnsoKernel kernel = new EnsoKernel();
    kernel.becomeHandlerForConnection(connection);

    connection.connect();
    connection.waitUntilClose();
  }

  private static final String RUN_OPTION = "run";
  private static final String HELP_OPTION = "help";
  private static final String NEW_OPTION = "new";
  private static final String JUPYTER_OPTION = "jupyter-kernel";

  /**
   * Builds the {@link Options} object representing the CLI syntax.
   *
   * @return an {@link Options} object representing the CLI syntax
   */
  private static Options buildOptions() {
    Option help = Option.builder("h").longOpt(HELP_OPTION).desc("Displays this message.").build();
    Option run =
        Option.builder()
            .hasArg(true)
            .numberOfArgs(1)
            .argName("file")
            .longOpt(RUN_OPTION)
            .desc("Runs a specified Enso file.")
            .build();

    Option newOpt =
        Option.builder()
            .hasArg(true)
            .numberOfArgs(1)
            .argName("path")
            .longOpt(NEW_OPTION)
            .desc("Creates a new Enso project.")
            .build();

    Option jupyterOption =
        Option.builder()
            .hasArg(true)
            .numberOfArgs(1)
            .argName("connection file")
            .longOpt(JUPYTER_OPTION)
            .desc("Runs Enso Jupyter Kernel.")
            .build();

    Options options = new Options();
    options.addOption(help).addOption(run).addOption(newOpt).addOption(jupyterOption);
    return options;
  }

  /**
   * Prints the help message to the standard output.
   *
   * @param options object representing the CLI syntax
   */
  private static void printHelp(Options options) {
    new HelpFormatter().printHelp(Constants.LANGUAGE_ID, options);
  }

  /** Terminates the process with a failure exit code. */
  private static void exitFail() {
    System.exit(1);
  }

  /** Terminates the process with a success exit code. */
  private static void exitSuccess() {
    System.exit(0);
  }

  /**
   * Handles the {@code --new} CLI option.
   *
   * @param path root path of the newly created project
   */
  private static void createNew(String path) {
    Package.getOrCreate(new File(path));
    exitSuccess();
  }

  private static Context createContext(String packagePath) {
    return createContext(packagePath, System.in, System.out);
  }

  private static Context createContext(String packagePath, InputStream in, OutputStream out) {
    Context context =
        Context.newBuilder(Constants.LANGUAGE_ID)
            .allowExperimentalOptions(true)
            .allowAllAccess(true)
            .option(RuntimeOptions.getPackagesPathOption(), packagePath)
            .out(out)
            .in(in)
            .build();
    context.getEngine().getInstruments().get("enso-repl").lookup(ReplDebuggerInstrument.class);
    return context;
  }

  /**
   * Handles the {@code --run} CLI option.
   *
   * @param path path of the project or file to execute
   * @throws IOException when source code cannot be parsed
   */
  private static void run(String path) throws IOException {
    File file = new File(path);

    if (!file.exists()) {
      System.out.println("File " + file + " does not exist.");
      exitFail();
    }

    boolean projectMode = file.isDirectory();
    String packagePath = projectMode ? file.getAbsolutePath() : "";
    File mainLocation = file;
    if (projectMode) {
      Optional<Package> pkg = ScalaConversions.asJava(Package.fromDirectory(file));
      Optional<File> main = pkg.map(Package::mainFile);
      if (!main.isPresent() || !main.get().exists()) {
        System.out.println("Main file does not exist.");
        exitFail();
      }
      mainLocation = main.get();
    }

    Context context = createContext(packagePath);
    Source source = Source.newBuilder(Constants.LANGUAGE_ID, mainLocation).build();
    context.eval(source);
    exitSuccess();
  }

  /**
   * Main entry point for the CLI program.
   *
   * @param args the command line arguments
   */
  public static void main(String[] args) throws Exception {
    Options options = buildOptions();
    CommandLineParser parser = new DefaultParser();
    CommandLine line;
    try {
      line = parser.parse(options, args);
    } catch (ParseException e) {
      printHelp(options);
      exitFail();
      return;
    }
    if (line.hasOption(HELP_OPTION)) {
      printHelp(options);
      exitSuccess();
      return;
    }
    if (line.hasOption(NEW_OPTION)) {
      createNew(line.getOptionValue(NEW_OPTION));
    }
    if (line.hasOption(RUN_OPTION)) {
      run(line.getOptionValue(RUN_OPTION));
    }
    if (line.hasOption(JUPYTER_OPTION)) {
      runJupyter(line.getOptionValue(JUPYTER_OPTION));
    }

    printHelp(options);
    exitFail();
  }
}
