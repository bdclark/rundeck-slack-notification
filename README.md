# Rundeck SlackAttachment Plugin

Use this [notification plugin][1] to post messages to Slack via
[incoming webhook][2] using the Slack attachments format.

## Installation

Copy `SlackAttachment.groovy` to the Rundeck plugins directory `$RDECK_BASE/libext/`.

## Configuration

#### Required Properties
This following properties are required, and can be set at the framework or project
level:
* `webhookUrl` - the incoming webhook URL configured in Slack custom integrations.

#### Optional Properties
The following properties are optional, and can be set at the framework or project
level. They can also be overridden at the instance-level for each job:
* `channel` - channel(s) to post messages to. If not specified, will send to default
  channel configured for the incoming webhook on slack.com. Accepts a comma-delimited
  list of multiple channels and/or users (e.g. `#general` or `#room1,#room2,@user1`).
* `title` - default title for message. Accepts the following tokens that will be
  substituted at runtime:
  * `$STATUS` - job status (all caps)
  * `$Status` - job status (mixed-case)
  * `$status` - job status (lowercase)
  * `$project` - project name
  * `$job` - job name
  * `$group` - group name of job
  * `$job_full` full name of group/job
  * `$user` - user executing job
  * `$id` - execution id

The default title format is `$Status [$project] $job_full run by $user (#$id)`.

#### Configuration Examples
Configure the webhook URL with framework scope by adding an entry in
`$RDECK_BASE/etc/framework.properties`:
```
framework.plugin.Notification.SlackAttachment.webhookUrl=https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX
```
Configure the webhook URL with project scope by adding an entry to
`$RDECK_BASE/projects/[ProjectName]/etc/project.properties`:
```
project.plugin.Notification.SlackAttachment.webhookUrl=https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX
```

[1]: http://rundeck.org/docs/developer/notification-plugin-development.html
[2]: https://api.slack.com/incoming-webhooks
