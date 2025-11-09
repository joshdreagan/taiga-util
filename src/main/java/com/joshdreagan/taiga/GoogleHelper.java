package com.joshdreagan.taiga;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.DocsScopes;
import com.google.api.services.docs.v1.model.BatchUpdateDocumentRequest;
import com.google.api.services.docs.v1.model.CreateParagraphBulletsRequest;
import com.google.api.services.docs.v1.model.Document;
import com.google.api.services.docs.v1.model.InsertTextRequest;
import com.google.api.services.docs.v1.model.Link;
import com.google.api.services.docs.v1.model.ParagraphStyle;
import com.google.api.services.docs.v1.model.Range;
import com.google.api.services.docs.v1.model.Request;
import com.google.api.services.docs.v1.model.StructuralElement;
import com.google.api.services.docs.v1.model.TextStyle;
import com.google.api.services.docs.v1.model.UpdateParagraphStyleRequest;
import com.google.api.services.docs.v1.model.UpdateTextStyleRequest;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.joshdreagan.taiga.model.Attachment;
import com.joshdreagan.taiga.model.Card;
import com.joshdreagan.taiga.model.Comment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.*;

public final class GoogleHelper {

  private static final List<String> AUTHORIZATION_CODE_FLOW_SCOPES = Arrays.asList(DriveScopes.DRIVE, DocsScopes.DOCUMENTS);
  private static final int LOCAL_SERVER_RECEIVER_PORT = 8888;
  private static final String OAUTH_APPLICATION_NAME = "taiga-importer";

  private final Path credentialsFile;
  private final String oauthUser;
  private final NetHttpTransport httpTransport;
  private final JsonFactory jsonFactory;
  private final Path tempDirectory;

  private boolean initialized = false;
  private Drive drive;
  private Docs docs;

  public GoogleHelper(Path credentialsFile, String oauthUser, Path tempDirectory) throws IOException, GeneralSecurityException {
    this(credentialsFile, oauthUser, GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), tempDirectory);
  }

  public GoogleHelper(Path credentialsFile, String oauthUser, NetHttpTransport httpTransport, JsonFactory jsonFactory, Path tempDirectory) {
    this.credentialsFile = Objects.requireNonNull(credentialsFile, "credentialsFile is null");
    this.oauthUser = Objects.requireNonNull(oauthUser, "oauthUser is null");
    this.httpTransport = Objects.requireNonNull(httpTransport, "httpTransport is null");
    this.jsonFactory = Objects.requireNonNull(jsonFactory, "jsonFactory is null");
    this.tempDirectory = Objects.requireNonNull(tempDirectory, "tempDirectory is null");
  }

  @SuppressWarnings("unused")
  public Path getCredentialsFile() {
    return credentialsFile;
  }

  @SuppressWarnings("unused")
  public String getOauthUser() {
    return oauthUser;
  }

  @SuppressWarnings("unused")
  public JsonFactory getJsonFactory() {
    return jsonFactory;
  }

  @SuppressWarnings("unused")
  public NetHttpTransport getHttpTransport() {
    return httpTransport;
  }

  @SuppressWarnings("unused")
  public Path getTempDirectory() {
    return tempDirectory;
  }

  public void initialize() throws IOException {
    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(jsonFactory, Files.newBufferedReader(credentialsFile));
    GoogleAuthorizationCodeFlow authorizationCodeFlow= new GoogleAuthorizationCodeFlow.Builder(
      httpTransport, jsonFactory, clientSecrets, AUTHORIZATION_CODE_FLOW_SCOPES)
      .setDataStoreFactory(new FileDataStoreFactory(tempDirectory.resolve("tokens").toFile()))
      .setAccessType("offline")
      .build();
    LocalServerReceiver localServerReceiver= new LocalServerReceiver.Builder().setPort(LOCAL_SERVER_RECEIVER_PORT).build();
    Credential credential= new AuthorizationCodeInstalledApp(authorizationCodeFlow, localServerReceiver).authorize(oauthUser);

    drive = new Drive.Builder(httpTransport, jsonFactory, credential)
      .setApplicationName(OAUTH_APPLICATION_NAME)
      .build();

    docs = new Docs.Builder(httpTransport, jsonFactory, credential)
      .setApplicationName(OAUTH_APPLICATION_NAME)
      .build();
    initialized = true;
  }

  private String sanitizeFilename(String name) {
    if (name == null) return null;
    String n = name.trim();
    // Remove path separators and control characters
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < n.length(); i++) {
      char c = n.charAt(i);
      if (c == '/' || c == '\\' || c < 32) {
        sb.append('_');
      } else {
        sb.append(c);
      }
    }
    String cleaned = sb.toString();
    if (cleaned.isEmpty()) cleaned = "attachment";
    // Avoid overly long filenames
    if (cleaned.length() > 200) cleaned = cleaned.substring(0, 200);
    return cleaned;
  }

  private Path writeTempFile(Attachment attachment) throws IOException {
    byte[] bytes = Base64.getDecoder().decode(attachment.getData());

    Files.createDirectories(tempDirectory);

    String filename = sanitizeFilename(attachment.getName());
    Path targetFile = tempDirectory.resolve(filename);
    Files.deleteIfExists(targetFile);
    Files.write(targetFile, bytes);
    return targetFile;
  }

  private String findExistingFileInFolder(String parentFolderId, String fileName) throws IOException {
    String escapedName = fileName.replace("'", "\\'");
    String q = "mimeType != 'application/vnd.google-apps.folder' and name='" + escapedName + "' and '" + parentFolderId + "' in parents and trashed=false";
    FileList list = drive.files().list()
      .setQ(q)
      .setSpaces("drive")
      .setFields("files(id,name)")
      .setPageSize(1)
      .execute();
    if (list.getFiles() != null && !list.getFiles().isEmpty()) {
      return list.getFiles().getFirst().getId();
    }
    return null;
  }

  private static String detectMimeType(Path filePath, String originalName) {
    // 1) Try system probe
    try {
      String probed = Files.probeContentType(filePath);
      if (probed != null && !probed.isBlank()) return probed;
    } catch (IOException ignored) {}
    // 2) Simple extension mapping
    String name = originalName != null ? originalName : filePath.getFileName().toString();
    String lower = name.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".png")) return "image/png";
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
    if (lower.endsWith(".gif")) return "image/gif";
    if (lower.endsWith(".pdf")) return "application/pdf";
    if (lower.endsWith(".txt")) return "text/plain";
    if (lower.endsWith(".csv")) return "text/csv";
    if (lower.endsWith(".md")) return "text/markdown";
    if (lower.endsWith(".json")) return "application/json";
    if (lower.endsWith(".xml")) return "application/xml";
    if (lower.endsWith(".zip")) return "application/zip";
    if (lower.endsWith(".gz")) return "application/gzip";
    if (lower.endsWith(".doc")) return "application/msword";
    if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
    if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    if (lower.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
    if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    return "application/octet-stream";
  }

  public String createFolder(String folderName, String parentFolderId) throws IOException {
    if (!initialized) throw new IllegalStateException("GoogleHelper has not been initialized");

    String escapedName = folderName.replace("'", "\\'");
    String q = "mimeType='application/vnd.google-apps.folder' and name='" + escapedName + "' and '" + parentFolderId + "' in parents and trashed=false";
    FileList list = drive.files().list()
      .setQ(q)
      .setSpaces("drive")
      .setFields("files(id,name)")
      .setPageSize(1)
      .execute();
    if (list.getFiles() != null && !list.getFiles().isEmpty()) {
      return list.getFiles().getFirst().getId();
    }
    File metadata = new File();
    metadata.setName(folderName);
    metadata.setMimeType("application/vnd.google-apps.folder");
    metadata.setParents(Collections.singletonList(parentFolderId));
    File created = drive.files().create(metadata).setFields("id").execute();
    return created.getId();
  }

  public String uploadAttachment(Attachment attachment, String parentFolderId, boolean overwrite) throws IOException {
    if (!initialized) throw new IllegalStateException("GoogleHelper has not been initialized");

    Path tempFile = writeTempFile(attachment);
    String safeName = sanitizeFilename(attachment.getName() != null ? attachment.getName() : tempFile.getFileName().toString());

    // Duplicate handling
    String existingId = findExistingFileInFolder(parentFolderId, safeName);
    if (existingId != null) {
      if (overwrite) {
        drive.files().delete(existingId).execute();
      } else {
        throw new FileAlreadyExistsException("File already exists: " + existingId);
      }
    }

    File metadata = new File();
    metadata.setName(safeName);
    metadata.setParents(Collections.singletonList(parentFolderId));

    String mimeType = detectMimeType(tempFile, safeName);
    try (InputStream in = Files.newInputStream(tempFile)) {
      InputStreamContent mediaContent = new InputStreamContent(mimeType, in);
      File result = drive.files().create(metadata, mediaContent)
        .setFields("id, name")
        .execute();
      Files.delete(tempFile);
      return result.getId();
    }
  }

  public String createBlankDocument(String documentName, String parentFolderId, boolean overwrite) throws IOException {
    String safeName = sanitizeFilename(documentName);

    String existingId = findExistingFileInFolder(parentFolderId, safeName);
    if (existingId != null) {
      if (overwrite) {
        drive.files().delete(existingId).execute();
      } else {
        throw new FileAlreadyExistsException("File already exists: " + existingId);
      }
    }

    File metadata = new File();
    metadata.setName(safeName);
    metadata.setParents(Collections.singletonList(parentFolderId));
    metadata.setMimeType("application/vnd.google-apps.document");
    File result = drive.files().create(metadata)
      .setFields("id, name")
      .execute();
    return result.getId();
  }

  public String createDocument(Card card, String parentFolderId, boolean overwrite) throws IOException {
    if (!initialized) throw new IllegalStateException("GoogleHelper has not been initialized");

    String title = card.getId() + (card.getSubject() != null ? ": " + card.getSubject() : "");

    String documentId = createBlankDocument(title, parentFolderId, overwrite);

    Map<String, String> attachmentUrls = new HashMap<>();
    if (card.getAttachments() != null && !card.getAttachments().isEmpty()) {
      String cardFolderId = createFolder(title, parentFolderId);
      for (Attachment attachment : card.getAttachments()) {
        String attachmentId = uploadAttachment(attachment, cardFolderId, overwrite);
        String attachmentDriveUrl = String.format("https://drive.google.com/file/d/%s/view?usp=drive_link",  attachmentId);
        attachmentUrls.put(attachment.getName(), attachmentDriveUrl);
      }
    }

    // Build document content via Docs API
    Document doc = docs.documents().get(documentId).execute();
    int endIndex = 1; // default for empty doc
    if (doc.getBody() != null && doc.getBody().getContent() != null && !doc.getBody().getContent().isEmpty()) {
      // Find the last structural element's endIndex
      java.util.List<StructuralElement> content = doc.getBody().getContent();
      StructuralElement last = content.getLast();
      if (last.getEndIndex() != null) {
        endIndex = last.getEndIndex();
      }
    }

    int index = Math.max(1, endIndex - 1); // cursor before the last newline
    java.util.List<Request> requests = new ArrayList<>();

    // small helpers via lambdas
    java.util.function.BiFunction<String, String, Range> rangeFor = (s, e) -> {
      // not used - keeping interface parity
      return null;
    };
    java.util.function.Function<Integer, Range> startAt = (i) -> new Range().setStartIndex(i).setEndIndex(i);

    // Convenience to insert text and return new end cursor
    java.util.function.BiFunction<String, Integer, Integer> insertText = (text, at) -> {
      requests.add(new Request().setInsertText(new InsertTextRequest().setText(text).setLocation(new com.google.api.services.docs.v1.model.Location().setIndex(at))));
      return at + text.length();
    };

    // Apply heading style over a range
    java.util.function.BiConsumer<String, int[]> applyHeading = (level, range) -> {
      ParagraphStyle ps = new ParagraphStyle();
      ps.setNamedStyleType(level); // e.g., HEADING_1, HEADING_2
      requests.add(new Request().setUpdateParagraphStyle(new UpdateParagraphStyleRequest()
        .setRange(new Range().setStartIndex(range[0]).setEndIndex(range[1]))
        .setParagraphStyle(ps)
        .setFields("namedStyleType")));
    };

    // Apply text style (e.g., bold or link)
    java.util.function.BiConsumer<TextStyle, int[]> applyTextStyle = (style, range) -> {
      requests.add(new Request().setUpdateTextStyle(new UpdateTextStyleRequest()
        .setRange(new Range().setStartIndex(range[0]).setEndIndex(range[1]))
        .setTextStyle(style)
        .setFields("bold,link")));
    };

    // Turn last paragraphs into bullets
    java.util.function.Consumer<int[]> bulletize = (range) -> {
      requests.add(new Request().setCreateParagraphBullets(new CreateParagraphBulletsRequest()
        .setRange(new Range().setStartIndex(range[0]).setEndIndex(range[1]))
        .setBulletPreset("BULLET_DISC_CIRCLE_SQUARE")));
    };

    // Section: Title (heading 1)
    int hStart = index;
    index = insertText.apply("Title\n", index);
    int hEnd = index;
    applyHeading.accept("HEADING_1", new int[]{hStart, hEnd});

    // Title content: use subject as "summary" fallback
    String summary = card.getSubject() != null ? card.getSubject() : "";
    if (!summary.isBlank()) {
      index = insertText.apply(summary + "\n\n", index);
    } else {
      index = insertText.apply("\n", index);
    }

    // Section: Tags (heading 2)
    hStart = index;
    index = insertText.apply("Tags\n", index);
    hEnd = index;
    applyHeading.accept("HEADING_2", new int[]{hStart, hEnd});

    int tagsStartForBullets = index;
    if (card.getTags() != null && !card.getTags().isEmpty()) {
      for (String tag : card.getTags()) {
        index = insertText.apply(tag + "\n", index);
      }
      int tagsEndForBullets = index;
      bulletize.accept(new int[]{tagsStartForBullets, tagsEndForBullets});
      index = insertText.apply("\n", index);
    } else {
      index = insertText.apply("(none)\n\n", index);
    }

    // Section: Description (heading 2)
    hStart = index;
    index = insertText.apply("Description\n", index);
    hEnd = index;
    applyHeading.accept("HEADING_2", new int[]{hStart, hEnd});

    String description = card.getDescription() != null ? card.getDescription() : "";
    if (!description.isBlank()) {
      index = insertText.apply(description + "\n\n", index);
    } else {
      index = insertText.apply("(no description)\n\n", index);
    }

    // Section: Attachments (heading 2)
    hStart = index;
    index = insertText.apply("Attachments\n", index);
    hEnd = index;
    applyHeading.accept("HEADING_2", new int[]{hStart, hEnd});

    int attachStartForBullets = index;
    if (!attachmentUrls.isEmpty()) {
      for (Map.Entry<String, String> e : attachmentUrls.entrySet()) {
        String text = (e.getKey() != null ? e.getKey() : e.getValue());
        int itemStart = index;
        index = insertText.apply(text, index);
        // hyperlink the text
        TextStyle ts = new TextStyle();
        ts.setLink(new Link().setUrl(e.getValue()));
        applyTextStyle.accept(ts, new int[]{itemStart, itemStart + text.length()});
        // end with newline
        index = insertText.apply("\n", index);
      }
      int attachEndForBullets = index;
      bulletize.accept(new int[]{attachStartForBullets, attachEndForBullets});
      index = insertText.apply("\n", index);
    } else {
      index = insertText.apply("(none)\n\n", index);
    }

    // Section: Comments (heading 2)
    hStart = index;
    index = insertText.apply("Comments\n", index);
    hEnd = index;
    applyHeading.accept("HEADING_2", new int[]{hStart, hEnd});

    java.util.List<Comment> comments = new ArrayList<>();
    if (card.getComments() != null) comments.addAll(card.getComments());
    comments.sort(Comparator.comparing(Comment::getCreated, Comparator.nullsLast(Comparator.naturalOrder())));

    if (!comments.isEmpty()) {
      for (Comment c : comments) {
        String header = (c.getUser() != null ? c.getUser() : "") + " - " + c.getCreated();
        int hdrStart = index;
        index = insertText.apply(header, index);
        // bold header
        TextStyle bold = new TextStyle().setBold(true);
        applyTextStyle.accept(bold, new int[]{hdrStart, hdrStart + header.length()});
        // newline and comment body
        int bdyStart = index;
        index = insertText.apply("\n", index);
        String body = c.getComment() != null ? c.getComment() : "";
        //body = body.replaceAll("([^!?])\\[(\\S*)\\]\\((\\S*)\\)", "$1$2");
        index = insertText.apply(body + "\n\n", index);
        TextStyle unbold = new TextStyle().setBold(false);
        applyTextStyle.accept(unbold, new int[]{bdyStart, bdyStart + body.length() + 3});
      }
    } else {
      index = insertText.apply("(no comments)\n", index);
    }

    // Execute batch update
    BatchUpdateDocumentRequest body = new BatchUpdateDocumentRequest().setRequests(requests);
    docs.documents().batchUpdate(documentId, body).execute();

    return documentId;
  }
}
