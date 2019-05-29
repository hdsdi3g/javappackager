/*
 * This file is part of javappackager.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * Copyright (C) hdsdi3g for hd3g.tv 2019
 *
*/
package tv.hd3g.javappackager;

import java.io.File;
import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tv.hd3g.javappackager.AppToPackage.Destination;
import tv.hd3g.processlauncher.cmdline.ExecutableFinder;

public class MainApp {

	private static Logger log = LogManager.getLogger();

	public static void main(final String[] args) throws Exception {
		final DefaultParser parser = new DefaultParser();
		final Options options = new Options();
		options.addRequiredOption("d", "root-dir", true, "App root directory (with pom file)");
		options.addOption("j", "copy-jvm", false, "Copy this current JVM (" + System.getenv("JAVA_HOME") + ") to new package");

		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);
		} catch (final ParseException e) {
			final HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("javappackager -d <root directory> [-j]", "Package simple java/mvn app for Windows statup", options, "");
			System.err.println(e.getMessage());
			System.exit(1);
		}

		// TODO add version somewere + git last commit

		final File rootDir = new File(cmd.getOptionValue("d"));
		final boolean copyJVM = cmd.hasOption("j");

		final GitIgnored gitState = new GitIgnored(rootDir);
		final AppToPackage app = new AppToPackage(rootDir, gitState);

		final ExecutableFinder execFinder = new ExecutableFinder();
		app.getMainConfigDir().ifPresent(execFinder::addPath);
		app.getMainResourceDir().ifPresent(execFinder::addPath);

		/*final List<File> localDirs = Stream.of("bin", "config").map(relativeDir -> {
			return Path.of(app.getMvnDir().getPath(), relativeDir);
		}).map(Path::toFile).filter(File::exists).filter(File::isDirectory).collect(Collectors.toUnmodifiableList());
		localDirs.forEach(execFinder::addPath);*/

		final Destination dest = app.getDestination();
		log.info("Get and move main jar to lib dir");
		dest.moveToDest(app.mavenPackage(), "lib");

		log.info("Get and move dependencies to lib dir");
		app.mavenCopyDependencies().forEach(dep -> {
			log.trace("Move {} to lib dir", dep.getPath());
			dest.moveToDest(dep, "lib");
		});

		log.info("Get and move dependencies licenses to licenses dir");
		dest.moveToLicensesDir(app.mavenLicenses());

		app.getExternalDeps(execFinder).forEach(dep -> {
			log.info("Copy {} to bin dir", dep.getPath());
			dest.copyToDest(dep, "bin");
		});

		app.getMainConfigDir().ifPresent(dir -> {
			log.info("Copy {} to config dir", dir.getPath());
			dest.copyToDest(dir, "config");
		});

		final WinRun4J wrj = new WinRun4J(execFinder);

		wrj.setClassPath(Arrays.asList("lib/*.jar", "lib", "bin", "config"));
		wrj.setMainClass(app.getMainClass());
		wrj.setMinVMVersion(app.getJVMVersion());
		// wrj.setSingleInstance(singleInstance)
		// wrj.getAppParameters();

		if (copyJVM) {
			final File javaHome = new File(System.getenv("JAVA_HOME"));
			if (javaHome.exists() && javaHome.isDirectory()) {
				final File jvmDir = dest.getTargetJVMDir();
				log.info("Copy JRE/JDK {} to current {} dir", jvmDir.getPath(), jvmDir.getName());

				FileUtils.copyDirectory(javaHome, jvmDir);
				FileUtils.deleteQuietly(new File(jvmDir.getPath() + File.separator + "include"));
				FileUtils.deleteQuietly(new File(jvmDir.getPath() + File.separator + "jmods"));
				FileUtils.deleteQuietly(new File(jvmDir.getPath() + File.separator + "lib" + File.separator + "src.zip"));
				FileUtils.listFiles(new File(jvmDir.getPath() + File.separator + "bin"), ExecutableFinder.WINDOWS_EXEC_EXTENSIONS.toArray(new String[0]), false).forEach(file -> {
					file.delete();
				});

				wrj.setJVMDir(jvmDir.getName());
			}
		}

		log.info("Starts rcedit to prepare final exe file");
		wrj.makeExecFile(dest.getTargetExecFile(), app.getWindowsIcon());

		wrj.copyLicenseTo(dest.getTargetLicensesDir());
		dest.makeAppLicenseFile();

		log.info("You can found package here: " + dest.getDir().getPath());
	}

}
