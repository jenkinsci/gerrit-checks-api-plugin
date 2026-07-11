// Copyright (C) 2026 Amarula Solutions
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

import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Job;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

@SuppressWarnings("rawtypes")
public class DownstreamRetriggerAction implements Action {
  private static final Logger LOG =
      Logger.getLogger(DownstreamRetriggerAction.class.getName());

  private final transient Run<?, ?> run;

  public DownstreamRetriggerAction(Run<?, ?> run) {
    this.run = run;
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
    return "gerrit-trigger-retrigger-this";
  }

  @RequirePOST
  @SuppressWarnings("unchecked")
  public void doIndex(StaplerRequest req, StaplerResponse rsp) throws Exception {
    Jenkins jenkins = Jenkins.get();
    try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
      Job<?, ?> job = run.getParent();
      if (job == null || !(job instanceof Queue.Task)) {
        rsp.sendError(400, "Job is not buildable");
        return;
      }

      if (!job.isBuildable()) {
        rsp.sendError(409, "Job is disabled");
        return;
      }

      ParametersAction paramsAction = run.getAction(ParametersAction.class);
      if (paramsAction != null) {
        jenkins.getQueue().schedule2(
            (Queue.Task) job, 0,
            new CauseAction(
                new Cause.RemoteCause(
                    "gerrit", "downstream checks-api rerun")),
            paramsAction);
      } else {
        jenkins.getQueue().schedule2(
            (Queue.Task) job, 0,
            new CauseAction(
                new Cause.RemoteCause(
                    "gerrit", "downstream checks-api rerun")));
      }

      rsp.sendRedirect2(job.getAbsoluteUrl());
    } catch (Exception e) {
      LOG.log(Level.WARNING,
          "Failed to schedule rerun for " + run.getExternalizableId(), e);
      rsp.sendError(500, "Failed to schedule rerun");
    }
  }
}
