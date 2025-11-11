package com.joshdreagan.taiga;

import com.joshdreagan.taiga.model.Attachment;
import com.joshdreagan.taiga.model.Card;
import com.joshdreagan.taiga.model.Comment;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MarkdownHelper {

  public Map<Path, Map<String, Path>> writeMarkdownDocument(Card card, Path parentDirectory, boolean overwrite) {

    // Build title "id: subject"
    String title = card.getId() + (card.getSubject() != null && !card.getSubject().isEmpty() ? ": " + card.getSubject() : "");
    String sanitizedName = title.replaceAll("[^a-zA-Z0-9_]", "_");

    Path mdFile = parentDirectory.resolve(sanitizedName + ".md");

    // Process attachments
    Map<String, Path> attachmentMap = new LinkedHashMap<>();
    List<Attachment> attachments = card.getAttachments();
    Path attachmentsDir = null;
    try {
      if (attachments != null && !attachments.isEmpty()) {
        attachmentsDir = parentDirectory.resolve(sanitizedName);
        if (!Files.exists(attachmentsDir)) {
          Files.createDirectories(attachmentsDir);
        }
        for (Attachment att : attachments) {
          if (att == null || att.getName() == null) continue;
          Path out = attachmentsDir.resolve(att.getName());
          if (Files.exists(out)) {
            if (overwrite) {
              // Overwrite existing file
              byte[] data = decodeBase64Safe(att.getData());
              Files.write(out, data, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            } else {
              // keep existing
            }
          } else {
            byte[] data = decodeBase64Safe(att.getData());
            Files.write(out, data, StandardOpenOption.CREATE_NEW);
          }
          attachmentMap.put(att.getName(), out);
        }
      }

      // Build markdown content
      StringBuilder md = new StringBuilder();
      md.append("# ").append(title).append("\n\n");

      // Status
      md.append("## Status\n\n");
      if (card.getStatus() != null) {
        md.append(card.getStatus()).append("\n\n");
      }

      // Tags
      md.append("## Tags\n\n");
      Set<String> tags = card.getTags();
      if (tags != null && !tags.isEmpty()) {
        for (String tag : tags) {
          md.append("- ").append(tag).append("\n");
        }
        md.append("\n");
      }

      // Details
      md.append("## Details\n\n");
      // Prepare text replacements for details using relative paths
      String fixedDetails = card.getDetails();
      if (fixedDetails != null && !fixedDetails.isBlank()) {
        fixedDetails = replaceTaigaMediaLinks(fixedDetails, attachmentMap, mdFile);
        fixedDetails = replaceLineEndings(fixedDetails);
        md.append(fixedDetails).append("\n\n");
      }

      // Attachments
      md.append("## Attachments\n\n");
      if (!attachmentMap.isEmpty()) {
        for (Map.Entry<String, Path> e : attachmentMap.entrySet()) {
          Path rel = mdFile.relativize(e.getValue());
          md.append("- [").append(e.getKey()).append("](").append(rel.toString()).append(")\n");
        }
        md.append("\n");
      }

      // Assignees
      md.append("## Assignees\n\n");
      if (card.getAssignees() != null && !card.getAssignees().isEmpty()) {
        for (String a : card.getAssignees()) {
          md.append("- ").append(a).append("\n");
        }
        md.append("\n");
      }

      // Comments
      md.append("## Comments\n\n");
      List<Comment> comments = card.getComments();
      if (comments != null && !comments.isEmpty()) {
        for (Comment c : comments) {
          if (c == null) continue;
          String header = (c.getUser() != null ? c.getUser() : "unknown") + " - " + c.getCreated();
          String text = c.getComment();
          if (text != null && !text.isBlank()) {
            text = replaceTaigaMediaLinks(text, attachmentMap, mdFile);
            text = replaceLineEndings(text);
            md.append("**").append(header).append("**\n\n").append(text).append("\n\n");
          }
        }
        md.append("\n");
      }

      // Write markdown file
      if (Files.exists(mdFile) && !overwrite) {
        throw new FileAlreadyExistsException(mdFile.toString());
      }
      Files.createDirectories(parentDirectory);
      Files.writeString(mdFile, md.toString(), StandardCharsets.UTF_8);

      Map<Path, Map<String, Path>> result = new LinkedHashMap<>();
      result.put(mdFile, attachmentMap);
      return result;
    } catch (IOException e) {
      throw new RuntimeException("Failed to create markdown document", e);
    }
  }

  private static byte[] decodeBase64Safe(String data) {
    if (data == null) return new byte[0];
    try {
      return Base64.getDecoder().decode(data);
    } catch (IllegalArgumentException ex) {
      // Try to strip data URL prefix if present
      int comma = data.indexOf(',');
      if (comma > 0) {
        String maybe = data.substring(comma + 1);
        return Base64.getDecoder().decode(maybe);
      }
      throw ex;
    }
  }

  private static String replaceTaigaMediaLinks(String inputText, Map<String, Path> attachmentMap, Path relativePath) {
    if (inputText == null || inputText.isEmpty() || attachmentMap == null || attachmentMap.isEmpty()) {
      return inputText;
    }

    relativePath = relativePath.toAbsolutePath();
    while (!Files.isDirectory(relativePath)) {
      relativePath = relativePath.getParent();
    }

    // Prepare a name->relativePath map for quick replacement
    Map<String, String> nameToRel = new LinkedHashMap<>();
    for (Map.Entry<String, Path> e : attachmentMap.entrySet()) {
      Path rel = relativePath.relativize(e.getValue());
      nameToRel.put(e.getKey(), rel.toString());
    }

    Pattern urlPattern = Pattern.compile("https?://media-protected\\.taiga\\.io[^\\s)]+", Pattern.CASE_INSENSITIVE);
    Matcher matcher = urlPattern.matcher(inputText);
    StringBuffer sb = new StringBuffer();

    while (matcher.find()) {
      String fullUrl = matcher.group();
      URI uri = URI.create(fullUrl);
      String path = uri.getPath();
      String lastSegment = null;
      if (path != null && !path.isEmpty()) {
        int lastSlash = path.lastIndexOf('/') + 1;
        if (lastSlash >= 0 && lastSlash < path.length()) {
          lastSegment = path.substring(lastSlash);
        }
      }
      if (lastSegment != null && !lastSegment.isEmpty()) {
        while (lastSegment.endsWith("/")) {
          lastSegment = lastSegment.substring(0, lastSegment.length() - 1);
        }
        String decodedName = URLDecoder.decode(lastSegment, StandardCharsets.UTF_8);
        String replacement = nameToRel.get(decodedName);
        if (replacement != null && !replacement.isEmpty()) {
          matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
          continue;
        }
      }
      matcher.appendReplacement(sb, Matcher.quoteReplacement(fullUrl));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  private static String replaceLineEndings(String inputText) {
    if (inputText == null || inputText.isEmpty()) {
      return inputText;
    }
    return inputText.replace("\r\n", "\n").replace("\r", "\n").replace('\u00A0', '\n');
  }
}
