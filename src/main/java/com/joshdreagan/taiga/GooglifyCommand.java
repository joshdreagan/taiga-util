package com.joshdreagan.taiga;

import com.joshdreagan.taiga.model.Card;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Command(name = "googlify", mixinStandardHelpOptions = true, description = "Converts the specified Taiga cards into Google Docs")
public class GooglifyCommand implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(GooglifyCommand.class);

  // One or more Taiga user story JSON files (each file represents a single user story)
  @Parameters(arity = "1..*", paramLabel = "<user-story>",
    description = "Path(s) to Taiga user story JSON file(s)")
  List<Path> userStories;

  // Path to the Google credentials file (required)
  @Option(names = "--credentials", paramLabel = "<credentials>", required = true,
    description = "Path to the Google credentials JSON file")
  Path credentials;

  // The OAuth2 user (required)
  @Option(names = "--oauth-user", paramLabel = "<oauth-user>", required = true,
    description = "The Google OAuth2 user")
  String oauthUser;

  // Force flag with default false
  @Option(names = "--overwrite", description = "Overwrite doc and attachments if they already exist", defaultValue = "false")
  boolean overwrite;

  // Folder ID string parameter
  @Option(names = "--folder-id", paramLabel = "<folder-id>", required = true, description = "The parent Google Drive folder ID to create documents in")
  String folderId;

  // Temporary working directory with default to system temp + project name
  @Option(names = "--temp-directory", paramLabel = "<temp-directory>",
    description = "Temporary working directory",
    defaultValue = "${sys:java.io.tmpdir}/taiga-importer")
  Path tempDirectory;

  @Override
  public void run() {

    if (userStories == null || userStories.isEmpty()) {
      LOG.warn("No --user-story files provided.");
      return;
    }

    TaigaHelper taigaHelper = new TaigaHelper();

    GoogleHelper googleHelper;
    try {
      googleHelper = new GoogleHelper(credentials, oauthUser, tempDirectory);
      googleHelper.initialize();
      LOG.info("Initialized Google Drive client.");
    } catch (Exception e) {
      LOG.error("Failed to initialize Google Drive client.", e);
      return;
    }

    LOG.info("Processing Taiga user story files: {}", Arrays.toString(userStories.toArray()));
    for (Path userStory : userStories) {
      LOG.trace("Processing Taiga user story file: {}", userStory);
      try {
        Card card = taigaHelper.parseUserStoryFile(userStory);
        LOG.trace("Parsed card: {} (source file: {})", card, userStory);
        String documentId = googleHelper.createDocument(card, folderId, overwrite);
        LOG.trace("Created Google Document documentId: {} (source file: {})", documentId, userStory);
        LOG.debug("Successfully processed Taiga user story file: {}", userStory);
      } catch (Exception e) {
        throw new RuntimeException("Failed to process Taiga user story from file: " + userStory, e);
      }
    }
  }
}
