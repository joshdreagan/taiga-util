package com.joshdreagan.taiga.model;

import java.time.Instant;
import java.util.List;
import java.util.SequencedSet;
import java.util.Set;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class Card {

  private Integer id;
  private String subject;
  private String details;
  private SequencedSet<String> assignees;
  private Instant created;
  private List<Attachment> attachments;
  private List<Comment> comments;
  private Set<String> tags;
  private String status;

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }

  public SequencedSet<String> getAssignees() {
    return assignees;
  }

  public Instant getCreated() {
    return created;
  }

  public void setCreated(Instant created) {
    this.created = created;
  }

  public void setAssignees(SequencedSet<String> assignees) {
    this.assignees = assignees;
  }

  public List<Attachment> getAttachments() {
    return attachments;
  }

  public void setAttachments(List<Attachment> attachments) {
    this.attachments = attachments;
  }

  public List<Comment> getComments() {
    return comments;
  }

  public void setComments(List<Comment> comments) {
    this.comments = comments;
  }

  public Set<String> getTags() {
    return tags;
  }

  public void setTags(Set<String> tags) {
    this.tags = tags;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
      .append("id", id)
      .append("subject", subject)
      .append("details", details)
      .append("assignees", assignees)
      .append("created", created)
      .append("attachments", attachments)
      .append("comments", comments)
      .append("tags", tags)
      .append("status", status)
      .toString();
  }
}
