// Copyright (C) 2022 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.jenkins.plugins.gerritchecksapi.rest;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import hudson.Extension;
import hudson.model.RootAction;
import io.jenkins.plugins.gerritchecksapi.CheckRunCollector;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.json.JsonHttpResponse;
import org.kohsuke.stapler.verb.GET;

@Extension
public class GerritChecksRestApi implements RootAction {
  private final Injector injector;
  private final CheckRunCollector checkRunCollector;

  public GerritChecksRestApi() {
    injector = Guice.createInjector(Stage.PRODUCTION, new PluginGuiceModule());
    checkRunCollector = injector.getInstance(CheckRunCollector.class);
  }

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
    return "gerrit-checks";
  }

  @GET
  @WebMethod(name = "runs")
  public JsonHttpResponse getCheckRuns(
      @QueryParameter(required = true) int change, @QueryParameter(required = true) int patchset) {
    return new JsonHttpResponse(
        JSONObject.fromObject(checkRunCollector.collectFor(change, patchset)), 200);
  }
}
