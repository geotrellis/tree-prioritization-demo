var server = ''

var getLayer = function(url, attrib) {
    return L.tileLayer(url, { maxZoom: 18, attribution: attrib });
};

var Layers = {
    stamen: {
        toner:  'http://{s}.tile.stamen.com/toner/{z}/{x}/{y}.png',
        terrain: 'http://{s}.tile.stamen.com/terrain/{z}/{x}/{y}.png',
        watercolor: 'http://{s}.tile.stamen.com/watercolor/{z}/{x}/{y}.png',
        attrib: 'Map data &copy;2013 OpenStreetMap contributors, Tiles &copy;2013 Stamen Design'
    },
    mapBox: {
        azavea: 'http://{s}.tiles.mapbox.com/v3/azavea.map-zbompf85/{z}/{x}/{y}.png',
        attrib: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery &copy; <a href="http://mapbox.com">MapBox</a>'
    }
};

var map = (function() {
    var selected = getLayer(Layers.mapBox.azavea, Layers.mapBox.attrib);
    var baseLayers = {
        "Default": selected,
        "Terrain": getLayer(Layers.stamen.terrain, Layers.stamen.attrib),
        "Watercolor": getLayer(Layers.stamen.watercolor, Layers.stamen.attrib),
        "Toner": getLayer(Layers.stamen.toner, Layers.stamen.attrib)
    };

    var m = L.map('map');

    m.setView([39.952335, -75.163789], 12);

    selected.addTo(m);

    m.lc = L.control.layers(baseLayers).addTo(m);
    return m;
})()

var weightedOverlay = (function() {
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
                map.lc.removeLayer(WOLayer);
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
                    map.lc.removeLayer(WOLayer);
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
                WOLayer.addTo(map);
                map.lc.addOverlay(WOLayer, "Weighted Overlay");

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

var setupSize = function() {
    var bottomPadding = 10;

    var resize = function(){
        var pane = $('#main');
        var height = $(window).height() - pane.offset().top - bottomPadding;
        pane.css({'height': height +'px'});

        var sidebar = $('#tabBody');
        var height = $(window).height() - sidebar.offset().top - bottomPadding;
        sidebar.css({'height': height +'px'});

        var mapDiv = $('#map');
		var wrapDiv = $('#wrap');
        var height = $(window).height() - mapDiv.offset().top - bottomPadding - wrapDiv.height();
        mapDiv.css({'height': height +'px'});
        map.invalidateSize();
    };
    resize();
    $(window).resize(resize);
};

$(document).ready(function() {
    //setupSize();
});

