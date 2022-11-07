package io.jenkins.plugins.gerritchangequery.rest;

import hudson.Extension;
import hudson.model.RootAction;
import io.jenkins.plugins.gerritchangequery.CheckRunCollector;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.json.JsonHttpResponse;
import org.kohsuke.stapler.verb.GET;

@Extension
public class GetCheckRuns implements RootAction {
  private final CheckRunCollector checkRunCollector = new CheckRunCollector();

  @Override
  public String getIconFileName() {
    return null;
  }

  @Override
  public String getDisplayName() {
    return null;
  }

  @Override
  public String getUrlName() {
    return "gerrit";
  }

  @GET
  @WebMethod(name = "check-runs")
  public JsonHttpResponse getCheckRuns(
      @QueryParameter(required = true) int change, @QueryParameter(required = true) int patchset) {
    return new JsonHttpResponse(
        JSONObject.fromObject(checkRunCollector.collectFor(change, patchset)), 200);
  }
}
