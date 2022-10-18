package io.jenkins.plugins.gerritchangequery.rest;

import java.util.ArrayList;
import java.util.List;

public class CheckRuns {
  private List<CheckRun> runs = new ArrayList<>();

  public CheckRuns() {}

  public CheckRuns(List<CheckRun> runs) {
    this.runs = runs;
  }

  public boolean addRun(CheckRun run) {
    return runs.add(run);
  }

  public List<CheckRun> getRuns() {
    return runs;
  }

  public void setRuns(List<CheckRun> runs) {
    this.runs = runs;
  }
}
