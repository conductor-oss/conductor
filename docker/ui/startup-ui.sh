#!/bin/sh
# startup script for the conductor ui docker image

# replace default values in nginx config file. replace `localhost` by placeholder
# placeholder will be feed by envsubst cmd
echo "Populate nginx config file"
sed -i 's,http://localhost:8080,\$WF_SERVER,g' /etc/nginx/conf.d/nginx.conf.template 
envsubst '$WF_SERVER' < /etc/nginx/conf.d/nginx.conf.template > /etc/nginx/conf.d/default.conf

echo "Starting Conductor UI"
echo "Port: $PORT"
echo "Conductor server: $WF_SERVER"

# run nginx
nginx -g 'daemon off;'
