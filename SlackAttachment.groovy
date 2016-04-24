import com.dtolabs.rundeck.plugins.notification.NotificationPlugin
import com.dtolabs.rundeck.core.plugins.configuration.StringRenderingConstants
import com.dtolabs.rundeck.core.plugins.configuration.ValidationException
import groovy.json.JsonOutput

/**
 * define the default title
 */
def defaultTitle = '$Status [$project] $job_full run by $user (#$id)'

/**
 * Expands the title string using a predefined set of tokens
 */
def titleString(text, binding) {
    // make status more friendly
    def status = binding.execution.status
    if (binding.execution.status == 'running') {
        status = 'started'
    } else if (binding.execution.abortedby) {
        status = 'killed'
    }
    // defines the tokens usable in the title configuration property
    def tokens = [
        '$STATUS': status.toUpperCase(),
        '$Status': status.capitalize(),
        '$status': status.toLowerCase(),
        '$project': binding.execution.project,
        '$job': binding.execution.job.name,
        '$group': binding.execution.job.group,
        '$job_full': (binding.execution.job.group ? binding.execution.job.group + '/' : '') + binding.execution.job.name,
        '$user': binding.execution.user,
        '$id': binding.execution.id.toString()
    ]
    text.replaceAll(/(\$\w+)/) {
        if (tokens[it[1]]) {
            tokens[it[1]]
        } else {
            it[0]
        }
    }
}

/**
 * Post HTTP(S) content
 * @param url the url to POST to
 * @param postContent data to post
 */
def post(String url, String postContent) {
    def connection = url.toURL().openConnection()
    connection.addRequestProperty("Content-Type", "application/json")
    connection.setRequestMethod("POST")
    connection.doOutput = true
    connection.outputStream.withWriter{
        it.write(postContent)
        it.flush()
    }
    connection.connect()

    try {
        connection.content.text
    } catch (IOException e) {
        try {
            ((HttpURLConnection)connection).errorStream.text
        } catch (Exception ignored) {
            throw e
        }
    }
}

/**
 * Send Slack attachment.
 * @param url the incoming webhook url
 * @param attachment the attachment map to send
 * @param channel override default channel
 */
def sendAttachment(String url, Map attachment, String channel = '') {
    def content = JsonOutput.toJson([channel: channel, attachments: [attachment]])
    def response = post(url, content)
    if (! "ok".equals(response)) {
        System.err.println("ERROR: SlackAttachment plugin response: ${response}, url: ${config.webhookUrl}, request: ${content}")
    }
}

/**
 * Trigger Slack message(s).
 * @param execution the execution data map
 * @param config the plugin configuration data map
 * @param color the color for the message
 */
def triggerMessage(Map execution, Map config, String defaultColor) {
    def expandedTitle = titleString(config.title, [execution: execution])
    def attachment = [
        fallback: expandedTitle + ' - ' + execution.href,
        title: expandedTitle,
        title_link: execution.href,
        color: config.color ?: defaultColor,
        fields: []
    ]
    if (execution.failedNodeList && config.includeFailedNodes) {
        attachment.fields << [
          title: "Failed nodes",
          value: execution.failedNodeListString
        ]
    }

    if (config.channel) {
        for (channel in config.channel.tokenize(', ')) {
            sendAttachment(config.webhookUrl, attachment, channel)
        }
    } else {
        sendAttachment(config.webhookUrl, attachment)
    }
}

rundeckPlugin(NotificationPlugin) {
  title = 'SlackAttachment'
  description = 'Send message to Slack'
  configuration {
      webhookUrl title: 'Webhook URL', description: 'Slack incoming webhook URL', scope: 'Project'

      channel title: 'Channel', description: 'Slack channel', scope: 'Instance'

      title title: 'Title', required: true, scope: 'Instance',
          defaultValue: defaultTitle,
          description: 'Message title. Can contain $Status, $STATUS, $status (job status), \
          $project (project name), $job (job name), $group (group name), \
          $job_full (job group/name), $user (user name), $id (execution id)'

      color title: 'Color', description: 'Override default message color'

      includeFailedNodes title: 'Include failed nodes field', type: 'Boolean', defaultValue: false, scope: 'Instance'
  }
  onstart { Map executionData, Map configuration ->
      triggerMessage(executionData, configuration, 'warning')
      true
  }
  onfailure { Map executionData, Map configuration ->
      triggerMessage(executionData, configuration, 'danger')
      true
  }
  onsuccess { Map executionData, Map configuration ->
      triggerMessage(executionData, configuration, 'good')
      true
  }
}
