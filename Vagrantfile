
# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.configure(2) do |config|
  config.vm.box = 'ubuntu/bionic64'

  config.vm.network 'forwarded_port', guest: 9100, host: 9100
  config.vm.hostname = 'coast'

  config.vm.synced_folder '.', '/coast'

  config.vm.provision 'shell', path: 'provision.sh'
end
