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

    var maskGroup = new L.FeatureGroup();

    m.setView([39.33429742980725, -97.05322265625], 5);

    var baseMap = L.tileLayer(
        'http://{s}.tiles.mapbox.com/v3/azavea.map-zbompf85/{z}/{x}/{y}.png', {
            maxZoom: 18,
            attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery &copy; <a href="http://mapbox.com">MapBox</a>'
        });

    m.on('draw:created', function(e) {
        maskGroup.addLayer(e.layer);
        weightedOverlay.update();
    });
    m.on('draw:edited', function(e) {
        weightedOverlay.update();
    });
    m.on('draw:deleted', function(e) {
        weightedOverlay.update();
    });

    m.addLayer(baseMap);
    m.addLayer(maskGroup);
    m.addControl(new WeightedOverlayControl());
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
        getMaskGeoJSON: function() {
            return maskGroup.toGeoJSON();
        },
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
        var layerNames = getLayers();
        if (layerNames == "") {
            return;
        }

        if (WOLayer) {
            map.getRawMap().removeLayer(WOLayer);
        }
        if (summary) {
            map.getRawMap().removeControl(summary);
            summary = null;
        }

        var geoJson = JSON.stringify(map.getMaskGeoJSON());
        var polyMask = JSON.stringify([geoJson]);

        $.ajax({
            url: 'gt/breaks',
            type: 'POST',
            data: {
                layers: getLayers(),
                weights: getWeights(),
                numBreaks: numBreaks,
                polyMask: polyMask
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
                    polyMask: polyMask,
                    attribution: 'Azavea'
                });

                WOLayer.setOpacity(opacity);
                map.getRawMap().addLayer(WOLayer, "Weighted Overlay");
            }
        });

        $.ajax({
            url: 'gt/histogram',
            type: 'POST',
            data: {
                layer: layers[0].name,
                polyMask: geoJson
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

