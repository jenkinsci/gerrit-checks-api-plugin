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
