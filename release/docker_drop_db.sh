#!/bin/bash
# Script to drop the DB by using a docker image to run psql
# Postgres password must be set in env var POSTGRES_PASSWORD
PrgName=$(basename "$0")

if [ -z "$DB_HOST" ]; then
  DB_HOST=systems-postgres
fi

DB_USER=postgres
DB_NAME=tapissysdb
DB_PW=${POSTGRES_PASSWORD}

# Determine absolute path to location from which we are running.
export RUN_DIR=$(pwd)
export PRG_RELPATH=$(dirname "$0")
cd "$PRG_RELPATH"/. || exit
export PRG_PATH=$(pwd)

if [ -z "${POSTGRES_PASSWORD}" ]; then
  echo "Please set env var POSTGRES_PASSWORD before running this script"
  exit 1
fi

# PGPASSWORD=${DB_PW} psql --host=${DB_HOST} --username=${DB_USER} -q << EOF
# DROP DATABASE ${DB_NAME}
# EOF

# Running with network=host exposes ports directly. Only works for linux
# docker run -e POSTGRES_PASSWORD="${POSTGRES_PASSWORD}" -i --rm --network="host" bitnami/postgresql:latest /bin/bash << create_db.sh
docker run -e DB_PW="${DB_PW}" -i --rm --network="host" bitnami/postgresql:latest /bin/bash << EOF
PGPASSWORD=${DB_PW} psql --host=${DB_HOST} --username=${DB_USER} -q -c "DROP DATABASE ${DB_NAME}"
EOF
