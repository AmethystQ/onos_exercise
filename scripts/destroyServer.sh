#!/bin/bash

# Destroy the ONOS server running as docker image
echo "Destroy onos server..."
docker stop onos-server

# Destory Atomix server running as docker image 
echo "Destroy atomix server..."
docker stop atomix-server

docker container prune --force