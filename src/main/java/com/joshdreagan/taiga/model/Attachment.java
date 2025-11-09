package com.joshdreagan.taiga.model;

import java.time.Instant;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class Attachment {

  private String name;
  private String data;
  private String owner;
  private Instant created;
  private Instant updated;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getData() {
    return data;
  }

  public void setData(String data) {
    this.data = data;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
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
    Integer dataLength = (data != null) ? data.length() : null;
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
      .append("name", name)
      .append("owner", owner)
      .append("created", created)
      .append("updated", updated)
      .append("dataLength", dataLength)
      .toString();
  }
}
