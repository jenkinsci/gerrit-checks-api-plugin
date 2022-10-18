package io.jenkins.plugins.gerritchangequery.rest;

public class Link {
  private String url;
  private String tooltip;
  private boolean primary;
  private LinkIcon icon;

  public Link() {}

  public Link(String url, String tooltip, boolean primary, LinkIcon icon) {
    this.url = url;
    this.tooltip = tooltip;
    this.primary = primary;
    this.icon = icon;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getTooltip() {
    return tooltip;
  }

  public void setTooltip(String tooltip) {
    this.tooltip = tooltip;
  }

  public boolean isPrimary() {
    return primary;
  }

  public void setPrimary(boolean primary) {
    this.primary = primary;
  }

  public LinkIcon getIcon() {
    return icon;
  }

  public void setIcon(LinkIcon icon) {
    this.icon = icon;
  }

  enum LinkIcon {
    EXTERNAL,
    IMAGE,
    HISTORY,
    DOWNLOAD,
    DOWNLOAD_MOBILE,
    HELP_PAGE,
    REPORT_BUG,
    CODE,
    FILE_PRESENT
  }
}
