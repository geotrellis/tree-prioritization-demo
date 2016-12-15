# OpenTreeMap Modeling Tile Service 

## Clone, provision, build and run

1. `cd otm-cloud/src`
1. `git clone git@github.com:opentreemap/otm-modeling`
1. `vagrant up modeling`
1. `cd otm-modeling`
1. `scripts/rebuild.sh`

## Make a release

Here's how to make a release of OTM modeling:

1. Update the version number in `otm-modeling`

   1. `cd `otm-cloud/src/otm-modeling`
   1. `git co develop`
   1. `git pull`
   1. Change `otm-modeling/project/build.scala` so that `val modeling = "X.Y.Z"`
   1. `git commit` (to the local develop branch)
   1. `git tag X.Y.Z`
   1. `git push origin develop --follow-tags`

1. The `otm-modeling` Jenkins job should create the new release (using `deployment/scripts/upload-jar-to-release.sh`).

1. Update `otm-cloud` to use the new release:
   1. Update `otm-cloud/deployment/ansible/group_vars/all` so that `modeling_version: "X.Y.Z"`
   1. Make a pull request, and merge it

## Endpoints

Here are the HTTP endpoints that are available from the tile service.

### /tile/gt/health-check

Inquire whether the tile service is healthy.

Accepted verbs: __GET__

Arguments: none

Returns `OK` with status code 200 if successful. Returns status code 503 ("Service unavailable") or 500 if unsuccessful.

### /tile/gt/breaks

Return class breaks for weighted layers overlay.

Accepted verbs: __POST__

Arguments:

| Name       | Required? | Type    |  Description |
|------------|-----------|---------|--------------|
| bbox       | Yes       | String  | Bounding box projected as WebMercator. Format: `xmin,ymin,xmax,ymax`
| layers     | Yes       | String  | Layer names (comma delimited). Should match the source raster file names.
| weights    | Yes       | String  | Layer weights (comma delimited integers) for corresponding layer. Negative weight inverts the tile value.
| numBreaks  | Yes       | Int     | Number of result class breaks.
| srid       | Yes       | Int     | Spatial Reference Identifier. Acceptable values are `3857` or `4326`.
| threshold  |           | Int     | Exclude values lower than this value in class breaks calculation. (Default: `NODATA`)
| polyMask   |           | GeoJSON | Exclude points not inside polygon. Should contain a FeatureCollection with Polygons or MultiPolygons.
| layerMask  |           | JSON    | Exclude values from result. Map of layer names to selected raster values. Format: `{ LayerName: [1, 2, 3], ...}`

Sample output:

    { "classBreaks": [12,16] }

### /tile/gt/z/x/y.png

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
| weights    | Yes       | String  | Layer weights (comma delimited integers) for corresponding layer. Negative weight inverts the tile value.
| breaks     | Yes       | String  | Class breaks (comma delimited integers)
| srid       | Yes       | Int     | Spatial Reference Identifier. Acceptable values are `3857` or `4326`.
| colorRamp  |           | String  | Color ramp name. (Default: `blue-to-red`)
| threshold  |           | Int     | Exclude values lower than this value in class breaks calculation. (Default: `NODATA`)
| polyMask   |           | GeoJSON | Exclude points not inside polygon. Should contain a FeatureCollection with Polygons or MultiPolygons.
| layerMask  |           | JSON    | Exclude values from result. Map of layer names to selected raster values. Format: `{ LayerName: [1, 2, 3], ...}`
