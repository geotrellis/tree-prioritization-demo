 # -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.require_version ">= 1.9"

Vagrant.configure(2) do |config|
  config.vm.box = "ubuntu/trusty64"

  config.vm.synced_folder "~/.aws", "/home/vagrant/.aws"

  config.vm.provider :virtualbox do |vb|
    vb.memory = 4096
    vb.cpus = 2
  end

  # nginx
  config.vm.network :forwarded_port, guest: 8080, host: 8080

  # webpack-dev-server
  config.vm.network :forwarded_port, guest: 8286, host: 8286
  # API
  config.vm.network :forwarded_port, guest: 8777, host: 8777

  # Change working directory to /vagrant upon session start.
  config.vm.provision "shell", inline: <<SCRIPT
    if ! grep -q "cd /vagrant" "/home/vagrant/.bashrc"; then
      echo "cd /vagrant" >> "/home/vagrant/.bashrc"
    fi
SCRIPT

  config.vm.provision "ansible" do |ansible|
    ansible.playbook = "deployment/ansible/tpsp-demo.yml"
    ansible.galaxy_role_file = "deployment/ansible/roles.yml"
  end
end
