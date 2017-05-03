# Planting Site Prioritization Tile Service 

## Endpoints

Here are the HTTP endpoints that are available from the tile service.

### /gt/health-check

Inquire whether the tile service is healthy.

Accepted verbs: __GET__

Arguments: none

Returns `OK` with status code 200 if successful. Returns status code 503 ("Service unavailable") or 500 if unsuccessful.

### /gt/breaks

Return class breaks for weighted layers overlay.

Accepted verbs: __POST__

Arguments:

| Name       | Required? | Type    |  Description |
|------------|-----------|---------|--------------|
| bbox       | Yes       | String  | Bounding box as lat/long. Format: `xmin,ymin,xmax,ymax`
| layers     | Yes       | String  | Layer names (comma delimited). Should match the source raster file names.
| weights    | Yes       | String  | Layer weights (comma delimited integers) for corresponding layer. Negative weight inverts the tile value.
| numBreaks  | Yes       | Int     | Number of result class breaks.
| threshold  |           | Int     | Exclude values lower than this value in class breaks calculation. (Default: `NODATA`)
| polyMask   |           | GeoJSON | Exclude points not inside polygon. Should contain a FeatureCollection with Polygons or MultiPolygons.
| layerMask  |           | JSON    | Exclude values from result. Map of layer names to selected raster values. Format: `{ LayerName: [1, 2, 3], ...}`

Sample output:

    { "classBreaks": [12,16] }

### /gt/z/x/y.png

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
| bbox       | Yes       | String  | Bounding box as lat/long. Format: `xmin,ymin,xmax,ymax`
| layers     | Yes       | String  | Layer names (comma delimited). Should match the source raster file names.
| weights    | Yes       | String  | Layer weights (comma delimited integers) for corresponding layer. Negative weight inverts the tile value.
| breaks     | Yes       | String  | Class breaks (comma delimited integers)
| colorRamp  |           | String  | Color ramp name. (Default: `blue-to-red`)
| threshold  |           | Int     | Exclude values lower than this value in class breaks calculation. (Default: `NODATA`)
| polyMask   |           | GeoJSON | Exclude points not inside polygon. Should contain a FeatureCollection with Polygons or MultiPolygons.
| layerMask  |           | JSON    | Exclude values from result. Map of layer names to selected raster values. Format: `{ LayerName: [1, 2, 3], ...}`
