package com.joshdreagan.taiga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Command(name = "diff", mixinStandardHelpOptions = true,
  description = "Diff two paths: either two files or two directories")
public class DiffCommand implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(DiffCommand.class);

  private static final String EXTENSION = ".json";

  @Parameters(index = "0", paramLabel = "<old>", description = "Old path (file or directory)")
  Path oldPath;

  @Parameters(index = "1", paramLabel = "<new>", description = "New path (file or directory)")
  Path newPath;

  TaigaHelper taigaHelper = new TaigaHelper();

  @Override
  public void run() {

    if (!Files.exists(oldPath)) {
      LOG.error("Old path does not exist: {}", oldPath);
      return;
    }
    if (!Files.exists(newPath)) {
      LOG.error("New path does not exist: {}", newPath);
      return;
    }

    if (Files.isRegularFile(oldPath) && Files.isRegularFile(newPath)) {
      boolean modified = diffFiles(oldPath, newPath);
      if (modified) {
        System.out.println(String.format("Modified (1):\n%s != %s\n", oldPath, newPath));
      }
      return;
    } else if (Files.isDirectory(oldPath) && Files.isDirectory(newPath)) {
      DirectoryDiffResult diffResult = diffDirectories(oldPath, newPath);
      System.out.println(String.format("Added (%s):\n%s\n", diffResult.added().size(), joinLines(diffResult.added())));
      System.out.println(String.format("Removed (%s):\n%s\n", diffResult.removed().size(), joinLines(diffResult.removed())));
      System.out.println(String.format("Modified (%s):\n%s\n", diffResult.modified().size(), joinLines(diffResult.modified())));
      System.out.println(String.format("Skipped (%s):\n%s\n", diffResult.skipped().size(), joinLines(diffResult.skipped())));
      return;
    } else {
      LOG.error("Paths must both be files or both be directories: old='{}', new='{}' ", oldPath, newPath);
      return;
    }
  }

  private boolean diffFiles(Path oldFile, Path newFile) {
    boolean modified = false;

    try {
      var oldCard = taigaHelper.parseUserStoryFile(oldFile);
      var newCard = taigaHelper.parseUserStoryFile(newFile);
      modified = (oldCard == null && newCard != null)
        || (oldCard != null && !oldCard.equals(newCard));
    } catch (Exception e) {
      LOG.error("Failed to diff files '{}' and '{}' due to exception", oldFile, newFile, e);
      // If we can't parse, assume modified to be safe
      modified = true;
    }
    return modified;
  }

  private DirectoryDiffResult diffDirectories(Path oldDirectory, Path newDirectory) {
    Set<Path> added = new HashSet<>();
    Set<Path> removed = new HashSet<>();
    Set<Path> modified = new HashSet<>();
    Set<Path> skipped = new HashSet<>();

    try {
      // Map filenames (not full path) to Path for both directories, non-recursive, regular files only
      var oldFiles = Files.list(oldDirectory)
        .filter(Files::isRegularFile)
        .collect(Collectors.toMap(p -> p.getFileName().toString(), p -> p, (a, b) -> a));
      var newFiles = Files.list(newDirectory)
        .filter(Files::isRegularFile)
        .collect(Collectors.toMap(p -> p.getFileName().toString(), p -> p, (a, b) -> a));

      Set<String> oldNames = new TreeSet<>(oldFiles.keySet());
      Set<String> newNames = new TreeSet<>(newFiles.keySet());

      // Added: in new but not in old
      for (String name : newNames) {
        if (!name.endsWith(EXTENSION)) {
          skipped.add(newFiles.get(name));
          continue;
        }
        if (!oldNames.contains(name)) {
          added.add(newFiles.get(name));
        }
      }
      // Removed: in old but not in new
      for (String name : oldNames) {
        if (!name.endsWith(EXTENSION)) {
          skipped.add(newFiles.get(name));
          continue;
        }
        if (!newNames.contains(name)) {
          removed.add(oldFiles.get(name));
        }
      }
      // Modified: present in both
      for (String name : newNames) {
        if (!name.endsWith(EXTENSION)) {
          skipped.add(newFiles.get(name));
          continue;
        }
        if (oldNames.contains(name)) {
          Path oldFile = oldFiles.get(name);
          Path newFile = newFiles.get(name);
          if (diffFiles(oldFile, newFile)) {
            // Include the new file path in the modified list
            modified.add(newFile);
          }
        }
      }
    } catch (Exception e) {
      LOG.error("Failed to diff directories '{}' and '{}'", oldDirectory, newDirectory, e);
    }
    return new DirectoryDiffResult(added, removed, modified, skipped);
  }

  private record DirectoryDiffResult(Set<Path> added, Set<Path> removed, Set<Path> modified, Set<Path> skipped) {
  }

  private String joinLines(Collection<Path> paths) {
    return paths.stream()
      .map(Path::toString)
      .collect(Collectors.joining("\n"));
  }
}
