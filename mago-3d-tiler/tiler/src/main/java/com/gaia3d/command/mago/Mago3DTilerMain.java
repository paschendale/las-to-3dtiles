package com.gaia3d.command.mago;

import com.gaia3d.command.Configurator;
import com.gaia3d.process.ProcessOptions;
import com.gaia3d.util.DecimalUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.Level;

import java.io.IOException;

/**
 * Main class for mago 3DTiler.
 */
@Slf4j
public class Mago3DTilerMain {
    public static void main(String[] args) {
        try {
            Options options = Configurator.createOptions();
            CommandLineParser parser = new DefaultParser();
            CommandLine command = parser.parse(Configurator.createOptions(), args);
            boolean isHelp = command.hasOption(ProcessOptions.HELP.getArgName());
            boolean isQuiet = command.hasOption(ProcessOptions.QUIET.getArgName());
            boolean hasLogPath = command.hasOption(ProcessOptions.LOG.getArgName());
            boolean isDebug = command.hasOption(ProcessOptions.DEBUG.getArgName());
            boolean isVersion = command.hasOption(ProcessOptions.VERSION.getArgName());

            // Logging configuration
            if (isQuiet) {
                Configurator.setLevel(Level.OFF);
            } else if (isDebug) {
                Configurator.initConsoleLogger("[%p][%d{HH:mm:ss}][%C{2}(%M:%L)]::%message%n");
                if (hasLogPath) {
                    Configurator.initFileLogger("[%p][%d{HH:mm:ss}][%C{2}(%M:%L)]::%message%n", command.getOptionValue(ProcessOptions.LOG.getArgName()));
                }
                Configurator.setLevel(Level.DEBUG);
            } else {
                Configurator.initConsoleLogger();
                if (hasLogPath) {
                    Configurator.initFileLogger(null, command.getOptionValue(ProcessOptions.LOG.getArgName()));
                }
                Configurator.setLevel(Level.INFO);
            }

            printStart();
            if (isVersion) {
                printVersion();
            }
            if (isHelp) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.setWidth(200);
                formatter.printHelp("mago 3DTiler help", options);
                return;
            }
            GlobalOptions.init(command);
            Mago3DTiler mago3DTiler = new Mago3DTiler();
            mago3DTiler.execute();
        } catch (ParseException e) {
            log.error("Failed to parse command line options, Please check the arguments.", e);
            throw new RuntimeException("Failed to parse command line options, Please check the arguments.", e);
        } catch (IOException e) {
            log.error("Failed to run process, Please check the arguments.", e);
            throw new RuntimeException("Failed to run process, Please check the arguments.", e);
        }
        printEnd();
        Configurator.destroyLogger();
    }

    /**
     * Prints the program information and the java version information.
     */
    private static void printStart() {
        GlobalOptions globalOptions = GlobalOptions.getInstance();
        String programInfo = globalOptions.getProgramInfo();
        log.info("\n" +
                "┳┳┓┏┓┏┓┏┓  ┏┓┳┓  ┏┳┓┳┓ ┏┓┳┓\n" +
                "┃┃┃┣┫┃┓┃┃   ┫┃┃   ┃ ┃┃ ┣ ┣┫\n" +
                "┛ ┗┛┗┗┛┗┛  ┗┛┻┛   ┻ ┻┗┛┗┛┛┗\n" +
                programInfo + "\n" +
                "----------------------------------------");
    }

    /**
     * Prints the program information and the java version information.
     */
    private static void printVersion() {
        GlobalOptions globalOptions = GlobalOptions.getInstance();
        String programInfo = globalOptions.getProgramInfo();
        String javaVersionInfo = globalOptions.getJavaVersionInfo();
        log.info(programInfo + "\n" + javaVersionInfo);
        log.info("----------------------------------------");
    }

    /**
     * Prints the total file count, total tile count, and the process time.
     */
    private static void printEnd() {
        GlobalOptions globalOptions = GlobalOptions.getInstance();
        long startTime = globalOptions.getStartTime();
        long endTime = System.currentTimeMillis();
        log.info("----------------------------------------");
        log.info("End Process Time : {}", DecimalUtils.millisecondToDisplayTime(endTime - startTime));
        log.info("Total tile contents count : {}", globalOptions.getTileCount());
        log.info("Total 'tileset.json' File Size : {}", DecimalUtils.byteCountToDisplaySize(globalOptions.getTilesetSize()));
        log.info("----------------------------------------");
    }
}
