package com.joshdreagan.taiga.model;

import java.time.Instant;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class Comment {

  private String user;
  private String comment;
  private Instant created;
  private Instant updated;

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public Instant getCreated() {
    return created;
  }

  public void setCreated(Instant created) {
    this.created = created;
  }

  public Instant getUpdated() {
    return updated;
  }

  public void setUpdated(Instant updated) {
    this.updated = updated;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
      .append("user", user)
      .append("comment", comment)
      .append("created", created)
      .append("updated", updated)
      .toString();
  }
}
