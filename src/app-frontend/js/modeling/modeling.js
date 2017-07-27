"use strict";

var $ = require('jquery'),
    L = require('leaflet'),
    toastr = require('toastr'),
    prioritization = require('./prioritization.js'),
    template = require('./template.js'),
    geocoder = require('./geocoder.js');

var dom = {
    geocode: '#geocode'
}

require("../../assets/css/sass/main.scss");

require('es6-promise').polyfill(); // https://gitlab.com/IvanSanchez/Leaflet.GridLayer.GoogleMutant
require('leaflet.gridlayer.googlemutant');

function centerToBounds(center) {
    // 10,000 meters is a roughly city size boundary
    return L.latLng(center).toBounds(5000);
}

function queryStringObject() {
    var params = location.search.substring(1);
    if (params.trim() !== '') {
        try {
            return JSON.parse('{"' + decodeURIComponent(params).replace(/"/g, '\\"').replace(/&/g, '","').replace(/=/g,'":"') + '"}');
        } catch (e) {
            console.log(e);
            return {};
        }
    } else {
        return {};
    }
}

function init() {
    if (window.location.hostname == "localhost"){
        var urlPrefix = 'http://' + window.location.hostname + ':8080/tile/gt/';
    } else {
        var urlPrefix = 'https://' + window.location.hostname + '/tile/gt/';
    }

    // Minneapolis / St Paul
    var bounds = L.latLngBounds([44.63635, -93.62626], [45.27205, -92.72795]);
    var query = queryStringObject();
    if (query.center) {
        bounds = centerToBounds(L.latLng(JSON.parse('[' + query.center + ']')));
    }

    var centerStream = geocoder.createGeocodeStream(dom.geocode);
    centerStream.map(centerToParam).onValue(pushCenterParamToUrl);
    centerStream.onError(function (message) {
        toastr.error(message);
    });

    var boundsStream = centerStream.map(centerToBounds);

    expandTemplates();
    prioritization.init({
        map: createMap(bounds, boundsStream),
        instanceBounds: bounds,
        boundsStream: boundsStream,
        urls: {
            breaksUrl: urlPrefix + 'breaks',
            zipCodeUrl: urlPrefix + 'masks/zip-codes',
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

function centerToParam(center) {
    var latLng = L.latLng(center);
    return '' + latLng.lat + ',' + latLng.lng;
}

function pushCenterParamToUrl(center) {
    var baseUrl = [location.protocol, '//', location.host, location.pathname].join('');
    if (window.history) {
        window.history.pushState(null, document.title, [baseUrl, '?', $.param({center: center}), location.hash].join(''));
    }
}

function createMap(bounds, boundsStream) {
    var map = L.map('map'),
        baseMapPaneName = 'base-map';
    map.createPane(baseMapPaneName);  // CSS class 'leaflet-base-map-pane'
    map.fitBounds(bounds);

    boundsStream.onValue(map.fitBounds.bind(map));

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
