#!/bin/sh
# Restarts the BazaarFlow demo. Its database lives in memory, so a restart wipes it and the
# app reseeds fresh sample data on start-up. Runs nightly from cron on the server:
#   30 21 * * * /home/ubuntu/liabletech/deploy/bazaarflow/reset-demo.sh >> /home/ubuntu/bazaarflow-reset.log 2>&1
# (21:30 UTC = 03:00 IST)
set -eu
cd "$(dirname "$0")/.."
echo "$(date -u +%FT%TZ) resetting BazaarFlow demo"
sudo docker compose restart bazaarflow
echo "$(date -u +%FT%TZ) done"
