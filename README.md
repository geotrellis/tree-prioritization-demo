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

    ./sbt ~re-start

### Run unit tests

    ./sbt ~test

### Build a distribution tarball

1. ``git clone git@github.com:OpenTreeMap/OpenTreeMap-Modeling.git``
1. ``cd OpenTreeMap-Modeling``
1. ``./make-tar``

## Endpoint description

Here are the HTTP endpoints that are available from this service.

* [/](#index)
* [/gt/colors](#gtcolors)
* [/gt/breaks](#gtbreaks)
* [/gt/wo](#gtwo)
* [/gt/histogram](#gthistogram)
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
| colorRamp  |           | String  | Color ramp name. (Default: `blue-to-red`)
| threshold  |           | Int     | Exclude values lower than this value in class breaks calculation. (Default: `NODATA`)
| polyMask   |           | GeoJSON | Exclude points not inside polygon. Should contain a FeatureCollection with Polygons or MultiPolygons.
| layerMask  |           | JSON    | Exclude values from result. Map of layer names to selected raster values. Format: `{ LayerName: [1, 2, 3], ...}`

### /gt/histogram

Return distribution of raster values for specified `layers` within `polyMask` (optional).

Accepted verbs: __POST__

Arguments:

| Name       | Required? | Type    |  Description |
|------------|-----------|---------|--------------|
| bbox       | Yes       | String  | Bounding box projected as WebMercator. Format: `xmin,ymin,xmax,ymax`
| layers     | Yes       | String  | Layer names (comma delimited). Should match the source raster file names.
| polyMask   |           | GeoJSON | Exclude points not inside polygon. Should contain a FeatureCollection with Polygons or MultiPolygons.

Sample output:

    {
        "elapsed": "62",
        "histogram": [[1,25069],[2,9809],[3,3661],[4,2683],[5,492],[8,348],[9,624],[10,3122],[14,1336]]
    }

### /gt/value

Return value for multiple points on a raster.

Accepted verbs: __POST__

Arguments:

| Name       | Required? | Type    |  Description |
|------------|-----------|---------|--------------|
| layer      | Yes       | String  | Layer name.
| coords     | Yes       | String  | Comma delimited list of values formatted like `Name,X,Y,...`. Coordinates should be projected as LatLng. (Example: `Tree1,0,0,Tree2,100,100`)

Sample output:

    {
        "coords": [
            ["Tree 1", -118.24722290039064, 33.972975771726006, 35],
            ["Tree 2", -117.91488647460938, 33.81680727566875, 23]
        ]
    }

