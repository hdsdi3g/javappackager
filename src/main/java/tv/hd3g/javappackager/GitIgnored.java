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
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;

public class GitIgnored implements Predicate<File> {
	private static Logger log = LogManager.getLogger();

	private final Set<File> ignored;

	public GitIgnored(final File rootDir) throws IOException {
		final Git git = Git.open(rootDir);
		final Repository db = git.getRepository();

		/**
		 * See from https://github.com/eclipse/jgit/blob/master/org.eclipse.jgit.test/tst/org/eclipse/jgit/ignore/CGitIgnoreTest.java
		 */
		ignored = new HashSet<>();
		try (TreeWalk walk = new TreeWalk(db)) {
			final FileTreeIterator iter = new FileTreeIterator(db);
			iter.setWalkIgnoredDirectories(true);
			walk.addTree(iter);
			walk.setRecursive(true);
			while (walk.next()) {
				if (walk.getTree(WorkingTreeIterator.class).isEntryIgnored()) {
					final File f = new File(rootDir.getPath() + File.separator + walk.getPathString().replace('/', File.separatorChar));
					log.trace("Found ignored file \"{}\"", f.getPath());
					ignored.add(f);
				}
			}
		}
	}

	@Override
	public boolean test(final File f) {
		return ignored.contains(f);
	}

}
