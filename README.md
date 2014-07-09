# OpenTreeMap Modeling and Prioritization Service

## Getting Started

### Copy sample rasters

    scp lr11:/var/trellis/usace/Peo10_no-huc12.* /var/projects/OpenTreeMap-Modeling/data/catalog/

### Run the service

1. ``git clone git@github.com:OpenTreeMap/OpenTreeMap-Modeling.git``
1. ``cd OpenTreeMap-Modeling``
1. ``./sbt run``

### Auto reloading

Use the *sbt-revolver* plugin to monitor and automatically reload the service when there are any file changes.

1. ``./sbt``
1. ``> ~re-restart``

### Build a distribution tarball

1. ``git clone git@github.com:OpenTreeMap/OpenTreeMap-Modeling.git``
1. ``cd OpenTreeMap-Modeling``
1. ``./make-tar``
