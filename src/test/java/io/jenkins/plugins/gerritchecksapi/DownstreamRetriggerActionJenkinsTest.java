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

package io.jenkins.plugins.gerritchecksapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import hudson.model.FreeStyleProject;
import hudson.model.Run;
import io.jenkins.plugins.gerritchecksapi.rest.GerritTriggerRerunAction;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/** Posts to the rerun URL that is advertised in the check run, like Gerrit does. */
public class DownstreamRetriggerActionJenkinsTest {

  @Rule public JenkinsRule j = new JenkinsRule();

  @Test
  public void rerunUrl_schedulesANewBuild() throws Exception {
    // No crumb handling for the plain POST below
    j.jenkins.setCrumbIssuer(null);

    FreeStyleProject project = j.createFreeStyleProject("rerun-job");
    Run<?, ?> run = j.buildAndAssertSuccess(project);
    assertNotNull(
        "Run has no rerun action to post to",
        run.getAction(DownstreamRetriggerAction.class));
    assertEquals(1, project.getBuilds().size());

    // Exactly the URL that is handed out to Gerrit in the check run
    String path = run.getUrl() + GerritTriggerRerunAction.PATH;
    assertEquals("Rerun URL is not routed: " + path, 302, post(path));

    j.waitUntilNoActivity();
    assertEquals("Rerun did not schedule a new build", 2, project.getBuilds().size());
    j.assertBuildStatusSuccess(project.getBuildByNumber(2));
  }

  private int post(String path) throws IOException {
    HttpURLConnection connection =
        (HttpURLConnection) new URL(j.getURL(), path).openConnection();
    connection.setRequestMethod("POST");
    connection.setInstanceFollowRedirects(false);
    connection.setDoOutput(true);
    connection.setFixedLengthStreamingMode(0);
    try {
      return connection.getResponseCode();
    } finally {
      connection.disconnect();
    }
  }
}
