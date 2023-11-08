#!/bin/bash

# add PATH for topo and onos command
export PATH="$PATH:bin:onos/bin:/mnt/sdn-ip/bin"

SSH_KEY=$(cut -d\  -f2 ~/.ssh/id_rsa.pub)

# Create Atomix using Atomix docker image
ATOMIX_IMAGE=atomix/atomix:3.0.10
echo "Setting up atomix..."
docker container run --detach --name atomix-server --hostname atomix-server \
    --restart=always -v /home/sdn/bin/config:/atomix $ATOMIX_IMAGE \
    --config /atomix/atomix.conf

# Create ONOS server using ONOS docker image
ONOS_IMAGE=onosproject/onos:1.15.0
echo "Setting up onos server..."
docker container run --detach --name onos-server --hostname onos-server --restart=always $ONOS_IMAGE
docker exec -i onos-server /bin/bash -c "mkdir config; cat > config/onos-server.json" < $(dirname $0)/config/onos-server.json
docker exec -it onos-server bin/onos-user-key sdn $SSH_KEY >/dev/null 2>&1
docker exec -it onos-server bin/onos-user-password onos rocks >/dev/null 2>&1

function waitForStart {
    sleep 5
    echo "Waiting for onos startup..."
    ip=$(docker container inspect onos-server | grep \"IPAddress | cut -d: -f2 | sort -u | tr -d '", ')
    for t in {1..60}; do
        curl --fail -sS http://$ip:8181/onos/v1/application --user onos:rocks 1>/dev/null 2>&1 && break;
        sleep 1;
    done
    echo $ip
    onos $ip summary >/dev/null 2>&1
}

# Extract the IP addresses of the ONOS nodes
OC=$(docker container inspect onos-server | grep \"IPAddress | cut -d: -f2 | sort -u | tr -d '", ')
ONOS_INSTANCE="$OC"

waitForStart

echo "Activating OpenFlow and ProxyARP applications..."
onos $OC app activate org.onosproject.openflow proxyarp layout
onos $OC