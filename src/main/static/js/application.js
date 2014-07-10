var server = '';

var weightedOverlay, map;

var WeightedOverlayControl = L.Control.extend({
    options: {
        position: 'topleft'
    },

    onAdd: function(map) {
        var layers = [
            {name: 'Budget_Sum-huc08', weight: 0},
            {name: 'Peo10_no-huc12', weight: 0},
            {name: 'HUC_sqmi-huc12', weight: 0}
        ];

        var container = L.DomUtil.create('div', 'test-panel leaflet-bar');

        var title = L.DomUtil.create('h4');
        title.innerText = 'Weighted Overlay';
        container.appendChild(title);

        var update = function() {
            weightedOverlay.setLayers(layers);
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
    m.setView([39.952335, -75.163789], 12);

    var baseMap = L.tileLayer(
        'http://{s}.tiles.mapbox.com/v3/azavea.map-zbompf85/{z}/{x}/{y}.png', {
            maxZoom: 18,
            attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery &copy; <a href="http://mapbox.com">MapBox</a>'
        });

    m.addLayer(baseMap);
    m.addControl(new WeightedOverlayControl({
        weightedOverlay: weightedOverlay
    }));
    return m;
})();

weightedOverlay = (function() {
    var layers = [];

    var layersToWeights = {}

    var breaks = null;
    var WOLayer = null;
    var opacity = 0.5;
    var colorRamp = "blue-to-red";
    var numBreaks = 10;

    getLayers = function() {
        return _.map(layers, function(l) { return l.name; }).join(",");
    };

    getWeights   = function() {
        return _.map(layers, function(l) { return l.weight; }).join(",");
    };

    update = function() {
        if(getLayers().length == 0) {
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
                    map.removeLayer(WOLayer);
                }

                var layerNames = getLayers();
                if(layerNames == "") return;

                var geoJson = "";
                //var polygon = summary.getPolygon();
                //if(polygon != null) {
                //    geoJson = GJ.fromPolygon(polygon);
                //}

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
                map.addLayer(WOLayer, "Weighted Overlay");
            }
        });
    };

    return {
        activeLayers: getLayers,
        activeWeights: getWeights,
        setLayers: function(ls) {
            layers = ls;
            update();
        },
        setNumBreaks: function(nb) {
            numBreaks = nb;
            update();
        },
        setOpacity: function(o) {
            opacity = o;
            update();
        },
        setColorRamp: function(key) {
            colorRamp = key;
            update();
        },
        getColorRamp: function() { return colorRamp; },
        getMapLayer: function() { return WOLayer; },
        update: update
    };
})();

