#!/usr/bin/groovy

def call(String yamlName, boolean dryRun) {
    sshDeploy(yamlName, dryRun)
}

def call(yaml, boolean dryRun) {
    if(!yaml.config)
        error "config missing in the given yml file."
    if(!yaml.config.credentials_id)
        error "config->credentials_id is missing."

    def failedRemotes = []
    def retriedRemotes = []

    withCredentials([usernamePassword(credentialsId: yaml.config.credentials_id, passwordVariable: 'password', usernameVariable: 'userName')]) {

        if(!userName && params.SSH_USER) {
            error "userName is null or empty, please check credentials_id."
        }

        if(!password && params.PASSWORD) {
            error "password is null or empty, please check credentials_id."
        }

        yaml.steps.each { stageName, step ->
            step.each {
                def remoteGroups = [:]
                def allRemotes = []

                it.remote_groups.each {
                    if(!yaml.remote_groups."$it") {
                        error "remotes groups are empty/invalid for the given stage: ${stageName}, command group: ${it}. Please check yml."
                    }
                    remoteGroups[it] = yaml.remote_groups."$it"
                }

                // Merge all the commands for the given group
                def commandGroups = [:]
                it.command_groups.each {
                    if(!yaml.command_groups."$it") {
                        error "command groups are empty/invalid for the given stage: ${stageName}, command group: ${it}. Please check yml."
                    }
                    commandGroups[it] = yaml.command_groups."$it"
                }

                def isSudo = false
                // Append user and identity for all the remotes.
                remoteGroups.each { remoteGroupName, remotes ->
                    allRemotes += remotes.collect { remote ->
                        if(!remote.host) {
                            throw IllegalArgumentException("host missing for one of the nodes in ${remoteGroupName}")
                        }
                        if(!remote.name)
                            remote.name = remote.host

                        if(params.SSH_USER) {
                            remote.user = params.SSH_USER
                            remote.password = params.PASSWORD
                            isSudo = true
                        } else {
                            remote.user = userName
                            remote.password = password
                        }

                        // For now we are settings host checking off.
                        remote.allowAnyHosts = true

                        remote.groupName = remoteGroupName
                        if(yaml.gateway) {
                            def gateway = [:]
                            gateway.name = yaml.gateway.name
                            gateway.host = yaml.gateway.host
                            gateway.allowAnyHosts = true

                            if(params.SSH_USER) {
                                gateway.user = params.SSH_USER
                                gateway.password = params.PASSWORD
                            } else {
                                gateway.user = userName
                                gateway.password = password
                            }

                            remote.gateway = gateway
                        }
                        remote
                    }
                }

                // Execute in parallel.
                if(allRemotes) {
                    if(allRemotes.size() > 1) {
                        def stepsForParallel = allRemotes.collectEntries { remote ->
                            ["${remote.groupName}-${remote.name}" : transformIntoStep(dryRun, stageName, remote.groupName, remote, commandGroups, isSudo, yaml.config, failedRemotes, retriedRemotes)]
                        }
                        stage(stageName + " \u2609 Size: ${allRemotes.size()}") {
                            parallel stepsForParallel
                        }
                    } else {
                        def remote = allRemotes.first()
                        stage(stageName + "\n" + remote.groupName + "-" + remote.name) {
                            transformIntoStep(dryRun, stageName, remote.groupName, remote, commandGroups, isSudo, yaml.config, failedRemotes, retriedRemotes).call()
                        }
                    }
                }
            }
        }
    }
    return [failedRemotes, retriedRemotes]
}

private transformIntoStep(dryRun, stageName, remoteGroupName, remote, commandGroups, isSudo, config, failedRemotes, retriedRemotes) {
    return {
        def finalRetryResult = true
        commandGroups.each { commandGroupName, commands ->
            echo "Running ${commandGroupName} group of commands."
            commands.each { command ->
                command.each { commandName, commandList ->
                    commandList.each {
                        validateCommands(stageName, remoteGroupName, commandGroupName, commandName, it)
                        if(!dryRun) {
                            def stepName = "${stageName} -> ${remoteGroupName.replace("_", " -> ")} -> ${commandGroupName} -> ${remote.host}"
                            if (config.retry_with_prompt) {
                                retryWithPrompt([stepName: stepName]) {
                                    executeCommands(remote, stageName, remoteGroupName, commandGroupName, commandName, it, isSudo)
                                }
                            } else if(config.retry_and_return) {
                                def retryCount = config.retry_count ? config.retry_count.toInteger() : 2
                                def (isSuccessful, failedAtleastOnce) = retryAndReturn([retryCount: retryCount]) {
                                    executeCommands(remote, stageName, remoteGroupName, commandGroupName, commandName, it, isSudo)
                                }
                                if(!isSuccessful) {
                                    finalRetryResult = false
                                    if(!(stepName in failedRemotes)) {
                                        failedRemotes.add(stepName)
                                    }
                                } else if(failedAtleastOnce) {
                                    if(!(stepName in retriedRemotes)) {
                                        retriedRemotes.add(stepName)
                                    }
                                }
                            } else {
                                executeCommands(remote, stageName, remoteGroupName, commandGroupName, commandName, it, isSudo)
                            }
                        } else {
                            echo "DryRun Mode: Running ${commandName}."
                            echo "Remote: ${remote}"
                            echo "Command: ${it}"
                        }
                    }
                }
            }
        }
    }
}

private validateCommands(stageName, remoteGroupName, commandGroupName, commandName, command) {
    if(commandName in ["gets", "puts"]) {
        if(!command.from)
            error "${stageName} -> ${remoteGroupName} -> ${commandGroupName} -> ${commandName} -> from is empty or null."
        if(!command.into)
            error "${stageName} -> ${remoteGroupName} -> ${commandGroupName} -> ${commandName} -> into is empty or null."
    }
}

private executeCommands(remote, stageName, remoteGroupName, commandGroupName, commandName, command, isSudo) {
    switch (commandName) {
        case "commands":
            sshCommand remote: remote, command: command, sudo: isSudo
            break
        case "scripts":
            sshScript remote: remote, script: command
            break
        case "gets":
            sshGet remote: remote, from: command.from, into: command.into, override: command.override
            break
        case "puts":
            sshPut remote: remote, from: command.from, into: command.into
            break
        case "removes":
            sshRemove remote: remote, path: command
            break
        default:
            error "Invalid Command: ${stageName} -> ${remoteGroupName} -> ${commandGroupName} -> ${commandName}"
            break
    }
}
