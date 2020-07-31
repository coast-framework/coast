#!/bin/bash

set -o nounset
set -o errexit

readonly BasePath="/coast"

clojure () {
    echo "Installing clojure"

    apt-get install -y default-jre
    curl -O https://download.clojure.org/install/linux-install-1.10.1.561.sh
    chmod +x linux-install-1.10.1.561.sh
    sudo ./linux-install-1.10.1.561.sh
}

main () {
    echo "PROVISIONING"

    export DEBIAN_FRONTEND=noninteractive

    # Update apt cache
    apt-get update
    apt-get autoclean
    apt-get autoremove -y

    # Install some base software
    apt-get install -y curl vim unzip

    # Create bin dir for user vagrant
    mkdir -p /home/vagrant/bin
    chown vagrant:vagrant /home/vagrant/bin

    # Navigate to project directory on login
    LINE="cd ${BasePath}"
    FILE=/home/vagrant/.bashrc
    grep -q "$LINE" "$FILE" || echo "$LINE" >> "$FILE"

    # Add greeting
    echo "Hello coaster :)" > /etc/motd

    clojure
}
main
