#!/bin/bash

echo
echo ----------------- health-check
curl http://localhost:8081/tile/gt/health-check

echo
echo ----------------- breaks
curl "http://localhost:8081/tile/gt/breaks?bbox=-93.62626,44.63635,-92.72795,45.27205&layers=us-census-population-density-30m-epsg3857&weights=2&numBreaks=10"

echo
echo ----------------- tile
curl "http://localhost:8081/tile/gt/tile/10/124/183.png?bbox=-93.62626,44.63635,-92.72795,45.27205&layers=us-census-population-density-30m-epsg3857&weights=2&breaks=10,20,28,36,48,68,94,130,164,198" >tile.png
