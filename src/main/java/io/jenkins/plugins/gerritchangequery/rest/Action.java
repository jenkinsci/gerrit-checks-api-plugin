package io.jenkins.plugins.gerritchangequery.rest;

import com.google.gson.JsonObject;

public abstract class Action {
  private final String name;
  private final String tooltip;
  private final boolean primary;
  private final boolean summary;
  private final String method;

  private boolean disabled;
  private String url;
  private JsonObject data;

  protected Action(String name, String tooltip, boolean primary, boolean summary, String method) {
    this.name = name;
    this.tooltip = tooltip;
    this.primary = primary;
    this.summary = summary;
    this.method = method;
  }

  protected Action(
      String name,
      String tooltip,
      boolean primary,
      boolean summary,
      String method,
      boolean disabled,
      String url,
      JsonObject data) {
    this.name = name;
    this.tooltip = tooltip;
    this.primary = primary;
    this.summary = summary;
    this.method = method;
    this.disabled = disabled;
    this.url = url;
    this.data = data;
  }

  public String getName() {
    return name;
  }

  public String getTooltip() {
    return tooltip;
  }

  public boolean isPrimary() {
    return primary;
  }

  public boolean isSummary() {
    return summary;
  }

  public String getMethod() {
    return method;
  }

  public boolean isDisabled() {
    return disabled;
  }

  public void setDisabled(boolean disabled) {
    this.disabled = disabled;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public JsonObject getData() {
    return data;
  }

  public void setData(JsonObject data) {
    this.data = data;
  }
}
