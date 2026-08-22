#!/bin/sh

set -eu

docker compose down
docker compose down --volumes --remove-orphans --rmi local
echo "Gaffer containers, network, volumes, and local images removed."
