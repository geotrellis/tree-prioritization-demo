#!/bin/bash

# Test breaks endpoint for all current datasets

echo
echo ----------------- mean temp
curl -d 'bbox=-13193643.578247702,3977047.2455633273,-13100389.403739786,4039419.8606440313&layers=us-30yr-mean-temperature-tms-epsg3857&weights=2&numBreaks=10&srid=3857' -X POST "http://localhost:8081/tile/gt/breaks"

echo
echo ----------------- housing vacancy
curl -d 'bbox=-13193643.578247702,3977047.2455633273,-13100389.403739786,4039419.8606440313&layers=us-census-housing-vacancy-tms-epsg3857&weights=2&numBreaks=10&srid=3857' -X POST "http://localhost:8081/tile/gt/breaks"

echo
echo ----------------- median household income
curl -d 'bbox=-13193643.578247702,3977047.2455633273,-13100389.403739786,4039419.8606440313&layers=us-census-median-household-income-30m-epsg3857&weights=2&numBreaks=10&srid=3857' -X POST "http://localhost:8081/tile/gt/breaks"

echo
echo ----------------- population density
curl -d 'bbox=-13193643.578247702,3977047.2455633273,-13100389.403739786,4039419.8606440313&layers=us-census-population-density-30m-epsg3857&weights=2&numBreaks=10&srid=3857' -X POST "http://localhost:8081/tile/gt/breaks"

echo
echo ----------------- property value
curl -d 'bbox=-13193643.578247702,3977047.2455633273,-13100389.403739786,4039419.8606440313&layers=us-census-property-value-30m-epsg3857&weights=2&numBreaks=10&srid=3857' -X POST "http://localhost:8081/tile/gt/breaks"

