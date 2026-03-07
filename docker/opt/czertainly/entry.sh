#!/bin/sh

czertainlyHome="/opt/czertainly"
source ${czertainlyHome}/static-functions

log "INFO" "Launching the Provisioning RabbitMQ"

log "INFO" "Launching the Core"
java $JAVA_OPTS -jar ./app.jar

#exec "$@"
