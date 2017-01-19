#!/bin/bash


# Wait for the DB to be up
until ping -c1 dyno1 2>&1 >/dev/null; do
  printf '.'
  sleep 0.1
done

echo "Successfully pinged dynomite."

#Wait up to a minute for port 22122 to be open for business.
if nc -z -w 60 dyno1 22122; then
  echo "Could not connect to the database: $?."
  # exit 1
else
  echo "Database port 22122 open."
fi


# Start the UI
cd /ui
npm run watch &


# Start the server
cd /
java -jar conductor-server-*-all.jar config.properties
