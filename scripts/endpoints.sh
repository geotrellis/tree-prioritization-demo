#!/bin/bash

echo
echo ----------------- breaks
curl -d 'bbox=-13193643.578247702,3977047.2455633273,-13100389.403739786,4039419.8606440313&layers=us-census-property-value-tms-epsg3857&weights=2&numBreaks=10&srid=3857' -X POST "http://localhost:8081/tile/gt/breaks"

echo
echo ----------------- tile
curl -d 'bbox=-13193642.578247702,3977047.2455633273,-13100389.403739786,4039419.8606440313&layers=us-census-property-value-tms-epsg3857&weights=2&numBreaks=10&srid=3857&breaks=346600,449400,542600,613000,688800,792800,902800,1148600,1571800,2000002' -X POST "http://localhost:8081/tile/gt/tile/11/350/817.png" > ~/tile.png

echo
echo ----------------- point values
curl --data-binary @summary/examples/request-values.json 'http://localhost:8081/sjs/jobs?sync=true&context=modeling&appName=otm-modeling-0.0.1&classPath=org.opentreemap.modeling.PointValuesJob'

echo
echo ----------------- histogram
curl --data-binary @summary/examples/request-histogram.json 'http://localhost:8081/sjs/jobs?sync=true&context=modeling&appName=otm-modeling-0.0.1&classPath=org.opentreemap.modeling.HistogramJob'

