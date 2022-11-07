package io.jenkins.plugins.gerritchangequery;

import com.google.gerrit.extensions.restapi.Url;
import hudson.model.Job;
import hudson.model.Run;
import hudson.search.Search;
import io.jenkins.plugins.gerritchangequery.rest.CheckRun;
import io.jenkins.plugins.gerritchangequery.rest.CheckRuns;
import io.jenkins.plugins.gerritchangequery.rest.GerritMultiBranchCheckRunFactory;
import io.jenkins.plugins.gerritchangequery.rest.GerritTriggerCheckRunFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.lucene.search.databackend.SearchBackendManager;

public class CheckRunCollector {
  private static final Logger LOGGER = Logger.getLogger(Search.class.getName());

  private final GerritTriggerCheckRunFactory gerritTriggerCheckRunFactory =
      new GerritTriggerCheckRunFactory();
  private final GerritMultiBranchCheckRunFactory gerritMultiBranchCheckRunFactory =
      new GerritMultiBranchCheckRunFactory();
  private final Jenkins jenkins = Jenkins.get();
  private final SearchBackendManager manager =
      jenkins.getExtensionList(SearchBackendManager.class).get(0);

  public CheckRuns collectFor(int change, int patchset) {
    CheckRuns checkRuns = new CheckRuns();
    checkRuns.addRuns(collectGerritTriggerRuns(change, patchset));
    checkRuns.addRuns(collectGerritMultiBranchRuns(change, patchset));
    return checkRuns;
  }

  @SuppressWarnings("rawtypes")
  private List<CheckRun> collectGerritTriggerRuns(int change, int patchset) {
    List<CheckRun> checkRuns = new ArrayList<>();
    List<Run> hits =
        manager
            .getHits(String.format("p:\"refs/changes/%s\"", convertToRef(change, patchset)), false)
            .stream()
            .map(
                hit ->
                    jenkins
                        .getItemByFullName(hit.getProjectName(), Job.class)
                        .getBuild(hit.getSearchName().substring(1)))
            .sorted(Comparator.comparing(Run::getNumber))
            .collect(Collectors.toList());
    for (int i = 0; i < hits.size(); i++) {
      checkRuns.add(
          gerritTriggerCheckRunFactory.create(
              change, patchset, hits.get(i).getParent(), hits.get(i), i + 1));
    }
    return checkRuns;
  }

  @SuppressWarnings("rawtypes")
  private List<CheckRun> collectGerritMultiBranchRuns(int change, int patchset) {
    List<CheckRun> checkRuns = new ArrayList<>();
    List<Run> hits =
        manager.getHits(String.format("j:\"%s\"", convertToUrlEncodedRef(change, patchset)), false)
            .stream()
            .map(
                hit ->
                    jenkins
                        .getItemByFullName(hit.getProjectName(), Job.class)
                        .getBuild(hit.getSearchName().substring(1)))
            .collect(Collectors.toList());
    for (Run hit : hits) {
      checkRuns.add(
          gerritMultiBranchCheckRunFactory.create(
              change, patchset, hit.getParent(), hit, hit.getNumber()));
    }
    return checkRuns;
  }

  private static String convertToRef(int change, int patchset) {
    return String.format("%02d/%d/%d", change % 100, change, patchset);
  }

  private static String convertToUrlEncodedRef(int change, int patchset) {
    return Url.encode(convertToRef(change, patchset));
  }
}
