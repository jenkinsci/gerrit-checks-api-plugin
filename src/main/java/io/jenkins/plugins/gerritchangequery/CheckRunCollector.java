package io.jenkins.plugins.gerritchangequery;

import hudson.model.Job;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.util.RunList;
import io.jenkins.plugins.gerritchangequery.rest.CheckRun;
import io.jenkins.plugins.gerritchangequery.rest.CheckRuns;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;

public class CheckRunCollector {
  private static final Pattern REF_PATTERN = Pattern.compile("[0-9]{2}/[0-9]+/[0-9]+");

  public static CheckRuns collectFor(int change, int patchset) {
    CheckRuns checkRuns = new CheckRuns();
    Jenkins jenkins = Jenkins.get();
    List<Job> jobs = jenkins.getAllItems(Job.class);
    for (Job job : jobs) {
      RunList<Run> builds = job.getBuilds();
      for (Run build : builds) {
        ParametersAction params = build.getAction(ParametersAction.class);
        if (params != null) {
          ParameterValue buildChange = params.getParameter("GERRIT_CHANGE_NUMBER");
          ParameterValue buildPatchset = params.getParameter("GERRIT_PATCHSET_NUMBER");
          if (buildChange != null || buildPatchset != null) {
            if (Integer.valueOf(buildChange.getValue().toString()) == change
                && Integer.valueOf(buildPatchset.getValue().toString()) == patchset) {
              checkRuns.addRun(
                  CheckRun.Factory.create(
                      change, patchset, CheckRun.Factory.JobType.GERRIT_TRIGGER, job, build));
            }
          }
        }
      }
      Collection<Job> childJobs = job.getAllJobs();
      for (Job childJob : childJobs) {
        if (childJob.getDisplayName().equals(convertToRef(change, patchset))) {
          RunList<Run> runs = childJob.getBuilds();
          for (Run run : runs) {
            checkRuns.addRun(
                CheckRun.Factory.create(
                    change, patchset, CheckRun.Factory.JobType.GERRIT_MULTI_BRANCH, job, run));
            continue;
          }
        }
      }
    }
    return checkRuns;
  }

  private static String convertToRef(int change, int patchset) {
    return String.format("%02d/%d/%d", change % 100, change, patchset);
  }
}
