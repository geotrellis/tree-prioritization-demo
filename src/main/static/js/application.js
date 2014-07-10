var server = '';

var weightedOverlay, map;

var layers = [
    {name: 'Budget_Sum-huc08', weight: 0},
    {name: 'Peo10_no-huc12', weight: 0},
    {name: 'HUC_sqmi-huc12', weight: 0}
];

var WeightedOverlayControl = L.Control.extend({
    options: {
        position: 'topleft'
    },

    onAdd: function(map) {
        var container = L.DomUtil.create('div', 'test-panel leaflet-bar');

        var title = L.DomUtil.create('h4');
        title.innerText = 'Weighted Overlay';
        container.appendChild(title);

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
        mask = null;

    m.setView([39.952335, -75.163789], 12);

    var baseMap = L.tileLayer(
        'http://{s}.tiles.mapbox.com/v3/azavea.map-zbompf85/{z}/{x}/{y}.png', {
            maxZoom: 18,
            attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery &copy; <a href="http://mapbox.com">MapBox</a>'
        });

    m.on('draw:created', function(e) {
        if (mask) {
            maskGroup.removeLayer(mask);
            mask = null;
        }
        mask = e.layer;
        maskGroup.addLayer(mask);
        weightedOverlay.update();
    });
    m.on('draw:edited', function(e) {
        weightedOverlay.update();
    });
    m.on('draw:deleted', function(e) {
        mask = null;
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
        getMask: function() {
            return mask;
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
        if (getLayers().length == 0) {
            if (WOLayer) {
                map.removeLayer(WOLayer);
                WOLayer = null;
            }
            return;
        };

        $.ajax({
            url: server + 'gt/breaks',
            data: { 'layers' : getLayers(),
                    'weights' : getWeights(),
                    'numBreaks': numBreaks },
            dataType: "json",
            success: function(r) {
                breaks = r.classBreaks;

                if (WOLayer) {
                    map.getRawMap().removeLayer(WOLayer);
                }

                var layerNames = getLayers();
                if (layerNames == "") return;

                var geoJson = "";
                var polygon = map.getMask();
                if (polygon != null) {
                    geoJson = GJ.fromPolygon(polygon);
                }

                WOLayer = new L.TileLayer.WMS(server + "gt/wo", {
                    layers: 'default',
                    format: 'image/png',
                    breaks: breaks,
                    transparent: true,
                    layers: layerNames,
                    weights: getWeights(),
                    colorRamp: colorRamp,
                    mask: encodeURIComponent(geoJson),
                    attribution: 'Azavea'
                })

                WOLayer.setOpacity(opacity);
                map.getRawMap().addLayer(WOLayer, "Weighted Overlay");
            }
        });
    };

    return {
        update: update
    };
})();

