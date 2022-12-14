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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import io.jenkins.plugins.gerritchecksapi.Caching;
import io.jenkins.plugins.gerritchecksapi.CachingCheckRunCollector;
import io.jenkins.plugins.gerritchecksapi.CheckRunCollector;
import io.jenkins.plugins.gerritchecksapi.Direct;
import io.jenkins.plugins.gerritchecksapi.DirectCheckRunCollector;
import io.jenkins.plugins.gerritchecksapi.PermissionAwareCheckRunCollector;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.lucene.search.databackend.SearchBackendManager;

public class PluginGuiceModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(CheckRunCollector.class).to(PermissionAwareCheckRunCollector.class);
    bind(CheckRunCollector.class).annotatedWith(Caching.class).to(CachingCheckRunCollector.class);
    bind(CheckRunCollector.class).annotatedWith(Direct.class).to(DirectCheckRunCollector.class);
    bind(GerritTriggerCheckRunFactory.class);
    bind(GerritMultiBranchCheckRunFactory.class);
  }

  @Provides
  Jenkins getJenkins() {
    return Jenkins.get();
  }

  @Provides
  SearchBackendManager getSearchBackendManager(Jenkins jenkins) {
    return jenkins.getExtensionList(SearchBackendManager.class).get(0);
  }
}
