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
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuilder;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.inheritance.DefaultInheritanceAssembler;
import org.apache.maven.model.interpolation.StringSearchModelInterpolator;
import org.apache.maven.model.io.DefaultModelReader;
import org.apache.maven.model.management.DefaultDependencyManagementInjector;
import org.apache.maven.model.management.DefaultPluginManagementInjector;
import org.apache.maven.model.normalization.DefaultModelNormalizer;
import org.apache.maven.model.path.DefaultModelPathTranslator;
import org.apache.maven.model.path.DefaultModelUrlNormalizer;
import org.apache.maven.model.path.DefaultPathTranslator;
import org.apache.maven.model.path.DefaultUrlNormalizer;
import org.apache.maven.model.profile.DefaultProfileSelector;
import org.apache.maven.model.superpom.DefaultSuperPomProvider;
import org.apache.maven.model.validation.DefaultModelValidator;
import org.codehaus.plexus.classworlds.ClassWorld;

import tv.hd3g.processlauncher.cmdline.ExecutableFinder;

public class AppToPackage {
	private static Logger log = LogManager.getLogger();
	private static final String destinationDirName = "javappackager";

	private final File mvnDir;
	private final GitInfo gitInfo;
	private final File pomFile;
	private final File targetDir;

	private final Model pom;
	private final String appVersion;
	private final String gitVersion;
	private final String appName;
	private final String appUrl;
	private final Properties appProperties;

	private final MavenCli mavenCli;

	public AppToPackage(final File mvnDir, final GitInfo gitInfo) throws IOException, ModelBuildingException {
		this.mvnDir = Objects.requireNonNull(mvnDir, "\"mvnDir\" can't to be null");
		this.gitInfo = Objects.requireNonNull(gitInfo, "\"gitInfo\" can't to be null");

		if (mvnDir.exists() == false) {
			throw new FileNotFoundException("Can't found dir " + mvnDir);
		} else if (mvnDir.isDirectory() == false) {
			throw new FileNotFoundException(mvnDir + " is not a directory");
		} else if (mvnDir.canRead() == false) {
			throw new IOException("Can't read " + mvnDir);
		}
		targetDir = new File(mvnDir.getAbsolutePath() + File.separator + "target");

		pomFile = new File(mvnDir.getPath() + File.separator + "pom.xml");
		if (pomFile.exists() == false) {
			throw new FileNotFoundException("Can't found pom file " + pomFile);
		} else if (pomFile.isFile() == false) {
			throw new FileNotFoundException(pomFile + " is not a file");
		} else if (pomFile.canRead() == false) {
			throw new IOException("Can't read " + pomFile);
		}

		FileUtils.deleteQuietly(targetDir);

		/**
		 * Inject maven log configuration
		 */
		final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
		final Configuration config = ctx.getConfiguration();

		final PatternLayout layout = PatternLayout.newBuilder().withConfiguration(config).withPattern("%m%n").build();
		final File mavenLog = new File(targetDir.getPath() + File.separator + "javappackager-maven.log");
		FileUtils.forceMkdirParent(mavenLog);

		final Appender appender = FileAppender.newBuilder().withFileName(mavenLog.getPath()).withName("Maven log file").withBufferSize(128).withLayout(layout).setConfiguration(config).build();
		appender.start();
		config.addAppender(appender);

		final AppenderRef ref = AppenderRef.createAppenderRef("Maven log file", null, null);
		final AppenderRef[] refs = new AppenderRef[] { ref };

		Stream.of("org.apache.maven", "Sisu", "org.codehaus.mojo").forEach(loggerName -> {
			final LoggerConfig loggerConfig = LoggerConfig.createLogger(false, Level.INFO, loggerName, "true", refs, null, config, null);
			loggerConfig.addAppender(appender, Level.INFO, null);
			config.addLogger(loggerName, loggerConfig);
		});

		ctx.updateLoggers();

		/**
		 * Parse pom file
		 */
		final DefaultModelBuilder builder = new DefaultModelBuilder();
		final DefaultProfileSelector profileSelector = new DefaultProfileSelector();
		builder.setProfileSelector(profileSelector);

		final DefaultModelProcessor modelProcessor = new DefaultModelProcessor();
		final DefaultModelReader reader = new DefaultModelReader();
		modelProcessor.setModelReader(reader);
		builder.setModelProcessor(modelProcessor);

		final DefaultModelValidator modelValidator = new DefaultModelValidator();
		builder.setModelValidator(modelValidator);

		final DefaultSuperPomProvider superPomProvider = new DefaultSuperPomProvider();
		superPomProvider.setModelProcessor(modelProcessor);
		builder.setSuperPomProvider(superPomProvider);

		final DefaultModelNormalizer modelNormalizer = new DefaultModelNormalizer();
		builder.setModelNormalizer(modelNormalizer);

		final DefaultInheritanceAssembler inheritanceAssembler = new DefaultInheritanceAssembler();
		builder.setInheritanceAssembler(inheritanceAssembler);

		final StringSearchModelInterpolator modelInterpolator = new StringSearchModelInterpolator();
		final DefaultPathTranslator pathTranslator = new DefaultPathTranslator();
		modelInterpolator.setPathTranslator(pathTranslator);
		builder.setModelInterpolator(modelInterpolator);

		final DefaultModelUrlNormalizer modelUrlNormalizer = new DefaultModelUrlNormalizer();
		final DefaultUrlNormalizer urlNormalizer = new DefaultUrlNormalizer();
		modelUrlNormalizer.setUrlNormalizer(urlNormalizer);
		builder.setModelUrlNormalizer(modelUrlNormalizer);

		final DefaultModelPathTranslator modelPathTranslator = new DefaultModelPathTranslator();
		modelPathTranslator.setPathTranslator(pathTranslator);
		builder.setModelPathTranslator(modelPathTranslator);

		final DefaultPluginManagementInjector pluginManagementInjector = new DefaultPluginManagementInjector();
		builder.setPluginManagementInjector(pluginManagementInjector);

		final DefaultDependencyManagementInjector depMgmtInjector = new DefaultDependencyManagementInjector();
		builder.setDependencyManagementInjector(depMgmtInjector);

		final ModelBuildingRequest req = new DefaultModelBuildingRequest();
		req.setProcessPlugins(false);
		req.setPomFile(pomFile);
		req.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);

		pom = builder.build(req).getEffectiveModel();

		if ("jar".equalsIgnoreCase(pom.getPackaging()) == false) {
			throw new IOException("This pom is package as \"" + pom.getPackaging() + "\", only \"jar\" is managed");
		}
		appVersion = pom.getVersion();
		gitVersion = gitInfo.getVersion();
		appName = pom.getName();
		appUrl = pom.getUrl();

		log.info("Operate on " + appName + "-" + appVersion + " / git " + gitVersion);

		appProperties = pom.getProperties();

		if (appProperties.getProperty("javappackager.mainclass") == null) {
			throw new RuntimeException("You must provide in pom file at least properties > javappackager.mainclass");
		}

		final ClassWorld world = new ClassWorld("default", Thread.currentThread().getContextClassLoader());
		mavenCli = new MavenCli(world);
	}

	public List<File> getExternalDeps(final ExecutableFinder execFinder) {
		return Arrays.stream(((String) appProperties.getOrDefault("javappackager.externaldeps", "")).trim().split(" ")).filter(dep -> {
			return dep.trim().equals("") == false;
		}).map(String::trim).map(dep -> {
			try {
				return execFinder.get(dep);
			} catch (final FileNotFoundException e) {
				throw new RuntimeException("Can't found dependency " + dep, e);
			}
		}).collect(Collectors.toUnmodifiableList());
	}

	private static File assertExists(final File file) {
		if (file.exists() == false) {
			throw new RuntimeException("Expected file not exists", new FileNotFoundException(file.getPath()));
		}
		return file;
	}

	private static Optional<File> optionalFileExists(final File file) {
		if (file.exists()) {
			return Optional.of(file);
		}
		return Optional.empty();
	}

	public String getAppName() {
		return appName;
	}

	public Optional<File> getMainResourceDir() {
		return optionalFileExists(Path.of(mvnDir.getPath(), "src", "main", "resources").toFile());
	}

	public Optional<File> getMainConfigDir() {
		return optionalFileExists(Path.of(mvnDir.getPath(), "src", "main", "config").toFile());
	}

	public File getMvnDir() {
		return mvnDir;
	}

	private void doMaven(final String verb) throws IOException {
		System.setProperty("maven.multiModuleProjectDirectory", mvnDir.getPath());

		final int result = mavenCli.doMain(new String[] { "-Dmaven.test.skip=true", verb }, mvnDir.getPath(), System.out, System.err);
		if (result != 0) {
			System.err.println();
			throw new IOException("Failed maven execution \"-Dmaven.test.skip=true " + verb);
		}
	}

	public List<File> mavenCopyDependencies() throws IOException {
		doMaven("dependency:copy-dependencies");

		return Files.walk(Path.of(targetDir.getPath(), "dependency")).map(Path::toFile).filter(founded -> {
			assertExists(founded);
			return founded.isFile() && founded.isHidden() == false && founded.getName().startsWith(".") == false && founded.getName().toLowerCase().endsWith(".jar");
		}).collect(Collectors.toUnmodifiableList());
	}

	public File mavenPackage() throws IOException {
		doMaven("package");
		return assertExists(Path.of(targetDir.getPath(), appName + "-" + appVersion + ".jar").toFile());
	}

	public File mavenLicenses() throws IOException {
		doMaven("license:add-third-party");
		return assertExists(Path.of(targetDir.getPath(), "generated-sources", "license", "THIRD-PARTY.txt").toFile());
	}

	public Optional<File> getWindowsIcon() throws IOException {
		final String path = appProperties.getProperty("javappackager.windowsicon");
		if (path == null) {
			return Optional.empty();
		}
		final File icon = new File(getMvnDir().getPath() + File.separator + FilenameUtils.separatorsToSystem(path));
		if (icon.exists() == false) {
			throw new IOException("Can't found icon file " + icon);
		}
		return Optional.of(icon);
	}

	public String getMainClass() {
		return appProperties.getProperty("javappackager.mainclass");
	}

	public String getJVMVersion() {
		return appProperties.getProperty("maven.compiler.target", System.getProperty("java.specification.version", System.getProperty("java.version")));
	}

	public Destination getDestination() {
		try {
			return new Destination();
		} catch (final IOException e) {
			throw new RuntimeException("Can't prepare destination dir " + destinationDirName, e);
		}
	}

	class Destination {
		private final File destDir;

		private Destination() throws IOException {
			destDir = Path.of(targetDir.getPath(), destinationDirName).toFile();
			FileUtils.forceMkdir(destDir);
		}

		private File getDestFile(final String[] subPath, final String fileName) throws IOException {
			final String fullSubPath;
			if (subPath == null) {
				fullSubPath = "";
			} else {
				fullSubPath = Arrays.stream(subPath).filter(p -> p != null).collect(Collectors.joining(File.separator)) + File.separator;
			}

			final File destFile = new File(destDir.getPath() + File.separator + fullSubPath + fileName);
			FileUtils.forceMkdirParent(destFile);
			return destFile;
		}

		public void moveToDest(final File item, final String... relativeSubPath) {
			try {
				final File destFile = getDestFile(relativeSubPath, item.getName());
				log.debug("Move file \"{}\" to destination \"{}\"", item, destFile);
				FileUtils.moveFile(item, destFile);
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}
		}

		public void copyToDest(final File item, final String... relativeSubPath) {
			try {
				if (item.isDirectory()) {
					final File copyDestDir = getDestFile(relativeSubPath, "");
					log.info("Copy dir \"{}\" to destination \"{}\"", item, copyDestDir);

					final String itemPath = item.getAbsolutePath();
					final String copyDestDirPath = copyDestDir.getAbsolutePath();
					Files.walk(item.toPath()).filter(Files::isRegularFile).map(Path::toFile).filter(gitInfo.negate()).forEach(o -> {
						final String relativePath = FilenameUtils.normalizeNoEndSeparator(o.getAbsolutePath().substring(itemPath.length()));
						final File destFile = new File(copyDestDirPath + File.separator + relativePath);

						try {
							FileUtils.forceMkdirParent(destFile);
							FileUtils.copyFileToDirectory(o, destFile.getParentFile());
						} catch (final IOException e) {
							throw new RuntimeException("Can't copy to " + destFile, e);
						}
					});
				} else {
					if (gitInfo.test(item)) {
						log.debug("Ignore copy file \"{}\"", item);
						return;
					}

					final File destFile = getDestFile(relativeSubPath, item.getName());
					log.debug("Copy file \"{}\" to destination \"{}\"", item, destFile);
					FileUtils.copyFile(item, destFile);
				}
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}
		}

		public File getTargetExecFile() {
			return new File(destDir.getPath() + File.separator + appName + ".exe");
		}

		public File getTargetJVMDir() {
			return new File(destDir.getPath() + File.separator + "jvm");
		}

		private static final String licenseDir = "licenses";

		public File getTargetLicensesDir() {
			return new File(destDir.getPath() + File.separator + licenseDir);
		}

		public void moveToLicensesDir(final File mavenLicenses) {
			moveToDest(mavenLicenses, licenseDir);
		}

		public void makeAppLicenseFile() throws IOException {
			final Path licenseFile = Path.of(destDir.getPath(), licenseDir, appName.toUpperCase() + ".TXT");
			final PrintStream out = new PrintStream(licenseFile.toFile());

			out.print(appName);
			Optional.ofNullable(pom.getUrl()).ifPresent(url -> {
				out.print(" - ");
				out.println(url);
				out.println();
			});

			Optional.ofNullable(pom.getOrganization()).ifPresent(o -> {
				Optional.ofNullable(o.getName()).ifPresent(n -> {
					out.print("Copyright (C) ");
					out.print(n);
				});
				Optional.ofNullable(o.getUrl()).ifPresent(u -> {
					out.print(" - ");
					out.print(u);
				});
				out.println();
			});

			pom.getLicenses().stream().forEach(l -> {
				out.println();
				Optional.ofNullable(l.getName()).ifPresent(n -> {
					out.println(n);
				});
				Optional.ofNullable(l.getUrl()).ifPresent(u -> {
					out.println(u);
				});
				Optional.ofNullable(l.getComments()).ifPresent(c -> {
					out.println(c);
				});
				out.println();
			});

			Optional.ofNullable(pom.getScm()).ifPresent(scm -> {
				Optional.ofNullable(scm.getUrl()).ifPresent(u -> {
					out.print("Sources available on ");
					out.println(u);
				});
			});

			Optional.ofNullable(pom.getIssueManagement()).ifPresent(im -> {
				out.print("Please report bugs on ");
				Optional.ofNullable(im.getSystem()).ifPresent(s -> {
					out.print(s);
					out.print(": ");
				});
				Optional.ofNullable(im.getUrl()).ifPresent(u -> {
					out.print(u);
				});
				out.println();
			});

			out.close();
		}

		public void makeVersionFile() throws IOException {
			final Path versionFile = Path.of(destDir.getPath(), "VERSION.TXT");
			final PrintStream out = new PrintStream(versionFile.toFile());
			out.println(appVersion);
			out.println(gitVersion);
			out.close();
		}

		public File getDir() {
			return destDir;
		}
	}

	public String getAppVersion() {
		return appVersion;
	}

	public String getGitVersion() {
		return gitVersion;
	}

	public String getAppUrl() {
		return appUrl;
	}
}
