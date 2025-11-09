package com.joshdreagan.taiga;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Command(name = "split", mixinStandardHelpOptions = true, description = "Split a raw Taiga export dump into individual user story JSON files")
public class SplitCommand implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(SplitCommand.class);

  @Option(names = "--output-directory", required = true, paramLabel = "<output-directory>",
    description = "Directory to write user story JSON files to")
  Path outputDirectory;

  @Option(names = "--overwrite", description = "Overwrite existing files", defaultValue = "false")
  boolean overwrite;

  @Parameters(index = "0", paramLabel = "<input-file>", description = "Path to the input JSON file", arity = "1")
  Path inputFile;

  @Override
  public void run() {
    // Validate and prepare output directory
    try {
      Files.createDirectories(outputDirectory);
    } catch (IOException e) {
      throw new RuntimeException("Failed to create output directory: " + outputDirectory, e);
    }

    Map<String, Object> data;
    try {
      byte[] jsonBytes = Files.readAllBytes(inputFile);
      ObjectMapper mapper = new ObjectMapper();
      data = mapper.readValue(jsonBytes, new TypeReference<HashMap<String, Object>>() {
      });
    } catch (IOException e) {
      throw new RuntimeException("Failed to read or parse input JSON file: " + inputFile, e);
    }

    if (data == null) {
      LOG.error("Input JSON was empty or could not be parsed: {}", inputFile);
      return;
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> userStories = (List<Map<String, Object>>) data.get("user_stories");
    if (userStories == null || userStories.isEmpty()) {
      LOG.warn("No 'user_stories' element found or it was empty in input file: {}", inputFile);
      return;
    }

    LOG.info("Writing {} user stories to {} (overwrite={})", userStories.size(), outputDirectory, overwrite);

    ObjectMapper mapper = new ObjectMapper();

    for (Map<String, Object> userStory : userStories) {
      Object refObj = userStory.get("ref");
      if (refObj == null) {
        LOG.warn("Skipping a user story without 'ref' field");
        continue;
      }
      String ref = String.valueOf(refObj);
      Path outFile = outputDirectory.resolve(ref + ".json");

      try {
        if (Files.exists(outFile)) {
          if (!overwrite) {
            LOG.info("File {} already exists. Skipping (use --overwrite to replace).", outFile);
            continue;
          } else {
            LOG.info("Overwriting existing file {} as --overwrite is true.", outFile);
          }
        }
        byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(userStory);
        Files.write(outFile, bytes);
      } catch (Exception e) {
        LOG.error("Failed to write user story ref={} to file {}", ref, outFile, e);
      }
    }
  }
}
