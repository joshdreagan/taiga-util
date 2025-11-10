package com.joshdreagan.taiga;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshdreagan.taiga.model.Attachment;
import com.joshdreagan.taiga.model.Card;
import com.joshdreagan.taiga.model.Comment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public final class TaigaHelper {

  final ObjectMapper objectMapper;

  public TaigaHelper() {
    this(new ObjectMapper());
  }

  public TaigaHelper(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is null");
  }

  @SuppressWarnings("unchecked")
  public Card parseUserStoryFile(Path file) throws IOException {
    byte[] jsonBytes = Files.readAllBytes(file);
    Map<String, Object> userStoryMap = objectMapper.readValue(jsonBytes, new TypeReference<HashMap<String, Object>>() {
    });

    // Capture required fields into variables
    String assigned_to = (String) userStoryMap.get("assigned_to");
    List<String> assigned_users = (List<String>) userStoryMap.get("assigned_users");
    List<Map<String, Object>> attachments = (List<Map<String, Object>>) userStoryMap.get("attachments");
    Instant created_date = toInstant(userStoryMap.get("created_date"));
    Integer ref = (Integer) userStoryMap.get("ref");
    String subject = (String) userStoryMap.get("subject");
    String description = (String) userStoryMap.get("description");
    List<Map<String, Object>> history = (List<Map<String, Object>>) userStoryMap.get("history");
    List<String> tags =(List<String>) userStoryMap.get("tags");
    String status = (String) userStoryMap.get("status");

    // Build assignees as an order-preserving set: assigned_to first, then assigned_users
    LinkedHashSet<String> assigneesSet = new LinkedHashSet<>();
    if (assigned_to != null && !assigned_to.isBlank()) {
      assigneesSet.add(assigned_to);
    }
    if (assigned_users != null && !assigned_users.isEmpty()) {
      assigneesSet.addAll(assigned_users);
    }

    // Process attachments: extract fields and write decoded file data, and build Attachment list for the card
    List<Attachment> cardAttachments = new ArrayList<>();
    if (attachments != null && !attachments.isEmpty()) {
      for (Map<String, Object> attachmentMap : attachments) {
        Attachment attachment = toAttachment(attachmentMap);
        if (attachment == null) continue;
        cardAttachments.add(attachment);
      }
    }

    // Process history: extract and log valid comments, and build Comment list for the card
    List<Comment> cardComments = new ArrayList<>();
    if (history != null && !history.isEmpty()) {
      for (Map<String, Object> historyMap : history) {
        Comment comment = toComment(historyMap);
        if (comment == null) continue;
        cardComments.add(comment);
      }
    }

    Card card = new Card();
    card.setId(ref);
    card.setSubject(subject);
    card.setDescription(description);
    card.setCreated(created_date);
    card.setAssignees(assigneesSet);
    card.setAttachments(cardAttachments);
    card.setComments(cardComments);
    card.setTags(new HashSet<>(tags));
    card.setStatus(status);
    return card;
  }

  @SuppressWarnings("unchecked")
  private Attachment toAttachment(Map<String, Object> attachmentMap) {
    String attName = (String) attachmentMap.get("name");
    Instant attCreated = toInstant(attachmentMap.get("created_date"));
    Instant attModified = toInstant(attachmentMap.get("modified_date"));
    String attOwner = (String) attachmentMap.get("owner");
    Map<String, Object> attachedFile = (Map<String, Object>) attachmentMap.get("attached_file");
    String data = null;
    if (attachedFile != null) {
      data = (String) attachedFile.get("data");
    }

    if (data == null) return null;

    Attachment attachment = new Attachment();
    attachment.setName(attName);
    attachment.setOwner(attOwner);
    attachment.setCreated(attCreated);
    attachment.setUpdated(attModified);
    attachment.setData(data);
    return attachment;
  }

  @SuppressWarnings("unchecked")
  private Comment toComment(Map<String, Object> historyMap) {
    if (historyMap == null) return null;
    String commentText = (String) historyMap.get("comment");
    if (commentText == null || commentText.isBlank()) return null;
    Instant deleteDate = toInstant(historyMap.get("delete_comment_date"));
    if (deleteDate != null) return null;
    List<String> histUsers = (List<String>) historyMap.get("user");
    String histUser = null;
    if (histUsers != null) {
      if (histUsers.size() == 2) {
        histUser = histUsers.get(1);
        if (histUsers.get(0) != null) {
          histUser = histUser + " <" + histUsers.get(0) + ">";
        }
      } else if (histUsers.size() == 1) {
        histUser = histUsers.get(0);
      }
      if (histUser == null) {
        for (String user : histUsers) {
          histUser = user;
          if (histUser != null && !histUser.isBlank()) break;
        }
      }
    }
    Instant createdAt = toInstant(historyMap.get("created_at"));
    Instant editDate = toInstant(historyMap.get("edit_comment_date"));

    Comment comment = new Comment();
    comment.setUser(histUser);
    comment.setComment(commentText);
    comment.setCreated(createdAt);
    comment.setUpdated(editDate);
    return comment;
  }

  private Instant toInstant(Object o) {
    if (o == null) return null;
    if (o instanceof Instant) return (Instant) o;
    if (o instanceof Date) return ((Date) o).toInstant();
    // Expecting a string like: 2025-04-22T14:12:01+0000 (offset without colon)
    String text = String.valueOf(o).trim();
    try {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
      OffsetDateTime odt = OffsetDateTime.parse(text, formatter);
      return odt.toInstant();
    } catch (DateTimeParseException e1) {
      // Fallback: try the standard ISO format (e.g., 2025-04-22T14:12:01+00:00 or Z)
      try {
        OffsetDateTime odt = OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return odt.toInstant();
      } catch (DateTimeParseException e2) {
        return null;
      }
    }
  }
}
