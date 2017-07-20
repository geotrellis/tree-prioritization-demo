"use strict";

var $ = require('jquery'),
    L = require('leaflet'),
    prioritization = require('./prioritization.js'),
    template = require('./template.js');

require("../../assets/css/sass/main.scss");

require('es6-promise').polyfill(); // https://gitlab.com/IvanSanchez/Leaflet.GridLayer.GoogleMutant
require('leaflet.gridlayer.googlemutant');

function init() {
    var urlPrefix = 'http://' + window.location.hostname + ':7072/gt/',
        // Minneapolis / St Paul
        bounds = L.latLngBounds([44.63635, -93.62626], [45.27205, -92.72795]);

    expandTemplates();
    prioritization.init({
        map: createMap(bounds),
        instanceBounds: bounds,
        urls: {
            breaksUrl: urlPrefix + 'breaks',
            tileUrl: urlPrefix + 'tile/{z}/{x}/{y}.png'
        }
    });
}

function expandTemplates() {
    $('#variables-container').html(template.render('#variables-tmpl', {
        variables: get_variables()
    }));

    $('#masks-container').html(template.render('#raster-masks-tmpl', {
        masks: get_masks()
    }));
}

function createMap(bounds) {
    var map = L.map('map'),
        baseMapPaneName = 'base-map';
    map.createPane(baseMapPaneName);  // CSS class 'leaflet-base-map-pane'
    map.fitBounds(bounds);

    var basemapMapping = {
            'Streets':   makeBaseLayer('roadmap'),
            'Hybrid':    makeBaseLayer('hybrid'),
            'Satellite': makeBaseLayer('satellite'),
            'Terrain':   makeBaseLayer('terrain')
        };

    function makeBaseLayer(type) {
        return L.gridLayer.googleMutant({
            type: type,
            pane: baseMapPaneName
        });
    }

    map.addLayer(basemapMapping['Streets']);
    L.control.layers(basemapMapping).addTo(map);

    return map;
}

function get_variables() {
    return [
        {
            title: 'Population Density',
            less: 'Lower Population Density',
            more: 'Higher Population Density',
            source: 'us-census-population-density-30m-epsg3857'
        }, {
            title: 'Percent Vacant Housing Units',
            less: 'Less Vacant Housing',
            more: 'More Vacant Housing',
            source: 'us-census-housing-vacancy-30m-epsg3857'
        }, {
            title: 'Owner-Occupied Property Value',
            less: 'Lower Property Values',
            more: 'Higher Property Values',
            source: 'us-census-property-value-tms-epsg3857'
        }, {
            title: 'Median Household Income',
            less: 'Lower Household Income',
            more: 'Higher Household Income',
            source: 'us-census-median-household-income-tms-epsg3857'
        }, {
            title: 'Percent Tree Canopy Coverage',
            less: 'Less Tree Canopy Coverage',
            more: 'More Tree Canopy Coverage',
            source: 'nlcd-2011-canopy-tms-epsg3857'
        }, {
            title: 'Percent Impervious Surface',
            less: 'Less Impervious Surface',
            more: 'More Impervious Surface',
            source: 'nlcd-2011-impervious-tms-epsg3857'
        }
    ];
}

function get_masks() {
    return [
        {
            title: 'Land Use',
            source: 'nlcd-zoomed',
            choices: [
                {
                    title: 'Developed (high density)',
                    name: 'resHi',
                    values: [
                        24, // Developed High Intensity
                    ]
                }, {
                    title: 'Developed (low/medium density)',
                    name: 'resLo',
                    values: [
                        22, // Developed, Low Intensity
                        23, // Developed, Medium Intensity
                    ]
                }, {
                    title: 'Urban Open',
                    name: 'urbanOpen',
                    values: [
                        21, // Developed, Open Space
                        71, // Grassland/Herbaceous
                        72, // Sedge/Herbaceous
                        81, // Pasture/Hay
                        82, // Cultivated Crops
                    ]
                }, {
                    title: 'Forest',
                    name: 'forest',
                    values: [
                        41, // Deciduous Forest
                        42, // Evergreen Forest
                        43, // Mixed Forest
                        51, // Dwarf Scrub
                        52, // Shrub/Scrub
                        90, // Woody Wetlands
                        95, // Emergent Herbaceous Wetlands
                    ]
                }, {
                    title: 'Other',
                    name: 'other',
                    values: [
                        11, // Open Water
                        12, // Perennial Ice/Snow
                        31, // Barren Land
                        73, // Lichens
                        74, // Moss
                    ]
                }
            ]
        }
    ];
}

init();
