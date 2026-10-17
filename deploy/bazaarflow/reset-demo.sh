#!/bin/sh
# Wipes the BazaarFlow demo database and restarts the app, which recreates and reseeds it.
# Runs nightly from cron on the server:
#   30 21 * * * /home/ubuntu/liabletech/deploy/bazaarflow/reset-demo.sh >> /home/ubuntu/bazaarflow-reset.log 2>&1
# (21:30 UTC = 03:00 IST)
set -eu
cd "$(dirname "$0")/.."

set -a
. ./bazaarflow.env
set +a

# OBENOL_DB_URL looks like jdbc:postgresql://HOST/bazaarflow?sslmode=require
target="${OBENOL_DB_URL#jdbc:postgresql://}"
echo "$(date -u +%FT%TZ) resetting BazaarFlow demo"
sudo docker run --rm -e PGPASSWORD="$OBENOL_DB_PASSWORD" postgres:17-alpine \
  psql "postgresql://$OBENOL_DB_USER@$target" -v ON_ERROR_STOP=1 \
  -c "DROP SCHEMA public CASCADE" -c "CREATE SCHEMA public"
sudo docker compose restart bazaarflow
echo "$(date -u +%FT%TZ) done"
