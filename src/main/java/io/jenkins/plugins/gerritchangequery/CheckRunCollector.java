package io.jenkins.plugins.gerritchangequery;

import hudson.model.Job;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.util.RunList;
import io.jenkins.plugins.gerritchangequery.rest.CheckRuns;
import io.jenkins.plugins.gerritchangequery.rest.GerritMultiBranchCheckRunFactory;
import io.jenkins.plugins.gerritchangequery.rest.GerritTriggerCheckRunFactory;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;

public class CheckRunCollector {
  private final GerritTriggerCheckRunFactory gerritTriggerCheckRunFactory =
      new GerritTriggerCheckRunFactory();
  private final GerritMultiBranchCheckRunFactory gerritMultiBranchCheckRunFactory =
      new GerritMultiBranchCheckRunFactory();

  @SuppressWarnings({"rawtypes", "unchecked"})
  public CheckRuns collectFor(int change, int patchset) {
    CheckRuns checkRuns = new CheckRuns();
    Jenkins jenkins = Jenkins.get();
    List<Job> jobs = jenkins.getAllItems(Job.class);
    for (Job job : jobs) {
      RunList<Run> runList = job.getBuilds();
      List<Run> runs =
          runList.stream()
              .filter(b -> isGerritTriggerBuildForRef(b, change, patchset))
              .sorted(Comparator.comparing(Run::getNumber))
              .collect(Collectors.toList());
      for (int i = 0; i < runs.size(); i++) {
        checkRuns.addRun(
            gerritTriggerCheckRunFactory.create(change, patchset, job, runs.get(i), i + 1));
      }
      Collection<Job> childJobs = job.getAllJobs();
      for (Job childJob : childJobs) {
        if (childJob.getDisplayName().equals(convertToRef(change, patchset))) {
          RunList<Run> childJobRunList = childJob.getBuilds();
          for (Run run : childJobRunList) {
            checkRuns.addRun(
                gerritMultiBranchCheckRunFactory.create(
                    change, patchset, job, run, run.getNumber()));
          }
        }
      }
    }
    return checkRuns;
  }

  private static boolean isGerritTriggerBuildForRef(Run run, int change, int patchset) {
    ParametersAction params = run.getAction(ParametersAction.class);
    if (params != null) {
      ParameterValue buildChange = params.getParameter("GERRIT_CHANGE_NUMBER");
      ParameterValue buildPatchset = params.getParameter("GERRIT_PATCHSET_NUMBER");
      if (buildChange != null || buildPatchset != null) {
        return Integer.valueOf(buildChange.getValue().toString()) == change
            && Integer.valueOf(buildPatchset.getValue().toString()) == patchset;
      }
    }
    return false;
  }

  private static String convertToRef(int change, int patchset) {
    return String.format("%02d/%d/%d", change % 100, change, patchset);
  }
}
