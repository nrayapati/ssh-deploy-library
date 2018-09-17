#!/usr/bin/groovy
def call(Map config = [:], body) {
    def retryCount = config.get('retryCount') ?: 2
    boolean failedAtleastOnce = false
    boolean isSuccessful = false
    int retries = 0
    while(!isSuccessful && retries++ < retryCount) {
        try {
            body()
            isSuccessful = true
        } catch(e) {
            failedAtleastOnce = true
            println "Error: ${e}"
        }
    }
    [isSuccessful, failedAtleastOnce]
}