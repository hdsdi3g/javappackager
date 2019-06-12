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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.io.FileUtils;

import tv.hd3g.processlauncher.Exec;
import tv.hd3g.processlauncher.cmdline.ExecutableFinder;
import tv.hd3g.processlauncher.cmdline.Parameters;

public class WinRun4J {

	private static final String winRun4jExecName = "WinRun4J64";
	private static final String rceditExecName = "RCEDIT64";
	private static final String rceditCdmlineIni = "/N <%exe_file%> <%ini_file%>";
	private static final String rceditCdmlineIcon = "/I <%exe_file%> <%ico_file%>";
	private static final String winRun4jLicenseFile = "WinRun4J-About.txt";

	private final ExecutableFinder executableFinder;
	private final File winRun4jExec;
	private final LinkedHashMap<String, String> iniContent;
	private final List<String> classPath;
	private final Parameters appParameters;

	private String mainClass;
	private BigDecimal minVMVersion;
	private boolean singleInstance;
	private String jvmDir;

	public WinRun4J(final ExecutableFinder executableFinder, final String appName, final String appVersion, final String gitVersion) throws IOException {
		this.executableFinder = Objects.requireNonNull(executableFinder, "\"executableFinder\" can't to be null");

		winRun4jExec = getExecFile(executableFinder, winRun4jExecName, getClass());
		getExecFile(executableFinder, rceditExecName, getClass());
		iniContent = new LinkedHashMap<>();
		classPath = new ArrayList<>();
		appParameters = new Parameters();

		iniContent.put("ini.override", "true");
		iniContent.put("log", "%LOCALAPPDATA%\\" + appName + "\\startup.log");
		iniContent.put("log.level", "warning");
		iniContent.put("log.roll.size", "2");
		iniContent.put("vmarg.1", "-Djavappackager.appname=\"" + appName + "\"");
		iniContent.put("vmarg.2", "-Djavappackager.appversion=\"" + appVersion + "\"");
		iniContent.put("vmarg.3", "-Djavappackager.gitversion=\"" + gitVersion + "\"");
		// iniContent.put("vm.sysfirst", "true");
	}

	private static File getExecFile(final ExecutableFinder executableFinder, final String baseName, final Class<?> ressourceFromClass) throws IOException {
		try {
			return executableFinder.get(baseName);
		} catch (final FileNotFoundException e) {
			final File item = File.createTempFile(baseName, ".exe");

			FileUtils.copyInputStreamToFile(ressourceFromClass.getResourceAsStream(baseName + ".exe"), item);
			executableFinder.registerExecutable(baseName, item);
			return item;
		}
	}

	public WinRun4J setMainClass(final String mainClass) {
		this.mainClass = mainClass;
		return this;
	}

	public WinRun4J setClassPath(final Collection<String> classPath) {
		this.classPath.clear();
		this.classPath.addAll(classPath);
		return this;
	}

	public WinRun4J setMinVMVersion(final String minVMVersion) {
		this.minVMVersion = new BigDecimal(minVMVersion);
		return this;
	}

	public Parameters getAppParameters() {
		return appParameters;
	}

	public WinRun4J setSingleInstance(final boolean singleInstance) {
		this.singleInstance = singleInstance;
		return this;
	}

	public void setJVMDir(final String name) {
		jvmDir = name;
	}

	public void makeExecFile(final File targetExec, final Optional<File> windowsIcon) throws IOException {
		FileUtils.copyFile(winRun4jExec, targetExec);

		final Exec exec = new Exec(rceditExecName, executableFinder);
		exec.getParameters().addBulkParameters(rceditCdmlineIni);
		exec.getVarsToInject().put("exe_file", targetExec.getPath());

		iniContent.putIfAbsent("main.class", mainClass);
		if (minVMVersion != null) {
			iniContent.putIfAbsent("vm.version.min", minVMVersion.toString());
		}

		for (int pos = 0; pos < classPath.size(); pos++) {
			iniContent.put("classpath." + (pos + 1), classPath.get(pos));
		}

		final List<String> listParam = appParameters.getParameters();
		for (int pos = 0; pos < listParam.size(); pos++) {
			iniContent.put("arg." + (pos + 1), listParam.get(pos));
		}

		if (singleInstance) {
			iniContent.put("single.instance", "process");
		} else {
			iniContent.remove("single.instance");
		}

		if (jvmDir != null) {
			iniContent.putIfAbsent("vm.location", jvmDir + "/bin/server/jvm.dll");
		}

		final File iniFile = File.createTempFile(targetExec.getName(), ".ini");

		try (FileOutputStream fos = new FileOutputStream(iniFile)) {
			final PrintStream ps = new PrintStream(fos);
			iniContent.forEach((k, v) -> {
				ps.print(k);
				ps.print("=");
				ps.print(v);
				ps.println();
			});
			ps.close();
		}
		exec.getVarsToInject().put("ini_file", iniFile.getPath());

		System.out.println(exec.runWaitGetText().getStdouterr(false, System.lineSeparator()));

		if (windowsIcon.isPresent()) {
			exec.getParameters().clear();
			exec.getParameters().addBulkParameters(rceditCdmlineIcon);
			exec.getVarsToInject().put("ico_file", windowsIcon.get().getPath());
			System.out.println(exec.runWaitGetText().getStdouterr(false, System.lineSeparator()));
		}
	}

	public void copyLicenseTo(final File destDir) throws IOException {
		final File dest = new File(destDir.getPath() + File.separator + winRun4jLicenseFile);
		FileUtils.copyInputStreamToFile(getClass().getResourceAsStream(winRun4jLicenseFile), dest);
	}

}
