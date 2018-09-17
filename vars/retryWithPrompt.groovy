#!/usr/bin/groovy
import static java.util.UUID.randomUUID

def call(Map config = [:], body) {

    // default timeout to 900 minutes.
    def timeOut = config.get('timeOut') ?: 900
    def stepName = config.get('stepName') ?: '?'

    waitUntil {
        try {
            body()
            return true
        }
        catch(e) {
            println "Error: ${e}"
            println "StackTrace: ${e.getStackTrace()}"
            def actionMessage = "One of the steps (${stepName}) failed. Please Retry(the failed step), Ignore(and continue on with the next step), or Abort the job"
            def userInput
            timeout(timeOut) {
                userInput = input(id: "userInput-${randomUUID() as String}", message: actionMessage, parameters: [[$class: 'ChoiceParameterDefinition', choices: "Retry\nIgnore\n", description: 'Select...', name: 'Select...']])
            }
            echo "User Selected: " + userInput.toString()
            return userInput == 'Ignore'
        }

    }
}