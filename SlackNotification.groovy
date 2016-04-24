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
def expandString(text, binding) {
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
    // replace any specified job options in ${option.name} format
    text.replaceAll(/\$\{option\.(\S+?\})/) { all, match ->
        binding.execution.context.option[match] ?: all
    }
    text.replaceAll(/(\$\w+)/) { all, match ->
        tokens[match] ?: all
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
 * Send Slack message.
 * @param url the incoming webhook url
 * @param message the message map to send
 */
def sendMessage(String url, Map message) {
    def content = JsonOutput.toJson(message)
    def response = post(url, content)
    if (! "ok".equals(response)) {
        System.err.println("ERROR: SlackAttachment plugin response: ${response}, url: ${url}, request: ${content}")
    }
}

/**
 * Trigger Slack message(s).
 * @param execution the execution data map
 * @param config the plugin configuration data map
 * @param color the color for the message
 */
def triggerMessage(Map execution, Map config, String defaultColor) {
    def expandedTitle = expandString(config.title, [execution: execution])
    def expandedText = expandString(config.additionalText, [execution: execution])
    def attachment = [
        fallback: expandedTitle + ' - ' + execution.href,
        title: expandedTitle,
        title_link: execution.href,
        text: expandedText,
        color: config.color ?: defaultColor,
        fields: []
    ]
    for (opt in config.optionFields.tokenize(', ')) {
        if (execution.context.option[opt]) {
            attachment.fields << [
                title: opt,
                value: execution.context.option[opt],
                short: true
            ]
        }
    }
    if (execution.failedNodeList && config.includeFailedNodes) {
        attachment.fields << [
          title: "Failed nodes",
          value: execution.failedNodeListString
        ]
    }

    def message = [
        username: config.username,
        icon_emoji: config.iconEmoji,
        attachments: [attachment]
    ]

    if (config.channel) {
        for (channel in config.channel.tokenize(', ')) {
            message.channel = channel
            sendMessage(config.webhookUrl, message)
        }
    } else {
        sendMessage(config.webhookUrl, message)
    }
}

rundeckPlugin(NotificationPlugin) {
  title = 'Slack'
  description = 'send webhook to slack'
  configuration {
      webhookUrl title: 'Webhook URL', description: 'Slack incoming webhook URL', scope: 'Project'

      iconEmoji title: 'Icon emoji', description: 'Override default bot icon', scope: 'Project'

      username title: 'Username', description: 'Override default bot username', scope: 'Project'

      channel title: 'Channel', description: 'Slack channel/user (comma-delimited for multiple)',
          scope: 'Instance'

      title title: 'Title', required: true, scope: 'Instance', defaultValue: defaultTitle,
          description: 'Message title. Can contain $Status, $STATUS, $status (job status), \
          $project (project name), $job (job name), $group (group name), \
          $job_full (job group/name), $user (user name), $id (execution id), \
          ${option.name} (any job option)'

      additionalText title: 'Additional Text', scope: 'InstanceOnly',
          description: 'Additional message text below title. Can contain $Status, \
          $STATUS, $status (job status), $project (project name), $job (job name), \
          $group (group name), $job_full (job group/name), $user (user name), \
          $id (execution id), ${option.name} (any job option)'

      optionFields title: 'Option fields', description: 'Comma-delimited list of job \
          options to include as fields in message (caution: can expose secure options)',
          scope: 'InstanceOnly'

      includeFailedNodes title: 'Include failed nodes field', type: 'Boolean',
          defaultValue: false, scope: 'Instance'

      color title: 'Color', description: 'Override default message color', scope: 'InstanceOnly'
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
