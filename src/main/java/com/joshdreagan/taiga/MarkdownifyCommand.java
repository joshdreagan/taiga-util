package com.joshdreagan.taiga;

import com.joshdreagan.taiga.model.Card;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Command(name = "markdownify", mixinStandardHelpOptions = true,
    description = "Converts Taiga user story JSON file(s) into markdown document(s)")
public class MarkdownifyCommand implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(MarkdownifyCommand.class);

  // One or more Taiga user story JSON files (each file represents a single user story)
  @Parameters(arity = "1..*", paramLabel = "<user-story>",
      description = "Path(s) to Taiga user story JSON file(s)")
  List<Path> userStories;

  // Output directory (required)
  @Option(names = "--output-directory", paramLabel = "<output-directory>", required = true,
      description = "Directory where markdown file(s) will be written")
  Path outputDirectory;

  // Overwrite flag with default false
  @Option(names = "--overwrite", description = "Overwrite markdown file(s) if they already exist", defaultValue = "false")
  boolean overwrite;

  @Override
  public void run() {

    if (userStories == null || userStories.isEmpty()) {
      LOG.warn("No --user-story files provided.");
      return;
    }

    TaigaHelper taigaHelper = new TaigaHelper();
    MarkdownHelper markdownHelper = new MarkdownHelper();

    LOG.info("Processing Taiga user story files: {}", userStories);
    for (Path userStory : userStories) {
      LOG.debug("Processing Taiga user story file: {}", userStory);
      try {
        Card card = taigaHelper.parseUserStoryFile(userStory);
        LOG.debug("Parsed card: {} (source file: {})", card, userStory);
        Map<Path, Map<String, Path>> outputFiles = markdownHelper.writeMarkdownDocument(card, outputDirectory, overwrite);
        LOG.debug("Created markdown file(s): {}", outputFiles);
        LOG.debug("Successfully processed Taiga user story file: {}", userStory);
      } catch (Exception e) {
        throw new RuntimeException("Failed to process Taiga user story from file: " + userStory, e);
      }
    }
  }
}
