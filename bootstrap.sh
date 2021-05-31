# start clam service itself and the updater in background as daemon
freshclam -d &
clamd &

# start Worker
java -Djava.security.egd=file:/dev/./urandom -jar /francetransfert-worker-api.jar
