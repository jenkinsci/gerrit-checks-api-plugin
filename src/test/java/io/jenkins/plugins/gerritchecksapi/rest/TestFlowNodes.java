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

import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;

/**
 * Builds the flow nodes of a Pipeline graph for tests, without needing a running Jenkins or the CPS
 * engine. The nodes are not wired to each other through their parents; the walk of the graph is
 * stubbed on the {@link FlowExecution} instead.
 */
final class TestFlowNodes {

  private TestFlowNodes() {}

  /** A stage, i.e. a block carrying the label of a {@code stage} step. */
  static BlockStartNode stage(FlowExecution execution, String id, String name) {
    BlockStartNode node = block(execution, id);
    node.addAction(new LabelAction(name));
    return node;
  }

  /** A block that is not a stage, e.g. a {@code node} or {@code withEnv} block. */
  static BlockStartNode block(FlowExecution execution, String id) {
    return new TestBlockStart(execution, id);
  }

  /** The end of the given block. Blocks without an end are still running. */
  static BlockEndNode<?> end(FlowExecution execution, String id, BlockStartNode start) {
    return new TestBlockEnd(execution, id, start);
  }

  /** Marks the block as the branch of a parallel step, named after the branch. */
  static ThreadNameAction threadName(String name) {
    return new TestThreadNameAction(name);
  }

  private static final class TestThreadNameAction implements ThreadNameAction {
    private final String name;

    private TestThreadNameAction(String name) {
      this.name = name;
    }

    @Override
    public String getThreadName() {
      return name;
    }

    @Override
    public String getIconFileName() {
      return null;
    }

    @Override
    public String getDisplayName() {
      return name;
    }

    @Override
    public String getUrlName() {
      return null;
    }
  }

  private static final class TestBlockStart extends BlockStartNode {
    private TestBlockStart(FlowExecution execution, String id) {
      super(execution, id);
    }

    @Override
    protected String getTypeDisplayName() {
      return "test block";
    }
  }

  private static final class TestBlockEnd extends BlockEndNode<BlockStartNode> {
    private TestBlockEnd(FlowExecution execution, String id, BlockStartNode start) {
      super(execution, id, start);
    }

    @Override
    protected String getTypeDisplayName() {
      return "test block end";
    }
  }
}
