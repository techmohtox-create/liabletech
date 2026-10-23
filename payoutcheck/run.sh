#!/usr/bin/env sh
# Builds PayoutCheck (UI + API) into one jar and starts it on http://localhost:8080.
# Needs Java 21 and Node 20+. Data is kept in ./data (H2); set SPRING_DATASOURCE_* for PostgreSQL.
set -e
cd "$(dirname "$0")"

if [ ! -d web/node_modules ]; then
  (cd web && npm ci)
fi
(cd web && npx ng build)
(cd api && ./mvnw -q -B package -DskipTests)

echo "Starting PayoutCheck on http://localhost:${PORT:-8080}  (demo login: demo@payoutcheck.local / demo1234)"
exec java -jar api/target/payoutcheck-1.0.0.jar
