# OpenTreeMap Modeling and Prioritization Service

## Getting Started

### Copy sample rasters

    scp lr11:/var/trellis/usace/Peo10_no-huc12.* /var/projects/OpenTreeMap-Modeling/data/catalog/

### Run the tile service

1. ``git clone git@github.com:opentreemap/otm-modeling.git``
1. ``cd OpenTreeMap-Modeling``
1. ``./sbt "project tile" run``

### Run the summary Spark Job Server job

1. Install [Docker](https://www.docker.com)
1. Setup your ``~/.aws`` directory so that the default account has access to the com.azavea.datahub S3 bucket.
1. ``./sbt "project summary" assembly``
1. Run Spark Job Server

    ```
    docker run \
      --volume ${HOME}/.aws:/root/ws:ro \
      --volume ${PWD}/summary/etc/spark-jobserver.conf:/opt/spark-jobserver/spark-jobserver.conf:ro \
      --publish 8090:8090 --name spark-jobserver quay.io/azavea/spark-jobserver:latest
    ```

1. Add the job jar to SJS

    ```
    curl --silent \
         --data-binary @summary/target/scala-2.10/otm-modeling-summary-assembly-0.0.1.jar \
         'http://localhost:8090/jars/summary'
    ```

1. Create a Spark context

    ```
    curl --silent --data "" 'http://localhost:8090/contexts/summary-context'``
    ```

1. Test a job

    ```
    curl --silent \
         --data-binary @summary/examples/request-histogram.json \
         'http://localhost:8090/jobs?sync=true&context=summary-context&appName=summary&classPath=org.opentreemap.modeling.HistogramJob
    ```

### Auto reloading

Use the *sbt-revolver* plugin to monitor and automatically reload the tile service when there are any file changes.

    ./sbt "project tile" ~re-start

### Run unit tests

    ./sbt "project tile" ~test

### Build a distribution tarball

1. ``git clone git@github.com:OpenTreeMap/OpenTreeMap-Modeling.git``
1. ``cd OpenTreeMap-Modeling``
1. ``./make-tar``

## Tile endpoint description

Here are the HTTP endpoints that are available from the tile service.

* [/](#index)
* [/gt/colors](#gtcolors)
* [/gt/breaks](#gtbreaks)
* [/gt/wo](#gtwo)
* [/gt/value](#gtvalue)

### <a name="index"></a> /

Test page with a Leaflet map and a few basic controls to test the other endpoint operations.

There is no guarantee that this endpoint is completely stable and it will soon become obsolete.

### /gt/colors

Returns a list of acceptable color ramps.

Accepted verbs: __GET__

Sample output:

    {
        "colors": [
        {
            "key": "blue-to-red",
            "image": "img/ramps/blue-to-red.png"
        },
        ...
    }

### /gt/breaks

Return class breaks for weighted layers overlay.

Accepted verbs: __POST__

Arguments:

| Name       | Required? | Type    |  Description |
|------------|-----------|---------|--------------|
| bbox       | Yes       | String  | Bounding box projected as WebMercator. Format: `xmin,ymin,xmax,ymax`
| layers     | Yes       | String  | Layer names (comma delimited). Should match the source raster file names.
| weights    | Yes       | String  | Layer weights (comma delimited integers) for corresponding layer.
| numBreaks  | Yes       | Int     | Number of result class breaks.
| srid       | Yes       | Int     | Spatial Reference Identifier. Acceptable values are `3857` or `4326`.
| threshold  |           | Int     | Exclude values lower than this value in class breaks calculation. (Default: `NODATA`)
| polyMask   |           | GeoJSON | Exclude points not inside polygon. Should contain a FeatureCollection with Polygons or MultiPolygons.
| layerMask  |           | JSON    | Exclude values from result. Map of layer names to selected raster values. Format: `{ LayerName: [1, 2, 3], ...}`

Sample output:

    { "classBreaks": [12,16] }

### /gt/wo

Render a weighted overlay map tile as a PNG image.

Accepted verbs: __POST__

Arguments:

| Name       | Required? | Type    |  Description |
|------------|-----------|---------|--------------|
| service    | Yes       | String  | *Used by Leaflet.*
| request    | Yes       | String  | *Used by Leaflet.*
| version    | Yes       | String  | *Used by Leaflet.*
| format     | Yes       | String  | *Used by Leaflet.*
| width      | Yes       | Int     | *Used by Leaflet.*
| height     | Yes       | Int     | *Used by Leaflet.*
| palette    |           | String  | *Used by Leaflet.* Comma delimited list of hexadecimal values. (Default: `ff0000,ffff00,00ff00,0000ff`)
| bbox       | Yes       | String  | Bounding box projected as WebMercator. Format: `xmin,ymin,xmax,ymax`
| layers     | Yes       | String  | Layer names (comma delimited). Should match the source raster file names.
| weights    | Yes       | String  | Layer weights (comma delimited integers) for corresponding layer.
| breaks     | Yes       | String  | Class breaks (comma delimited integers)
| srid       | Yes       | Int     | Spatial Reference Identifier. Acceptable values are `3857` or `4326`.
| colorRamp  |           | String  | Color ramp name. (Default: `blue-to-red`)
| threshold  |           | Int     | Exclude values lower than this value in class breaks calculation. (Default: `NODATA`)
| polyMask   |           | GeoJSON | Exclude points not inside polygon. Should contain a FeatureCollection with Polygons or MultiPolygons.
| layerMask  |           | JSON    | Exclude values from result. Map of layer names to selected raster values. Format: `{ LayerName: [1, 2, 3], ...}`


### /gt/value

Return value for multiple points on a raster.

Accepted verbs: __POST__

Arguments:

| Name       | Required? | Type    |  Description |
|------------|-----------|---------|--------------|
| layer      | Yes       | String  | Layer name.
| coords     | Yes       | String  | Comma delimited list of values formatted like `Name,X,Y,...`.
| srid       | Yes       | Int     | Spatial Reference Identifier. Acceptable values are `3857` or `4326`.

Sample request body:

    layer=nlcd
    &srid=4326
    &coords=Tree1,-118.24722290039064,33.972975771726006,
            Tree2,-117.91488647460938,33.81680727566875

Sample output:

    {
        "coords": [
            ["Tree 1", -118.24722290039064, 33.972975771726006, 35],
            ["Tree 2", -117.91488647460938, 33.81680727566875, 23]
        ]
    }

## Summary job descriptions

### org.opentreemap.modeling.HistogramJob

Return distribution of raster values for specified `layer` at the
specified `zoom` within `polyMask`.

Arguments:


    {
      "input": {
        "zoom": 11,
        "layer": "nlcd-wm-ext-tms",
        "polyMask": "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Polygon\",\"crs\":\"EPSG:3857\",\"coordinates\":[[[-13150073.472125152,4014380.7622378],[-13181073.472125152,4014380.7622378],[-13151073.472125152,4015380.7622378],[-13150073.472125152,4015380.7622378],[-13150073.472125152,4014380.7622378]]]}}]}"
      }
    }


| Name       | Required? | Type    |  Description |
|------------|-----------|---------|--------------|
| layer      | Yes       | String  | Layer name. Should match the name of a layer in the Azavea datahub S3 bucket.
| zoom       | Yes       | Int     | Which OSM zoom level (resolution) to use. 11 is the closest match for 30m NLCD.
| polyMask   | Yes       | GeoJSON | Exclude points not inside polygon. Should contain a FeatureCollection with Polygons or MultiPolygons.

Sample output:

    {
      "status": "OK",
      "result": {
        "elapsed": 272,
        "envelope": [-13181073.472125152, 4014380.7622378, -13150073.472125152, 4015380.7622378],
        "histogram": [[11, 80], [21, 281], [22, 1358], [23, 14987], [24, 8316], [71, 98]]
      }
    }
