# Tree Planting Site Prioritization Demo

### Requirements

* Vagrant 1.9+
* VirtualBox 4.3+
* Ansible 2.2+

#### Build

* Make sure you have a `geotrellis` profile in your aws-cli profiles, with keys that can access your data on S3
* Clone the project
* `cd` into the directory
* Run `scripts/setup.sh`, which will:
  * Build the tile server JAR
  * Create a Vagrant VM
  * Build two Docker containers:
    1. Front end, running Webpack dev server and listening on port 8286
    2. Back end, running the tile server and listening on port 7072

#### Run

The other project scripts are meant to execute in the VM in the `/vagrant` directory. To run the container during development use the following commands:

* `vagrant ssh`
* `scripts/server.sh`
* Browse to http://localhost:8286

#### Notes

`scripts/setup.sh` can also be used to restore the project to its initial state: it will re-provision the VM, then remove and
rebuild the Docker container(s). (Note: this will destroy the VM's existing Docker container before rebuilding it.)

HTML for the front end is in `src/app-frontend/template.handlebars`. 
It contains underscore templates that are expanded by JS code.
Because Webpack's default HTML loader tries to expand the templates at load time
we use the Handlebars loader instead as it ignores the underscore template syntax.

## License

* Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
