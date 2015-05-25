#!/bin/sh

# Waits a certain amount of time (for things to start up, presumably) and
# then runs integration tests, outputting to test/integration/test-results.xml.
#
# Why? Because this is the easiest way to get a Travis system up and running:
# "./run --sh travis/wait-and-test-integration.sh". A better way would be for the
# runner to poll port 9000 and only run the shell script when the server is up,
# but that would take more work.

sleep 30
(cd "$(dirname "$0")/../test/integration" && npm run-script test-with-jenkins)
