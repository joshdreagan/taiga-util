package com.joshdreagan.taiga.model;

import java.time.Instant;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringExclude;
import org.apache.commons.lang3.builder.ToStringStyle;

public class Attachment {

  private String name;
  @ToStringExclude
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
    // Use reflection-based toString and exclude the potentially large 'data' field via annotation
    return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    // Reflection-based equals reduces boilerplate while including all fields (including 'data')
    return EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public int hashCode() {
    // Reflection-based hashCode consistent with equals
    return HashCodeBuilder.reflectionHashCode(this);
  }
}
