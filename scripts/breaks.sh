#!/bin/bash

# Test breaks endpoint for all current datasets
#
# Bounding box used is for the area around the mouth of the Delaware, (-75.5,38.8) to (-74.5,39.6)

echo
echo ----------------- housing vacancy
curl -d 'bbox=-8404621.554892154,4693063.644295793,-8293302.064098882,4807984.493190501&layers=us-census-housing-vacancy-30m-epsg3857&weights=1&numBreaks=10&srid=3857' -X POST "http://localhost:8081/tile/gt/breaks"

echo
echo ----------------- median household income
curl -d 'bbox=-8404621.554892154,4693063.644295793,-8293302.064098882,4807984.493190501&layers=us-census-median-household-income-30m-epsg3857&weights=1&numBreaks=10&srid=3857' -X POST "http://localhost:8081/tile/gt/breaks"

echo
echo ----------------- population density
curl -d 'bbox=-8404621.554892154,4693063.644295793,-8293302.064098882,4807984.493190501&layers=us-census-population-density-30m-epsg3857&weights=1&numBreaks=10&srid=3857' -X POST "http://localhost:8081/tile/gt/breaks"

echo
echo ----------------- property value
curl -d 'bbox=-8404621.554892154,4693063.644295793,-8293302.064098882,4807984.493190501&layers=us-census-property-value-30m-epsg3857&weights=1&numBreaks=10&srid=3857' -X POST "http://localhost:8081/tile/gt/breaks"

