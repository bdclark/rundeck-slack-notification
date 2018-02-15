# Rundeck SlackNotification Plugin

Use this [notification plugin][1] to post messages to Slack via
[incoming webhook][2] using the Slack attachments format.

## Installation

Copy `SlackNotification.groovy` to the Rundeck plugins directory `$RDECK_BASE/libext/`.

## Configuration

#### Framework/Project Properties
This following properties can be set at the framework or project level:
* `webhookUrl` - (required) the incoming webhook URL configured in Slack custom integrations.
* `iconEmoji` - override default bot emoji
* `username` - override default bot username
* `proxyHost` - (optional) egress proxy host if outbound requests need to be proxied.
* `proxyPort` - (required if `proxyHost` is set) egress proxy port.

#### Additional Properties
The following properties are optional, and can be set at the framework/project
level, as well as the instance-level for each job:
* `channel` - channel(s) to post messages to. If not specified, will send to default
  channel configured for the incoming webhook on slack.com. Accepts a comma-delimited
  list of multiple channels and/or users (e.g. `#general` or `#room1,#room2,@user1`).
* `title` - default title for message. Accepts `${job.*}` and `${option.*}` context
  variables, in addition to the following custom tokens that will be substituted
  at runtime:
  * `${job.STATUS}` - job status (all caps)
  * `${job.Status}` - job status (capitalized)
  * `${job.fullName}` - job's full name in `group/job` format

The default title format is `${job.Status} [${job.project}] ${job.fullName} run by ${job.username} (#${job.execid})`.

#### Configuration Examples
Configure the webhook URL with framework scope by adding an entry in
`$RDECK_BASE/etc/framework.properties`:
```
framework.plugin.Notification.SlackNotification.webhookUrl=https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX
```
Configure the webhook URL with project scope by adding an entry to
`$RDECK_BASE/projects/[ProjectName]/etc/project.properties`:
```
project.plugin.Notification.SlackNotification.webhookUrl=https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX
# and maybe set a project-specific channel
project.plugin.Notification.SlackNotification.channel=\#myproject
```

[1]: http://rundeck.org/docs/developer/notification-plugin-development.html
[2]: https://api.slack.com/incoming-webhooks
