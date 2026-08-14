#!/bin/sh

set -eu

docker compose down --volumes --remove-orphans --rmi local
echo "Gaffer containers, volumes, network, and local image removed."
