#!/bin/sh
# Restarts the BazaarFlow demo. Its database lives in memory, so a restart wipes it and the
# app reseeds fresh sample data on start-up. Then it warms the app up with a few requests, so
# the first visitor does not wait for Java to compile the hot paths. Runs nightly from cron:
#   30 21 * * * /home/ubuntu/liabletech/deploy/bazaarflow/reset-demo.sh >> /home/ubuntu/bazaarflow-reset.log 2>&1
# (21:30 UTC = 03:00 IST)
set -eu
cd "$(dirname "$0")/.."
echo "$(date -u +%FT%TZ) resetting BazaarFlow demo"
sudo docker compose restart bazaarflow

# Requests go through the caddy container, which can reach bazaarflow on the private network.
call() { sudo docker compose exec -T caddy wget -q -O - "$@" 2>/dev/null; }

i=0
until call http://bazaarflow:8080/api/catalog/categories >/dev/null; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then echo "$(date -u +%FT%TZ) demo did not come up"; exit 1; fi
  sleep 5
done

token=$(call --header 'Content-Type: application/json' \
  --post-data '{"email":"admin@fieldstone.demo","password":"demo@123"}' \
  http://bazaarflow:8080/api/auth/login | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
for round in 1 2 3; do
  for path in catalog/products?size=12 catalog/categories; do
    call "http://bazaarflow:8080/api/$path" >/dev/null || true
  done
  for path in admin/dashboard admin/orders?size=20 admin/inventory admin/accounts/payments; do
    call --header "Authorization: Bearer $token" "http://bazaarflow:8080/api/$path" >/dev/null || true
  done
done
echo "$(date -u +%FT%TZ) done (warmed up)"
