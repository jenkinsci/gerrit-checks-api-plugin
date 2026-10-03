# gerrit-checks-api

## Introduction

This plugin adds a REST API endpoint that allows to query for Runs working
on a given Patchset in Gerrit. The REST API returns a list of CheckRun objects
that are meant to be used by a plugin implementing the Checks-API in Gerrit to
fill checks-related information in the Gerrit UI.

## Getting started

This plugin depends on the Lucene index provided by the
[lucene-search](https://plugins.jenkins.io/lucene-search) plugin. If the index
was not already built, this has to be done before the REST API can be used. The
process of how to build the index can be found
[here](https://plugins.jenkins.io/lucene-search/#plugin-content-database-rebuild).
**If Lucene Search is used only for gerrit jenkins connection disable console log indexing
in the Lucene Search plugin.**

## Using the REST-API

### Query Runs

Get all Runs for a given patchset. The response follows the Checks-API
objects defined in Gerrit as closely as possible.

Request:

```
GET /gerrit-checks/runs?change=$CHANGE_NUMBER&patchset=$PATCHSET_NUMBER
```

Response:

```js
{
    // List of all Runs found for the queried patchset
    "runs": [
        {
            // Timestamp at which the Run was scheduled
            "scheduledTimestamp": "2022-11-07T13:04:12.609Z",
            // Description of the Jenkins job
            "checkDescription": "",
            // Change number (same as provided in request parameter)
            "change": 101,
            // For now the same as `checkLink`
            "statusLink": "https://example.com/jenkins/job/gerrit-trigger/5/",
            // ID of the Run used by Jenkins. For runs directly triggered by
            // Gerrit, this is the Jenkins externalizable ID (jobName#buildNumber).
            // For downstream builds, this is a JSON object encoding the
            // parent→child relationship (see "Downstream Build Discovery" below).
            "externalId": "gerrit-trigger#5",
            // Timestamp at which the Run finished. `null` if the Run hasn't
            // finished yet
            "finishedTimestamp": "2022-11-07T13:04:13.051Z",
            // Name of the job
            "checkName": "gerrit-trigger",
            // Attempt-number of this check for the given patchset
            "attempt": 1,
            // Patchset number (same as provided in request parameter)
            "patchSet": 4,
            // Timestamp at which this Run started
            "startedTimestamp": "2022-11-07T13:04:12.626Z",
            // Single line summary of the build status
            "statusDescription": "stable",
            // URL to the Run
            "checkLink": "https://example.com/jenkins/job/gerrit-trigger/5/",
            // NOT IMPLEMENTED YET. Which label does this Run vote on
            "labelName": "",
            // A list of actions the client can trigger for this Run
            "actions": [
                // Rerun action
                {
                    // Whether to show the action below the commit message
                    "summary": false,
                    // Which data has to be sent with the request
                    "data": null,
                    // Which method has to be used with the request
                    "method": "POST",
                    // Name of the action
                    "name": "Rerun",
                    // Tooltip for the action
                    "tooltip": "Run the build for the patchset again.",
                    // Whether the action is disabled
                    "disabled": false,
                    // URL to be used for the action
                    "url": "https://example.com/jenkins/job/gerrit-trigger/5/gerrit-trigger-retrigger-this",
                    // Whether to show this action prominently as a button
                    "primary": true
                }
            ],
            // List of the Run's results
            "results": [
                {
                    // NOT IMPLEMENTED YET. Summary of the result
                    "summary": "",
                    // Same as th Run's external ID
                    "externalId": "gerrit-trigger#5",
                    // List of links for this Run
                    "links": [
                        // Link to the logs of the Run
                        {
                            // Which icon to use (https://gerrit.googlesource.com/gerrit/+/master/polygerrit-ui/app/api/checks.ts#505)
                            "icon": "CODE",
                            // Tooltip for the link
                            "tooltip": "Build log.",
                            // The link URL
                            "url": "https://example.com/jenkins/job/gerrit-trigger/5/console",
                            // Whether the link will be shown directly in the table
                            "primary": true
                        }
                    ],
                    // The result category of the Run (https://gerrit.googlesource.com/gerrit/+/master/polygerrit-ui/app/api/checks.ts#464)
                    "category": "SUCCESS",
                    // NOT IMPLEMENTED YET.
                    "codePointers": [],
                    // NOT IMPLEMENTED YET.
                    "message": "",
                    // NOT IMPLEMENTED YET.
                    "actions": [],
                    // NOT IMPLEMENTED YET.
                    "fixes": [],
                    // NOT IMPLEMENTED YET.
                    "tags": []
                }
            ],
            // Status of the build (https://gerrit.googlesource.com/gerrit/+/master/polygerrit-ui/app/api/checks.ts#322)
            "status": "COMPLETED"
        }
    ],
}
```

## Pipeline Stages

Every stage of a Pipeline build is reported as a `CheckRun` of its own, nested
below the `CheckRun` of the build. Gerrit lists the stages below the build, so
a stage that failed, was unstable or was skipped is visible without opening
Jenkins. Runs that are not Pipeline builds are reported as a single `CheckRun`,
as before.

A stage encodes the nesting in its `externalId`, the same way a downstream
build does. The `run` part is the externalizable ID of the Run followed by the
ID of the stage's flow node:

```js
{
    "change": 101,
    "patchSet": 4,
    "attempt": 1,
    "externalId": "{\"parent\":\"my-pipeline#7\",\"run\":\"my-pipeline#7#12\"}",
    // Name of the stage, numbered if stages share a name
    "checkName": "Test",
    "checkLink": "https://example.com/jenkins/job/my-pipeline/7/stages/?selected-node=12",
    "status": "COMPLETED",
    "statusDescription": "broken since this build",
    "statusLink": "https://example.com/jenkins/job/my-pipeline/7/",
    // Stages are not rerun on their own, the Run carries the actions
    "actions": [],
    "scheduledTimestamp": "2022-11-07T13:04:12.609Z",
    "startedTimestamp": "2022-11-07T13:04:12.626Z",
    "finishedTimestamp": "2022-11-07T13:04:13.051Z",
    "results": [
        {
            // A completed run without results would count as passing
            "externalId": "my-pipeline#7#12",
            "category": "ERROR",
            "message": "script returned exit code 1",
            "links": [
                {
                    "icon": "CODE",
                    "tooltip": "Build log.",
                    "url": "https://example.com/jenkins/job/my-pipeline/7/console",
                    "primary": true
                }
            ]
        }
    ]
}
```

A stage is reported as `SUCCESS`, as `WARNING` if it was unstable, as `ERROR`
if it failed and as `INFO` while it is still running. Stages that were skipped,
because a `when` condition was not met or because an earlier stage failed, are
not reported at all.

Gerrit identifies a check run by its name, change, patchset and attempt, so
stages that share a name have to be told apart. Stages of a parallel branch are
named after their branch (`linux / Test`), and stages that share a name even
then, e.g. because they run in a loop, are numbered (`Test`, `Test (2)`). Only
the second and later occurrences are numbered, so the name of a stage does not
change once it has been reported.

The `checkLink` of a stage points at its entry in the Pipeline stage view
(`.../stages/?selected-node=<nodeId>`). That requires the
[pipeline-graph-view](https://plugins.jenkins.io/pipeline-graph-view) plugin.
Without it, the link points at the build.

## Configuration

Which stages are reported can be configured globally, for a folder and for a
single job. A folder is where a multibranch Pipeline is configured, since its
branch jobs cannot be configured individually. Every setting can be left to be
inherited, in which case the enclosing folder decides and finally the global
configuration.

The global defaults are found under *Manage Jenkins » System*, the settings of a
job under *Properties* on its configuration page and the settings of a folder or
multibranch project on its own configuration page.

| Setting | Meaning |
| --- | --- |
| Report stages | Whether the stages are reported as check runs at all. Turning this off reports the build as a single check run, as it was before. |
| Stage depth | `All stages` reports every stage. `Top level stages only` leaves out the stages nested inside another stage, and `Up to a maximum depth` reports the stages up to the given depth. A build with tens of nested stages is difficult to read in Gerrit. |
| Skip the stages Jenkins adds to a declarative pipeline | Leaves out the stages that Jenkins itself adds, e.g. `Declarative: Checkout SCM` and `Declarative: Post Actions`. |

An error of a stage that is left out is reported on the stage enclosing it, so a
nested stage that fails still turns its parent stage red.

## Downstream Build Discovery

When a build directly triggered by Gerrit causes downstream builds (via
`Cause.UpstreamCause`), those downstream builds are also discovered and returned
as `CheckRun` entries. This allows the Gerrit Checks UI to display the full
pipeline graph for a patchset.

### How it works

1. The plugin finds directly Gerrit-triggered builds via the lucene-search index
   (same as before).
2. For each direct build, it scans all Jenkins jobs for builds that have a
   `Cause.UpstreamCause` pointing to it.
3. This continues recursively through the entire downstream chain.
4. Each downstream `CheckRun` encodes the parent→child connection in its
   `externalId` as a JSON object:

```json
{"parent": "trigger-job#5", "run": "downstream-job#3"}
```

### Example downstream CheckRun response

```js
{
    "scheduledTimestamp": "2022-11-07T13:04:14.109Z",
    "checkDescription": "description of downstream-job",
    "change": 101,
    "statusLink": "https://example.com/jenkins/job/downstream-job/3/",
    "externalId": "{\"parent\":\"trigger-job#5\",\"run\":\"downstream-job#3\"}",
    "finishedTimestamp": "2022-11-07T13:04:15.551Z",
    "checkName": "downstream-job",
    "attempt": 1,
    "patchSet": 4,
    "startedTimestamp": "2022-11-07T13:04:14.626Z",
    "statusDescription": "stable",
    "checkLink": "https://example.com/jenkins/job/downstream-job/3/",
    "labelName": "",
    // Downstream runs have no rerun action — the trigger chain cannot be
    // reconstructed through Jenkins' rerun mechanism.
    "actions": [],
    "results": [
        {
            "summary": "",
            "externalId": "{\"parent\":\"trigger-job#5\",\"run\":\"downstream-job#3\"}",
            "links": [
                {
                    "icon": "CODE",
                    "tooltip": "Build log.",
                    "url": "https://example.com/jenkins/job/downstream-job/3/console",
                    "primary": true
                }
            ],
            "category": "SUCCESS",
            "codePointers": [],
            "message": "",
            "actions": [],
            "fixes": [],
            "tags": []
        }
    ],
    "status": "COMPLETED"
}
```

### Duplicate avoidance

If a run is found both by the lucene index (direct) **and** as a downstream child
of another run, its `externalId` is updated in-place to the JSON format rather
than creating a duplicate entry.

### Limitations

- Only the most recent 100 builds per job are scanned for downstream
  relationships.
- Traversal depth is capped at 10 levels.
- Downstream builds have no rerun action, since the trigger chain cannot be
  reconstructed through Jenkins' rerun mechanism.

## Contributing

Refer to our [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md)

## LICENSE

Licensed under Apache 2.0, see [LICENSE](LICENSE.md)
