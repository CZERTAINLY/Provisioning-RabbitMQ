#!/bin/sh

czertainlyHome="/opt/czertainly"
source ${czertainlyHome}/static-functions

log "INFO" "Launching the Provisioning RabbitMQ"

exec java $JAVA_OPTS -jar ./app.jar

#exec "$@"
