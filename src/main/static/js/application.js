var weightedOverlay, map, summary;

var layers = [
    {name: 'Budget_Sum-huc08', weight: 0},
    {name: 'Peo10_no-huc12', weight: 0},
    {name: 'HUC_sqmi-huc12', weight: 0}
];

// Convert JSON to HTML table.
var tablify = function(json) {
    if (typeof json !== 'object') {
        return json;
    }
    var rows = [];
    for (var k in json) {
        rows.push('<td style="border:1px solid #999;padding:5px;">' + k + '</td>'
           + '<td style="border:1px solid #999;padding:5px;">' + tablify(json[k]) + '</td>');
    }
    return '<table>' + rows.join('</tr><tr>')  + '</table>';
};

var SummaryControl = L.Control.extend({
    options: {
        position: 'topright'
    },

    initialize: function(options) {
        this.json = options.json;
    },

    onAdd: function(rawMap) {
        var container = L.DomUtil.create('div', 'test-panel leaflet-bar');
        container.innerHTML = tablify(this.json);
        L.DomEvent.disableClickPropagation(container);
        return container;
    }
});

var FeatureMaskControl = L.Control.extend({
    options: {
        position: 'topleft'
    },

    onAdd: function(rawMap) {
        var container = L.DomUtil.create('div', 'test-panel leaflet-bar');

        var featureMask = function(featureId, center, zoom) {
            var btn = L.DomUtil.create('button');
            btn.textContent = featureId;
            container.appendChild(btn);

            container.appendChild(document.createTextNode(' '));

            L.DomEvent.addListener(btn, 'click', function() {
                var args = {
                    layerName: "test",
                    featureId: featureId
                };
                $.getJSON('mask', args, function(data) {
                    var layer = new L.GeoJSON(data);
                    layer.setStyle({
                        fill: false
                    });
                    map.setFeatureMask(featureId, layer);
                    rawMap.setView(center, zoom);
                });
            });
        };

        featureMask('Philadelphia', [39.9852753581228, -75.15214920043945], 12);
        featureMask('NorthCarolina', [35.303918565311704, -79.85687255859375], 8);

        L.DomEvent.disableClickPropagation(container);
        return container;
    }
});

var WeightedOverlayControl = L.Control.extend({
    options: {
        position: 'topleft'
    },

    onAdd: function(rawMap) {
        var container = L.DomUtil.create('div', 'test-panel leaflet-bar');

        var update = function() {
            weightedOverlay.update();
        };

        var addLayer = function(layer) {
            var p = L.DomUtil.create('p');

            var lbl = L.DomUtil.create('label');
            lbl.innerText = layer.name + ' (' + layer.weight + ')';
            p.appendChild(lbl);

            var slider = L.DomUtil.create('input');
            slider.type = 'range';
            slider.min = -5;
            slider.max = 5;
            slider.step = 1;
            L.DomEvent.addListener(slider, 'input', function(e) {
                layer.weight = parseInt(e.target.value);
                lbl.innerText = layer.name + ' (' + layer.weight + ')';
            });
            L.DomEvent.addListener(slider, 'change', update);
            p.appendChild(slider);

            container.appendChild(p);
        };

        _.each(layers, addLayer);

        var btn = L.DomUtil.create('button');
        btn.textContent = 'Update';
        container.appendChild(btn);

        L.DomEvent.addListener(btn, 'click', function() {
            update();
        });

        L.DomEvent.disableClickPropagation(container);
        return container;
    }
});

map = (function() {
    var m = L.map('map', {
        zoomControl: false
    });

    var maskGroup = new L.FeatureGroup(),
        polyMask = null,
        featureMask = null;

    m.setView([39.33429742980725, -97.05322265625], 5);

    var baseMap = L.tileLayer(
        'http://{s}.tiles.mapbox.com/v3/azavea.map-zbompf85/{z}/{x}/{y}.png', {
            maxZoom: 18,
            attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery &copy; <a href="http://mapbox.com">MapBox</a>'
        });

    var setPolygonMask = function(layer) {
        featureMask = null;
        polyMask = layer;
        maskGroup.clearLayers();
        maskGroup.addLayer(layer);
        weightedOverlay.update();
    };

    var setFeatureMask = function(featureId, layer) {
        polyMask = null;
        featureMask = {
            layer: layer,
            featureId: featureId
        };
        maskGroup.clearLayers();
        maskGroup.addLayer(layer);
        weightedOverlay.update();
    };

    m.on('draw:created', function(e) {
        setPolygonMask(e.layer);
    });
    m.on('draw:edited', function(e) {
        weightedOverlay.update();
    });
    m.on('draw:deleted', function(e) {
        polyMask = null;
        featureMask = null;
        weightedOverlay.update();
    });

    m.addLayer(baseMap);
    m.addLayer(maskGroup);
    m.addControl(new WeightedOverlayControl());
    m.addControl(new FeatureMaskControl());
    m.addControl(new L.Control.Draw({
        draw: {
            polyline: false,
            circle: false,
            marker: false,
            polygon: {
                shapeOptions: {
                    fill: false
                }
            },
            rectangle: {
                shapeOptions: {
                    fill: false
                }
            }
        },
        edit: {
            featureGroup: maskGroup
        }
    }));

    return {
        getPolygonMask: function() {
            return polyMask;
        },
        getFeatureMask: function() {
            return featureMask;
        },
        setFeatureMask: setFeatureMask,
        getRawMap: function() {
            return m;
        }
    };
})();

weightedOverlay = (function() {
    var layersToWeights = {}
    var breaks = null;
    var WOLayer = null;
    var opacity = 0.5;
    var colorRamp = "blue-to-red";
    var numBreaks = 10;

    var getLayers = function() {
        return _.map(layers, function(l) { return l.name; }).join(",");
    };

    var getWeights = function() {
        return _.map(layers, function(l) { return l.weight; }).join(",");
    };

    var update = function() {
        if (getLayers().length == 0) {
            if (WOLayer) {
                map.removeLayer(WOLayer);
                WOLayer = null;
            }
            return;
        };

        if (WOLayer) {
            map.getRawMap().removeLayer(WOLayer);
        }

        if (summary) {
            map.getRawMap().removeControl(summary);
            summary = null;
        }

        var layerNames = getLayers();
        if (layerNames == "") return;

        var geoJson = "";
        var layerName = "";
        var featureId = "";

        var polyMask = map.getPolygonMask();
        var featureMask = map.getFeatureMask();

        if (polyMask) {
            geoJson = GJ.fromPolygon(polyMask);
        } else if (featureMask) {
            layerName = "test";
            featureId = featureMask.featureId;
        }

        $.ajax({
            url: 'gt/breaks',
            data: {
                layers: getLayers(),
                weights: getWeights(),
                numBreaks: numBreaks,
                mask: geoJson,
                layerName: layerName,
                featureId: featureId
            },
            dataType: "json",
            success: function(r) {
                breaks = r.classBreaks;

                WOLayer = new L.TileLayer.WMS("gt/wo", {
                    layers: 'default',
                    format: 'image/png',
                    breaks: breaks,
                    transparent: true,
                    layers: layerNames,
                    weights: getWeights(),
                    colorRamp: colorRamp,
                    mask: encodeURIComponent(geoJson),
                    layerName: layerName,
                    featureId: featureId,
                    attribution: 'Azavea'
                })

                WOLayer.setOpacity(opacity);
                map.getRawMap().addLayer(WOLayer, "Weighted Overlay");
            }
        });

        $.ajax({
            url: 'gt/histogram',
            data: {
                layer: layers[0].name,
                mask: geoJson,
                layerName: layerName,
                featureId: featureId
            },
            dataType: "json",
            success: function(r) {
                summary = new SummaryControl({ json: r });
                map.getRawMap().addControl(summary);
            }
        });
    };

    return {
        update: update
    };
})();

